package de.zeiterfassung.data

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import de.zeiterfassung.domain.WorkInterval
import de.zeiterfassung.domain.WorkIntervalStore

class SQLiteWorkIntervalStore(context: Context) : WorkIntervalStore {
    private val helper = WorkDatabase(context.applicationContext)

    override fun getAll(): List<WorkInterval> {
        val intervals = mutableListOf<WorkInterval>()
        helper.readableDatabase.query(
            TABLE_INTERVALS,
            arrayOf(COLUMN_ID, COLUMN_START, COLUMN_END),
            null,
            null,
            null,
            null,
            "$COLUMN_START DESC"
        ).use { cursor ->
            while (cursor.moveToNext()) {
                intervals += WorkInterval(
                    id = cursor.getLong(0),
                    startEpochMillis = cursor.getLong(1),
                    endEpochMillis = if (cursor.isNull(2)) null else cursor.getLong(2)
                )
            }
        }
        return intervals
    }

    override fun save(interval: WorkInterval): WorkInterval {
        val values = ContentValues().apply {
            put(COLUMN_START, interval.startEpochMillis)
            interval.endEpochMillis?.let { put(COLUMN_END, it) } ?: putNull(COLUMN_END)
        }
        val database = helper.writableDatabase
        val id = if (interval.id == 0L) {
            database.insertOrThrow(TABLE_INTERVALS, null, values)
        } else {
            val updated = database.update(
                TABLE_INTERVALS,
                values,
                "$COLUMN_ID = ?",
                arrayOf(interval.id.toString())
            )
            if (updated == 0) throw IllegalStateException("Der Zeiteintrag wurde nicht gefunden.")
            interval.id
        }
        return interval.copy(id = id)
    }

    override fun delete(id: Long) {
        val deleted = helper.writableDatabase.delete(
            TABLE_INTERVALS,
            "$COLUMN_ID = ?",
            arrayOf(id.toString())
        )
        if (deleted == 0) throw IllegalStateException("Der Zeiteintrag wurde nicht gefunden.")
    }

    private class WorkDatabase(context: Context) :
        SQLiteOpenHelper(context, DATABASE_NAME, null, DATABASE_VERSION) {
        override fun onCreate(database: SQLiteDatabase) {
            database.execSQL(
                """
                CREATE TABLE $TABLE_INTERVALS (
                    $COLUMN_ID INTEGER PRIMARY KEY AUTOINCREMENT,
                    $COLUMN_START INTEGER NOT NULL,
                    $COLUMN_END INTEGER
                )
                """.trimIndent()
            )
        }

        override fun onUpgrade(database: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val DATABASE_NAME = "work-times.db"
        const val DATABASE_VERSION = 1
        const val TABLE_INTERVALS = "work_intervals"
        const val COLUMN_ID = "id"
        const val COLUMN_START = "start_epoch_millis"
        const val COLUMN_END = "end_epoch_millis"
    }
}
