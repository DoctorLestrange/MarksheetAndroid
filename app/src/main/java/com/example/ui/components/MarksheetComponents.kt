package com.example.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
import com.example.domain.ScoreMath
import com.example.ui.theme.LocalMarksheetColors
import com.example.ui.theme.MarksheetMonoStyles
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

fun formatSessionTimestamp(millis: Long): String {
    val formatter = SimpleDateFormat("MMM d, yyyy • h:mm a", Locale.getDefault())
    return formatter.format(Date(millis))
}

@Composable
fun SessionScoreboardCard(
    session: ExamSession,
    modifier: Modifier = Modifier
) {
    val summary = session.summary
    val semantic = LocalMarksheetColors.current
    val animatedProgress by animateFloatAsState(
        targetValue = summary.progressFraction,
        animationSpec = spring(stiffness = 380f),
        label = "session_progress"
    )

    val scoreColor = when {
        summary.totalScore.compareTo(BigDecimal.ZERO) < 0 -> semantic.negativeAccent
        summary.totalScore.compareTo(BigDecimal.ZERO) > 0 -> MaterialTheme.colorScheme.onSurface
        else -> MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 18.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // Top row: Score label + Percent & Accuracy pills
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = stringResource(R.string.label_score),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    MetricBadge(
                        label = stringResource(R.string.label_percentage),
                        value = ScoreMath.formatPercent(summary.scorePercent)
                    )
                    MetricBadge(
                        label = stringResource(R.string.label_accuracy),
                        value = summary.accuracyPercent?.let { ScoreMath.formatPercent(it) } ?: "—"
                    )
                }
            }

            // Hero score display with adaptive sizing so large or long-decimal scores never clip
            val formattedScore = ScoreMath.formatMark(summary.totalScore)
            val heroStyle = when {
                formattedScore.length >= 9 -> MaterialTheme.typography.headlineLarge.copy(
                    fontFamily = MaterialTheme.typography.displayLarge.fontFamily,
                    fontFeatureSettings = "tnum"
                )
                formattedScore.length >= 7 -> MaterialTheme.typography.displayMedium
                else -> MaterialTheme.typography.displayLarge
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.Bottom,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.Bottom,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = formattedScore,
                        style = heroStyle,
                        color = scoreColor,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.testTag("total_score_value")
                    )
                    Text(
                        text = stringResource(
                            R.string.label_of_max,
                            ScoreMath.formatMark(summary.maxScore)
                        ),
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(bottom = 6.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Scheme pill
                val schemeText = if (session.negativeMark.compareTo(BigDecimal.ZERO) == 0) {
                    "+${ScoreMath.formatMark(session.positiveMark)} / 0"
                } else {
                    "+${ScoreMath.formatMark(session.positiveMark)} / ${ScoreMath.formatDeduction(session.negativeMark)}"
                }
                Surface(
                    shape = RoundedCornerShape(10.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = schemeText,
                        style = MarksheetMonoStyles.microNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }

            // Progress bar
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                LinearProgressIndicator(
                    progress = { animatedProgress },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(CircleShape),
                    color = if (summary.isComplete) semantic.positiveAccent else MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHigh,
                    strokeCap = StrokeCap.Round
                )
            }

            HorizontalDivider(color = semantic.cardBorder, thickness = 1.dp)

            // 3-column breakdown: Correct / Wrong / Skipped
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                BreakdownStatCell(
                    label = stringResource(R.string.label_correct),
                    count = summary.correctCount,
                    subtotal = if (summary.positiveTotal.compareTo(BigDecimal.ZERO) == 0) {
                        "+0"
                    } else {
                        ScoreMath.formatMark(summary.positiveTotal, showPositiveSign = true)
                    },
                    accentColor = semantic.positiveAccent,
                    containerColor = semantic.positiveContainer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_correct")
                )
                BreakdownStatCell(
                    label = stringResource(R.string.label_wrong),
                    count = summary.wrongCount,
                    subtotal = if (summary.negativeTotal.compareTo(BigDecimal.ZERO) == 0) {
                        "0"
                    } else {
                        ScoreMath.formatMark(summary.negativeTotal)
                    },
                    accentColor = semantic.negativeAccent,
                    containerColor = semantic.negativeContainer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_wrong")
                )
                BreakdownStatCell(
                    label = stringResource(R.string.label_skipped),
                    count = summary.skippedCount,
                    subtotal = "0",
                    accentColor = semantic.skipAccent,
                    containerColor = semantic.skipContainer,
                    modifier = Modifier
                        .weight(1f)
                        .testTag("stat_skipped")
                )
            }
        }
    }
}

