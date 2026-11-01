package com.joey.findmyphone

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import com.garmin.android.connectiq.ConnectIQ
import com.garmin.android.connectiq.ConnectIQ.ConnectIQListener
import com.garmin.android.connectiq.ConnectIQ.IQConnectType
import com.garmin.android.connectiq.ConnectIQ.IQMessageStatus
import com.garmin.android.connectiq.ConnectIQ.IQSdkErrorStatus
import com.garmin.android.connectiq.IQApp
import com.garmin.android.connectiq.IQDevice
import com.garmin.android.connectiq.IQDevice.IQDeviceStatus

/**
 * Foreground service that stays connected to Garmin Connect Mobile and listens for
 * messages from the watch app.
 */
class FindMyPhoneService : Service() {

    private lateinit var connectIQ: ConnectIQ
    private val watchApp = IQApp(WATCH_APP_ID)
    private var sdkReady = false
    private var lastDevice: IQDevice? = null
    private var destroyed = false
    private var reportedNoDevice = false
    private var retryDelayMs = RETRY_MIN_MS
    private val handler = Handler(Looper.getMainLooper())
    private var retryRunnable = Runnable {}
    // Lazy: a Service has no base context until after construction.
    private val ringerDelegate = lazy { PhoneRinger(this) { ringing -> onRingingChanged(ringing) } }
    private val ringer by ringerDelegate

    override fun onCreate() {
        super.onCreate()
        running = true
        try {
            startForegroundWithNotification()

            // Crash-loop guard: with START_STICKY a crashing service is restarted over and over,
            // which can prevent the error dialog from ever showing. Stop cleanly instead; the
            // saved error stays pending so the dialog shows it on next app open.
            if (ErrorReporter.recentCrashCount(this, CRASH_LOOP_WINDOW_MS) >= 2) {
                Log.w(TAG, "Crashed repeatedly; not restarting service")
                stopSelf()
                return
            }

            initializeSdk()
        } catch (t: Throwable) {
            ErrorReporter.report(this, "FindMyPhoneService.onCreate failed", t)
            stopSelf()
        }
    }

    private fun initializeSdk() {
        if (destroyed) return
        connectIQ = ConnectIQ.getInstance(this, IQConnectType.WIRELESS)
        connectIQ.initialize(this, true, object : ConnectIQListener {
            override fun onSdkReady() {
                Log.i(TAG, "Connect IQ SDK ready")
                sdkReady = true
                retryDelayMs = RETRY_MIN_MS
                registerForWatchMessages()
            }

            override fun onInitializeError(status: IQSdkErrorStatus) {
                if (status == IQSdkErrorStatus.SERVICE_ERROR) {
                    // Garmin Connect may be restarting; keep trying quietly.
                    Log.w(TAG, "Connect IQ SERVICE_ERROR; retrying in ${retryDelayMs}ms")
                    scheduleRetry { initializeSdk() }
                    return
                }
                ErrorReporter.report(
                    this@FindMyPhoneService,
                    "Connect IQ SDK init failed",
                    "IQSdkErrorStatus: $status\n\n" +
                        "GCM_NOT_INSTALLED = Garmin Connect Mobile is not installed\n" +
                        "GCM_UPGRADE_NEEDED = Garmin Connect Mobile needs updating",
                )
                stopSelf()
            }

            override fun onSdkShutDown() {
                Log.i(TAG, "Connect IQ SDK shut down")
                sdkReady = false
                // Garmin Connect went away (update, crash, force stop): reconnect when it is back.
                if (!destroyed) scheduleRetry { initializeSdk() }
            }
        })
    }

