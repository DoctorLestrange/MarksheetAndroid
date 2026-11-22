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
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.RestartAlt
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.domain.AppConfig
import com.example.domain.DefaultExamPresets
import com.example.domain.ExamPreset
import com.example.domain.ScoreMath
import com.example.domain.ThemeMode
import com.example.ui.theme.LocalMarksheetColors
import com.example.ui.theme.MarksheetMonoStyles

private data class PendingConfigSave(
    val positive: String,
    val negative: String,
    val totalQuestions: Int,
    val isReset: Boolean = false
)

/**
 * Keeps only digits and at most one decimal separator (converting locale comma to dot),
 * eliminating stray minus signs, spaces, letters, or multiple decimal points.
 */
private fun sanitizeUnsignedDecimalInput(raw: String, maxLength: Int = 9): String {
    val sb = StringBuilder()
    var hasDot = false
    for (ch in raw) {
        if (sb.length >= maxLength) break
        when {
            ch.isDigit() -> sb.append(ch)
            (ch == '.' || ch == ',') && !hasDot -> {
                sb.append('.')
                hasDot = true
            }
        }
    }
    return sb.toString()
}

@Composable
fun SettingsScreen(
    config: AppConfig,
    activeSessionAnsweredCount: Int,
    onSetThemeMode: (ThemeMode) -> Unit,
    onSetAmoledBlack: (Boolean) -> Unit,
    onSetHapticsEnabled: (Boolean) -> Unit,
    onSaveMarkingConfig: (String, String, Int, Boolean) -> Unit,
    onResetDefaults: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    val focusManager = LocalFocusManager.current

    var positiveInput by rememberSaveable { mutableStateOf(config.positiveMark) }
    var negativeInput by rememberSaveable { mutableStateOf(config.negativeMark) }
    var totalQuestionsInput by rememberSaveable { mutableStateOf(config.totalQuestions.toString()) }

    var lastSyncedScheme by rememberSaveable {
        mutableStateOf("${config.positiveMark}|${config.negativeMark}|${config.totalQuestions}")
    }
    val currentSchemeKey = "${config.positiveMark}|${config.negativeMark}|${config.totalQuestions}"

    // Sync inputs only when the persisted scheme actually changes (e.g. preset/reset/save),
    // never clobbering unsaved edits during device rotation.
    LaunchedEffect(currentSchemeKey) {
        if (currentSchemeKey != lastSyncedScheme) {
            positiveInput = config.positiveMark
            negativeInput = config.negativeMark
            totalQuestionsInput = config.totalQuestions.toString()
            lastSyncedScheme = currentSchemeKey
        }
    }

    val parsedPositive = ScoreMath.parsePositiveMark(positiveInput)
    val parsedNegative = ScoreMath.parseNegativeMark(negativeInput)
    val parsedTotal = ScoreMath.parseTotalQuestions(totalQuestionsInput)

    val isFormValid = parsedPositive != null && parsedNegative != null && parsedTotal != null
    val hasChanges = isFormValid && (
        ScoreMath.formatMark(parsedPositive) != config.positiveMark ||
            ScoreMath.formatMark(parsedNegative) != config.negativeMark ||
            parsedTotal != config.totalQuestions
        )

    var pendingSave by remember { mutableStateOf<PendingConfigSave?>(null) }

    fun requestSave(pos: String, neg: String, total: Int, isReset: Boolean = false) {
        focusManager.clearFocus()
        if (activeSessionAnsweredCount > 0) {
            pendingSave = PendingConfigSave(pos, neg, total, isReset)
        } else {
            if (isReset) {
                onResetDefaults(true)
            } else {
                onSaveMarkingConfig(pos, neg, total, true)
            }
        }
    }

    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .widthIn(max = 640.dp)
                .align(Alignment.TopCenter)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Section 1: Appearance
            SettingsSectionCard(title = stringResource(R.string.settings_section_appearance)) {
                Text(
                    text = stringResource(R.string.settings_theme_label),
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ThemeOptionCard(
                        label = stringResource(R.string.settings_theme_light),
                        selected = config.themeMode == ThemeMode.LIGHT,
                        icon = { Icon(Icons.Rounded.LightMode, contentDescription = null) },
                        onClick = { onSetThemeMode(ThemeMode.LIGHT) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_light")
                    )
                    ThemeOptionCard(
                        label = stringResource(R.string.settings_theme_dark),
                        selected = config.themeMode == ThemeMode.DARK,
                        icon = { Icon(Icons.Rounded.DarkMode, contentDescription = null) },
                        onClick = { onSetThemeMode(ThemeMode.DARK) },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("theme_option_dark")
                    )
                }

                HorizontalDivider(color = semantic.cardBorder)

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_amoled_label),
                    subtitle = stringResource(R.string.settings_amoled_subtitle),
                    checked = config.amoledBlack,
                    enabled = config.themeMode == ThemeMode.DARK,
                    onCheckedChange = onSetAmoledBlack,
                    testTag = "switch_amoled_black"
                )

                HorizontalDivider(color = semantic.cardBorder)

                SettingsSwitchRow(
                    title = stringResource(R.string.settings_haptics_label),
                    subtitle = stringResource(R.string.settings_haptics_subtitle),
                    checked = config.hapticsEnabled,
                    enabled = true,
                    onCheckedChange = onSetHapticsEnabled,
                    testTag = "switch_haptics"
                )
            }

            // Section 2: Default Marking Scheme
            SettingsSectionCard(title = stringResource(R.string.settings_section_marking)) {
                // Positive mark input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = positiveInput,
                        onValueChange = { value ->
                            positiveInput = sanitizeUnsignedDecimalInput(value)
                        },
                        label = { Text(stringResource(R.string.settings_positive_label)) },
                        prefix = {
                            Text(
                                text = "+",
                                style = MarksheetMonoStyles.statNumber,
                                color = semantic.positiveAccent
                            )
                        },
                        isError = parsedPositive == null,
                        supportingText = if (parsedPositive == null) {
                            { Text(stringResource(R.string.error_positive_invalid)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MarksheetMonoStyles.statNumber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_positive_mark")
                    )
                    QuickValueChipRow(
                        values = listOf("1", "2", "4", "0.5"),
                        selectedValue = parsedPositive?.let { ScoreMath.formatMark(it) },
                        prefix = "+",
                        onSelect = { positiveInput = it }
                    )
                }

                // Negative mark input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = negativeInput,
                        onValueChange = { value ->
                            negativeInput = sanitizeUnsignedDecimalInput(value)
                        },
                        label = { Text(stringResource(R.string.settings_negative_label)) },
                        prefix = {
                            Text(
                                text = "-",
                                style = MarksheetMonoStyles.statNumber,
                                color = semantic.negativeAccent
                            )
                        },
                        isError = parsedNegative == null,
                        supportingText = if (parsedNegative == null) {
                            { Text(stringResource(R.string.error_negative_invalid)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Decimal,
                            imeAction = ImeAction.Next
                        ),
                        textStyle = MarksheetMonoStyles.statNumber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_negative_mark")
                    )
                    QuickValueChipRow(
                        values = listOf("0", "0.25", "0.33", "0.5", "1"),
                        selectedValue = parsedNegative?.let { ScoreMath.formatMark(it) },
                        prefix = "-",
                        zeroDisplay = "0",
                        onSelect = { negativeInput = it }
                    )
                }

                // Total questions input
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    OutlinedTextField(
                        value = totalQuestionsInput,
                        onValueChange = { value ->
                            val digitsOnly = value.filter { it.isDigit() }.take(4)
                            totalQuestionsInput = digitsOnly
                        },
                        label = { Text(stringResource(R.string.settings_questions_label)) },
                        isError = parsedTotal == null,
                        supportingText = if (parsedTotal == null) {
                            { Text(stringResource(R.string.error_questions_invalid)) }
                        } else null,
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        keyboardActions = KeyboardActions(
                            onDone = {
                                focusManager.clearFocus()
                                if (hasChanges && parsedPositive != null && parsedNegative != null && parsedTotal != null) {
                                    requestSave(
                                        pos = ScoreMath.formatMark(parsedPositive),
                                        neg = ScoreMath.formatMark(parsedNegative),
                                        total = parsedTotal
                                    )
                                }
                            }
                        ),
                        textStyle = MarksheetMonoStyles.statNumber,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("input_total_questions")
                    )
                    QuickValueChipRow(
                        values = listOf("50", "90", "100", "150", "180", "200"),
                        selectedValue = parsedTotal?.toString(),
                        prefix = "",
                        onSelect = { totalQuestionsInput = it }
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        if (parsedPositive != null && parsedNegative != null && parsedTotal != null) {
                            requestSave(
                                pos = ScoreMath.formatMark(parsedPositive),
                                neg = ScoreMath.formatMark(parsedNegative),
                                total = parsedTotal
                            )
                        }
                    },
                    enabled = hasChanges,
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(min = 50.dp)
                        .testTag("save_config_button")
                ) {
                    Icon(
                        imageVector = Icons.Rounded.Check,
                        contentDescription = null,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(stringResource(R.string.settings_save_config))
                }
            }

            // Section 3: Quick Exam Presets
            SettingsSectionCard(title = stringResource(R.string.settings_section_presets)) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    DefaultExamPresets.chunked(2).forEach { rowPresets ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            rowPresets.forEach { preset ->
                                val isCurrent = config.positiveMark == preset.positiveMark &&
                                    config.negativeMark == preset.negativeMark &&
                                    config.totalQuestions == preset.totalQuestions
                                PresetCard(
                                    preset = preset,
                                    isSelected = isCurrent,
                                    onClick = {
                                        requestSave(
                                            pos = preset.positiveMark,
                                            neg = preset.negativeMark,
                                            total = preset.totalQuestions
                                        )
                                    },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                            if (rowPresets.size == 1) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }

            // Reset to defaults button
            OutlinedButton(
                onClick = {
                    requestSave(
                        pos = AppConfig.DEFAULT_POSITIVE_MARK,
                        neg = AppConfig.DEFAULT_NEGATIVE_MARK,
                        total = AppConfig.DEFAULT_TOTAL_QUESTIONS,
                        isReset = true
                    )
                },
                border = BorderStroke(1.dp, semantic.cardBorder),
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(min = 48.dp)
                    .testTag("reset_defaults_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.RestartAlt,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.settings_reset_defaults))
            }

            Spacer(modifier = Modifier.height(16.dp))
        }
    }

    // Mid-session apply confirmation dialog
    val saveRequest = pendingSave
    if (saveRequest != null) {
        AlertDialog(
            onDismissRequest = { pendingSave = null },
            containerColor = MaterialTheme.colorScheme.surfaceContainer,
            title = {
                Text(
                    text = stringResource(R.string.dialog_apply_settings_title),
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = stringResource(
                        R.string.dialog_apply_settings_message,
                        activeSessionAnsweredCount
                    ),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        pendingSave = null
                        if (saveRequest.isReset) {
                            onResetDefaults(true)
                        } else {
                            onSaveMarkingConfig(
                                saveRequest.positive,
                                saveRequest.negative,
                                saveRequest.totalQuestions,
                                true
                            )
                        }
                    },
                    modifier = Modifier.testTag("apply_settings_current")
                ) {
                    Text(stringResource(R.string.action_apply_current))
                }
            },
            dismissButton = {
                TextButton(
                    onClick = {
                        pendingSave = null
                        if (saveRequest.isReset) {
                            onResetDefaults(false)
                        } else {
                            onSaveMarkingConfig(
                                saveRequest.positive,
                                saveRequest.negative,
                                saveRequest.totalQuestions,
                                false
                            )
                        }
                    },
                    modifier = Modifier.testTag("apply_settings_future")
                ) {
                    Text(stringResource(R.string.action_future_only))
                }
            }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    content: @Composable () -> Unit
) {
    val semantic = LocalMarksheetColors.current
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainer,
        border = BorderStroke(1.dp, semantic.cardBorder)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
            content()
        }
    }
}

