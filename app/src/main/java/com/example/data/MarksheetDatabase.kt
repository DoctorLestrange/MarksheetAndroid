package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [ExamSessionEntity::class, QuestionRecordEntity::class],
    version = 1,
    exportSchema = false
)
abstract class MarksheetDatabase : RoomDatabase() {
    abstract fun marksheetDao(): MarksheetDao

    companion object {
        @Volatile
        private var INSTANCE: MarksheetDatabase? = null

        fun getInstance(context: Context): MarksheetDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    MarksheetDatabase::class.java,
                    "marksheet_db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}
