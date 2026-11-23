package com.example.data

import androidx.room.withTransaction
import com.example.domain.AppConfig
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
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

data class ConfigUpdateResult(
    val clampedActiveSessionTo: Int? = null
)

class MarksheetRepository(
    private val database: MarksheetDatabase,
    private val dao: MarksheetDao,
    private val settingsDataStore: SettingsDataStore
) {
    private val operationMutex = Mutex()

    val configFlow: Flow<AppConfig> = settingsDataStore.configFlow

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

    /**
     * Loads the current active session with all its records, creating one atomically if none exists.
     */
    suspend fun getOrCreateActiveSessionSnapshot(): ExamSession = operationMutex.withLock {
        database.withTransaction {
            val existing = dao.getActiveSessionWithRecordsOnce()
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            val newId = createNewActiveSessionLocked()
            val created = dao.getSessionOnce(newId)!!
            SessionWithRecords(created, emptyList()).toDomain()
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

    /**
     * Fast, single-transaction persistence of a single question record upsert + session timestamp touch.
     * Avoids redundant SELECT * queries on every calculator tap.
     */
    suspend fun persistUpsertRecord(
        sessionId: Long,
        record: QuestionRecord,
        sessionUpdatedAt: Long
    ) = operationMutex.withLock {
        database.withTransaction {
            dao.upsertRecord(
                QuestionRecordEntity(
                    sessionId = sessionId,
                    questionNumber = record.questionNumber,
                    action = record.action.name,
                    markDelta = ScoreMath.formatMark(record.markDelta),
                    recordedAt = record.recordedAt
                )
            )
            dao.touchSession(sessionId, sessionUpdatedAt)
        }
    }

    /**
     * Fast, single-transaction deletion of a single question record + session timestamp touch.
     */
    suspend fun persistDeleteRecord(
        sessionId: Long,
        questionNumber: Int,
        sessionUpdatedAt: Long
    ) = operationMutex.withLock {
        database.withTransaction {
            dao.deleteRecord(sessionId, questionNumber)
            dao.touchSession(sessionId, sessionUpdatedAt)
        }
    }

    /**
     * Updates or clears a question mark on any session (used when editing historical sessions).
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
                    dao.touchSession(sessionId, now)
                }
                return@withTransaction
            }

            val pos = ScoreMath.parsePositiveMark(session.positiveMark) ?: BigDecimal.ONE
            val neg = ScoreMath.parseNegativeMark(session.negativeMark) ?: BigDecimal("0.25")
            val delta = ScoreMath.deltaForAction(action, pos, neg)
            val deltaStr = ScoreMath.formatMark(delta)

            if (previous != null && previous.action == action.name && previous.markDelta == deltaStr) {
                return@withTransaction
            }

            val maxExisting = dao.getMaxRecordedAt(sessionId) ?: 0L
            val recordedAt = previous?.recordedAt ?: maxOf(now, maxExisting + 1L)
            dao.upsertRecord(
                QuestionRecordEntity(
                    sessionId = sessionId,
                    questionNumber = questionNumber,
                    action = action.name,
                    markDelta = deltaStr,
                    recordedAt = recordedAt
                )
            )
            dao.touchSession(sessionId, now)
        }
    }

    /**
     * Atomically clears all recorded marks from the active session and syncs it to latest defaults.
     */
    suspend fun clearSession(sessionId: Long): ExamSession = operationMutex.withLock {
        val config = settingsDataStore.getCurrentConfig()
        database.withTransaction {
            val session = dao.getSessionOnce(sessionId)
            val now = System.currentTimeMillis()
            if (session != null) {
                dao.deleteAllRecordsForSession(sessionId)
                val updated = session.copy(
                    title = defaultSessionTitle(now),
                    positiveMark = config.positiveMark,
                    negativeMark = config.negativeMark,
                    totalQuestions = config.totalQuestions,
                    updatedAt = now
                )
                dao.updateSession(updated)
                return@withTransaction SessionWithRecords(updated, emptyList()).toDomain()
            }
            val newId = createNewActiveSessionLocked()
            val created = dao.getSessionOnce(newId)!!
            SessionWithRecords(created, emptyList()).toDomain()
        }
    }

    /**
     * Atomically marks the session as completed with the chosen title and returns a fresh active session.
     */
    suspend fun finishAndStartNewSession(sessionId: Long, customTitle: String): ExamSession =
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
                }
                val newId = createNewActiveSessionLocked()
                val created = dao.getSessionOnce(newId)!!
                SessionWithRecords(created, emptyList()).toDomain()
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
     * Atomically reopens a saved history session in the calculator and returns its full snapshot.
     */
    suspend fun openSessionInCalculator(targetSessionId: Long): ExamSession? = operationMutex.withLock {
        database.withTransaction {
            val target = dao.getSessionOnce(targetSessionId) ?: return@withTransaction null
            val currentActive = dao.getActiveSessionOnce()
            if (currentActive != null && currentActive.id == targetSessionId && !target.isCompleted) {
                return@withTransaction dao.getActiveSessionWithRecordsOnce()?.toDomain()
            }

            val now = System.currentTimeMillis()
            if (currentActive != null && currentActive.id != targetSessionId) {
                val count = dao.getRecordCount(currentActive.id)
                if (count == 0) {
                    dao.deleteSessionById(currentActive.id)
                } else {
                    dao.updateSession(currentActive.copy(isCompleted = true, updatedAt = now))
                }
            }
            dao.updateSession(target.copy(isCompleted = false, updatedAt = now))
            dao.getActiveSessionWithRecordsOnce()?.toDomain()
        }
    }

    suspend fun deleteSession(sessionId: Long): ExamSession = operationMutex.withLock {
        database.withTransaction {
            dao.deleteSessionById(sessionId)
            val existing = dao.getActiveSessionWithRecordsOnce()
            if (existing != null) {
                return@withTransaction existing.toDomain()
            }
            val newId = createNewActiveSessionLocked()
            val created = dao.getSessionOnce(newId)!!
            SessionWithRecords(created, emptyList()).toDomain()
        }
    }

    /**
     * Saves new marking scheme defaults to DataStore and atomically updates the active session
     * if empty or if [applyToCurrentIfNonEmpty] is true.
     */
    suspend fun updateMarkingConfig(
        positiveMark: String,
        negativeMark: String,
        totalQuestions: Int,
        applyToCurrentIfNonEmpty: Boolean
    ): Pair<ConfigUpdateResult, ExamSession?> = operationMutex.withLock {
        val validPos = ScoreMath.parsePositiveMark(positiveMark)
            ?: return ConfigUpdateResult() to null
        val validNeg = ScoreMath.parseNegativeMark(negativeMark)
            ?: return ConfigUpdateResult() to null
        val validTotal = totalQuestions.coerceIn(1, 1000)
        val posStr = ScoreMath.formatMark(validPos)
        val negStr = ScoreMath.formatMark(validNeg)

        settingsDataStore.updateMarkingScheme(posStr, negStr, validTotal)

        database.withTransaction {
            val active = dao.getActiveSessionOnce()
                ?: return@withTransaction ConfigUpdateResult() to null
            val existingRecords = dao.getRecordsForSessionOnce(active.id)

            val result = if (existingRecords.isEmpty()) {
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
                ConfigUpdateResult(
                    clampedActiveSessionTo = if (safeTotal > validTotal) safeTotal else null
                )
            } else {
                ConfigUpdateResult()
            }

            val updatedSnapshot = dao.getActiveSessionWithRecordsOnce()?.toDomain()
            result to updatedSnapshot
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

    suspend fun resetDefaults(applyToCurrentIfNonEmpty: Boolean): Pair<ConfigUpdateResult, ExamSession?> {
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
}
