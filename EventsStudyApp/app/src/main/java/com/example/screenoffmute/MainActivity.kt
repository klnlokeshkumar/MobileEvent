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
        root.addView(remindersCard())
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

        fun addSetting(
            labelPrefix: String,
            min: Int,
            max: Int,
            initial: Int,
            onValue: (Int) -> Unit
        ): SeekBar {
            val label = TextView(this).apply {
                textSize = 15f
                setTextColor(Color.DKGRAY)
            }
            card.addView(label, lp())
            val bar = SeekBar(this).apply {
                this.max = max - min
                this.progress = (initial.coerceIn(min, max) - min)
            }
            card.addView(bar, lp())
            fun sync() {
                val value = min + bar.progress
                label.text = "$labelPrefix: $value${if (labelPrefix.contains("threshold", true)) "%" else " minute(s)"}"
                onValue(value)
            }
            bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) = sync()
                override fun onStartTrackingTouch(seekBar: SeekBar?) = Unit
                override fun onStopTrackingTouch(seekBar: SeekBar?) = Unit
            })
            sync()
            return bar
        }

        addSetting(
            "Highest battery threshold", 1, 100, EventPrefs.batteryHigh(this)
        ) { value ->
            val low = EventPrefs.batteryLow(this)
            if (value <= low) EventPrefs.setBatteryLow(this, (value - 1).coerceAtLeast(0))
            EventPrefs.setBatteryHigh(this, value)
        }

        addSetting(
            "Lowest battery threshold", 0, 99, EventPrefs.batteryLow(this)
        ) { value ->
            val high = EventPrefs.batteryHigh(this)
            if (value >= high) EventPrefs.setBatteryHigh(this, (value + 1).coerceAtMost(100))
            EventPrefs.setBatteryLow(this, value)
        }

        addSetting(
            "High-level alarm interval", 1, 60, EventPrefs.batteryHighInterval(this)
        ) { EventPrefs.setBatteryHighInterval(this, it) }

        addSetting(
            "Low-level alarm interval", 1, 60, EventPrefs.batteryLowInterval(this)
        ) { EventPrefs.setBatteryLowInterval(this, it) }

        card.addView(TextView(this).apply {
            text = """Charging at/above the highest level: "Energised, Enough of charging" every selected interval until the charger is disconnected.

Below the lowest level while not charging: "I am thirsty, Please connect the charger" every selected interval until the charger is connected.

The two thresholds must remain separate. Battery reminders depend on charging status.

Tip: keep Events allowed to run in the background and set Battery to No restrictions on HyperOS for reliable reminders."""
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())
        return card
    }


    private fun remindersCard(): LinearLayout {
        ReminderStore.ensureDefaults(this)
        val card = card("REMINDERS & HOME WIDGETS")
        card.addView(TextView(this).apply {
            text = "Create reminder cards with a title, date and optional note. Add a 2×2 home-screen widget for each reminder; tap the widget to edit it."
            textSize = 13f
            setTextColor(Color.DKGRAY)
        }, lp())

        card.addView(Button(this).apply {
            text = "ADD NEW REMINDER"
            setOnClickListener {
                startActivity(Intent(this@MainActivity, ReminderEditActivity::class.java))
            }
        }, lp())

        ReminderStore.all(this).forEach { reminder ->
            card.addView(Button(this).apply {
                text = "EDIT: ${reminder.name} — ${reminder.date}"
                setOnClickListener {
                    startActivity(Intent(this@MainActivity, ReminderEditActivity::class.java).apply {
                        putExtra(ReminderEditActivity.EXTRA_REMINDER_ID, reminder.id)
                    })
                }
            }, lp())
        }
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
        val today = java.util.Calendar.getInstance()
        showStatsForDate(today.get(java.util.Calendar.YEAR), today.get(java.util.Calendar.MONTH), today.get(java.util.Calendar.DAY_OF_MONTH))
    }

    private fun showStatsForDate(year: Int, month: Int, day: Int) {
        val calendar = java.util.Calendar.getInstance().apply {
            set(java.util.Calendar.YEAR, year)
            set(java.util.Calendar.MONTH, month)
            set(java.util.Calendar.DAY_OF_MONTH, day)
            set(java.util.Calendar.HOUR_OF_DAY, 0)
            set(java.util.Calendar.MINUTE, 0)
            set(java.util.Calendar.SECOND, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }
        val dayStart = calendar.timeInMillis
        val dayEnd = dayStart + 24L * 60L * 60L * 1000L
        val dayLabel = java.text.SimpleDateFormat("dd MMM yyyy", Locale.getDefault()).format(java.util.Date(dayStart))

        val array = org.json.JSONArray(StudyPrefs.statsJson(this))
        val seen = mutableSetOf<String>()
        val rows = mutableListOf<String>()
        var total = 0L
        var hall = 0L

        for (i in 0 until array.length()) {
            val obj = array.optJSONObject(i) ?: continue
            val start = obj.optLong("start")
            val end = obj.optLong("end")
            if (start <= 0L || end <= 0L || end <= start) continue

            // De-duplicate identical saved sessions. This also protects the
            // statistics display if an older build wrote the same session twice.
            val key = "$start|$end|${obj.optString("source")}"
            if (!seen.add(key)) continue

            // Include a session if any part of it falls on the selected date.
            val clippedStart = maxOf(start, dayStart)
            val clippedEnd = minOf(end, dayEnd)
            if (clippedEnd <= clippedStart) continue

            val duration = clippedEnd - clippedStart
            total += duration
            if (obj.optString("source") == "wifi") hall += duration

            val startText = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(clippedStart))
            val endText = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(clippedEnd))
            rows.add("$startText → $endText  ${fmt(duration)}  [${obj.optString("source")}]")
        }

        // Include only the portion of a currently active session that falls
        // inside the selected date.
        if (StudyPrefs.isActive(this) && StudyPrefs.currentStart(this) > 0L) {
            val start = StudyPrefs.currentStart(this)
            val end = System.currentTimeMillis()
            val clippedStart = maxOf(start, dayStart)
            val clippedEnd = minOf(end, dayEnd)
            if (clippedEnd > clippedStart) {
                val duration = clippedEnd - clippedStart
                total += duration
                if (StudyPrefs.source(this) == "wifi") hall += duration
                val startText = java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(clippedStart))
                val endText = if (clippedEnd == end) "now" else java.text.SimpleDateFormat("HH:mm", Locale.getDefault()).format(java.util.Date(clippedEnd))
                rows.add("$startText → $endText  ${fmt(duration)}  [${StudyPrefs.source(this)} — active]")
            }
        }

        val message = buildString {
            append("Total Study Mode: ${fmt(total)}")
            append("\nStudy-hall Wi-Fi time: ${fmt(hall)}")
            append("\nSessions: ${rows.size}")
            if (rows.isNotEmpty()) append("\n\n${rows.joinToString("\n")}")
            else append("\n\nNo Study Mode activity recorded for this date.")
        }

        AlertDialog.Builder(this)
            .setTitle("Study Hall Statistics — $dayLabel")
            .setMessage(message)
            .setNeutralButton("SELECT DATE") { _, _ ->
                val picker = android.app.DatePickerDialog(
                    this,
                    { _, y, m, d -> showStatsForDate(y, m, d) },
                    year,
                    month,
                    day
                )
                picker.show()
            }
            .setPositiveButton("OK", null)
            .show()
    }

    private fun fmt(ms: Long): String {
        val minutes = ms / 60_000L
        return "${minutes / 60}h ${minutes % 60}m"
    }
}
