package com.example.data

import androidx.room.withTransaction
import com.example.domain.AppConfig
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.ScoreMath
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class UndoResult(
    val questionNumber: Int,
    val wasRestore: Boolean
)

data class ConfigUpdateResult(
    val clampedActiveSessionTo: Int? = null
)

internal sealed interface UndoEntry {
    data class Inserted(val questionNumber: Int) : UndoEntry
    data class Updated(val previous: QuestionRecordEntity) : UndoEntry
    data class Deleted(val previous: QuestionRecordEntity) : UndoEntry
}

class MarksheetRepository(
    private val database: MarksheetDatabase,
    private val dao: MarksheetDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val operationMutex = Mutex()
    private val undoStacks = HashMap<Long, ArrayDeque<UndoEntry>>()

    val configFlow: Flow<AppConfig> = settingsDataStore.configFlow

    val activeSessionFlow: Flow<ExamSession?> = dao.observeActiveSession()
        .map { it?.toDomain() }

    /**
     * History displays all completed sessions plus any active session that has at least
     * one recorded question (clearly badged as In Progress), so no user work is ever hidden.
     */
    val historySessionsFlow: Flow<List<ExamSession>> = dao.observeAllSessions()
        .map { sessions ->
            sessions
                .map { it.toDomain() }
                .filter { it.isCompleted || it.records.isNotEmpty() }
        }

    fun observeSession(sessionId: Long): Flow<ExamSession?> =
        dao.observeSessionById(sessionId).map { it?.toDomain() }

    suspend fun ensureActiveSession(): Long = operationMutex.withLock {
        database.withTransaction {
            val existing = dao.getActiveSessionOnce()
            existing?.id ?: createNewActiveSessionLocked()
        }
    }

    private suspend fun createNewActiveSessionLocked(): Long {
        val config = settingsDataStore.getCurrentConfig()
        val now = System.currentTimeMillis()
        val entity = ExamSessionEntity(
            title = defaultSessionTitle(now),
            positiveMark = config.positiveMark,
            negativeMark = config.negativeMark,
            totalQuestions = config.totalQuestions,
            isCompleted = false,
            createdAt = now,
            updatedAt = now
        )
        return dao.insertSession(entity)
    }

    private suspend fun nextMonotonicTimestamp(sessionId: Long): Long {
        val now = System.currentTimeMillis()
        val maxExisting = dao.getMaxRecordedAt(sessionId) ?: 0L
        return maxOf(now, maxExisting + 1L)
    }

    private fun pushUndo(sessionId: Long, entry: UndoEntry) {
        val stack = undoStacks.getOrPut(sessionId) { ArrayDeque() }
        if (stack.size >= MAX_UNDO_HISTORY) {
            stack.removeFirst()
        }
        stack.addLast(entry)
    }

    private fun clearUndo(sessionId: Long) {
        undoStacks.remove(sessionId)
    }

    /**
     * Records the next unanswered question (lowest Q# in 1..totalQuestions) with the chosen action.
     * Guarded by [operationMutex] and wrapped in an ACID transaction so rapid taps never collide.
     */
    suspend fun recordNextQuestion(sessionId: Long, action: MarkAction): Int? =
        operationMutex.withLock {
            database.withTransaction {
                val session = dao.getSessionOnce(sessionId) ?: return@withTransaction null
                val existingRecords = dao.getRecordsForSessionOnce(sessionId)
                val answeredSet = existingRecords.map { it.questionNumber }.toHashSet()

                var nextQuestion: Int? = null
                for (q in 1..session.totalQuestions) {
                    if (!answeredSet.contains(q)) {
                        nextQuestion = q
                        break
                    }
                }
                if (nextQuestion == null) return@withTransaction null

                val pos = ScoreMath.parsePositiveMark(session.positiveMark) ?: BigDecimal.ONE
                val neg = ScoreMath.parseNegativeMark(session.negativeMark) ?: BigDecimal("0.25")
                val delta = ScoreMath.deltaForAction(action, pos, neg)
                val recordedAt = nextMonotonicTimestamp(sessionId)

                dao.upsertRecord(
                    QuestionRecordEntity(
                        sessionId = sessionId,
                        questionNumber = nextQuestion,
                        action = action.name,
                        markDelta = ScoreMath.formatMark(delta),
                        recordedAt = recordedAt
                    )
                )
                dao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                pushUndo(sessionId, UndoEntry.Inserted(nextQuestion))
                nextQuestion
            }
        }

    /**
     * Updates or clears the recorded mark for a specific question number in a session.
     * Preserves original recordedAt when editing an existing record and records a reversible
     * undo entry so tapping Undo restores the prior state accurately.
     */
    suspend fun setQuestionMark(
        sessionId: Long,
        questionNumber: Int,
        action: MarkAction?
    ) = operationMutex.withLock {
        database.withTransaction {
            val session = dao.getSessionOnce(sessionId) ?: return@withTransaction
            if (questionNumber !in 1..session.totalQuestions) return@withTransaction

            val previous = dao.getRecordOnce(sessionId, questionNumber)
            val now = System.currentTimeMillis()

            if (action == null) {
                if (previous != null) {
                    dao.deleteRecord(sessionId, questionNumber)
                    dao.updateSession(session.copy(updatedAt = now))
                    pushUndo(sessionId, UndoEntry.Deleted(previous))
                }
                return@withTransaction
            }

            val pos = ScoreMath.parsePositiveMark(session.positiveMark) ?: BigDecimal.ONE
            val neg = ScoreMath.parseNegativeMark(session.negativeMark) ?: BigDecimal("0.25")
            val delta = ScoreMath.deltaForAction(action, pos, neg)
            val deltaStr = ScoreMath.formatMark(delta)

            // Skip redundant write if nothing actually changed
            if (previous != null && previous.action == action.name && previous.markDelta == deltaStr) {
                return@withTransaction
            }

            val recordedAt = previous?.recordedAt ?: nextMonotonicTimestamp(sessionId)
            dao.upsertRecord(
                QuestionRecordEntity(
                    sessionId = sessionId,
                    questionNumber = questionNumber,
                    action = action.name,
                    markDelta = deltaStr,
                    recordedAt = recordedAt
                )
            )
            dao.updateSession(session.copy(updatedAt = now))
            if (previous != null) {
                pushUndo(sessionId, UndoEntry.Updated(previous))
            } else {
                pushUndo(sessionId, UndoEntry.Inserted(questionNumber))
            }
        }
    }

    /**
     * Reverts the most recent change in the session:
     * - If an in-memory undo stack entry exists, reverses inserts, edits, and clears accurately.
     * - After process restart, falls back cleanly to removing the most recently recorded mark.
     */
    suspend fun undoLastQuestion(sessionId: Long): UndoResult? = operationMutex.withLock {
        database.withTransaction {
            val session = dao.getSessionOnce(sessionId) ?: return@withTransaction null
            val stack = undoStacks[sessionId]

            while (stack != null && stack.isNotEmpty()) {
                when (val entry = stack.removeLast()) {
                    is UndoEntry.Inserted -> {
                        val existing = dao.getRecordOnce(sessionId, entry.questionNumber)
                        if (existing != null) {
                            dao.deleteRecord(sessionId, entry.questionNumber)
                            dao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                            return@withTransaction UndoResult(
                                questionNumber = entry.questionNumber,
                                wasRestore = false
                            )
                        }
                    }

                    is UndoEntry.Updated -> {
                        dao.upsertRecord(entry.previous)
                        dao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                        return@withTransaction UndoResult(
                            questionNumber = entry.previous.questionNumber,
                            wasRestore = true
                        )
                    }

                    is UndoEntry.Deleted -> {
                        dao.upsertRecord(entry.previous)
                        dao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
                        return@withTransaction UndoResult(
                            questionNumber = entry.previous.questionNumber,
                            wasRestore = true
                        )
                    }
                }
            }

            val mostRecent = dao.getMostRecentRecord(sessionId) ?: return@withTransaction null
            dao.deleteRecord(sessionId, mostRecent.questionNumber)
            dao.updateSession(session.copy(updatedAt = System.currentTimeMillis()))
            UndoResult(
                questionNumber = mostRecent.questionNumber,
                wasRestore = false
            )
        }
    }

    /**
     * Atomically clears all recorded marks from the active session and syncs it to latest defaults.
     */
    suspend fun clearSession(sessionId: Long) = operationMutex.withLock {
        val config = settingsDataStore.getCurrentConfig()
        database.withTransaction {
            val session = dao.getSessionOnce(sessionId) ?: return@withTransaction
            val now = System.currentTimeMillis()
            dao.deleteAllRecordsForSession(sessionId)
            dao.updateSession(
                session.copy(
                    title = defaultSessionTitle(now),
                    positiveMark = config.positiveMark,
                    negativeMark = config.negativeMark,
                    totalQuestions = config.totalQuestions,
                    updatedAt = now
                )
            )
            clearUndo(sessionId)
        }
    }

    /**
     * Atomically marks the session as completed with the chosen title and starts a fresh active session.
     */
    suspend fun finishAndStartNewSession(sessionId: Long, customTitle: String): Long =
        operationMutex.withLock {
            database.withTransaction {
                val session = dao.getSessionOnce(sessionId)
                val now = System.currentTimeMillis()
                if (session != null) {
                    val finalTitle = customTitle.trim().ifEmpty { session.title }
                    dao.updateSession(
                        session.copy(
                            title = finalTitle,
                            isCompleted = true,
                            updatedAt = now
                        )
                    )
                    clearUndo(sessionId)
                }
                createNewActiveSessionLocked()
            }
        }

    suspend fun renameSession(sessionId: Long, newTitle: String) = operationMutex.withLock {
        val trimmed = newTitle.trim()
        if (trimmed.isEmpty()) return
        database.withTransaction {
            val session = dao.getSessionOnce(sessionId) ?: return@withTransaction
            if (session.title == trimmed) return@withTransaction
            dao.updateSession(
                session.copy(
                    title = trimmed,
                    updatedAt = System.currentTimeMillis()
                )
            )
        }
    }

    /**
     * Atomically reopens a saved history session in the calculator.
     * If already active, performs no unnecessary write.
     */
    suspend fun openSessionInCalculator(targetSessionId: Long) = operationMutex.withLock {
        database.withTransaction {
            val target = dao.getSessionOnce(targetSessionId) ?: return@withTransaction
            val currentActive = dao.getActiveSessionOnce()
            if (currentActive != null && currentActive.id == targetSessionId && !target.isCompleted) {
                return@withTransaction
            }

            val now = System.currentTimeMillis()
            if (currentActive != null && currentActive.id != targetSessionId) {
                val count = dao.getRecordCount(currentActive.id)
                if (count == 0) {
                    dao.deleteSessionById(currentActive.id)
                } else {
                    dao.updateSession(currentActive.copy(isCompleted = true, updatedAt = now))
                }
                clearUndo(currentActive.id)
            }
            dao.updateSession(target.copy(isCompleted = false, updatedAt = now))
        }
    }

    suspend fun deleteSession(sessionId: Long) = operationMutex.withLock {
        database.withTransaction {
            val target = dao.getSessionOnce(sessionId) ?: return@withTransaction
            dao.deleteSessionById(sessionId)
            clearUndo(sessionId)
            if (!target.isCompleted && dao.getActiveSessionOnce() == null) {
                createNewActiveSessionLocked()
            }
        }
    }

    /**
     * Saves new marking scheme defaults to DataStore and atomically updates the active session
     * if empty or if [applyToCurrentIfNonEmpty] is true. Returns clamping info if totalQuestions
     * had to be kept higher to preserve already-recorded question numbers.
     */
    suspend fun updateMarkingConfig(
        positiveMark: String,
        negativeMark: String,
        totalQuestions: Int,
        applyToCurrentIfNonEmpty: Boolean
    ): ConfigUpdateResult = operationMutex.withLock {
        val validPos = ScoreMath.parsePositiveMark(positiveMark) ?: return ConfigUpdateResult()
        val validNeg = ScoreMath.parseNegativeMark(negativeMark) ?: return ConfigUpdateResult()
        val validTotal = totalQuestions.coerceIn(1, 1000)
        val posStr = ScoreMath.formatMark(validPos)
        val negStr = ScoreMath.formatMark(validNeg)

        settingsDataStore.updateMarkingScheme(posStr, negStr, validTotal)

        database.withTransaction {
            val active = dao.getActiveSessionOnce() ?: return@withTransaction ConfigUpdateResult()
            val existingRecords = dao.getRecordsForSessionOnce(active.id)

            if (existingRecords.isEmpty()) {
                dao.updateSession(
                    active.copy(
                        positiveMark = posStr,
                        negativeMark = negStr,
                        totalQuestions = validTotal,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                ConfigUpdateResult()
            } else if (applyToCurrentIfNonEmpty) {
                val maxRecordedQ = existingRecords.maxOfOrNull { it.questionNumber } ?: 1
                val safeTotal = maxOf(validTotal, maxRecordedQ)
                val recalculated = existingRecords.map { record ->
                    val action = runCatching { MarkAction.valueOf(record.action) }
                        .getOrDefault(MarkAction.SKIPPED)
                    val newDelta = ScoreMath.deltaForAction(action, validPos, validNeg)
                    record.copy(markDelta = ScoreMath.formatMark(newDelta))
                }
                dao.upsertRecords(recalculated)
                dao.updateSession(
                    active.copy(
                        positiveMark = posStr,
                        negativeMark = negStr,
                        totalQuestions = safeTotal,
                        updatedAt = System.currentTimeMillis()
                    )
                )
                // Undo snapshots held old mark deltas; clear them so old deltas can't be restored.
                clearUndo(active.id)
                ConfigUpdateResult(
                    clampedActiveSessionTo = if (safeTotal > validTotal) safeTotal else null
                )
            } else {
                ConfigUpdateResult()
            }
        }
    }

    suspend fun toggleTheme() {
        val current = settingsDataStore.getCurrentConfig().themeMode
        val next = if (current == ThemeMode.LIGHT) ThemeMode.DARK else ThemeMode.LIGHT
        settingsDataStore.setThemeMode(next)
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        settingsDataStore.setThemeMode(mode)
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        settingsDataStore.setAmoledBlack(enabled)
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        settingsDataStore.setHapticsEnabled(enabled)
    }

    suspend fun resetDefaults(applyToCurrentIfNonEmpty: Boolean): ConfigUpdateResult {
        return updateMarkingConfig(
            positiveMark = AppConfig.DEFAULT_POSITIVE_MARK,
            negativeMark = AppConfig.DEFAULT_NEGATIVE_MARK,
            totalQuestions = AppConfig.DEFAULT_TOTAL_QUESTIONS,
            applyToCurrentIfNonEmpty = applyToCurrentIfNonEmpty
        )
    }

    private fun defaultSessionTitle(timestamp: Long): String {
        val formatter = SimpleDateFormat("MMM d, h:mm a", Locale.getDefault())
        return "Session • ${formatter.format(Date(timestamp))}"
    }

    companion object {
        private const val MAX_UNDO_HISTORY = 200
    }
}
