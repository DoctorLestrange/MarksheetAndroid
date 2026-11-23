package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Redo
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Remove
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.R
import com.example.domain.ExamSession
import com.example.domain.MarkAction
import com.example.domain.ScoreMath
import com.example.ui.HapticStyle
import com.example.ui.HapticsHelper
import com.example.ui.components.QuestionEditDialog
import com.example.ui.components.QuestionRecordChip
import com.example.ui.components.QuestionSheetBottomSheet
import com.example.ui.components.SessionNameDialog
import com.example.ui.components.SessionScoreboardCard
import com.example.ui.theme.LocalMarksheetColors
import com.example.ui.theme.MarksheetMonoStyles

@Composable
fun CalculatorScreen(
    session: ExamSession?,
    hapticsEnabled: Boolean,
    onRecordMark: (MarkAction) -> Unit,
    onSetQuestionMark: (Long, Int, MarkAction?) -> Unit,
    onUndo: () -> Unit,
    onClearSession: () -> Unit,
    onFinishSession: (String) -> Unit,
    onRenameSession: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    if (session == null) {
        Box(
            modifier = modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            CircularProgressIndicator()
        }
        return
    }

    var editingQuestionNumber by rememberSaveable { mutableStateOf<Int?>(null) }
    var showFullSheet by rememberSaveable { mutableStateOf(false) }
    var showSaveDialog by rememberSaveable { mutableStateOf(false) }
    var showRenameDialog by rememberSaveable { mutableStateOf(false) }
    var showClearDialog by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val view = LocalView.current
    val triggerHaptic: (HapticStyle) -> Unit = { style ->
        HapticsHelper.perform(
            context = context,
            view = view,
            enabled = hapticsEnabled,
            style = style
        )
    }
    fun styleForAction(action: MarkAction?): HapticStyle = when (action) {
        MarkAction.CORRECT -> HapticStyle.POSITIVE
        MarkAction.WRONG -> HapticStyle.NEGATIVE
        MarkAction.SKIPPED, null -> HapticStyle.NEUTRAL
    }

    BoxWithConstraints(modifier = modifier.fillMaxSize()) {
        val isWideLayout = maxWidth >= 700.dp

        if (isWideLayout) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp, vertical = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(24.dp),
                verticalAlignment = Alignment.Top
            ) {
                Column(
                    modifier = Modifier
                        .weight(1.15f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    SessionHeaderBar(
                        session = session,
                        onRenameClick = { showRenameDialog = true },
                        onClearClick = {
                            if (session.records.isNotEmpty()) showClearDialog = true
                        },
                        onSaveClick = { showSaveDialog = true }
                    )
                    SessionScoreboardCard(session = session)
                    QuestionTapeSection(
                        session = session,
                        onUndo = {
                            triggerHaptic(HapticStyle.NEUTRAL)
                            onUndo()
                        },
                        onOpenSheet = { showFullSheet = true },
                        onTapQuestion = { editingQuestionNumber = it }
                    )
                }

                Column(
                    modifier = Modifier
                        .weight(0.85f)
                        .fillMaxHeight(),
                    verticalArrangement = Arrangement.Center
                ) {
                    CompletionBanner(
                        visible = session.summary.isComplete,
                        totalQuestions = session.totalQuestions,
                        onSaveAndNew = { showSaveDialog = true }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    OperationalKeypad(
                        session = session,
                        onAction = { action ->
                            triggerHaptic(styleForAction(action))
                            onRecordMark(action)
                        }
                    )
                }
            }
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .widthIn(max = 600.dp)
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(top = 8.dp, bottom = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    SessionHeaderBar(
                        session = session,
                        onRenameClick = { showRenameDialog = true },
                        onClearClick = {
                            if (session.records.isNotEmpty()) showClearDialog = true
                        },
                        onSaveClick = { showSaveDialog = true }
                    )

                    SessionScoreboardCard(session = session)

                    QuestionTapeSection(
                        session = session,
                        onUndo = {
                            triggerHaptic(HapticStyle.NEUTRAL)
                            onUndo()
                        },
                        onOpenSheet = { showFullSheet = true },
                        onTapQuestion = { editingQuestionNumber = it }
                    )

                    CompletionBanner(
                        visible = session.summary.isComplete,
                        totalQuestions = session.totalQuestions,
                        onSaveAndNew = { showSaveDialog = true }
                    )
                }

                // Bottom thumb-zone operational buttons
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    color = Color.Transparent
                ) {
                    OperationalKeypad(
                        session = session,
                        onAction = { action ->
                            triggerHaptic(styleForAction(action))
                            onRecordMark(action)
                        }
                    )
                }
            }
        }
    }

    // Full Question Sheet Modal
    if (showFullSheet) {
        QuestionSheetBottomSheet(
            session = session,
            onQuestionTap = { qNum ->
                showFullSheet = false
                editingQuestionNumber = qNum
            },
            onDismiss = { showFullSheet = false }
        )
    }

    // Edit Specific Question Dialog
    val qToEdit = editingQuestionNumber
    if (qToEdit != null) {
        QuestionEditDialog(
            questionNumber = qToEdit,
            currentRecord = session.recordsByQuestion[qToEdit],
            positiveMark = session.positiveMark,
            negativeMark = session.negativeMark,
            onSelectAction = { newAction ->
                triggerHaptic(styleForAction(newAction))
                onSetQuestionMark(session.id, qToEdit, newAction)
                editingQuestionNumber = null
            },
            onDismiss = { editingQuestionNumber = null }
        )
    }

    // Save & Start New Session Dialog
    if (showSaveDialog) {
        SessionNameDialog(
            title = stringResource(R.string.dialog_save_title),
            subtitle = stringResource(R.string.dialog_save_subtitle),
            initialName = session.title,
            confirmLabel = stringResource(R.string.action_save_and_new),
            onConfirm = { customName ->
                showSaveDialog = false
                onFinishSession(customName)
            },
            onDismiss = { showSaveDialog = false }
        )
    }

    // Rename Session Dialog
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

    // Clear Current Session Confirmation Dialog
    if (showClearDialog) {
        val semantic = LocalMarksheetColors.current
        AlertDialog(
            onDismissRequest = { showClearDialog = false },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.dialog_clear_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_clear_message,
                        session.summary.answeredCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showClearDialog = false
                        onClearSession()
                    },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.negativeButtonBg,
                        contentColor = semantic.onNegativeButton
                    ),
                    modifier = Modifier.testTag("confirm_clear_session")
                ) {
                    Text(stringResource(R.string.action_clear))
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearDialog = false }) {
                    Text(stringResource(R.string.action_cancel))
                }
            }
        )
    }
}

