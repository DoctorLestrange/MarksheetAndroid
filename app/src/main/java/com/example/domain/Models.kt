package com.example.domain

import java.math.BigDecimal

enum class MarkAction {
    CORRECT,
    WRONG,
    SKIPPED
}

enum class ThemeMode {
    LIGHT,
    DARK
}

data class AppConfig(
    val positiveMark: String = DEFAULT_POSITIVE_MARK,
    val negativeMark: String = DEFAULT_NEGATIVE_MARK,
    val totalQuestions: Int = DEFAULT_TOTAL_QUESTIONS,
    val themeMode: ThemeMode = ThemeMode.LIGHT,
    val amoledBlack: Boolean = false,
    val hapticsEnabled: Boolean = true
) {
    val positiveDecimal: BigDecimal
        get() = ScoreMath.parsePositiveMark(positiveMark) ?: BigDecimal(DEFAULT_POSITIVE_MARK)

    val negativeDecimal: BigDecimal
        get() = ScoreMath.parseNegativeMark(negativeMark) ?: BigDecimal(DEFAULT_NEGATIVE_MARK)

    companion object {
        const val DEFAULT_POSITIVE_MARK = "1"
        const val DEFAULT_NEGATIVE_MARK = "0.25"
        const val DEFAULT_TOTAL_QUESTIONS = 100
    }
}

data class QuestionRecord(
    val questionNumber: Int,
    val action: MarkAction,
    val markDelta: BigDecimal,
    val recordedAt: Long
)

data class SessionSummary(
    val totalScore: BigDecimal,
    val maxScore: BigDecimal,
    val positiveTotal: BigDecimal,
    val negativeTotal: BigDecimal,
    val correctCount: Int,
    val wrongCount: Int,
    val skippedCount: Int,
    val answeredCount: Int,
    val totalQuestions: Int,
    val scorePercent: BigDecimal,
    val accuracyPercent: BigDecimal?,
    val nextQuestionNumber: Int?
) {
    val isComplete: Boolean
        get() = answeredCount >= totalQuestions

    val progressFraction: Float
        get() = if (totalQuestions <= 0) 0f else (answeredCount.toFloat() / totalQuestions.toFloat()).coerceIn(0f, 1f)
}

data class ExamSession(
    val id: Long,
    val title: String,
    val positiveMark: BigDecimal,
    val negativeMark: BigDecimal,
    val totalQuestions: Int,
    val isCompleted: Boolean,
    val createdAt: Long,
    val updatedAt: Long,
    val records: List<QuestionRecord>
) {
    val summary: SessionSummary = ScoreMath.computeSummary(
        positiveMark = positiveMark,
        negativeMark = negativeMark,
        totalQuestions = totalQuestions,
        records = records
    )

    val recordsByQuestion: Map<Int, QuestionRecord> = records.associateBy { it.questionNumber }
}

data class ExamPreset(
    val name: String,
    val subtitle: String,
    val positiveMark: String,
    val negativeMark: String,
    val totalQuestions: Int
)

val DefaultExamPresets = listOf(
    ExamPreset("Standard", "+1 / -0.25 • 100 Qs", "1", "0.25", 100),
    ExamPreset("JEE / NEET", "+4 / -1 • 90 Qs", "4", "1", 90),
    ExamPreset("NEET Full", "+4 / -1 • 180 Qs", "4", "1", 180),
    ExamPreset("UPSC Prelims", "+2 / -0.66 • 100 Qs", "2", "0.66", 100),
    ExamPreset("SSC / Banking", "+2 / -0.5 • 100 Qs", "2", "0.5", 100),
    ExamPreset("No Negative", "+1 / 0 • 100 Qs", "1", "0", 100)
)
