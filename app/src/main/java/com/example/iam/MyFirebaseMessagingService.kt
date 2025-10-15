package com.example.iam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.iam.network.TokenUploader

class MyFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "MyFirebaseMsgSvc"
        private const val CHANNEL_ID = "fcm_default_channel"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Token nuevo: $token")
        // Envía token a tu backend (implementa la función real según tu capa de red)
        TokenUploader.uploadToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // data payload (recomendado)
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            showNotification(remoteMessage.data["title"], remoteMessage.data["body"])
        }

        // notification payload (sistema puede manejarla si app está background)
        remoteMessage.notification?.let {
            showNotification(it.title, it.body)
        }
    }

    private fun showNotification(title: String?, body: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Notificaciones", NotificationManager.IMPORTANCE_HIGH)
            nm.createNotificationChannel(channel)
        }

        val n = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle(title ?: "Nuevo mensaje")
            .setContentText(body ?: "")
            .setSmallIcon(R.drawable.ic_notification)
            .setAutoCancel(true)
            .build()

        nm.notify(System.currentTimeMillis().toInt(), n)
    }
}