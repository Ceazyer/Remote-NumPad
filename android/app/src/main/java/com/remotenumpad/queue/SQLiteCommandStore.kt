package com.remotenumpad.queue

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.util.UUID

class SQLiteCommandStore(context: Context) :
    SQLiteOpenHelper(context.applicationContext, DATABASE_NAME, null, DATABASE_VERSION), CommandStore {

    override val clientId: String
        get() = synchronized(this) {
            val db = writableDatabase
            readMeta(db, CLIENT_ID_KEY) ?: UUID.randomUUID().toString().also { id ->
                writeMeta(db, CLIENT_ID_KEY, id)
            }
        }

    override fun pending(): List<QueuedCommand> = synchronized(this) {
        val result = mutableListOf<QueuedCommand>()
        readableDatabase.rawQuery(
            "SELECT sequence, command FROM commands ORDER BY sequence ASC",
            null
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += QueuedCommand(cursor.getLong(0), cursor.getString(1))
            }
        }
        result
    }

    override fun enqueue(command: String, capacity: Int): QueuedCommand? = synchronized(this) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val count = db.rawQuery("SELECT COUNT(*) FROM commands", null).use { cursor ->
                if (cursor.moveToFirst()) cursor.getInt(0) else 0
            }
            if (count >= capacity) return@synchronized null

            val values = ContentValues().apply { put("command", command) }
            val sequence = db.insertOrThrow("commands", null, values)
            db.setTransactionSuccessful()
            QueuedCommand(sequence, command)
        } finally {
            db.endTransaction()
        }
    }

    override fun acknowledge(sequence: Long): Boolean = synchronized(this) {
        val db = writableDatabase
        db.beginTransaction()
        try {
            val headSequence = db.rawQuery(
                "SELECT sequence FROM commands ORDER BY sequence ASC LIMIT 1",
                null
            ).use { cursor -> if (cursor.moveToFirst()) cursor.getLong(0) else null }

            if (headSequence != sequence) return@synchronized false
            val deleted = db.delete("commands", "sequence = ?", arrayOf(sequence.toString())) == 1
            if (deleted) db.setTransactionSuccessful()
            deleted
        } finally {
            db.endTransaction()
        }
    }

    override fun clear() {
        synchronized(this) {
            writableDatabase.delete("commands", null, null)
        }
    }

    override fun serverInstanceId(): String? = synchronized(this) {
        readMeta(readableDatabase, SERVER_INSTANCE_KEY)
    }

    override fun saveServerInstanceId(id: String) = synchronized(this) {
        writeMeta(writableDatabase, SERVER_INSTANCE_KEY, id)
    }

    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE commands (sequence INTEGER PRIMARY KEY AUTOINCREMENT, command TEXT NOT NULL)")
        db.execSQL("CREATE TABLE metadata (key TEXT PRIMARY KEY NOT NULL, value TEXT NOT NULL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit

    private fun readMeta(db: SQLiteDatabase, key: String): String? = db.rawQuery(
        "SELECT value FROM metadata WHERE key = ?",
        arrayOf(key)
    ).use { cursor -> if (cursor.moveToFirst()) cursor.getString(0) else null }

    private fun writeMeta(db: SQLiteDatabase, key: String, value: String) {
        val values = ContentValues().apply {
            put("key", key)
            put("value", value)
        }
        db.insertWithOnConflict("metadata", null, values, SQLiteDatabase.CONFLICT_REPLACE)
    }

    companion object {
        private const val DATABASE_NAME = "remote-numpad-queue.db"
        private const val DATABASE_VERSION = 1
        private const val CLIENT_ID_KEY = "client_id"
        private const val SERVER_INSTANCE_KEY = "server_instance_id"
    }
}