@Composable
private fun SessionHeaderBar(
    session: ExamSession,
    onRenameClick: () -> Unit,
    onClearClick: () -> Unit,
    onSaveClick: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .weight(1f)
                .heightIn(min = 48.dp)
                .clip(MaterialTheme.shapes.small)
                .clickable(onClick = onRenameClick)
                .padding(horizontal = 4.dp, vertical = 6.dp)
                .testTag("session_title_row"),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = session.title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f, fill = false)
            )
            Icon(
                imageVector = Icons.Rounded.Edit,
                contentDescription = stringResource(R.string.cd_edit_title),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.size(16.dp)
            )
        }

        Row(
            horizontalArrangement = Arrangement.spacedBy(6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = onClearClick,
                enabled = session.records.isNotEmpty(),
                modifier = Modifier.testTag("clear_session_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = stringResource(R.string.cd_reset_session),
                    tint = if (session.records.isNotEmpty()) {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
                    }
                )
            }

            FilledTonalButton(
                onClick = onSaveClick,
                enabled = session.records.isNotEmpty(),
                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 0.dp),
                modifier = Modifier
                    .heightIn(min = 48.dp)
                    .testTag("finish_session_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Check,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = stringResource(R.string.action_save),
                    style = MaterialTheme.typography.labelLarge
                )
            }
        }
    }
}

