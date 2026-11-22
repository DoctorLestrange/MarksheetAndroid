package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Calculate
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Calculate
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LightMode
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarDuration
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.VerticalDivider
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.R
import com.example.domain.ThemeMode
import com.example.ui.screens.CalculatorScreen
import com.example.ui.screens.HistoryScreen
import com.example.ui.screens.SessionDetailScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.LocalMarksheetColors

enum class MarksheetTab(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector
) {
    CALCULATOR("calculator", R.string.nav_calculator, Icons.Rounded.Calculate, Icons.Outlined.Calculate),
    HISTORY("history", R.string.nav_history, Icons.Rounded.History, Icons.Outlined.History),
    SETTINGS("settings", R.string.nav_settings, Icons.Rounded.Tune, Icons.Outlined.Tune)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MarksheetApp(
    viewModel: MarksheetViewModel,
    config: com.example.domain.AppConfig
) {
    val activeSession by viewModel.activeSession.collectAsStateWithLifecycle()
    val historySessions by viewModel.historySessions.collectAsStateWithLifecycle()
    val selectedHistorySession by viewModel.selectedHistorySession.collectAsStateWithLifecycle()

    var currentTab by rememberSaveable { mutableStateOf(MarksheetTab.CALCULATOR) }
    val snackbarHostState = remember { SnackbarHostState() }
    val semantic = LocalMarksheetColors.current

    LaunchedEffect(viewModel) {
        viewModel.snackbarEvents.collect { message ->
            snackbarHostState.currentSnackbarData?.dismiss()
            snackbarHostState.showSnackbar(
                message = message,
                duration = SnackbarDuration.Short
            )
        }
    }

    // Back press on non-calculator tabs returns to Calculator cleanly
    BackHandler(enabled = currentTab != MarksheetTab.CALCULATOR && selectedHistorySession == null) {
        currentTab = MarksheetTab.CALCULATOR
    }

    val topTitle = when {
        currentTab == MarksheetTab.HISTORY && selectedHistorySession != null ->
            stringResource(R.string.history_detail_title)
        currentTab == MarksheetTab.HISTORY ->
            stringResource(R.string.history_title)
        currentTab == MarksheetTab.SETTINGS ->
            stringResource(R.string.settings_title)
        else ->
            stringResource(R.string.app_name)
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val useNavRail = maxWidth >= 600.dp

        Scaffold(
            modifier = Modifier.fillMaxSize(),
            containerColor = MaterialTheme.colorScheme.background,
            contentWindowInsets = WindowInsets.statusBars,
            snackbarHost = { SnackbarHost(hostState = snackbarHostState) },
            topBar = {
                androidx.compose.foundation.layout.Column {
                    CenterAlignedTopAppBar(
                        title = {
                            Text(
                                text = topTitle,
                                style = MaterialTheme.typography.titleLarge,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        },
                        actions = {
                            IconButton(
                                onClick = { viewModel.toggleTheme() },
                                modifier = Modifier.testTag("top_theme_toggle")
                            ) {
                                Icon(
                                    imageVector = if (config.themeMode == ThemeMode.DARK) {
                                        Icons.Rounded.LightMode
                                    } else {
                                        Icons.Rounded.DarkMode
                                    },
                                    contentDescription = stringResource(R.string.cd_toggle_theme),
                                    tint = MaterialTheme.colorScheme.onSurface
                                )
                            }
                        },
                        colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                            containerColor = MaterialTheme.colorScheme.background,
                            titleContentColor = MaterialTheme.colorScheme.onSurface
                        )
                    )
                    HorizontalDivider(color = semantic.cardBorder, thickness = 1.dp)
                }
            },
            bottomBar = {
                if (!useNavRail) {
                    androidx.compose.foundation.layout.Column {
                        HorizontalDivider(color = semantic.cardBorder, thickness = 1.dp)
                        NavigationBar(
                            containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                            tonalElevation = 0.dp,
                            windowInsets = WindowInsets.navigationBars
                        ) {
                            MarksheetTab.entries.forEach { tab ->
                                val selected = currentTab == tab
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = {
                                        if (tab != MarksheetTab.HISTORY) {
                                            viewModel.selectHistorySession(null)
                                        } else if (currentTab == MarksheetTab.HISTORY) {
                                            viewModel.selectHistorySession(null)
                                        }
                                        currentTab = tab
                                    },
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                            contentDescription = stringResource(tab.labelRes)
                                        )
                                    },
                                    label = { Text(stringResource(tab.labelRes)) },
                                    modifier = Modifier.testTag("nav_${tab.route}")
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
            ) {
                if (useNavRail) {
                    NavigationRail(
                        containerColor = MaterialTheme.colorScheme.surfaceContainerLow
                    ) {
                        MarksheetTab.entries.forEach { tab ->
                            val selected = currentTab == tab
                            NavigationRailItem(
                                selected = selected,
                                onClick = {
                                    if (tab != MarksheetTab.HISTORY || currentTab == MarksheetTab.HISTORY) {
                                        viewModel.selectHistorySession(null)
                                    }
                                    currentTab = tab
                                },
                                icon = {
                                    Icon(
                                        imageVector = if (selected) tab.selectedIcon else tab.unselectedIcon,
                                        contentDescription = stringResource(tab.labelRes)
                                    )
                                },
                                label = { Text(stringResource(tab.labelRes)) },
                                modifier = Modifier.testTag("rail_${tab.route}")
                            )
                        }
                    }
                    VerticalDivider(
                        modifier = Modifier.fillMaxHeight(),
                        color = semantic.cardBorder,
                        thickness = 1.dp
                    )
                }

                Box(modifier = Modifier.weight(1f).fillMaxHeight()) {
                    when (currentTab) {
                        MarksheetTab.CALCULATOR -> {
                            CalculatorScreen(
                                session = activeSession,
                                hapticsEnabled = config.hapticsEnabled,
                                onRecordMark = { viewModel.recordMark(it) },
                                onSetQuestionMark = { sessionId, qNum, action ->
                                    viewModel.setQuestionMark(sessionId, qNum, action)
                                },
                                onUndo = { viewModel.undoLastMark() },
                                onClearSession = { viewModel.clearActiveSession() },
                                onFinishSession = { title -> viewModel.finishAndSaveSession(title) },
                                onRenameSession = { id, title -> viewModel.renameSession(id, title) }
                            )
                        }

                        MarksheetTab.HISTORY -> {
                            val detailSession = selectedHistorySession
                            if (detailSession != null) {
                                SessionDetailScreen(
                                    session = detailSession,
                                    onBack = { viewModel.selectHistorySession(null) },
                                    onOpenInCalculator = { sessionId ->
                                        viewModel.openSessionInCalculator(sessionId) {
                                            currentTab = MarksheetTab.CALCULATOR
                                        }
                                    },
                                    onRenameSession = { id, title ->
                                        viewModel.renameSession(id, title)
                                    },
                                    onDeleteSession = { id ->
                                        viewModel.deleteSession(id)
                                    },
                                    onSetQuestionMark = { sessionId, qNum, action ->
                                        viewModel.setQuestionMark(sessionId, qNum, action)
                                    }
                                )
                            } else {
                                HistoryScreen(
                                    sessions = historySessions,
                                    onSessionClick = { id -> viewModel.selectHistorySession(id) }
                                )
                            }
                        }

                        MarksheetTab.SETTINGS -> {
                            SettingsScreen(
                                config = config,
                                activeSessionAnsweredCount = activeSession?.summary?.answeredCount ?: 0,
                                onSetThemeMode = { viewModel.setThemeMode(it) },
                                onSetAmoledBlack = { viewModel.setAmoledBlack(it) },
                                onSetHapticsEnabled = { viewModel.setHapticsEnabled(it) },
                                onSaveMarkingConfig = { pos, neg, total, applyCurrent ->
                                    viewModel.saveMarkingConfiguration(pos, neg, total, applyCurrent)
                                },
                                onResetDefaults = { applyCurrent ->
                                    viewModel.resetDefaults(applyCurrent)
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}
