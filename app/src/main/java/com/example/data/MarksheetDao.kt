package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MarksheetDao {

    @Transaction
    @Query("SELECT * FROM exam_sessions WHERE isCompleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    fun observeActiveSession(): Flow<SessionWithRecords?>

    @Transaction
    @Query("SELECT * FROM exam_sessions ORDER BY updatedAt DESC")
    fun observeAllSessions(): Flow<List<SessionWithRecords>>

    @Transaction
    @Query("SELECT * FROM exam_sessions WHERE id = :sessionId")
    fun observeSessionById(sessionId: Long): Flow<SessionWithRecords?>

    @Query("SELECT * FROM exam_sessions WHERE isCompleted = 0 ORDER BY updatedAt DESC LIMIT 1")
    suspend fun getActiveSessionOnce(): ExamSessionEntity?

    @Query("SELECT * FROM exam_sessions WHERE id = :sessionId")
    suspend fun getSessionOnce(sessionId: Long): ExamSessionEntity?

    @Query("SELECT * FROM question_records WHERE sessionId = :sessionId ORDER BY questionNumber ASC")
    suspend fun getRecordsForSessionOnce(sessionId: Long): List<QuestionRecordEntity>

    @Query("SELECT * FROM question_records WHERE sessionId = :sessionId AND questionNumber = :questionNumber LIMIT 1")
    suspend fun getRecordOnce(sessionId: Long, questionNumber: Int): QuestionRecordEntity?

    @Query("SELECT MAX(recordedAt) FROM question_records WHERE sessionId = :sessionId")
    suspend fun getMaxRecordedAt(sessionId: Long): Long?

    @Query("SELECT COUNT(*) FROM question_records WHERE sessionId = :sessionId")
    suspend fun getRecordCount(sessionId: Long): Int

    @Query(
        "SELECT * FROM question_records WHERE sessionId = :sessionId " +
            "ORDER BY recordedAt DESC, questionNumber DESC LIMIT 1"
    )
    suspend fun getMostRecentRecord(sessionId: Long): QuestionRecordEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ExamSessionEntity): Long

    @Update
    suspend fun updateSession(session: ExamSessionEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecord(record: QuestionRecordEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertRecords(records: List<QuestionRecordEntity>)

    @Query("DELETE FROM question_records WHERE sessionId = :sessionId AND questionNumber = :questionNumber")
    suspend fun deleteRecord(sessionId: Long, questionNumber: Int)

    @Query("DELETE FROM question_records WHERE sessionId = :sessionId")
    suspend fun deleteAllRecordsForSession(sessionId: Long)

    @Query("DELETE FROM exam_sessions WHERE id = :sessionId")
    suspend fun deleteSessionById(sessionId: Long)

    @Query("UPDATE exam_sessions SET isCompleted = 1, updatedAt = :timestamp WHERE isCompleted = 0")
    suspend fun markAllActiveCompleted(timestamp: Long)
}
