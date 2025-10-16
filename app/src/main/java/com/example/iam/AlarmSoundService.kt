package com.example.iam

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat


class AlarmSoundService : Service(), MediaPlayer.OnCompletionListener, MediaPlayer.OnErrorListener {

    private var mediaPlayer: MediaPlayer? = null
    private var wakeLock: PowerManager.WakeLock? = null
    private val TAG = "AlarmSoundService"

    companion object {
        const val CHANNEL_ID = "AlarmSoundServiceChannel"
        const val NOTIFICATION_ID = 12345
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "AlarmSoundService started")

        // Acquire WakeLock
        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = powerManager.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "IAM::AlarmWakeLock")
        wakeLock?.acquire(10*60*1000L /*10 minutes timeout*/)

        createNotificationChannel()
        val notification = createNotification()
        startForeground(NOTIFICATION_ID, notification)

        playSound()

        return START_NOT_STICKY
    }

    private fun playSound() {
        if (mediaPlayer != null) {
            mediaPlayer?.release()
        }

        mediaPlayer = MediaPlayer.create(this, R.raw.trabajar_sonido)

        if (mediaPlayer == null) {
            Log.e(TAG, "Failed to create MediaPlayer, resource might be missing.")
            stopSelf()
            return
        }

        // This is crucial: use the ALARM stream
        mediaPlayer?.setAudioAttributes(
            AudioAttributes.Builder()
                .setLegacyStreamType(AudioManager.STREAM_ALARM)
                .setUsage(AudioAttributes.USAGE_ALARM)
                .build()
        )

        mediaPlayer?.setOnCompletionListener(this)
        mediaPlayer?.setOnErrorListener(this)
        mediaPlayer?.start()
        Log.d(TAG, "MediaPlayer started")
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Alarm Sound Service Channel",
                NotificationManager.IMPORTANCE_HIGH
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(serviceChannel)
        }
    }

    private fun createNotification(): Notification {
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Alarma sonando")
            .setContentText("La alarma se está reproduciendo.")
            .setSmallIcon(R.drawable.ic_notification)
            .build()
    }

    override fun onCompletion(mp: MediaPlayer?) {
        Log.d(TAG, "MediaPlayer completed.")
        stopSelf()
    }

    override fun onError(mp: MediaPlayer?, what: Int, extra: Int): Boolean {
        Log.e(TAG, "MediaPlayer error: what=$what, extra=$extra")
        stopSelf()
        return true // Indicates we handled the error
    }

    override fun onDestroy() {
        super.onDestroy()
        mediaPlayer?.release()
        mediaPlayer = null
        wakeLock?.let {
            if (it.isHeld) {
                it.release()
            }
        }
        Log.d(TAG, "AlarmSoundService destroyed")
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}
