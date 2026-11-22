package com.example.domain

import java.math.BigDecimal
import java.math.RoundingMode

object ScoreMath {
    private val MAX_MARK = BigDecimal("1000")
    private val HUNDRED = BigDecimal("100")

    fun parsePositiveMark(raw: String): BigDecimal? {
        val trimmed = raw.trim().replace(",", ".")
        if (trimmed.isEmpty()) return null
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        if (parsed.compareTo(BigDecimal.ZERO) <= 0) return null
        if (parsed.compareTo(MAX_MARK) > 0) return null
        val normalized = normalize(parsed)
        if (normalized.scale() > 4) return null
        return normalized
    }

    /**
     * Parses a negative deduction magnitude. Accepts either "0.25" or "-0.25"
     * and normalizes to a non-negative magnitude so deductions never suffer
     * from double-negation bugs.
     */
    fun parseNegativeMark(raw: String): BigDecimal? {
        val trimmed = raw.trim().replace(",", ".")
        if (trimmed.isEmpty()) return null
        val parsed = trimmed.toBigDecimalOrNull() ?: return null
        val magnitude = parsed.abs()
        if (magnitude.compareTo(MAX_MARK) > 0) return null
        val normalized = normalize(magnitude)
        if (normalized.scale() > 4) return null
        return normalized
    }

    fun parseTotalQuestions(raw: String): Int? {
        val trimmed = raw.trim()
        val parsed = trimmed.toIntOrNull() ?: return null
        return if (parsed in 1..1000) parsed else null
    }

    fun normalize(value: BigDecimal): BigDecimal {
        if (value.compareTo(BigDecimal.ZERO) == 0) return BigDecimal.ZERO
        val stripped = value.stripTrailingZeros()
        return if (stripped.scale() < 0) stripped.setScale(0) else stripped
    }

    fun deltaForAction(
        action: MarkAction,
        positiveMark: BigDecimal,
        negativeMark: BigDecimal
    ): BigDecimal = when (action) {
        MarkAction.CORRECT -> normalize(positiveMark)
        MarkAction.WRONG -> normalize(negativeMark.abs().negate())
        MarkAction.SKIPPED -> BigDecimal.ZERO
    }

    fun formatMark(value: BigDecimal, showPositiveSign: Boolean = false): String {
        val normalized = normalize(value)
        val cmp = normalized.compareTo(BigDecimal.ZERO)
        if (cmp == 0) return "0"
        val plain = normalized.toPlainString()
        return if (cmp > 0 && showPositiveSign) "+$plain" else plain
    }

    fun formatDeduction(negativeMagnitude: BigDecimal): String {
        val normalized = normalize(negativeMagnitude.abs())
        return if (normalized.compareTo(BigDecimal.ZERO) == 0) {
            "0"
        } else {
            "-${normalized.toPlainString()}"
        }
    }

    fun formatPercent(percent: BigDecimal): String {
        val scaled = percent.setScale(1, RoundingMode.HALF_UP)
        return "${formatMark(scaled)}%"
    }

    fun computeSummary(
        positiveMark: BigDecimal,
        negativeMark: BigDecimal,
        totalQuestions: Int,
        records: List<QuestionRecord>
    ): SessionSummary {
        var totalScore = BigDecimal.ZERO
        var positiveTotal = BigDecimal.ZERO
        var negativeTotal = BigDecimal.ZERO
        var correctCount = 0
        var wrongCount = 0
        var skippedCount = 0

        val answeredNumbers = HashSet<Int>(records.size)
        for (record in records) {
            answeredNumbers.add(record.questionNumber)
            totalScore = totalScore.add(record.markDelta)
            when (record.action) {
                MarkAction.CORRECT -> {
                    correctCount++
                    positiveTotal = positiveTotal.add(record.markDelta)
                }
                MarkAction.WRONG -> {
                    wrongCount++
                    negativeTotal = negativeTotal.add(record.markDelta)
                }
                MarkAction.SKIPPED -> {
                    skippedCount++
                }
            }
        }

        val safeTotalQuestions = totalQuestions.coerceAtLeast(1)
        val maxScore = normalize(positiveMark.multiply(BigDecimal(safeTotalQuestions)))
        val normTotalScore = normalize(totalScore)

        val scorePercent = if (maxScore.compareTo(BigDecimal.ZERO) > 0) {
            normTotalScore.multiply(HUNDRED).divide(maxScore, 2, RoundingMode.HALF_UP)
        } else {
            BigDecimal.ZERO
        }

        val attempted = correctCount + wrongCount
        val accuracyPercent = if (attempted > 0) {
            BigDecimal(correctCount)
                .multiply(HUNDRED)
                .divide(BigDecimal(attempted), 2, RoundingMode.HALF_UP)
        } else {
            null
        }

        var nextQuestion: Int? = null
        for (q in 1..safeTotalQuestions) {
            if (!answeredNumbers.contains(q)) {
                nextQuestion = q
                break
            }
        }

        return SessionSummary(
            totalScore = normTotalScore,
            maxScore = maxScore,
            positiveTotal = normalize(positiveTotal),
            negativeTotal = normalize(negativeTotal),
            correctCount = correctCount,
            wrongCount = wrongCount,
            skippedCount = skippedCount,
            answeredCount = answeredNumbers.size,
            totalQuestions = safeTotalQuestions,
            scorePercent = normalize(scorePercent),
            accuracyPercent = accuracyPercent?.let { normalize(it) },
            nextQuestionNumber = nextQuestion
        )
    }
}
