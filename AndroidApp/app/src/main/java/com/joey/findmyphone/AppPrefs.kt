package com.joey.findmyphone

import android.content.Context

/** Small persisted settings: whether the user wants the service running, and ring duration. */
object AppPrefs {
    private const val PREFS = "app_prefs"
    private const val KEY_ENABLED = "service_enabled"
    private const val KEY_RING_SECONDS = "ring_seconds"

    val RING_SECONDS_OPTIONS = listOf(30, 60, 120, 300)
    const val DEFAULT_RING_SECONDS = 60

    private fun Context.prefs() =
        applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isServiceEnabled(context: Context): Boolean =
        context.prefs().getBoolean(KEY_ENABLED, false)

    fun setServiceEnabled(context: Context, enabled: Boolean) {
        context.prefs().edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    fun ringSeconds(context: Context): Int =
        context.prefs().getInt(KEY_RING_SECONDS, DEFAULT_RING_SECONDS)

    fun setRingSeconds(context: Context, seconds: Int) {
        context.prefs().edit().putInt(KEY_RING_SECONDS, seconds).apply()
    }
}
