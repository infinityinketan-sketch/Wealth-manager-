package com.example.ledger.data

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.net.Uri
import com.example.ledger.core.BackupCrypto
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject

/**
 * Backup and restore of everything the person has: all tables plus app settings, in one file
 * encrypted with their password. The file goes wherever they choose (Google Drive, Downloads…);
 * the app itself never uploads anything.
 */
object Backup {
    private val tables = listOf(
        "accounts", "categories", "transactions", "budgets", "recurring_rules", "merchant_rules", "dues", "overrides"
    )
    private const val PREFS = "ledger_settings"
    private const val FORMAT = 1

    data class Summary(val transactions: Int, val categories: Int, val createdMillis: Long)

    private fun cursorRow(c: Cursor): JSONObject {
        val o = JSONObject()
        for (i in 0 until c.columnCount) {
            val name = c.getColumnName(i)
            when (c.getType(i)) {
                Cursor.FIELD_TYPE_NULL -> o.put(name, JSONObject.NULL)
                Cursor.FIELD_TYPE_INTEGER -> o.put(name, c.getLong(i))
                Cursor.FIELD_TYPE_FLOAT -> o.put(name, c.getDouble(i))
                else -> o.put(name, c.getString(i))
            }
        }
        return o
    }

    suspend fun export(context: Context, db: LedgerDatabase, password: CharArray, uri: Uri): Summary = withContext(Dispatchers.IO) {
        val sql = db.openHelper.readableDatabase
        val root = JSONObject()
        root.put("format", FORMAT)
        root.put("dbVersion", sql.version)
        root.put("created", System.currentTimeMillis())
        val data = JSONObject()
        var txCount = 0
        var catCount = 0
        for (t in tables) {
            val rows = JSONArray()
            sql.query("SELECT * FROM `$t`").use { c -> while (c.moveToNext()) rows.put(cursorRow(c)) }
            if (t == "transactions") txCount = rows.length()
            if (t == "categories") catCount = rows.length()
            data.put(t, rows)
        }
        root.put("tables", data)

        // Settings, with their types so they come back exactly.
        val prefs = JSONArray()
        for ((k, v) in context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).all) {
            val e = JSONObject().put("k", k)
            when (v) {
                is Boolean -> e.put("t", "b").put("v", v)
                is Int -> e.put("t", "i").put("v", v)
                is Long -> e.put("t", "l").put("v", v)
                is String -> e.put("t", "s").put("v", v)
                is Set<*> -> e.put("t", "set").put("v", JSONArray(v.map { it.toString() }))
                else -> continue
            }
            prefs.put(e)
        }
        root.put("settings", prefs)

        val file = BackupCrypto.encrypt(root.toString().toByteArray(Charsets.UTF_8), password)
        context.contentResolver.openOutputStream(uri, "w")?.use { it.write(file) } ?: error("Could not write the backup file")
        Summary(txCount, catCount, root.getLong("created"))
    }

    /** Replaces everything on this phone with the backup. Throws [BackupCrypto.WrongPasswordOrFile]. */
    suspend fun restore(context: Context, db: LedgerDatabase, password: CharArray, uri: Uri): Summary = withContext(Dispatchers.IO) {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: error("Could not open the file")
        val root = JSONObject(String(BackupCrypto.decrypt(bytes, password), Charsets.UTF_8))
        val sql = db.openHelper.writableDatabase
        if (root.optInt("dbVersion", 0) > sql.version) error("This backup is from a newer version of the app. Update the app first.")
        val data = root.getJSONObject("tables")
        var txCount = 0
        var catCount = 0
        sql.beginTransaction()
        try {
            for (t in tables) {
                // Only columns this version knows; columns added later get their defaults.
                val known = HashSet<String>()
                sql.query("PRAGMA table_info(`$t`)").use { c -> while (c.moveToNext()) known.add(c.getString(c.getColumnIndexOrThrow("name"))) }
                sql.execSQL("DELETE FROM `$t`")
                val rows = data.optJSONArray(t) ?: continue
                for (i in 0 until rows.length()) {
                    val r = rows.getJSONObject(i)
                    val cv = ContentValues()
                    for (k in r.keys()) {
                        if (k !in known) continue
                        when (val v = r.get(k)) {
                            JSONObject.NULL -> cv.putNull(k)
                            is Int -> cv.put(k, v.toLong())
                            is Long -> cv.put(k, v)
                            is Double -> cv.put(k, v)
                            is Boolean -> cv.put(k, if (v) 1L else 0L)
                            else -> cv.put(k, v.toString())
                        }
                    }
                    sql.insert(t, android.database.sqlite.SQLiteDatabase.CONFLICT_REPLACE, cv)
                }
                if (t == "transactions") txCount = rows.length()
                if (t == "categories") catCount = rows.length()
            }
            sql.setTransactionSuccessful()
        } finally {
            sql.endTransaction()
        }
        db.invalidationTracker.refreshVersionsAsync()

        val current = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val editor = current.edit()
        root.optJSONArray("settings")?.let { list ->
            for (i in 0 until list.length()) {
                val e = list.getJSONObject(i)
                val k = e.getString("k")
                // Pro comes only from Google Play, never from a file.
                if (k.startsWith("sub_") || k == "pro_purchased" || k == "trial_end_seen" || k == "trial_start" || k == "usage_consent" || k == "latest_seen") continue
                // Keep the earlier first-use date so the trial is not restarted by a restore.
                if (k == "first_use") {
                    val mine = current.getLong(k, 0L)
                    val theirs = e.getLong("v")
                    editor.putLong(k, if (mine == 0L) theirs else minOf(mine, theirs))
                    continue
                }
                when (e.getString("t")) {
                    "b" -> editor.putBoolean(k, e.getBoolean("v"))
                    "i" -> editor.putInt(k, e.getInt("v"))
                    "l" -> editor.putLong(k, e.getLong("v"))
                    "s" -> editor.putString(k, e.getString("v"))
                    "set" -> {
                        val a = e.getJSONArray("v")
                        editor.putStringSet(k, (0 until a.length()).map { a.getString(it) }.toSet())
                    }
                }
            }
        }
        editor.commit()
        Summary(txCount, catCount, root.optLong("created", 0L))
    }
}
