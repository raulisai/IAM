package com.example.iam

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.media.MediaPlayer
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.example.iam.network.TokenUploader
import com.example.iam.R

class MyFirebaseMessagingService : FirebaseMessagingService() {
    companion object {
        private const val TAG = "MyFirebaseMsgSvc"
        private const val CHANNEL_ID = "fcm_default_channel"
        private const val ALARM_CHANNEL_ID = "alarm_channel"
    }

    override fun onNewToken(token: String) {
        super.onNewToken(token)
        Log.d(TAG, "Token nuevo: $token")
        TokenUploader.uploadToken(applicationContext, token)
    }

    override fun onMessageReceived(remoteMessage: RemoteMessage) {
        super.onMessageReceived(remoteMessage)
        Log.d(TAG, "From: ${remoteMessage.from}")

        // Revisa si el mensaje tiene un "data payload"
        if (remoteMessage.data.isNotEmpty()) {
            Log.d(TAG, "Data payload: ${remoteMessage.data}")
            val type = remoteMessage.data["tipo"]
            if (type == "alarma") {
                // Es una notificación de tipo alarma
                dispararAlarma(remoteMessage.data["title"], remoteMessage.data["body"])
            } else {
                // Es una notificación de datos normal
                showNotification(remoteMessage.data["title"], remoteMessage.data["body"])
            }
        } else {
            // Si no hay "data payload", puede ser una notificación estándar desde la consola de Firebase
            remoteMessage.notification?.let {
                showNotification(it.title, it.body)
            }
        }
    }

    private fun showNotification(title: String?, body: String?) {
        val nm = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(CHANNEL_ID, "Notificaciones Generales", NotificationManager.IMPORTANCE_DEFAULT)
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

    private fun dispararAlarma(title: String?, body: String?) {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        // Crear un canal de notificación de alta prioridad para las alarmas (para Android 8.0+)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                ALARM_CHANNEL_ID, "Alarmas",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notificaciones críticas de alarma"
            }
            notificationManager.createNotificationChannel(channel)
        }

        val notification = NotificationCompat.Builder(this, ALARM_CHANNEL_ID)
            .setContentTitle(title ?: "¡Alarma!")
            .setContentText(body ?: "Ha llegado un trigger importante del backend.")
            .setSmallIcon(R.drawable.ic_notification) // <-- Considera cambiar esto a un icono de alarma como R.drawable.ic_alarm
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .build()

        // Usamos un ID fijo para las notificaciones de alarma para que se actualicen en lugar de apilarse
        notificationManager.notify(1, notification)

        // Reproducir un sonido de alarma
        try {
            // IMPORTANTE: Debes añadir un archivo de sonido (ej: sonido_alarma.mp3)
            // en la carpeta de recursos `res/raw`.
            val mediaPlayer = MediaPlayer.create(this, R.raw.sonido_alarma)
            mediaPlayer?.setOnCompletionListener { it.release() } // Libera recursos al terminar
            mediaPlayer?.start()
        } catch (e: Exception) {
            Log.e(TAG, "Error al reproducir sonido. ¿Falta el archivo en res/raw/sonido_alarma?", e)
        }
    }
}
