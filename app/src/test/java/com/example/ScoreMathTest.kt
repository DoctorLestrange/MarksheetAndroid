package com.example

import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
import com.example.domain.ScoreMath
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.math.BigDecimal

class ScoreMathTest {

    @Test
    fun parsePositiveMark_acceptsValidAndRejectsInvalid() {
        assertEquals(BigDecimal("1"), ScoreMath.parsePositiveMark("1"))
        assertEquals(BigDecimal("2.5"), ScoreMath.parsePositiveMark("2.50"))
        assertEquals(BigDecimal("0.25"), ScoreMath.parsePositiveMark(".25"))
        assertEquals(BigDecimal("1.5"), ScoreMath.parsePositiveMark("1,5"))
        assertEquals(BigDecimal("0.3333"), ScoreMath.parsePositiveMark("0.3333"))
        assertEquals(BigDecimal("1000"), ScoreMath.parsePositiveMark("1000"))

        assertNull(ScoreMath.parsePositiveMark(""))
        assertNull(ScoreMath.parsePositiveMark("0"))
        assertNull(ScoreMath.parsePositiveMark("-1"))
        assertNull(ScoreMath.parsePositiveMark("1000.0001"))
        assertNull(ScoreMath.parsePositiveMark("0.12345"))
        assertNull(ScoreMath.parsePositiveMark("abc"))
    }

    @Test
    fun parseNegativeMark_normalizesSignedAndUnsignedInput() {
        assertEquals(BigDecimal("0.25"), ScoreMath.parseNegativeMark("0.25"))
        assertEquals(BigDecimal("0.25"), ScoreMath.parseNegativeMark("-0.25"))
        assertEquals(BigDecimal("0.33"), ScoreMath.parseNegativeMark("0,33"))
        assertEquals(BigDecimal.ZERO, ScoreMath.parseNegativeMark("0"))
        assertEquals(BigDecimal.ZERO, ScoreMath.parseNegativeMark("-0.00"))
        assertNull(ScoreMath.parseNegativeMark("1001"))
        assertNull(ScoreMath.parseNegativeMark("0.12345"))
    }

    @Test
    fun parseTotalQuestions_enforcesBounds() {
        assertEquals(1, ScoreMath.parseTotalQuestions("1"))
        assertEquals(100, ScoreMath.parseTotalQuestions("100"))
        assertEquals(1000, ScoreMath.parseTotalQuestions("1000"))
        assertNull(ScoreMath.parseTotalQuestions("0"))
        assertNull(ScoreMath.parseTotalQuestions("-10"))
        assertNull(ScoreMath.parseTotalQuestions("1001"))
        assertNull(ScoreMath.parseTotalQuestions("10.5"))
    }

    @Test
    fun bigDecimalArithmetic_avoidsFloatingPointDrift() {
        val pos = BigDecimal("1")
        val neg = BigDecimal("0.33")
        val records = listOf(
            QuestionRecord(1, MarkAction.CORRECT, ScoreMath.deltaForAction(MarkAction.CORRECT, pos, neg), 1L),
            QuestionRecord(2, MarkAction.WRONG, ScoreMath.deltaForAction(MarkAction.WRONG, pos, neg), 2L),
            QuestionRecord(3, MarkAction.WRONG, ScoreMath.deltaForAction(MarkAction.WRONG, pos, neg), 3L),
            QuestionRecord(4, MarkAction.WRONG, ScoreMath.deltaForAction(MarkAction.WRONG, pos, neg), 4L),
            QuestionRecord(5, MarkAction.SKIPPED, ScoreMath.deltaForAction(MarkAction.SKIPPED, pos, neg), 5L)
        )

        val summary = ScoreMath.computeSummary(pos, neg, 100, records)
        assertEquals("0.01", ScoreMath.formatMark(summary.totalScore))
        assertEquals("100", ScoreMath.formatMark(summary.maxScore))
        assertEquals(1, summary.correctCount)
        assertEquals(3, summary.wrongCount)
        assertEquals(1, summary.skippedCount)
        assertEquals(5, summary.answeredCount)
        assertEquals(6, summary.nextQuestionNumber)
        assertEquals("25%", ScoreMath.formatPercent(summary.accuracyPercent!!))
    }