@Composable
private fun ThemeOptionCard(
    label: String,
    selected: Boolean,
    icon: @Composable () -> Unit,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    val bgColor = if (selected) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.surfaceContainerLow
    }
    val contentColor = if (selected) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSurface
    }

    Surface(
        modifier = modifier
            .heightIn(min = 56.dp)
            .clip(MaterialTheme.shapes.medium)
            .clickable(onClick = onClick),
        shape = MaterialTheme.shapes.medium,
        color = bgColor,
        border = if (!selected) BorderStroke(1.dp, semantic.cardBorder) else null
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center
        ) {
            Box(modifier = Modifier.size(18.dp), contentAlignment = Alignment.Center) {
                CompositionLocalProvider(LocalContentColor provides contentColor) {
                    icon()
                }
            }
            Spacer(modifier = Modifier.width(8.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.labelLarge,
                color = contentColor,
                maxLines = 1
            )
        }
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 52.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(enabled = enabled) { onCheckedChange(!checked) }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(end = 12.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleSmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurface
                } else {
                    MaterialTheme.colorScheme.onSurface.copy(alpha = 0.45f)
                }
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = if (enabled) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
                }
            )
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            enabled = enabled,
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
private fun QuickValueChipRow(
    values: List<String>,
    selectedValue: String?,
    prefix: String,
    zeroDisplay: String? = null,
    onSelect: (String) -> Unit
) {
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(vertical = 2.dp)
    ) {
        items(values) { value ->
            val label = if (value == "0" && zeroDisplay != null) zeroDisplay else "$prefix$value"
            FilterChip(
                selected = selectedValue == value,
                onClick = { onSelect(value) },
                label = {
                    Text(
                        text = label,
                        style = MarksheetMonoStyles.chipValue
                    )
                }
            )
        }
    }
}

@Composable
private fun PresetCard(
    preset: ExamPreset,
    isSelected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val semantic = LocalMarksheetColors.current
    Surface(
        modifier = modifier
            .heightIn(min = 60.dp)
            .clip(MaterialTheme.shapes.small)
            .clickable(onClick = onClick)
            .testTag("preset_${preset.name.lowercase().replace(" ", "_")}"),
        shape = MaterialTheme.shapes.small,
        color = if (isSelected) {
            MaterialTheme.colorScheme.primaryContainer
        } else {
            MaterialTheme.colorScheme.surfaceContainerLow
        },
        border = BorderStroke(
            width = if (isSelected) 1.5.dp else 1.dp,
            color = if (isSelected) MaterialTheme.colorScheme.primary else semantic.cardBorder
        )
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            Text(
                text = preset.name,
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurface,
                maxLines = 1
            )
            Text(
                text = preset.subtitle,
                style = MarksheetMonoStyles.microNumber,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
    }
}
