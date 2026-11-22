package com.example.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.domain.AppConfig
import com.example.domain.ScoreMath
import com.example.domain.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "marksheet_settings"
)

class SettingsDataStore(context: Context) {
    private val dataStore = context.applicationContext.settingsDataStore

    private object Keys {
        val POSITIVE_MARK = stringPreferencesKey("positive_mark")
        val NEGATIVE_MARK = stringPreferencesKey("negative_mark")
        val TOTAL_QUESTIONS = intPreferencesKey("total_questions")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val AMOLED_BLACK = booleanPreferencesKey("amoled_black")
        val HAPTICS_ENABLED = booleanPreferencesKey("haptics_enabled")
    }

    val configFlow: Flow<AppConfig> = dataStore.data
        .catch { exception ->
            if (exception is IOException) {
                emit(emptyPreferences())
            } else {
                throw exception
            }
        }
        .map { prefs ->
            val rawPos = prefs[Keys.POSITIVE_MARK] ?: AppConfig.DEFAULT_POSITIVE_MARK
            val rawNeg = prefs[Keys.NEGATIVE_MARK] ?: AppConfig.DEFAULT_NEGATIVE_MARK
            val totalQ = (prefs[Keys.TOTAL_QUESTIONS] ?: AppConfig.DEFAULT_TOTAL_QUESTIONS)
                .coerceIn(1, 1000)
            val themeName = prefs[Keys.THEME_MODE] ?: ThemeMode.LIGHT.name
            val themeMode = runCatching { ThemeMode.valueOf(themeName) }.getOrDefault(ThemeMode.LIGHT)

            AppConfig(
                positiveMark = ScoreMath.parsePositiveMark(rawPos)?.let { ScoreMath.formatMark(it) }
                    ?: AppConfig.DEFAULT_POSITIVE_MARK,
                negativeMark = ScoreMath.parseNegativeMark(rawNeg)?.let { ScoreMath.formatMark(it) }
                    ?: AppConfig.DEFAULT_NEGATIVE_MARK,
                totalQuestions = totalQ,
                themeMode = themeMode,
                amoledBlack = prefs[Keys.AMOLED_BLACK] ?: false,
                hapticsEnabled = prefs[Keys.HAPTICS_ENABLED] ?: true
            )
        }

    suspend fun getCurrentConfig(): AppConfig = configFlow.first()

    suspend fun updateMarkingScheme(
        positiveMark: String,
        negativeMark: String,
        totalQuestions: Int
    ) {
        val validPos = ScoreMath.parsePositiveMark(positiveMark) ?: return
        val validNeg = ScoreMath.parseNegativeMark(negativeMark) ?: return
        val validTotal = totalQuestions.coerceIn(1, 1000)
        dataStore.edit { prefs ->
            prefs[Keys.POSITIVE_MARK] = ScoreMath.formatMark(validPos)
            prefs[Keys.NEGATIVE_MARK] = ScoreMath.formatMark(validNeg)
            prefs[Keys.TOTAL_QUESTIONS] = validTotal
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        dataStore.edit { prefs ->
            prefs[Keys.THEME_MODE] = mode.name
        }
    }

    suspend fun setAmoledBlack(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.AMOLED_BLACK] = enabled
        }
    }

    suspend fun setHapticsEnabled(enabled: Boolean) {
        dataStore.edit { prefs ->
            prefs[Keys.HAPTICS_ENABLED] = enabled
        }
    }

    suspend fun resetDefaults() {
        dataStore.edit { prefs ->
            prefs[Keys.POSITIVE_MARK] = AppConfig.DEFAULT_POSITIVE_MARK
            prefs[Keys.NEGATIVE_MARK] = AppConfig.DEFAULT_NEGATIVE_MARK
            prefs[Keys.TOTAL_QUESTIONS] = AppConfig.DEFAULT_TOTAL_QUESTIONS
        }
    }
}
