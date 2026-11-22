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
import com.example.domain.ThemeMode
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

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

    val activeSession: StateFlow<ExamSession?> = repository.activeSessionFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.Eagerly,
            initialValue = null
        )

    val historySessions: StateFlow<List<ExamSession>> = repository.historySessionsFlow
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5_000),
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
            started = SharingStarted.WhileSubscribed(5_000),
            initialValue = null
        )

    private val _snackbarEvents = MutableSharedFlow<String>(extraBufferCapacity = 4)
    val snackbarEvents: SharedFlow<String> = _snackbarEvents.asSharedFlow()

    init {
        // Self-healing active session guard: guarantees calculator never stalls on null session.
        viewModelScope.launch {
            repository.ensureActiveSession()
            repository.activeSessionFlow.collect { session ->
                if (session == null) {
                    repository.ensureActiveSession()
                }
            }
        }
    }

    fun recordMark(action: MarkAction) {
        viewModelScope.launch {
            val sessionId = activeSession.value?.id ?: repository.ensureActiveSession()
            repository.recordNextQuestion(sessionId, action)
        }
    }

    fun setQuestionMark(sessionId: Long, questionNumber: Int, action: MarkAction?) {
        viewModelScope.launch {
            repository.setQuestionMark(sessionId, questionNumber, action)
        }
    }

    fun undoLastMark() {
        viewModelScope.launch {
            val sessionId = activeSession.value?.id ?: return@launch
            val result = repository.undoLastQuestion(sessionId) ?: return@launch
            val message = if (result.wasRestore) {
                "Restored Question ${result.questionNumber}"
            } else {
                "Undid Question ${result.questionNumber}"
            }
            _snackbarEvents.tryEmit(message)
        }
    }

    fun clearActiveSession() {
        viewModelScope.launch {
            val sessionId = activeSession.value?.id ?: return@launch
            repository.clearSession(sessionId)
            _snackbarEvents.tryEmit("Current session cleared")
        }
    }

    fun finishAndSaveSession(customTitle: String) {
        viewModelScope.launch {
            val sessionId = activeSession.value?.id ?: return@launch
            repository.finishAndStartNewSession(sessionId, customTitle)
            _snackbarEvents.tryEmit("Session saved to history")
        }
    }

    fun renameSession(sessionId: Long, newTitle: String) {
        viewModelScope.launch {
            repository.renameSession(sessionId, newTitle)
            _snackbarEvents.tryEmit("Session renamed")
        }
    }

    fun deleteSession(sessionId: Long) {
        viewModelScope.launch {
            if (selectedHistorySessionId.value == sessionId) {
                savedStateHandle[KEY_SELECTED_HISTORY_ID] = null
            }
            repository.deleteSession(sessionId)
            _snackbarEvents.tryEmit("Session deleted")
        }
    }

    fun selectHistorySession(sessionId: Long?) {
        savedStateHandle[KEY_SELECTED_HISTORY_ID] = sessionId
    }

    fun openSessionInCalculator(sessionId: Long, onOpened: () -> Unit) {
        viewModelScope.launch {
            repository.openSessionInCalculator(sessionId)
            savedStateHandle[KEY_SELECTED_HISTORY_ID] = null
            onOpened()
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
        viewModelScope.launch {
            val result = repository.updateMarkingConfig(
                positiveMark = positiveMark,
                negativeMark = negativeMark,
                totalQuestions = totalQuestions,
                applyToCurrentIfNonEmpty = applyToCurrentIfNonEmpty
            )
            val msg = if (result.clampedActiveSessionTo != null) {
                "Saved. Active session kept at ${result.clampedActiveSessionTo} Qs to preserve recorded marks."
            } else {
                "Configuration saved"
            }
            _snackbarEvents.tryEmit(msg)
        }
    }

    fun resetDefaults(applyToCurrentIfNonEmpty: Boolean) {
        viewModelScope.launch {
            val result = repository.resetDefaults(applyToCurrentIfNonEmpty)
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
    }
}
