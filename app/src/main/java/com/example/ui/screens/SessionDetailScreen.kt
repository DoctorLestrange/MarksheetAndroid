package com.example.ui.screens

import androidx.activity.compose.BackHandler
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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.QuestionRecord
import com.example.domain.ScoreMath
import com.example.ui.components.QuestionEditDialog
import com.example.ui.components.QuestionSheetBottomSheet
import com.example.ui.components.SessionNameDialog
import com.example.ui.components.SessionScoreboardCard
import com.example.ui.components.formatSessionTimestamp
import com.example.ui.theme.LocalMarksheetColors
import com.example.ui.theme.MarksheetMonoStyles

private enum class QuestionFilter { ALL, CORRECT, WRONG, SKIPPED }

@Composable
fun SessionDetailScreen(
    session: ExamSession,
    onBack: () -> Unit,
    onOpenInCalculator: (Long) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    onDeleteSession: (Long) -> Unit,
    onSetQuestionMark: (Long, Int, MarkAction?) -> Unit,
    modifier: Modifier = Modifier
) {
    BackHandler(onBack = onBack)

    var selectedFilterName by rememberSaveable { mutableStateOf(QuestionFilter.ALL.name) }
    val selectedFilter = runCatching { QuestionFilter.valueOf(selectedFilterName) }
        .getOrDefault(QuestionFilter.ALL)

    var editingQuestion by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFullSheet by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showDeleteDialog by rememberSaveable { mutableStateOf(false) }

    val semantic = LocalMarksheetColors.current
    val filteredRecords = remember(session.records, selectedFilter) {
        when (selectedFilter) {
            QuestionFilter.ALL -> session.records
            QuestionFilter.CORRECT -> session.records.filter { it.action == MarkAction.CORRECT }
            QuestionFilter.WRONG -> session.records.filter { it.action == MarkAction.WRONG }
            QuestionFilter.SKIPPED -> session.records.filter { it.action == MarkAction.SKIPPED }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter),
            contentPadding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // Back & title header
            item(key = "detail_header") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.weight(1f)
                    ) {
                        IconButton(
                            onClick = onBack,
                            modifier = Modifier.testTag("detail_back_button")
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                                contentDescription = stringResource(R.string.cd_back)
                            )
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = session.title,
                                style = MaterialTheme.typography.titleLarge,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = formatSessionTimestamp(session.updatedAt),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = { showRenameDialog = true },
                            modifier = Modifier.testTag("detail_rename_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.Edit,
                                contentDescription = stringResource(R.string.cd_edit_title)
                            )
                        }
                        IconButton(
                            onClick = { showDeleteDialog = true },
                            modifier = Modifier.testTag("detail_delete_button")
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.DeleteOutline,
                                contentDescription = stringResource(R.string.cd_delete_session),
                                tint = semantic.negativeAccent
                            )
                        }
                    }
                }
            }

            // Scoreboard card
            item(key = "detail_scoreboard") {
                SessionScoreboardCard(session = session)
            }

            // Action row: Open in Calculator + Full Question Sheet
            item(key = "detail_actions") {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = { onOpenInCalculator(session.id) },
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .weight(1f)
                            .heightIn(min = 48.dp)
                            .testTag("detail_open_calculator")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Calculate,
                            contentDescription = stringResource(R.string.cd_resume_session),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.history_resume),
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }

                    OutlinedButton(
                        onClick = { showFullSheet = true },
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 8.dp),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("detail_full_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.cd_view_sheet),
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${session.summary.answeredCount}/${session.totalQuestions}",
                            style = MarksheetMonoStyles.chipValue,
                            maxLines = 1
                        )
                    }
                }
            }

            // Section header & filter chips
            item(key = "detail_filters") {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = stringResource(R.string.history_question_breakdown),
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text(
                            text = stringResource(R.string.label_tap_to_edit),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        item {
                            FilterChip(
                                selected = selectedFilter == QuestionFilter.ALL,
                                onClick = { selectedFilterName = QuestionFilter.ALL.name },
                                label = {
                                    Text("${stringResource(R.string.history_filter_all)} (${session.records.size})")
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == QuestionFilter.CORRECT,
                                onClick = { selectedFilterName = QuestionFilter.CORRECT.name },
                                label = {
                                    Text("${stringResource(R.string.label_correct)} (${session.summary.correctCount})")
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == QuestionFilter.WRONG,
                                onClick = { selectedFilterName = QuestionFilter.WRONG.name },
                                label = {
                                    Text("${stringResource(R.string.label_wrong)} (${session.summary.wrongCount})")
                                }
                            )
                        }
                        item {
                            FilterChip(
                                selected = selectedFilter == QuestionFilter.SKIPPED,
                                onClick = { selectedFilterName = QuestionFilter.SKIPPED.name },
                                label = {
                                    Text("${stringResource(R.string.label_skipped)} (${session.summary.skippedCount})")
                                }
                            )
                        }
                    }
                }
            }

            if (filteredRecords.isEmpty()) {
                item(key = "detail_empty_filter") {
                    Surface(
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        border = BorderStroke(1.dp, semantic.cardBorder)
                    ) {
                        Text(
                            text = stringResource(R.string.history_no_matching_questions),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(20.dp)
                        )
                    }
                }
            } else {
                val rows = filteredRecords.chunked(2)
                items(
                    items = rows,
                    key = { row -> row.first().questionNumber }
                ) { rowItems ->
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        for (record in rowItems) {
                            DetailQuestionRowCard(
                                record = record,
                                onClick = { editingQuestion = record.questionNumber },
                                modifier = Modifier.weight(1f)
                            )
                        }
                        if (rowItems.size == 1) {
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                }
            }

            item { Spacer(modifier = Modifier.height(16.dp)) }
        }
    }

    if (showFullSheet) {
        QuestionSheetBottomSheet(
            session = session,
            onQuestionTap = { qNum ->
                showFullSheet = false
                editingQuestion = qNum
            },
            onDismiss = { showFullSheet = false }
        )
    }

    val qToEdit = editingQuestion
    if (qToEdit != null) {
        QuestionEditDialog(
            questionNumber = qToEdit,
            currentRecord = session.recordsByQuestion[qToEdit],
            positiveMark = session.positiveMark,
            negativeMark = session.negativeMark,
            onSelectAction = { newAction ->
                onSetQuestionMark(session.id, qToEdit, newAction)
                editingQuestion = null
            },
            onDismiss = { editingQuestion = null }
        )
    }

    if (showRenameDialog) {
        SessionNameDialog(
            title = stringResource(R.string.dialog_rename_title),
            subtitle = null,
            initialName = session.title,
            confirmLabel = stringResource(R.string.action_save_name),
            onConfirm = { newTitle ->
                showRenameDialog = false
                onRenameSession(session.id, newTitle)
            },
            onDismiss = { showRenameDialog = false }
        )
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(R.string.dialog_delete_message, session.title),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        onDeleteSession(session.id)
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.negativeButtonBg,
                        contentColor = semantic.onNegativeButton
                    ),
                    modifier = Modifier.testTag("confirm_delete_session")
                ) {
                    Text(stringResource(R.string.action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun DetailQuestionRowCard(
    record: QuestionRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    val (badgeBg, badgeFg, statusLabel, deltaText) = when (record.action) {
        MarkAction.CORRECT -> Quad(
            semantic.positiveContainer,
            semantic.onPositiveContainer,
            stringResource(R.string.label_correct),
            ScoreMath.formatMark(record.markDelta, showPositiveSign = true)
        )
        MarkAction.WRONG -> Quad(
            semantic.negativeContainer,
            semantic.onNegativeContainer,
            stringResource(R.string.label_wrong),
            ScoreMath.formatMark(record.markDelta)
        )
        MarkAction.SKIPPED -> Quad(
            semantic.skipContainer,
            semantic.onSkipContainer,
            stringResource(R.string.label_skipped),
            "0"
        )
    }

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("detail_q_${record.questionNumber}"),
        shape = MaterialTheme.shapes.small,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column {
                Text(
                    text = "Q${record.questionNumber}",
                    style = MarksheetMonoStyles.statNumber,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Text(
                    text = statusLabel,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Surface(
                shape = RoundedCornerShape(8.dp),
                color = badgeBg
            ) {
                Text(
                    text = deltaText,
                    style = MarksheetMonoStyles.chipValue,
                    color = badgeFg,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                )
            }
        }
    }
}

private data class Quad<A, B, C, D>(
    val first: A,
    val second: B,
    val third: C,
    val fourth: D
)
