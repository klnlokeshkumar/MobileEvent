package com.example.screenoffmute

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context

object AutomationNotifier {
    private const val CHANNEL = "automation"
    private const val FOREGROUND_ID = 2001

    fun ensureChannel(c: Context) {
        c.getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel(CHANNEL, "Events automation", NotificationManager.IMPORTANCE_LOW)
        )
    }

    // Normal event notification. It is dismissible like a regular messaging notification and stays until the user dismisses it.
    fun post(c: Context, text: String) {
        runCatching {
            ensureChannel(c)
            val id = (System.currentTimeMillis() and 0x7fffffff).toInt()
            c.getSystemService(NotificationManager::class.java).notify(
                id,
                eventNotification(c, text)
            )
        }
    }

    // Required foreground-service notification. Android requires a foreground
    // service to keep a notification while the service is running.
    fun notification(c: Context, text: String): Notification =
        Notification.Builder(c, CHANNEL)
            .setContentTitle("Events")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setCategory(Notification.CATEGORY_SERVICE)
            .setOngoing(true)
            .setShowWhen(false)
            .build()

    private fun eventNotification(c: Context, text: String): Notification =
        Notification.Builder(c, CHANNEL)
            .setContentTitle("Events")
            .setContentText(text)
            .setSmallIcon(android.R.drawable.ic_popup_sync)
            .setCategory(Notification.CATEGORY_EVENT)
            .setAutoCancel(true)
            .setShowWhen(true)
            .build()
}
