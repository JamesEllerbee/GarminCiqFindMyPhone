package com.joey.findmyphone

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/** Restarts the service after a reboot if the user had it enabled. */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_BOOT_COMPLETED) return
        if (!AppPrefs.isServiceEnabled(context)) return
        try {
            ContextCompat.startForegroundService(context, Intent(context, FindMyPhoneService::class.java))
        } catch (t: Throwable) {
            ErrorReporter.report(context, "Failed to start service on boot", t)
        }
    }
}
