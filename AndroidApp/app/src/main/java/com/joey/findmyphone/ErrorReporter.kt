package com.joey.findmyphone

import android.content.Context
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import java.text.DateFormat
import java.util.Date

/**
 * Persists errors so they can be shown in a dialog, even after the process crashed.
 * Useful for debugging without logcat / developer mode.
 */
object ErrorReporter {
    private const val PREFS = "error_reporter"
    private const val KEY = "pending"
    private const val KEY_CRASHES = "crash_times"
    private const val MAX_CRASHES_KEPT = 5
    private const val TAG = "ErrorReporter"

    /** Latest unseen error text; observed by the UI. */
    var latest: String? by mutableStateOf(null)
        private set

    /** Call once from Application.onCreate: loads a previous crash and hooks uncaught exceptions. */
    fun install(context: Context) {
        val app = context.applicationContext
        latest = app.prefs().getString(KEY, null)

        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, e ->
            try {
                report(app, "Uncaught crash on thread '${thread.name}'", e)
                recordCrash(app)
            } catch (_: Throwable) {
            }
            previous?.uncaughtException(thread, e)
        }
    }

    fun report(context: Context, where: String, t: Throwable) =
        report(context, where, Log.getStackTraceString(t))

    fun report(context: Context, where: String, message: String) {
        Log.e(TAG, "$where: $message")
        val text = "$where\n${DateFormat.getDateTimeInstance().format(Date())}\n\n$message"
        // commit() (not apply()) so it is written even if the process dies right after.
        context.applicationContext.prefs().edit().putString(KEY, text).commit()
        latest = text
    }

    /** Number of uncaught crashes recorded within the last [windowMs] milliseconds. */
    fun recentCrashCount(context: Context, windowMs: Long): Int {
        val now = System.currentTimeMillis()
        return crashTimes(context).count { now - it in 0..windowMs }
    }

    private fun recordCrash(context: Context) {
        val times = (crashTimes(context) + System.currentTimeMillis()).takeLast(MAX_CRASHES_KEPT)
        context.applicationContext.prefs().edit()
            .putString(KEY_CRASHES, times.joinToString(","))
            .commit()
    }

    private fun crashTimes(context: Context): List<Long> =
        context.applicationContext.prefs().getString(KEY_CRASHES, null)
            ?.split(",")?.mapNotNull { it.toLongOrNull() }.orEmpty()

    fun clear(context: Context) {
        context.applicationContext.prefs().edit().remove(KEY).remove(KEY_CRASHES).commit()
        latest = null
    }

    private fun Context.prefs() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