@Composable
private fun QuestionTapeSection(
    session: ExamSession,
    onUndo: () -> Unit,
    onOpenSheet: () -> Unit,
    onTapQuestion: (Int) -> Unit
) {
    val semantic = LocalMarksheetColors.current
    val summary = session.summary
    val listState = rememberLazyListState()

    // Keep the most recently recorded chip visible without jittery mid-flight scroll animations on rapid taps.
    val latestRecord = remember(session.records) {
        session.records.maxByOrNull { it.recordedAt }
    }
    LaunchedEffect(latestRecord?.questionNumber, latestRecord?.recordedAt, session.records.size) {
        val targetQ = latestRecord?.questionNumber ?: return@LaunchedEffect
        val targetIndex = session.records.indexOfFirst { it.questionNumber == targetQ }
        if (targetIndex >= 0) {
            val isAlreadyVisible = listState.layoutInfo.visibleItemsInfo.any { it.index == targetIndex }
            if (!isAlreadyVisible) {
                listState.scrollToItem(targetIndex)
            }
        }
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    val headerText = if (summary.nextQuestionNumber != null) {
                        stringResource(
                            R.string.label_question_progress,
                            summary.nextQuestionNumber,
                            session.totalQuestions
                        )
                    } else {
                        stringResource(R.string.label_exam_complete, session.totalQuestions)
                    }
                    Text(
                        text = headerText,
                        style = MaterialTheme.typography.titleMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        modifier = Modifier.testTag("current_question_label")
                    )
                    Text(
                        text = if (session.records.isEmpty()) {
                            stringResource(R.string.label_no_questions_yet)
                        } else {
                            stringResource(R.string.label_tap_to_edit)
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilledTonalIconButton(
                        onClick = onUndo,
                        enabled = session.records.isNotEmpty(),
                        colors = IconButtonDefaults.filledTonalIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh
                        ),
                        modifier = Modifier
                            .size(48.dp)
                            .testTag("undo_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.Undo,
                            contentDescription = stringResource(R.string.cd_undo)
                        )
                    }

                    OutlinedButton(
                        onClick = onOpenSheet,
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp),
                        border = BorderStroke(1.dp, semantic.cardBorder),
                        modifier = Modifier
                            .heightIn(min = 48.dp)
                            .testTag("view_sheet_button")
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.GridView,
                            contentDescription = stringResource(R.string.cd_view_sheet),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = "${summary.answeredCount}/${session.totalQuestions}",
                            style = MarksheetMonoStyles.chipValue
                        )
                    }
                }
            }

            if (session.records.isNotEmpty()) {
                LazyRow(
                    state = listState,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    items(
                        items = session.records,
                        key = { it.questionNumber }
                    ) { record ->
                        QuestionRecordChip(
                            record = record,
                            onClick = { onTapQuestion(record.questionNumber) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompletionBanner(
    visible: Boolean,
    totalQuestions: Int,
    onSaveAndNew: () -> Unit
) {
    val semantic = LocalMarksheetColors.current
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically()
    ) {
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.medium,
            color = semantic.positiveContainer,
            border = BorderStroke(1.dp, semantic.positiveAccent.copy(alpha = 0.35f))
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Rounded.TaskAlt,
                        contentDescription = null,
                        tint = semantic.positiveAccent
                    )
                    Text(
                        text = stringResource(R.string.label_exam_complete, totalQuestions),
                        style = MaterialTheme.typography.titleSmall,
                        color = semantic.onPositiveContainer
                    )
                }
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onSaveAndNew,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = semantic.positiveButtonBg,
                        contentColor = semantic.onPositiveButton
                    ),
                    contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                    modifier = Modifier
                        .heightIn(min = 48.dp)
                        .testTag("complete_save_new_button")
                ) {
                    Text(
                        text = stringResource(R.string.action_save_and_new),
                        style = MaterialTheme.typography.labelMedium
                    )
                }
            }
        }
    }
}

