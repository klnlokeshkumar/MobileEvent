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

    fun activate(c: Context, source: String) {
        if (!StudyPrefs.isActive(c)) {
            val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager
            c.getSharedPreferences(RUNTIME, Context.MODE_PRIVATE).edit()
                .putInt(PREV_MEDIA, audio.getStreamVolume(AudioManager.STREAM_MUSIC))
                .putInt(PREV_RINGER, audio.ringerMode)
                .apply()

            StudyPrefs.setActive(c, true)
            StudyPrefs.setSource(c, source)
            StudyPrefs.setCurrentStart(c, System.currentTimeMillis())
        }
        applyFeatureSettings(c)
    }

    fun applyFeatureSettings(c: Context) {
        if (!StudyPrefs.isActive(c)) return

        val audio = c.getSystemService(Context.AUDIO_SERVICE) as AudioManager

        // Existing feature: media volume goes to zero.
        if (StudyPrefs.mediaMute(c)) {
            runCatching {
                audio.setStreamVolume(AudioManager.STREAM_MUSIC, 0, 0)
            }
        }

        // Requested behavior: TRUE SILENT mode. We do not manage vibration separately.
        if (StudyPrefs.silent(c)) {
            val nm = c.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            if (!nm.isNotificationPolicyAccessGranted) {
                AutomationNotifier.post(
                    c,
                    "Study Mode is active. Allow Notification Policy Access once to enable Silent Mode."
                )
                return
            }

            runCatching {
                audio.ringerMode = AudioManager.RINGER_MODE_SILENT
            }.onFailure {
                AutomationNotifier.post(c, "Silent Mode could not be enabled.")
                return@onFailure
            }

            if (audio.ringerMode != AudioManager.RINGER_MODE_SILENT) {
                AutomationNotifier.post(
                    c,
                    "HyperOS did not switch to Silent Mode. Please allow Notification Policy Access."
                )
            }
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
                    AutomationNotifier.post(
                        c,
                        "Study Mode OFF, but Silent Mode access is missing; ringer mode could not be restored."
                    )
                }
            }
        }

        runtime.edit().clear().apply()
        StudyPrefs.setActive(c, false)
        StudyPrefs.setSource(c, "")
        StudyPrefs.setCurrentStart(c, 0L)
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
