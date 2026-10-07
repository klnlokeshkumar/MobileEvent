package com.example.screenoffmute

import android.content.Context

object EventPrefs {
    private const val PREFS = "events"
    private const val SCREEN_ENABLED = "screen_off_enabled"
    private const val DELAY_MS = "screen_off_delay"
    private const val BATTERY_ENABLED = "battery_enabled"
    private const val BATTERY_THRESHOLD = "battery_threshold"
    private fun p(c: Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun isScreenMuteEnabled(c:Context)=p(c).getBoolean(SCREEN_ENABLED,true)
    fun setScreenMuteEnabled(c:Context,v:Boolean)=p(c).edit().putBoolean(SCREEN_ENABLED,v).apply()
    fun delayMs(c:Context)=p(c).getLong(DELAY_MS,60_000L)
    fun isBatteryEnabled(c:Context)=p(c).getBoolean(BATTERY_ENABLED,false)
    fun setBatteryEnabled(c:Context,v:Boolean)=p(c).edit().putBoolean(BATTERY_ENABLED,v).apply()
    fun batteryThreshold(c:Context)=p(c).getInt(BATTERY_THRESHOLD,90)
    fun setBatteryThreshold(c:Context,v:Int)=p(c).edit().putInt(BATTERY_THRESHOLD,v).apply()
    fun anyEnabled(c:Context)=isScreenMuteEnabled(c)||isBatteryEnabled(c)||StudyPrefs.isAutoWifiEnabled(c)||StudyPrefs.isActive(c)
}