@Composable
private fun MetricBadge(
    label: String,
    value: String
) {
    Surface(
        shape = RoundedCornerShape(8.dp),
        color = MaterialTheme.colorScheme.surfaceContainerLow
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = value,
                style = MarksheetMonoStyles.microNumber,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
private fun BreakdownStatCell(
    label: String,
    count: Int,
    subtotal: String,
    accentColor: Color,
    containerColor: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = MaterialTheme.shapes.small,
        color = containerColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .clip(CircleShape)
                        .background(accentColor)
                )
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Bottom
            ) {
                Text(
                    text = count.toString(),
                    style = MarksheetMonoStyles.statNumber,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = subtotal,
                    style = MarksheetMonoStyles.microNumber,
                    color = accentColor
                )
            }
        }
    }
}

@Composable
fun QuestionRecordChip(
    record: QuestionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    val (bgColor, fgColor, valueText) = when (record.action) {
        MarkAction.CORRECT -> Triple(
            semantic.positiveContainer,
            semantic.onPositiveContainer,
            ScoreMath.formatMark(record.markDelta, showPositiveSign = true)
        )
        MarkAction.WRONG -> Triple(
            semantic.negativeContainer,
            semantic.onNegativeContainer,
            ScoreMath.formatMark(record.markDelta)
        )
        MarkAction.SKIPPED -> Triple(
            semantic.skipContainer,
            semantic.onSkipContainer,
            stringResource(R.string.badge_skip)
        )
    }

    Surface(
        modifier = modifier
            .heightIn(min = 48.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("question_chip_${record.questionNumber}"),
        shape = MaterialTheme.shapes.small,
        color = bgColor
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = "Q${record.questionNumber}",
                style = MarksheetMonoStyles.microNumber,
                color = fgColor.copy(alpha = 0.78f)
            )
            Text(
                text = valueText,
                style = MarksheetMonoStyles.chipValue,
                color = fgColor
            )
        }
    }
}

@Composable
fun QuestionEditDialog(
    questionNumber: Int,
    currentRecord: QuestionRecord?,
    positiveMark: BigDecimal,
    negativeMark: BigDecimal,
    onSelectAction: (MarkAction?) -> Unit,
    onDismiss: () -> Unit
) {
    val semantic = LocalMarksheetColors.current

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(
                text = stringResource(R.string.dialog_edit_question_title, questionNumber),
                style = MaterialTheme.typography.titleLarge
            )
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(
                    text = stringResource(R.string.dialog_edit_question_subtitle, questionNumber),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Add Mark option
                EditActionRowButton(
                    label = stringResource(R.string.action_add_mark),
                    value = "+${ScoreMath.formatMark(positiveMark)}",
                    isSelected = currentRecord?.action == MarkAction.CORRECT,
                    containerColor = semantic.positiveContainer,
                    contentColor = semantic.onPositiveContainer,
                    accentColor = semantic.positiveAccent,
                    icon = { Icon(Icons.Rounded.Add, contentDescription = null) },
                    onClick = { onSelectAction(MarkAction.CORRECT) },
                    modifier = Modifier.testTag("edit_q_positive")
                )

                // Negative Mark option
                EditActionRowButton(
                    label = stringResource(R.string.action_negative_mark),
                    value = ScoreMath.formatDeduction(negativeMark),
                    isSelected = currentRecord?.action == MarkAction.WRONG,
                    containerColor = semantic.negativeContainer,
                    contentColor = semantic.onNegativeContainer,
                    accentColor = semantic.negativeAccent,
                    icon = { Icon(Icons.Rounded.Remove, contentDescription = null) },
                    onClick = { onSelectAction(MarkAction.WRONG) },
                    modifier = Modifier.testTag("edit_q_negative")
                )

                // Skip Question option
                EditActionRowButton(
                    label = stringResource(R.string.action_skip_question),
                    value = "0",
                    isSelected = currentRecord?.action == MarkAction.SKIPPED,
                    containerColor = semantic.skipContainer,
                    contentColor = semantic.onSkipContainer,
                    accentColor = semantic.skipAccent,
                    icon = { Icon(Icons.AutoMirrored.Rounded.Redo, contentDescription = null) },
                    onClick = { onSelectAction(MarkAction.SKIPPED) },
                    modifier = Modifier.testTag("edit_q_skip")
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        },
        dismissButton = {
            if (currentRecord != null) {
                TextButton(
                    onClick = { onSelectAction(null) },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = semantic.negativeAccent
                    ),
                    modifier = Modifier.testTag("edit_q_clear")
                ) {
                    Icon(
                        Icons.Rounded.DeleteOutline,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(stringResource(R.string.action_clear_mark))
                }
            }
        }
    )
}

