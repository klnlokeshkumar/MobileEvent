package com.example.screenoffmute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AutomationNotifier {
    private const val CHANNEL = "automation"
    private const val ID = 2001

    fun ensureChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Events automation", NotificationManager.IMPORTANCE_LOW)
        )
    }

    fun post(c: Context, text: String) {
        runCatching {
            ensureChannel(c)
            c.getSystemService(NotificationManager::class.java).notify(ID, notification(c, text))
        }
    }

    fun notification(c: Context, text: String): Notification =
        Notification.Builder(c, CHANNEL)
            .setContentTitle("Events")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()
}
