package com.example.screenoffmute

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

class BootReceiver:BroadcastReceiver(){override fun onReceive(c:Context,intent:Intent?){if(EventPrefs.anyEnabled(c))runCatching{ContextCompat.startForegroundService(c,Intent(c,AutomationService::class.java))}}}
