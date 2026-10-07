package com.example.screenoffmute

import android.content.Context
import org.json.JSONArray

object StudyPrefs {
    private const val PREFS="study"
    private const val AUTO_WIFI="auto_wifi"; private const val WIFI_SSID="wifi_ssid"; private const val WIFI_FP="wifi_fp"; private const val WIFI_DESC="wifi_desc"; private const val ACTIVE="active"; private const val SOURCE="source"; private const val OVERRIDE="override"
    private const val MEDIA="media"; private const val SILENT="silent"; private const val VIBRATE_OLD="vibrate"; private const val DND="dnd"; private const val ANTI="anti"; private const val APPS="apps"; private const val WIFI_CONNECTED="wifi_connected"; private const val STATS="stats"; private const val START="start"
    private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun isAutoWifiEnabled(c:Context)=p(c).getBoolean(AUTO_WIFI,false); fun setAutoWifiEnabled(c:Context,v:Boolean)=p(c).edit().putBoolean(AUTO_WIFI,v).apply()
    fun wifiSsid(c:Context)=p(c).getString(WIFI_SSID,"")?:""; fun setWifiSsid(c:Context,v:String)=p(c).edit().putString(WIFI_SSID,v).apply(); fun wifiFingerprint(c:Context)=p(c).getString(WIFI_FP,"")?:""; fun setWifiFingerprint(c:Context,v:String)=p(c).edit().putString(WIFI_FP,v).apply(); fun wifiFingerprintDescription(c:Context)=p(c).getString(WIFI_DESC,"")?:""; fun setWifiFingerprintDescription(c:Context,v:String)=p(c).edit().putString(WIFI_DESC,v).apply()
    fun isActive(c:Context)=p(c).getBoolean(ACTIVE,false); fun setActive(c:Context,v:Boolean)=p(c).edit().putBoolean(ACTIVE,v).apply()
    fun source(c:Context)=p(c).getString(SOURCE,"")?:""; fun setSource(c:Context,v:String)=p(c).edit().putString(SOURCE,v).apply()
    fun manualOverride(c:Context)=p(c).getBoolean(OVERRIDE,false); fun setManualOverride(c:Context,v:Boolean)=p(c).edit().putBoolean(OVERRIDE,v).apply()
    fun mediaMute(c:Context)=p(c).getBoolean(MEDIA,true); fun setMediaMute(c:Context,v:Boolean)=p(c).edit().putBoolean(MEDIA,v).apply()
    fun silent(c:Context)=p(c).getBoolean(SILENT,p(c).getBoolean(VIBRATE_OLD,true)); fun setSilent(c:Context,v:Boolean)=p(c).edit().putBoolean(SILENT,v).apply()
    fun dnd(c:Context)=p(c).getBoolean(DND,true); fun setDnd(c:Context,v:Boolean)=p(c).edit().putBoolean(DND,v).apply()
    fun anti(c:Context)=p(c).getBoolean(ANTI,false); fun setAnti(c:Context,v:Boolean)=p(c).edit().putBoolean(ANTI,v).apply()
    fun apps(c:Context):Set<String>{ val a=JSONArray(p(c).getString(APPS,"[]")?:"[]"); return buildSet{for(i in 0 until a.length()) add(a.optString(i))} }
    fun setApps(c:Context,v:Set<String>){val a=JSONArray();v.sorted().forEach{a.put(it)};p(c).edit().putString(APPS,a.toString()).apply()}
    fun wifiConnected(c:Context)=p(c).getBoolean(WIFI_CONNECTED,false); fun setWifiConnected(c:Context,v:Boolean)=p(c).edit().putBoolean(WIFI_CONNECTED,v).apply()
    fun currentStart(c:Context)=p(c).getLong(START,0L); fun setCurrentStart(c:Context,v:Long)=p(c).edit().putLong(START,v).apply()
    fun statsJson(c:Context)=p(c).getString(STATS,"[]")?:"[]"; fun setStatsJson(c:Context,v:String)=p(c).edit().putString(STATS,v).apply()
}
