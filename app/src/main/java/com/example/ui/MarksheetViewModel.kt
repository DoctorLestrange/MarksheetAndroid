package com.example.ui

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.example.data.MarksheetRepository
import com.example.domain.AppConfig
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
import com.example.domain.ScoreMath
import com.example.domain.ThemeMode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private sealed interface ActiveUndoEntry {
    data class Inserted(val questionNumber: Int) : ActiveUndoEntry
    data class Updated(val previous: QuestionRecord) : ActiveUndoEntry
    data class Deleted(val previous: QuestionRecord) : ActiveUndoEntry
}

@OptIn(ExperimentalCoroutinesApi::class)
class MarksheetViewModel(
    private val repository: MarksheetRepository,
    private val savedStateHandle: SavedStateHandle
) : ViewModel() {

    /**
     * Nullable until DataStore delivers the persisted theme & defaults so cold launch
     * never flashes the wrong theme before preferences finish loading.
     */
    val configState: StateFlow<AppConfig?> = repository.configFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    /**
     * Instant optimistic active-session state updated synchronously on every tap (<1ms)
     * while all SQLite writes drain strictly sequentially via [writeQueue] on Dispatchers.IO.
     */
    private val _activeSession = MutableStateFlow<ExamSession?>(null)
    val activeSession: StateFlow<ExamSession?> = _activeSession.asStateFlow()

    /**
     * Collected lazily ONLY while the user is viewing the History tab so calculator taps
     * never trigger full-database history re-queries or recompositions.
     */
    val historySessions: StateFlow<List<ExamSession>> = repository.historySessionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = emptyList()
        )

    val selectedHistorySessionId: StateFlow<Long?> =
        savedStateHandle.getStateFlow<Long?>(KEY_SELECTED_HISTORY_ID, null)

    val selectedHistorySession: StateFlow<ExamSession?> = selectedHistorySessionId
        .flatMapLatest { id ->
            if (id == null) flowOf(null) else repository.observeSession(id)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(2_000),
            initialValue = null
        )

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    // Strict FIFO persistence queue drained sequentially on Dispatchers.IO
    private val writeQueue = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    // Bounded in-memory undo stack for the active session
    private val activeUndoStack = ArrayDeque<ActiveUndoEntry>()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            val initial = repository.getOrCreateActiveSessionSnapshot()
            _activeSession.value = initial
            for (op in writeQueue) {
                op()
            }
        }
    }

    private fun pushActiveUndo(entry: ActiveUndoEntry) {
        if (activeUndoStack.size >= MAX_UNDO_HISTORY) {
            activeUndoStack.removeFirst()
        }
        activeUndoStack.addLast(entry)
    }

    private fun nextMonotonicTimestamp(session: ExamSession): Long {
        val now = System.currentTimeMillis()
        val maxExisting = session.records.maxOfOrNull { it.recordedAt } ?: 0L
        return maxOf(now, maxExisting + 1L)
    }

    private fun upsertInSortedRecords(
        records: List<QuestionRecord>,
        newRecord: QuestionRecord
    ): List<QuestionRecord> {
        val result = ArrayList<QuestionRecord>(records.size + 1)
        var inserted = false
        for (item in records) {
            when {
                item.questionNumber == newRecord.questionNumber -> {
                    result.add(newRecord)
                    inserted = true
                }
                !inserted && item.questionNumber > newRecord.questionNumber -> {
                    result.add(newRecord)
                    result.add(item)
                    inserted = true
                }
                else -> result.add(item)
            }
        }
        if (!inserted) {
            result.add(newRecord)
        }
        return result
    }

    /**
     * Records the next unanswered question synchronously in memory for instant 0ms response
     * and enqueues the exact ACID SQLite write on Dispatchers.IO.
     */
    fun recordMark(action: MarkAction) {
        val current = _activeSession.value ?: return
        val nextQ = current.summary.nextQuestionNumber ?: return

        val delta = ScoreMath.deltaForAction(action, current.positiveMark, current.negativeMark)
        val now = System.currentTimeMillis()
        val record = QuestionRecord(
            questionNumber = nextQ,
            action = action,
            markDelta = delta,
            recordedAt = nextMonotonicTimestamp(current)
        )

        val updatedSession = current.copy(
            updatedAt = now,
            records = upsertInSortedRecords(current.records, record)
        )
        _activeSession.value = updatedSession
        pushActiveUndo(ActiveUndoEntry.Inserted(nextQ))

        writeQueue.trySend {
            repository.persistUpsertRecord(current.id, record, now)
        }
    }

    fun setQuestionMark(sessionId: Long, questionNumber: Int, action: MarkAction?) {
        val current = _activeSession.value
        if (current != null && current.id == sessionId) {
            if (questionNumber !in 1..current.totalQuestions) return
            val previous = current.recordsByQuestion[questionNumber]
            val now = System.currentTimeMillis()

            if (action == null) {
                if (previous == null) return
                val updatedRecords = current.records.filter { it.questionNumber != questionNumber }
                _activeSession.value = current.copy(updatedAt = now, records = updatedRecords)
                pushActiveUndo(ActiveUndoEntry.Deleted(previous))

                writeQueue.trySend {
                    repository.persistDeleteRecord(sessionId, questionNumber, now)
                }
                return
            }

            val delta = ScoreMath.deltaForAction(action, current.positiveMark, current.negativeMark)
            if (previous != null && previous.action == action && previous.markDelta.compareTo(delta) == 0) {
                return
            }

            val recordedAt = previous?.recordedAt ?: nextMonotonicTimestamp(current)
            val newRecord = QuestionRecord(
                questionNumber = questionNumber,
                action = action,
                markDelta = delta,
                recordedAt = recordedAt
            )
            _activeSession.value = current.copy(
                updatedAt = now,
                records = upsertInSortedRecords(current.records, newRecord)
            )
            if (previous != null) {
                pushActiveUndo(ActiveUndoEntry.Updated(previous))
            } else {
                pushActiveUndo(ActiveUndoEntry.Inserted(questionNumber))
            }

            writeQueue.trySend {
                repository.persistUpsertRecord(sessionId, newRecord, now)
            }
        } else {
            writeQueue.trySend {
                repository.setQuestionMark(sessionId, questionNumber, action)
            }
        }
    }

    fun undoLastMark() {
        val current = _activeSession.value ?: return
        val now = System.currentTimeMillis()

        while (activeUndoStack.isNotEmpty()) {
            when (val entry = activeUndoStack.removeLast()) {
                is ActiveUndoEntry.Inserted -> {
                    if (current.recordsByQuestion.containsKey(entry.questionNumber)) {
                        val updatedRecords = current.records.filter { it.questionNumber != entry.questionNumber }
                        _activeSession.value = current.copy(updatedAt = now, records = updatedRecords)
                        writeQueue.trySend {
                            repository.persistDeleteRecord(current.id, entry.questionNumber, now)
                        }
                        _snackbarEvents.tryEmit("Undid Question ${entry.questionNumber}")
                        return
                    }
                }

                is ActiveUndoEntry.Updated -> {
                    val prev = entry.previous
                    _activeSession.value = current.copy(
                        updatedAt = now,
                        records = upsertInSortedRecords(current.records, prev)
                    )
                    writeQueue.trySend {
                        repository.persistUpsertRecord(current.id, prev, now)
                    }
                    _snackbarEvents.tryEmit("Restored Question ${prev.questionNumber}")
                    return
                }

                is ActiveUndoEntry.Deleted -> {
                    val prev = entry.previous
                    _activeSession.value = current.copy(
                        updatedAt = now,
                        records = upsertInSortedRecords(current.records, prev)
                    )
                    writeQueue.trySend {
                        repository.persistUpsertRecord(current.id, prev, now)
                    }
                    _snackbarEvents.tryEmit("Restored Question ${prev.questionNumber}")
                    return
                }
            }
        }

        // Fallback after app restart when in-memory stack is empty
        val mostRecent = current.records.maxWithOrNull(
            compareBy<QuestionRecord> { it.recordedAt }.thenBy { it.questionNumber }
        ) ?: return

        val updatedRecords = current.records.filter { it.questionNumber != mostRecent.questionNumber }
        _activeSession.value = current.copy(updatedAt = now, records = updatedRecords)
        writeQueue.trySend {
            repository.persistDeleteRecord(current.id, mostRecent.questionNumber, now)
        }
        _snackbarEvents.tryEmit("Undid Question ${mostRecent.questionNumber}")
    }

    fun clearActiveSession() {
        val sessionId = _activeSession.value?.id ?: return
        activeUndoStack.clear()
        writeQueue.trySend {
            val fresh = repository.clearSession(sessionId)
            _activeSession.value = fresh
            _snackbarEvents.tryEmit("Current session cleared")
        }
    }

    fun finishAndSaveSession(customTitle: String) {
        val sessionId = _activeSession.value?.id ?: return
        activeUndoStack.clear()
        writeQueue.trySend {
            val nextActive = repository.finishAndStartNewSession(sessionId, customTitle)
            _activeSession.value = nextActive
            _snackbarEvents.tryEmit("Session saved to history")
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        val current = _activeSession.value
        if (current != null && current.id == sessionId) {
            _activeSession.value = current.copy(title = trimmed, updatedAt = System.currentTimeMillis())
        }
        writeQueue.trySend {
            repository.renameSession(sessionId, trimmed)
            _snackbarEvents.tryEmit("Session renamed")
        }
    }

    fun deleteSession(sessionId: Long) {
        if (selectedHistorySessionId.value == sessionId) {
            savedStateHandle[KEY_SELECTED_HISTORY_ID] = null
        }
        if (_activeSession.value?.id == sessionId) {
            activeUndoStack.clear()
        }
        writeQueue.trySend {
            val active = repository.deleteSession(sessionId)
            _activeSession.value = active
            _snackbarEvents.tryEmit("Session deleted")
        }
    }

    fun selectHistorySession(sessionId: Long?) {
        savedStateHandle[KEY_SELECTED_HISTORY_ID] = sessionId
    }

    fun openSessionInCalculator(sessionId: Long, onOpened: () -> Unit) {
        activeUndoStack.clear()
        writeQueue.trySend {
            val loaded = repository.openSessionInCalculator(sessionId)
            if (loaded != null) {
                _activeSession.value = loaded
            }
            withContext(Dispatchers.Main) {
                savedStateHandle[KEY_SELECTED_HISTORY_ID] = null
                onOpened()
            }
            _snackbarEvents.tryEmit("Loaded session in calculator")
        }
    }

    fun toggleTheme() {
        viewModelScope.launch {
            repository.toggleTheme()
        }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            repository.setThemeMode(mode)
        }
    }

    fun setAmoledBlack(enabled: Boolean) {
        viewModelScope.launch {
            repository.setAmoledBlack(enabled)
        }
    }

    fun setHapticsEnabled(enabled: Boolean) {
        viewModelScope.launch {
            repository.setHapticsEnabled(enabled)
        }
    }

    fun saveMarkingConfiguration(
        positiveMark: String,
        negativeMark: String,
        totalQuestions: Int,
        applyToCurrentIfNonEmpty: Boolean
    ) {
        if (applyToCurrentIfNonEmpty) {
            activeUndoStack.clear()
        }
        writeQueue.trySend {
            val (result, updatedActive) = repository.updateMarkingConfig(
                positiveMark = positiveMark,
                negativeMark = negativeMark,
                totalQuestions = totalQuestions,
                applyToCurrentIfNonEmpty = applyToCurrentIfNonEmpty
            )
            if (updatedActive != null) {
                _activeSession.value = updatedActive
            }
            val msg = if (result.clampedActiveSessionTo != null) {
                "Saved. Active session kept at ${result.clampedActiveSessionTo} Qs to preserve recorded marks."
            } else {
                "Configuration saved"
            }
            _snackbarEvents.tryEmit(msg)
        }
    }

    fun resetDefaults(applyToCurrentIfNonEmpty: Boolean) {
        if (applyToCurrentIfNonEmpty) {
            activeUndoStack.clear()
        }
        writeQueue.trySend {
            val (result, updatedActive) = repository.resetDefaults(applyToCurrentIfNonEmpty)
            if (updatedActive != null) {
                _activeSession.value = updatedActive
            }
            val msg = if (result.clampedActiveSessionTo != null) {
                "Defaults restored. Active session kept at ${result.clampedActiveSessionTo} Qs to preserve recorded marks."
            } else {
                "Defaults restored (+1 / -0.25 / 100 Qs)"
            }
            _snackbarEvents.tryEmit(msg)
        }
    }

    class Factory(
        private val repository: MarksheetRepository
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
            if (modelClass.isAssignableFrom(MarksheetViewModel::class.java)) {
                return MarksheetViewModel(
                    repository = repository,
                    savedStateHandle = extras.createSavedStateHandle()
                ) as T
            }
            throw IllegalArgumentException("Unknown ViewModel class: ${modelClass.name}")
        }
    }

    companion object {
        private const val KEY_SELECTED_HISTORY_ID = "selected_history_session_id"
        private const val MAX_UNDO_HISTORY = 200
    }
}