@Composable
private fun EditActionRowButton(
    label: String,
    value: String,
    isSelected: Boolean,
    containerColor: Color,
    contentColor: Color,
    accentColor: Color,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.small,
        color = containerColor,
        border = if (isSelected) BorderStroke(2.dp, accentColor) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Box(modifier = Modifier.size(22.dp), contentAlignment = Alignment.Center) {
                    icon()
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.titleMedium,
                    color = contentColor
                )
            }
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = value,
                    style = MarksheetMonoStyles.statNumber,
                    color = accentColor
                )
                if (isSelected) {
                    Icon(
                        imageVector = Icons.Rounded.CheckCircle,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun QuestionSheetBottomSheet(
    session: ExamSession,
    onQuestionTap: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val gridState = rememberLazyGridState()
    val semantic = LocalMarksheetColors.current
    val recordMap = session.recordsByQuestion

    LaunchedEffect(session.id) {
        val focusIndex = ((session.summary.nextQuestionNumber ?: session.totalQuestions) - 1)
            .coerceIn(0, (session.totalQuestions - 1).coerceAtLeast(0))
        if (focusIndex > 15) {
            gridState.scrollToItem((focusIndex - 5).coerceAtLeast(0))
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surfaceContainer
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.sheet_title),
                        style = MaterialTheme.typography.headlineMedium
                    )
                    Text(
                        text = stringResource(R.string.sheet_subtitle),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "${session.summary.answeredCount}/${session.totalQuestions}",
                    style = MarksheetMonoStyles.statNumber,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            // Compact color legend
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SheetLegendDot(semantic.positiveAccent, stringResource(R.string.label_correct))
                SheetLegendDot(semantic.negativeAccent, stringResource(R.string.label_wrong))
                SheetLegendDot(semantic.skipAccent, stringResource(R.string.label_skipped))
                SheetLegendDot(semantic.cardBorder, stringResource(R.string.sheet_legend_unanswered))
            }

            LazyVerticalGrid(
                state = gridState,
                columns = GridCells.Adaptive(minSize = 62.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 420.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                contentPadding = PaddingValues(vertical = 6.dp)
            ) {
                items(
                    count = session.totalQuestions,
                    key = { index -> index + 1 }
                ) { index ->
                    val qNum = index + 1
                    val rec = recordMap[qNum]
                    val isNext = session.summary.nextQuestionNumber == qNum

                    val bg = when (rec?.action) {
                        MarkAction.CORRECT -> semantic.positiveContainer
                        MarkAction.WRONG -> semantic.negativeContainer
                        MarkAction.SKIPPED -> semantic.skipContainer
                        null -> MaterialTheme.colorScheme.surfaceContainerLow
                    }
                    val fg = when (rec?.action) {
                        MarkAction.CORRECT -> semantic.onPositiveContainer
                        MarkAction.WRONG -> semantic.onNegativeContainer
                        MarkAction.SKIPPED -> semantic.onSkipContainer
                        null -> MaterialTheme.colorScheme.onSurfaceVariant
                    }
                    val sub = when (rec?.action) {
                        MarkAction.CORRECT -> ScoreMath.formatMark(rec.markDelta, showPositiveSign = true)
                        MarkAction.WRONG -> ScoreMath.formatMark(rec.markDelta)
                        MarkAction.SKIPPED -> "SKIP"
                        null -> "—"
                    }

                    Box(
                        modifier = Modifier
                            .heightIn(min = 52.dp)
                            .clip(MaterialTheme.shapes.small)
                            .background(bg)
                            .then(
                                if (isNext) {
                                    Modifier.border(
                                        1.5.dp,
                                        MaterialTheme.colorScheme.primary,
                                        MaterialTheme.shapes.small
                                    )
                                } else {
                                    Modifier.border(
                                        1.dp,
                                        semantic.cardBorder.copy(alpha = 0.6f),
                                        MaterialTheme.shapes.small
                                    )
                                }
                            )
                            .clickable { onQuestionTap(qNum) }
                            .padding(vertical = 6.dp, horizontal = 4.dp)
                            .testTag("sheet_q_$qNum"),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(1.dp)
                        ) {
                            Text(
                                text = "Q$qNum",
                                style = MarksheetMonoStyles.microNumber,
                                color = fg.copy(alpha = 0.8f)
                            )
                            Text(
                                text = sub,
                                style = MarksheetMonoStyles.chipValue,
                                color = fg,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun SheetLegendDot(color: Color, label: String) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(5.dp)
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(color)
        )
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            maxLines = 1
        )
    }
}

@Composable
fun SessionNameDialog(
    title: String,
    subtitle: String?,
    initialName: String,
    confirmLabel: String,
    onConfirm: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var text by rememberSaveable(initialName) { mutableStateOf(initialName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surfaceContainer,
        title = {
            Text(text = title, style = MaterialTheme.typography.titleLarge)
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { if (it.length <= 60) text = it },
                    label = { Text(stringResource(R.string.dialog_session_name_label)) },
                    placeholder = { Text(stringResource(R.string.dialog_session_name_placeholder)) },
                    singleLine = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("session_name_input")
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onConfirm(text.trim().ifEmpty { initialName }) },
                modifier = Modifier.testTag("session_name_confirm")
            ) {
                Text(confirmLabel)
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel))
            }
        }
    )
}