    @Test
    fun negativeTotalScoreAndZeroNegativeMark_formatCleanly() {
        val pos = BigDecimal("1")
        val neg = BigDecimal("0.25")
        val allWrong = listOf(
            QuestionRecord(1, MarkAction.WRONG, BigDecimal("-0.25"), 1L),
            QuestionRecord(2, MarkAction.WRONG, BigDecimal("-0.25"), 2L)
        )
        val negSummary = ScoreMath.computeSummary(pos, neg, 10, allWrong)
        assertEquals("-0.5", ScoreMath.formatMark(negSummary.totalScore))
        assertEquals("-5%", ScoreMath.formatPercent(negSummary.scorePercent))
        assertEquals("0%", ScoreMath.formatPercent(negSummary.accuracyPercent!!))

        // Zero negative marking should never produce "-0"
        val zeroNegDelta = ScoreMath.deltaForAction(MarkAction.WRONG, BigDecimal("2"), BigDecimal.ZERO)
        assertEquals("0", ScoreMath.formatMark(zeroNegDelta))
        assertEquals("0", ScoreMath.formatDeduction(BigDecimal.ZERO))
    }

    @Test
    fun allSkipped_hasNullAccuracyAndZeroScore() {
        val records = listOf(
            QuestionRecord(1, MarkAction.SKIPPED, BigDecimal.ZERO, 1L),
            QuestionRecord(2, MarkAction.SKIPPED, BigDecimal.ZERO, 2L)
        )
        val summary = ScoreMath.computeSummary(BigDecimal("1"), BigDecimal("0.25"), 10, records)
        assertEquals("0", ScoreMath.formatMark(summary.totalScore))
        assertNull(summary.accuracyPercent)
        assertFalse(summary.isComplete)
        assertEquals(3, summary.nextQuestionNumber)
    }

    @Test
    fun defaultScheme_computesExactScoreAndHandlesGaps() {
        val pos = BigDecimal("1")
        val neg = BigDecimal("0.25")
        val records = listOf(
            QuestionRecord(1, MarkAction.CORRECT, BigDecimal("1"), 1L),
            QuestionRecord(3, MarkAction.WRONG, BigDecimal("-0.25"), 2L),
            QuestionRecord(4, MarkAction.WRONG, BigDecimal("-0.25"), 3L)
        )

        val summary = ScoreMath.computeSummary(pos, neg, 100, records)
        assertEquals("0.5", ScoreMath.formatMark(summary.totalScore))
        assertEquals(2, summary.nextQuestionNumber)
    }

    @Test
    fun extremeBounds_handlesMaxQuestionsAndMaxMarksWithoutOverflow() {
        val pos = BigDecimal("1000")
        val neg = BigDecimal("250.25")
        val records = (1..1000).map { q ->
            QuestionRecord(q, MarkAction.CORRECT, pos, q.toLong())
        }
        val summary = ScoreMath.computeSummary(pos, neg, 1000, records)
        assertEquals("1000000", ScoreMath.formatMark(summary.totalScore))
        assertEquals("1000000", ScoreMath.formatMark(summary.maxScore))
        assertEquals("100%", ScoreMath.formatPercent(summary.scorePercent))
        assertTrue(summary.isComplete)
        assertNull(summary.nextQuestionNumber)
    }

    @Test
    fun completion_setsNextQuestionNullAndIsCompleteTrue() {
        val pos = BigDecimal("4")
        val neg = BigDecimal("1")
        val records = listOf(
            QuestionRecord(1, MarkAction.CORRECT, BigDecimal("4"), 1L),
            QuestionRecord(2, MarkAction.WRONG, BigDecimal("-1"), 2L)
        )
        val summary = ScoreMath.computeSummary(pos, neg, 2, records)
        assertTrue(summary.isComplete)
        assertNull(summary.nextQuestionNumber)
        assertEquals("3", ScoreMath.formatMark(summary.totalScore))
        assertEquals("8", ScoreMath.formatMark(summary.maxScore))
        assertEquals("37.5%", ScoreMath.formatPercent(summary.scorePercent))
    }
}
