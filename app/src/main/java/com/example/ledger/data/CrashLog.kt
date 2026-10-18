package com.example.ledger.data

import android.content.Context
import java.io.PrintWriter
import java.io.StringWriter

/**
 * Keeps the details of the last crash (or background error) on the phone, so the person can copy
 * them and send them. Nothing is sent anywhere by the app.
 */
object CrashLog {
    private const val PREFS = "crash_log"
    private const val KEY = "last"

    fun save(context: Context, e: Throwable, fatal: Boolean) {
        val sw = StringWriter()
        e.printStackTrace(PrintWriter(sw))
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).versionName
        } catch (x: Exception) {
            "?"
        }
        val text = (if (fatal) "App closed" else "Background error") + " · v" + version + " · " +
            java.time.LocalDateTime.now().withNano(0) + " · Android " + android.os.Build.VERSION.RELEASE + "\n" +
            sw.toString().lines().take(40).joinToString("\n")
        // commit(), not apply(): the process may be about to end.
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putString(KEY, text).commit()
    }

    fun read(context: Context): String? = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null)

    fun clear(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().remove(KEY).apply()
    }
}
