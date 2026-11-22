package com.example.data

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
import com.example.domain.ScoreMath
import java.math.BigDecimal

@Entity(tableName = "exam_sessions")
data class ExamSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val positiveMark: String,
    val negativeMark: String,
    val totalQuestions: Int,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(
    tableName = "question_records",
    primaryKeys = ["sessionId", "questionNumber"],
    foreignKeys = [
        ForeignKey(
            entity = ExamSessionEntity::class,
            parentColumns = ["id"],
            childColumns = ["sessionId"],
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index(value = ["sessionId"])]
)
data class QuestionRecordEntity(
    val sessionId: Long,
    val questionNumber: Int,
    val action: String,
    val markDelta: String,
    val recordedAt: Long
)

data class SessionWithRecords(
    @Embedded val session: ExamSessionEntity,
    @Relation(
        parentColumn = "id",
        entityColumn = "sessionId"
    )
    val records: List<QuestionRecordEntity>
)

fun SessionWithRecords.toDomain(): ExamSession {
    val positive = ScoreMath.parsePositiveMark(session.positiveMark) ?: BigDecimal.ONE
    val negative = ScoreMath.parseNegativeMark(session.negativeMark) ?: BigDecimal("0.25")
    val sortedRecords = records
        .sortedBy { it.questionNumber }
        .map { record ->
            val action = runCatching { MarkAction.valueOf(record.action) }
                .getOrDefault(MarkAction.SKIPPED)
            val delta = record.markDelta.toBigDecimalOrNull()
                ?: ScoreMath.deltaForAction(action, positive, negative)
            QuestionRecord(
                questionNumber = record.questionNumber,
                action = action,
                markDelta = ScoreMath.normalize(delta),
                recordedAt = record.recordedAt
            )
        }

    return ExamSession(
        id = session.id,
        title = session.title,
        positiveMark = positive,
        negativeMark = negative,
        totalQuestions = session.totalQuestions.coerceAtLeast(1),
        isCompleted = session.isCompleted,
        createdAt = session.createdAt,
        updatedAt = session.updatedAt,
        records = sortedRecords
    )
}
