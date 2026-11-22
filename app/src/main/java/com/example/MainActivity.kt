package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.MarksheetApp
import com.example.ui.MarksheetViewModel
import com.example.ui.theme.MarksheetTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val repository = (application as MarksheetApplication).repository
        val factory = MarksheetViewModel.Factory(repository)

        setContent {
            val viewModel: MarksheetViewModel = viewModel(factory = factory)
            val config by viewModel.configState.collectAsStateWithLifecycle()

            // Wait briefly for persisted DataStore theme before rendering so dark mode never flashes white
            val loadedConfig = config ?: return@setContent

            MarksheetTheme(
                themeMode = loadedConfig.themeMode,
                amoledBlack = loadedConfig.amoledBlack
            ) {
                MarksheetApp(
                    viewModel = viewModel,
                    config = loadedConfig
                )
            }
        }
    }
}
