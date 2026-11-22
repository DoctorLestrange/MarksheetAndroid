package com.example.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.HistoryEdu
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ExamSession
import com.example.domain.ScoreMath
import com.example.ui.components.formatSessionTimestamp
import com.example.ui.theme.LocalMarksheetColors
import com.example.ui.theme.MarksheetMonoStyles
import java.math.BigDecimal
import java.math.RoundingMode

@Composable
fun HistoryScreen(
    sessions: List<ExamSession>,
    onSessionClick: (Long) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current

    if (sessions.isEmpty()) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(32.dp),
            contentAlignment = Alignment.Center
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(12.dp),
                modifier = Modifier.widthIn(max = 360.dp)
            ) {
                Surface(
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.surfaceContainer,
                    border = BorderStroke(1.dp, semantic.cardBorder)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.HistoryEdu,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier
                            .padding(20.dp)
                            .size(40.dp)
                    )
                }
                Text(
                    text = stringResource(R.string.history_empty_title),
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    textAlign = TextAlign.Center
                )
                Text(
                    text = stringResource(R.string.history_empty_subtitle),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
            }
        }
        return
    }

    val aggregateStats = remember(sessions) {
        val completed = sessions.filter { it.isCompleted }
        val statSource = completed.ifEmpty { sessions }
        val count = statSource.size
        val avgPercent = statSource
            .map { it.summary.scorePercent }
            .fold(BigDecimal.ZERO) { acc, v -> acc.add(v) }
            .divide(BigDecimal(count), 2, RoundingMode.HALF_UP)
        val bestPercent = statSource.maxOf { it.summary.scorePercent }
        Triple(sessions.size, avgPercent, bestPercent)
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item(key = "history_stats_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    HistorySummaryPill(
                        label = stringResource(R.string.history_stat_sessions),
                        value = aggregateStats.first.toString(),
                        modifier = Modifier.weight(1f)
                    )
                    HistorySummaryPill(
                        label = stringResource(R.string.history_stat_avg),
                        value = ScoreMath.formatPercent(aggregateStats.second),
                        modifier = Modifier.weight(1f)
                    )
                    HistorySummaryPill(
                        label = stringResource(R.string.history_stat_best),
                        value = ScoreMath.formatPercent(aggregateStats.third),
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            items(
                items = sessions,
                key = { it.id }
            ) { session ->
                HistorySessionCard(
                    session = session,
                    onClick = { onSessionClick(session.id) }
                )
            }

            item { Spacer(modifier = Modifier.height(12.dp)) }
        }
    }
}

@Composable
private fun HistorySummaryPill(
    label: String,
    value: String,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
            Text(
                text = value,
                style = MarksheetMonoStyles.statNumber,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun HistorySessionCard(
    session: ExamSession,
    onClick: () -> Unit
) {
    val semantic = LocalMarksheetColors.current
    val summary = session.summary

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick)
            .testTag("history_session_card_${session.id}"),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            text = session.title,
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f, fill = false)
                        )
                        if (!session.isCompleted) {
                            Surface(
                                shape = RoundedCornerShape(6.dp),
                                color = semantic.positiveContainer
                            ) {
                                Text(
                                    text = stringResource(R.string.history_in_progress),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = semantic.onPositiveContainer,
                                    modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                )
                            }
                        }
                    }
                    Text(
                        text = formatSessionTimestamp(session.updatedAt),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "${ScoreMath.formatMark(summary.totalScore)} / ${ScoreMath.formatMark(summary.maxScore)}",
                            style = MarksheetMonoStyles.statNumber,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = ScoreMath.formatPercent(summary.scorePercent),
                            style = MarksheetMonoStyles.microNumber,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Icon(
                        imageVector = Icons.Rounded.ChevronRight,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }

            // Bottom breakdown row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniCountPill(
                        text = "+${summary.correctCount}",
                        bg = semantic.positiveContainer,
                        fg = semantic.onPositiveContainer
                    )
                    MiniCountPill(
                        text = "-${summary.wrongCount}",
                        bg = semantic.negativeContainer,
                        fg = semantic.onNegativeContainer
                    )
                    MiniCountPill(
                        text = "${summary.skippedCount} skip",
                        bg = semantic.skipContainer,
                        fg = semantic.onSkipContainer
                    )
                }

                val schemeText = "+${ScoreMath.formatMark(session.positiveMark)} / ${ScoreMath.formatDeduction(session.negativeMark)} • ${summary.answeredCount}/${session.totalQuestions}Q"
                Text(
                    text = schemeText,
                    style = MarksheetMonoStyles.microNumber,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}

@Composable
private fun MiniCountPill(
    text: String,
    bg: androidx.compose.ui.graphics.Color,
    fg: androidx.compose.ui.graphics.Color
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = bg
    ) {
        Text(
            text = text,
            style = MarksheetMonoStyles.microNumber,
            color = fg,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}
