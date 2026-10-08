package com.example.screenoffmute

import android.content.Context

object EventPrefs {
    private const val PREFS = "events"
    private const val BATTERY_ENABLED = "battery_enabled"
    private const val BATTERY_HIGH = "battery_high"
    private const val BATTERY_LOW = "battery_low"
    private const val BATTERY_HIGH_INTERVAL = "battery_high_interval"
    private const val BATTERY_LOW_INTERVAL = "battery_low_interval"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun isBatteryEnabled(c: Context) = p(c).getBoolean(BATTERY_ENABLED, false)
    fun setBatteryEnabled(c: Context, v: Boolean) =
        p(c).edit().putBoolean(BATTERY_ENABLED, v).apply()

    fun batteryHigh(c: Context) = p(c).getInt(BATTERY_HIGH, 90)
    fun setBatteryHigh(c: Context, v: Int) =
        p(c).edit().putInt(BATTERY_HIGH, v.coerceIn(1, 100)).apply()

    fun batteryLow(c: Context) = p(c).getInt(BATTERY_LOW, 30)
    fun setBatteryLow(c: Context, v: Int) =
        p(c).edit().putInt(BATTERY_LOW, v.coerceIn(0, 99)).apply()

    fun batteryHighInterval(c: Context) = p(c).getInt(BATTERY_HIGH_INTERVAL, 2)
    fun setBatteryHighInterval(c: Context, v: Int) =
        p(c).edit().putInt(BATTERY_HIGH_INTERVAL, v.coerceIn(1, 60)).apply()

    fun batteryLowInterval(c: Context) = p(c).getInt(BATTERY_LOW_INTERVAL, 10)
    fun setBatteryLowInterval(c: Context, v: Int) =
        p(c).edit().putInt(BATTERY_LOW_INTERVAL, v.coerceIn(1, 60)).apply()

    // Backward-compatible access for older builds.
    fun batteryThreshold(c: Context) = batteryHigh(c)
    fun setBatteryThreshold(c: Context, v: Int) = setBatteryHigh(c, v)

    // BootReceiver uses this to decide whether the automation service should
    // be started again after a reboot. Boot auto-start is intentionally kept.
    fun anyEnabled(c: Context) =
        isBatteryEnabled(c) || StudyPrefs.isAutoWifiEnabled(c) || StudyPrefs.isActive(c)
}
