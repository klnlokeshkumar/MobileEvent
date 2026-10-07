package com.example.screenoffmute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class ServiceRestartReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent?) {
        if (EventPrefs.anyEnabled(context)) {
            runCatching {
                ContextCompat.startForegroundService(
                    context,
                    Intent(context, AutomationService::class.java)
                )
            }
        }
    }
}
