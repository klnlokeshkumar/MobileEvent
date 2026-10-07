package com.example.screenoffmute

import android.app.Activity
import android.app.AlertDialog
import android.app.NotificationManager
import android.content.Intent
import android.graphics.Color
import android.os.Bundle
import android.provider.Settings
import android.view.ViewGroup
import android.widget.*
import androidx.core.content.ContextCompat
import java.util.Locale

class MainActivity : Activity() {
    private lateinit var wifiStatus: TextView
    private lateinit var silentSwitch: Switch

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        buildUi()
        startAutomation()
    }

    override fun onResume() {
        super.onResume()
        if (::wifiStatus.isInitialized) refreshUi()
    }

    private fun buildUi() {
        val scroll = ScrollView(this)
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 30, 24, 30)
        }
        scroll.addView(root)

        root.addView(TextView(this).apply {
            text = "Events"
            textSize = 30f
            setTextColor(Color.BLACK)
        }, lp())

        root.addView(TextView(this).apply {
            text = "Automation for reading halls and battery reminders"
            textSize = 16f
            setTextColor(Color.DKGRAY)
        }, lp())

        root.addView(studyEvent())
        root.addView(eventBattery())
        root.addView(hyperosCard())
        setContentView(scroll)
        refreshUi()
    }

    private fun studyEvent(): LinearLayout {
        val card = card("EVENT — Study Hall Wi-Fi → Study Mode")

        wifiStatus = TextView(this).apply {
            textSize = 14f
            setTextColor(Color.DKGRAY)
        }
        card.addView(wifiStatus, lp())

        val autoWifi = Switch(this).apply {
            text = "Auto-activate for study-hall Wi-Fi"
            isChecked = StudyPrefs.isAutoWifiEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, value ->
                safeUi {
                    if (value) {
                        saveCurrentWifi()
                    } else {
                        StudyPrefs.setAutoWifiEnabled(this@MainActivity, false)
                        StudyManager.wifiState(this@MainActivity, false)
                        startAutomation()
                        refreshUi()
                    }
                }
            }
        }
        card.addView(autoWifi, lp())

        card.addView(Button(this).apply {
            text = "USE CURRENT WI-FI AS STUDY HALL"
            setOnClickListener { safeUi { saveCurrentWifi() } }
        }, lp())

        card.addView(TextView(this).apply {
            text = "Wi-Fi fingerprint is used; phone Location can stay OFF. Study Mode activates only on the saved network and deactivates on disconnect or when you join a different network."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())

        card.addView(Switch(this).apply {
            text = "Media volume → 0"
            isChecked = StudyPrefs.mediaMute(this@MainActivity)
            setOnCheckedChangeListener { _, value ->
                StudyPrefs.setMediaMute(this@MainActivity, value)
                if (StudyPrefs.isActive(this@MainActivity)) {
                    StudyManager.applyFeatureSettings(this@MainActivity)
                }
            }
        }, lp())

        silentSwitch = Switch(this).apply {
            text = "Silent mode"
            isChecked = StudyPrefs.silent(this@MainActivity)
            setOnCheckedChangeListener { _, value ->
                StudyPrefs.setSilent(this@MainActivity, value)

                if (value && !hasSilentAccess()) {
                    Toast.makeText(
                        this@MainActivity,
                        "Allow Notification Policy Access so Events can switch the phone to Silent Mode.",
                        Toast.LENGTH_LONG
                    ).show()
                    startActivity(
                        Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                    )
                } else if (StudyPrefs.isActive(this@MainActivity)) {
                    StudyManager.applyFeatureSettings(this@MainActivity)
                }
            }
        }
        card.addView(silentSwitch, lp())

        card.addView(Button(this).apply {
            text = "ALLOW SILENT MODE ACCESS"
            setOnClickListener {
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                )
            }
        }, lp())

        card.addView(TextView(this).apply {
            text = if (hasSilentAccess()) {
                "Silent Mode access: allowed. Events will switch to Silent automatically on the saved Wi-Fi."
            } else {
                "Silent Mode access: not allowed yet. Tap the button above once."
            }
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())

        card.addView(Button(this).apply {
            text = "Study Hall Statistics"
            setOnClickListener { showStats() }
        }, lp())

        return card
    }

    private fun eventBattery(): LinearLayout {
        val card = card("EVENT — Battery Indications")

        card.addView(Switch(this).apply {
            text = "Battery Indications ON / OFF"
            isChecked = EventPrefs.isBatteryEnabled(this@MainActivity)
            setOnCheckedChangeListener { _, value ->
                EventPrefs.setBatteryEnabled(this@MainActivity, value)
                startAutomation()
            }
        }, lp())

        val label = TextView(this).apply { textSize = 15f }
        card.addView(label, lp())

        val bar = SeekBar(this).apply {
            max = 50
            progress = EventPrefs.batteryThreshold(this@MainActivity) - 50
        }
        card.addView(bar, lp())

        fun sync() {
            val pct = 50 + bar.progress
            EventPrefs.setBatteryThreshold(this@MainActivity, pct)
            label.text = "Alert threshold: $pct%"
        }

        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = sync()
            override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
            override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
        })
        sync()

        card.addView(TextView(this).apply {
            text = "At/above threshold: 2-second alarm every 2 minutes while charging until unplugged; every 5 minutes when not charging."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())
        return card
    }

    private fun hyperosCard(): LinearLayout {
        val card = card("REDMI / HYPEROS")
        card.addView(Button(this).apply {
            text = "OPEN BATTERY SETTINGS"
            setOnClickListener {
                runCatching {
                    startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
                }.getOrElse {
                    startActivity(Intent(Settings.ACTION_SETTINGS))
                }
            }
        }, lp())
        card.addView(TextView(this).apply {
            text = "For reliability: allow Autostart and No restrictions battery use if available. Do not force-stop Events."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())
        return card
    }

    private fun card(title: String): LinearLayout = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(20, 18, 20, 18)
        setBackgroundColor(Color.rgb(245, 247, 250))
        addView(TextView(this@MainActivity).apply {
            text = title
            textSize = 19f
            setTextColor(Color.BLACK)
            setTypeface(null, android.graphics.Typeface.BOLD)
        }, lp())
    }

    private fun lp(): LinearLayout.LayoutParams = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 12 }

    private fun refreshUi() {
        if (!::wifiStatus.isInitialized) return
        val saved = StudyPrefs.wifiFingerprint(this).isNotBlank()
        val desc = StudyPrefs.wifiFingerprintDescription(this)
        val status = if (StudyPrefs.wifiConnected(this)) "CONNECTED — Study Mode active" else "not connected to saved study-hall Wi-Fi"
        wifiStatus.text = buildString {
            append("Study-hall Wi-Fi: ")
            append(if (saved) "Saved network" else "Not set")
            if (desc.isNotBlank()) append("\n$desc")
            append("\nStatus: $status")
        }
        if (::silentSwitch.isInitialized) silentSwitch.isChecked = StudyPrefs.silent(this)
    }

    private fun startAutomation() {
        runCatching {
            ContextCompat.startForegroundService(this, Intent(this, AutomationService::class.java))
        }.onFailure {
            Toast.makeText(this, "Background automation could not start: ${it.message ?: "unknown error"}", Toast.LENGTH_LONG).show()
        }
    }

    private fun saveCurrentWifi() {
        runCatching {
            val snapshot = WifiIdentity.current(this)
                ?: throw IllegalStateException(
                    "No active Wi-Fi network detected. Connect to the study-hall Wi-Fi first."
                )

            val oldFingerprint = StudyPrefs.wifiFingerprint(this)
            if (StudyPrefs.isActive(this) &&
                oldFingerprint.isNotBlank() &&
                oldFingerprint != snapshot.fingerprint
            ) {
                StudyManager.deactivate(this, "wifi_changed")
            }

            // Only this button changes the saved Wi-Fi.
            StudyPrefs.setWifiFingerprint(this, snapshot.fingerprint)
            StudyPrefs.setWifiFingerprintDescription(this, snapshot.description)
            StudyPrefs.setWifiSsid(this, "")
            StudyPrefs.setAutoWifiEnabled(this, true)
            StudyPrefs.setWifiConnected(this, true)

            // We are currently on the network, so activate immediately.
            StudyManager.wifiState(this, true)
            startAutomation()
            refreshUi()

            Toast.makeText(
                this,
                "Study-hall Wi-Fi saved.",
                Toast.LENGTH_SHORT
            ).show()

            if (StudyPrefs.silent(this) && !hasSilentAccess()) {
                Toast.makeText(
                    this,
                    "One-time setup: allow Silent Mode access.",
                    Toast.LENGTH_LONG
                ).show()
                startActivity(
                    Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
                )
            }
        }.onFailure {
            Toast.makeText(
                this,
                "Could not save Wi-Fi: ${it.message ?: "unknown error"}",
                Toast.LENGTH_LONG
            ).show()
        }
    }

    private fun safeUi(action: () -> Unit) {
        runCatching { action() }.onFailure {
            Toast.makeText(this, "Action failed: ${it.message ?: it::class.java.simpleName}", Toast.LENGTH_LONG).show()
        }
    }

    private fun hasSilentAccess(): Boolean {
        val nm = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        return runCatching { nm.isNotificationPolicyAccessGranted }.getOrDefault(false)
    }

    private fun showStats() {
        val array = org.json.JSONArray(StudyPrefs.statsJson(this))
        var total = 0L
        var hall = 0L
        val rows = mutableListOf<String>()
        for (i in array.length() - 1 downTo 0) {
            val obj = array.optJSONObject(i) ?: continue
            val start = obj.optLong("start")
            val end = obj.optLong("end")
            val duration = (end - start).coerceAtLeast(0L)
            total += duration
            if (obj.optString("source") == "wifi") hall += duration
            val startText = java.text.SimpleDateFormat("dd MMM, HH:mm", Locale.getDefault()).format(java.util.Date(start))
            val endText = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(end))
            rows.add("$startText → $endText  ${fmt(duration)}  [${obj.optString("source")}]")
        }
        if (StudyPrefs.isActive(this) && StudyPrefs.currentStart(this) > 0) {
            val duration = System.currentTimeMillis() - StudyPrefs.currentStart(this)
            total += duration
            if (StudyPrefs.source(this) == "wifi") hall += duration
        }
        val message = buildString {
            append("Total Study Mode: ${fmt(total)}")
            append("\nStudy-hall Wi-Fi time: ${fmt(hall)}")
            append("\nCompleted sessions: ${rows.size}")
            if (rows.isNotEmpty()) append("\n\n${rows.take(20).joinToString("\n")}")
        }
        AlertDialog.Builder(this).setTitle("Study Hall Statistics").setMessage(message).setPositiveButton("OK", null).show()
    }

    private fun fmt(ms: Long): String {
        val minutes = ms / 60_000L
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
