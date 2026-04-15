package com.ultron.app.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat

object NotificationHelper {
    const val CHANNEL_ID = "ultron_listener"
    const val NOTIFICATION_ID = 1

    fun createChannel(context: Context) {
        val channel = NotificationChannel(
            CHANNEL_ID,
            "Ultron 음성 대기",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "울트론 웨이크워드 감지 중"
        }
        val manager = context.getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(channel)
    }

    fun buildListeningNotification(context: Context, status: String = "대기 중"): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setContentTitle("Ultron")
            .setContentText(status)
            .setSmallIcon(android.R.drawable.ic_btn_speak_now)
            .setOngoing(true)
            .build()
    }
}
