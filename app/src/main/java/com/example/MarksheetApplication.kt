package com.example

import android.app.Application
import com.example.data.MarksheetDatabase
import com.example.data.MarksheetRepository
import com.example.data.SettingsDataStore

class MarksheetApplication : Application() {
    val database: MarksheetDatabase by lazy {
        MarksheetDatabase.getInstance(this)
    }

    val settingsDataStore: SettingsDataStore by lazy {
        SettingsDataStore(this)
    }

    val repository: MarksheetRepository by lazy {
        MarksheetRepository(
            database = database,
            dao = database.marksheetDao(),
            settingsDataStore = settingsDataStore
        )
    }
}
