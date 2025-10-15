package com.example.iam

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.iam.network.TokenUploader
import java.util.concurrent.atomic.AtomicInteger

class MyFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "MyFirebaseMsgSvc"
        private const val CHANNEL_ID = "fcm_default_channel"
        private const val ALARM_CHANNEL_ID = "alarm_channel"
        // Use an AtomicInteger to ensure thread-safe increments for notification IDs.
        private val notificationId = AtomicInteger(0)
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Token nuevo: $token")
        TokenUploader.uploadToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            val type = remoteMessage.data["tipo"]
            if (type == "alarma") {
                Log.d(TAG, "Mensaje de debug")
                scheduleAlarm()
                showNotification(remoteMessage.data["title"], remoteMessage.data["body"], ALARM_CHANNEL_ID, NotificationCompat.PRIORITY_HIGH)
            } else {
                Log.d(TAG, "Mensaje de notificación")
                showNotification(remoteMessage.data["title"], remoteMessage.data["body"])
            }
        } else {
            remoteMessage.notification?.let {
                showNotification(it.title, it.body)
            }
        }
    }

    private fun scheduleAlarm() {
        val alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent(this, AlarmReceiver::class.java)
        val pendingIntent = PendingIntent.getBroadcast(this, 0, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val triggerTime = System.currentTimeMillis() + 500 // 500ms from now

        // On Android 12 (S) and above, check for permission to schedule exact alarms.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && alarmManager.canScheduleExactAlarms()) {
            // Permission is granted, schedule the exact alarm.
            alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.d(TAG, "Exact alarm scheduled successfully.")
        } else {
            // Permission not granted or on an older Android version.
            // Fall back to an inexact alarm. This will still work but might be slightly delayed.
            alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerTime, pendingIntent)
            Log.w(TAG, "Scheduling inexact alarm as permission for exact alarms is not granted.")
        }
    }

    private fun showNotification(title: String?, body: String?, channelId: String = CHANNEL_ID, priority: Int = NotificationCompat.PRIORITY_DEFAULT) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = if (channelId == ALARM_CHANNEL_ID) "Alarmas" else "Notificaciones Generales"
            val importance = if (channelId == ALARM_CHANNEL_ID) NotificationManager.IMPORTANCE_HIGH else NotificationManager.IMPORTANCE_DEFAULT
            val channel = NotificationChannel(channelId, name, importance)
            nm.createNotificationChannel(channel)
        }

        val n = NotificationCompat.Builder(this, channelId)
            .setContentTitle(title ?: "Nuevo mensaje")
            .setContentText(body ?: "")
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(priority)
            .setAutoCancel(true)
            .build()

        // Use an incrementing integer for the notification ID
        nm.notify(notificationId.incrementAndGet(), n)
    }
}
