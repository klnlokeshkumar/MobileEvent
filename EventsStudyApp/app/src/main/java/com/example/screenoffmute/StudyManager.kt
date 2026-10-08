package com.example.screenoffmute

import android.app.NotificationManager
import android.content.Context
import android.media.AudioManager
import org.json.JSONArray
import org.json.JSONObject

object StudyManager {
    private const val RUNTIME = "study_runtime"
    private const val PREV_MEDIA = "prev_media"
    private const val PREV_RINGER = "prev_ringer"
    private const val SILENT_RECHECK_MS = 30 * 60 * 1000L

    fun activate(c: Context, source: String) {
        var newSession = false
        if (!StudyPrefs.isActive(c)) {
            val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            c.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE).edit()
                .putInt(PREV_MEDIA, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                .putInt(PREV_RINGER, audio.ringerMode)
                .apply()

            StudyPrefs.setActive(c, true)
            StudyPrefs.setSource(c, source)
            StudyPrefs.setCurrentStart(c, System.currentTimeMillis())
            StudyPrefs.setSilentCheckAt(c, System.currentTimeMillis())
            newSession = true
        }
        // Apply entry actions only for a genuinely new Study Mode session.
        // A service restart, network callback, or UI refresh while the phone is
        // already in the Study Hall must never reset media volume again.
        if (newSession) applyFeatureSettings(c)
    }

    fun applyFeatureSettings(c: Context) {
        if (!StudyPrefs.isActive(c)) return

        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Entry action: media volume goes to zero once when Study Mode starts.
        if (StudyPrefs.mediaMute(c)) {
            runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
        }

        applySilentMode(c)
    }

    // Used for periodic/background checks. This deliberately does NOT touch
    // media volume, so a user can temporarily raise media volume while still
    // connected to the Study Hall Wi-Fi without Events resetting it to zero.
    fun reapplySilentMode(c: Context) {
        if (!StudyPrefs.isActive(c)) return
        if (!StudyPrefs.silent(c)) return

        val now = System.currentTimeMillis()
        val last = StudyPrefs.silentCheckAt(c)
        if (last > 0L && now - last < SILENT_RECHECK_MS) return

        // Re-check/re-apply Silent Mode only every 30 minutes while the
        // Study Hall session remains active. This allows the user to
        // temporarily turn Silent Mode off without Events immediately
        // switching it back on.
        applySilentMode(c)
        StudyPrefs.setSilentCheckAt(c, now)
    }

    private fun applySilentMode(c: Context) {
        if (!StudyPrefs.silent(c)) return

        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

        if (!nm.isNotificationPolicyAccessGranted) {
            return
        }

        runCatching {
            audio.ringerMode = AudioManager.RINGER_MODE_SILENT
        }.onFailure {
            return@onFailure
        }
    }

    fun deactivate(c: Context, reason: String) {
        if (!StudyPrefs.isActive(c)) {
            if (reason == "wifi_disconnected" || reason == "wifi_changed") {
                StudyPrefs.setWifiConnected(c, false)
            }
            return
        }

        val start = StudyPrefs.currentStart(c)
        val now = System.currentTimeMillis()

        if (start > 0) {
            runCatching {
                val a = JSONArray(StudyPrefs.statsJson(c))
                a.put(JSONObject().apply {
                    put("start", start)
                    put("end", now)
                    put("source", StudyPrefs.source(c))
                    put("reason", reason)
                })
                while (a.length() > 300) a.remove(0)
                StudyPrefs.setStatsJson(c, a.toString())
            }
        }

        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
        val runtime = c.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE)

        // Restore the exact values captured before Study Mode activated.
        runCatching {
            if (runtime.contains(PREV_MEDIA)) {
                audio.setStreamVolume(
                    AudioManager.STREAM_MUSIC,
                    runtime.getInt(
                        PREV_MEDIA,
                        audio.getStreamVolume(AudioManager.STREAM_MUSIC)
                    ),
                    0
                )
            }

            if (runtime.contains(PREV_RINGER)) {
                val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
                if (nm.isNotificationPolicyAccessGranted) {
                    audio.ringerMode = runtime.getInt(
                        PREV_RINGER,
                        AudioManager.RINGER_MODE_NORMAL
                    )
                } else {
                }
            }
        }

        runtime.edit().clear().apply()
        StudyPrefs.setActive(c, false)
        StudyPrefs.setSource(c, "")
        StudyPrefs.setCurrentStart(c, 0L)
        StudyPrefs.setSilentCheckAt(c, 0L)
        StudyPrefs.setWifiConnected(c, false)

        AutomationNotifier.post(c, "Study Mode OFF — previous sound settings restored")
    }

    fun wifiState(c: Context, connected: Boolean) {
        StudyPrefs.setWifiConnected(c, connected)
        if (!StudyPrefs.isAutoWifiEnabled(c)) return

        if (connected) {
            activate(c, "wifi")
        } else {
            deactivate(c, "wifi_disconnected")
        }
    }
}