    /** Runs [action] after an exponentially growing delay (5 s .. 60 s), replacing any pending retry. */
    private fun scheduleRetry(action: () -> Unit) {
        handler.removeCallbacks(retryRunnable)
        retryRunnable = Runnable {
            try {
                action()
            } catch (t: Throwable) {
                ErrorReporter.report(this, "Retry failed", t)
            }
        }
        handler.postDelayed(retryRunnable, retryDelayMs)
        retryDelayMs = (retryDelayMs * 2).coerceAtMost(RETRY_MAX_MS)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) ringer.stop()
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        destroyed = true
        running = false
        handler.removeCallbacks(retryRunnable)
        if (ringerDelegate.isInitialized()) ringer.release()
        if (sdkReady) {
            try {
                connectIQ.unregisterAllForEvents()
                connectIQ.shutdown(this)
            } catch (t: Throwable) {
                ErrorReporter.report(this, "Error shutting down Connect IQ SDK", t)
            }
        }
        super.onDestroy()
    }

    private fun registerForWatchMessages() {
        try {
            val devices = connectIQ.knownDevices.orEmpty()
            Log.i(TAG, "Known devices: $devices")
            if (devices.isEmpty()) {
                if (!reportedNoDevice) {
                    reportedNoDevice = true
                    ErrorReporter.report(
                        this,
                        "No Garmin device found",
                        "Connect IQ reports no known devices. Open Garmin Connect Mobile and make " +
                            "sure your watch is paired. The app keeps retrying in the background.",
                    )
                }
                scheduleRetry { registerForWatchMessages() }
                return
            }
            devices.forEach { device -> registerDevice(device) }
        } catch (t: Throwable) {
            ErrorReporter.report(this, "Failed to register for watch messages", t)
        }
    }

    private fun registerDevice(device: IQDevice) {
        connectIQ.registerForEvents(
            device,
            { dev, deviceStatus ->
                Log.i(TAG, "Device ${dev.friendlyName} is $deviceStatus")
                // Re-register when the watch (re)connects so we never miss its messages.
                if (deviceStatus == IQDeviceStatus.CONNECTED && sdkReady) {
                    try {
                        connectIQ.registerForAppEvents(dev, watchApp) { d, _, messages, status ->
                            onWatchMessage(d, messages, status)
                        }
                    } catch (t: Throwable) {
                        ErrorReporter.report(this, "Failed to re-register for watch messages", t)
                    }
                }
            },
            watchApp,
        ) { dev, _, messages, status -> onWatchMessage(dev, messages, status) }
    }

    private fun onWatchMessage(device: IQDevice, messages: List<Any>, status: IQMessageStatus) {
        Log.i(TAG, "Message from ${device.friendlyName}: $messages ($status)")
        if (status != IQMessageStatus.SUCCESS) {
            ErrorReporter.report(this, "Watch message failed", "IQMessageStatus: $status")
            return
        }
        lastDevice = device
        try {
            messages.forEach { payload ->
                when (commandOf(payload)) {
                    Protocol.CMD_RING -> ringer.start()
                    Protocol.CMD_STOP -> ringer.stop()
                    else -> Log.w(TAG, "Ignoring unknown payload: $payload")
                }
            }
        } catch (t: Throwable) {
            ErrorReporter.report(this, "Failed to handle watch message $messages", t)
        }
    }

    private fun commandOf(payload: Any?): String? = when (payload) {
        is Map<*, *> -> payload[Protocol.KEY_CMD]?.toString()
        is String -> payload
        else -> null
    }

    /** Called whenever ringing starts/stops (including phone-side Stop and timeout). */
    private fun onRingingChanged(ringing: Boolean) {
        updateNotification(ringing)
        sendStateToWatch(ringing)
    }

    private fun sendStateToWatch(ringing: Boolean) {
        val device = lastDevice ?: return
        if (!sdkReady) return
        try {
            val state = if (ringing) Protocol.STATE_RINGING else Protocol.STATE_STOPPED
            connectIQ.sendMessage(device, watchApp, mapOf(Protocol.KEY_STATE to state)) { _, _, status ->
                if (status != IQMessageStatus.SUCCESS) Log.w(TAG, "Ack to watch failed: $status")
            }
        } catch (t: Throwable) {
            ErrorReporter.report(this, "Failed to send state to watch", t)
        }
    }

    private fun createNotificationChannel() {
        getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                getString(R.string.notification_channel_name),
                NotificationManager.IMPORTANCE_LOW,
            )
        )
    }

    private fun buildNotification(ringing: Boolean): Notification {
        val builder = Notification.Builder(this, CHANNEL_ID)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText(
                getString(if (ringing) R.string.notification_text_ringing else R.string.notification_text)
            )
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setOngoing(true)
        if (ringing) {
            val stop = PendingIntent.getService(
                this,
                0,
                Intent(this, FindMyPhoneService::class.java).setAction(ACTION_STOP),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            builder.addAction(
                Notification.Action.Builder(null, getString(R.string.notification_action_stop), stop).build()
            )
        }
        return builder.build()
    }

    private fun startForegroundWithNotification() {
        createNotificationChannel()
        startForeground(
            NOTIFICATION_ID,
            buildNotification(false),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
    }

    private fun updateNotification(ringing: Boolean) {
        getSystemService(NotificationManager::class.java)
            .notify(NOTIFICATION_ID, buildNotification(ringing))
    }

    companion object {
        private const val TAG = "FindMyPhoneService"
        private const val CHANNEL_ID = "watch_connection"
        private const val NOTIFICATION_ID = 1
        private const val CRASH_LOOP_WINDOW_MS = 60_000L
        private const val RETRY_MIN_MS = 5_000L
        private const val RETRY_MAX_MS = 60_000L

        /** True while a service instance exists in this process; used by the UI. */
        @Volatile
        var running = false
            private set
        private const val ACTION_STOP = "com.joey.findmyphone.action.STOP"
    }
}
