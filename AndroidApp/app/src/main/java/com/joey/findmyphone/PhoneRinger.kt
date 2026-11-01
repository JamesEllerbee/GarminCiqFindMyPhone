package com.joey.findmyphone

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.RingtoneManager
import android.os.Handler
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.Looper

/** Plays the default alarm sound on the alarm stream at max volume until stopped (or timed out). */
class PhoneRinger(
    private val context: Context,
    private val onStateChanged: (ringing: Boolean) -> Unit,
) {
    private val audio by lazy { context.getSystemService(AudioManager::class.java) }
    private val vibrator by lazy { context.getSystemService(Vibrator::class.java) }
    private val handler = Handler(Looper.getMainLooper())
    private var player: MediaPlayer? = null
    private var savedVolume = -1

    val isRinging: Boolean get() = player != null

    private val autoStop = Runnable { stop() }

    fun start() {
        if (isRinging) {
            // Restart the timeout; tell the watch we're (still) ringing.
            handler.removeCallbacks(autoStop)
            handler.postDelayed(autoStop, timeoutMs())
            onStateChanged(true)
            return
        }

        val uri = RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_ALARM)
            ?: RingtoneManager.getActualDefaultRingtoneUri(context, RingtoneManager.TYPE_RINGTONE)
            ?: RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM)

        savedVolume = audio.getStreamVolume(AudioManager.STREAM_ALARM)
        audio.setStreamVolume(
            AudioManager.STREAM_ALARM,
            audio.getStreamMaxVolume(AudioManager.STREAM_ALARM),
            0,
        )

        try {
            player = MediaPlayer().apply {
                setAudioAttributes(
                    AudioAttributes.Builder()
                        .setUsage(AudioAttributes.USAGE_ALARM)
                        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                        .build()
                )
                setDataSource(context, uri)
                isLooping = true
                prepare()
                start()
            }
        } catch (t: Throwable) {
            releasePlayer()
            restoreVolume()
            throw t
        }
        startVibration()
        handler.postDelayed(autoStop, timeoutMs())
        onStateChanged(true)
    }

    fun stop() {
        if (!isRinging) {
            onStateChanged(false)
            return
        }
        handler.removeCallbacks(autoStop)
        releasePlayer()
        stopVibration()
        restoreVolume()
        onStateChanged(false)
    }

    /** Stop silently (no state callback), e.g. when the service is destroyed. */
    fun release() {
        handler.removeCallbacks(autoStop)
        releasePlayer()
        stopVibration()
        restoreVolume()
    }

    private fun timeoutMs(): Long = AppPrefs.ringSeconds(context) * 1000L

    @Suppress("DEPRECATION")
    private fun startVibration() {
        try {
            if (vibrator?.hasVibrator() != true) return
            val effect = VibrationEffect.createWaveform(longArrayOf(0, 800, 600), 0)
            val attrs = AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_ALARM).build()
            vibrator.vibrate(effect, attrs)
        } catch (_: Throwable) {
            // Vibration is a nice-to-have; never let it break ringing.
        }
    }

    private fun stopVibration() {
        try {
            vibrator?.cancel()
        } catch (_: Throwable) {
        }
    }

    private fun releasePlayer() {
        player?.let {
            try {
                it.stop()
            } catch (_: IllegalStateException) {
            }
            it.release()
        }
        player = null
    }

    private fun restoreVolume() {
        if (savedVolume >= 0) {
            audio.setStreamVolume(AudioManager.STREAM_ALARM, savedVolume, 0)
            savedVolume = -1
        }
    }

}
