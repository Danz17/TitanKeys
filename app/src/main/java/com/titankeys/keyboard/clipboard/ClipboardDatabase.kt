package com.titankeys.keyboard.clipboard

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * SQLite database helper for clipboard history.
 */
class ClipboardDatabase private constructor(context: Context) :
    SQLiteOpenHelper(context, NAME, null, VERSION) {

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(ClipboardDao.CREATE_TABLE)
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // Migration from version 1 to 2: add IMAGE_PATH column
        if (oldVersion < 2) {
            db.execSQL("ALTER TABLE ${ClipboardDao.TABLE} ADD COLUMN ${ClipboardDao.COLUMN_IMAGE_PATH} TEXT")
        }
    }

    companion object {
        private const val TAG = "ClipboardDatabase"
        private const val VERSION = 2 // Incremented for image support
        private const val NAME = "titankeys_clipboard.db"

        @Volatile
        private var instance: ClipboardDatabase? = null

        fun getInstance(context: Context): ClipboardDatabase {
            return instance ?: synchronized(this) {
                instance ?: ClipboardDatabase(context.applicationContext).also {
                    instance = it
                }
            }
        }
    }
}