@Composable
private fun OperationalKeypad(
    session: ExamSession,
    onAction: (MarkAction) -> Unit
) {
    val semantic = LocalMarksheetColors.current
    val enabled = !session.summary.isComplete
    val nextQ = session.summary.nextQuestionNumber

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        if (nextQ != null) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.Center
            ) {
                Surface(
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceContainerHigh
                ) {
                    Text(
                        text = stringResource(
                            R.string.label_recording_next,
                            nextQ,
                            session.totalQuestions
                        ),
                        style = MarksheetMonoStyles.microNumber,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            PrimaryMarkButton(
                title = stringResource(R.string.action_add_mark),
                valueText = "+${ScoreMath.formatMark(session.positiveMark)}",
                icon = Icons.Rounded.Add,
                containerColor = semantic.positiveButtonBg,
                contentColor = semantic.onPositiveButton,
                enabled = enabled,
                onClick = { onAction(MarkAction.CORRECT) },
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .testTag("btn_add_mark")
            )

            PrimaryMarkButton(
                title = stringResource(R.string.action_negative_mark),
                valueText = ScoreMath.formatDeduction(session.negativeMark),
                icon = Icons.Rounded.Remove,
                containerColor = semantic.negativeButtonBg,
                contentColor = semantic.onNegativeButton,
                enabled = enabled,
                onClick = { onAction(MarkAction.WRONG) },
                modifier = Modifier
                    .weight(1f)
                    .height(90.dp)
                    .testTag("btn_negative_mark")
            )
        }

        SkipMarkButton(
            title = stringResource(R.string.action_skip_question),
            containerColor = semantic.skipButtonBg,
            contentColor = semantic.onSkipButton,
            borderColor = semantic.cardBorder,
            enabled = enabled,
            onClick = { onAction(MarkAction.SKIPPED) },
            modifier = Modifier
                .fillMaxWidth()
                .height(58.dp)
                .testTag("btn_skip_question")
        )
    }
}

@Composable
private fun PrimaryMarkButton(
    title: String,
    valueText: String,
    icon: ImageVector,
    containerColor: Color,
    contentColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.965f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "button_press_scale"
    )

    val effectiveBg = if (enabled) containerColor else containerColor.copy(alpha = 0.35f)
    val effectiveFg = if (enabled) contentColor else contentColor.copy(alpha = 0.55f)
    val valueStyle = if (valueText.length >= 7) {
        MarksheetMonoStyles.buttonValue.copy(fontSize = 18.sp, lineHeight = 22.sp)
    } else {
        MarksheetMonoStyles.buttonValue
    }

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.large)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.large,
        color = effectiveBg
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalArrangement = Arrangement.SpaceBetween,
            horizontalAlignment = Alignment.Start
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = valueText,
                    style = valueStyle,
                    color = effectiveFg,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = effectiveFg.copy(alpha = 0.9f),
                    modifier = Modifier.size(26.dp)
                )
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = effectiveFg.copy(alpha = 0.92f),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun SkipMarkButton(
    title: String,
    containerColor: Color,
    contentColor: Color,
    borderColor: Color,
    enabled: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled) 0.98f else 1f,
        animationSpec = spring(stiffness = 700f),
        label = "skip_press_scale"
    )

    val effectiveBg = if (enabled) containerColor else containerColor.copy(alpha = 0.4f)
    val effectiveFg = if (enabled) contentColor else contentColor.copy(alpha = 0.45f)

    Surface(
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .clip(MaterialTheme.shapes.medium)
            .clickable(
                interactionSource = interactionSource,
                indication = androidx.compose.material3.ripple(),
                enabled = enabled,
                onClick = onClick
            ),
        shape = MaterialTheme.shapes.medium,
        color = effectiveBg,
        border = BorderStroke(1.dp, borderColor)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.Redo,
                    contentDescription = null,
                    tint = effectiveFg,
                    modifier = Modifier.size(22.dp)
                )
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    color = effectiveFg
                )
            }
            Text(
                text = "0",
                style = MarksheetMonoStyles.statNumber,
                color = effectiveFg.copy(alpha = 0.75f)
            )
        }
    }
}
