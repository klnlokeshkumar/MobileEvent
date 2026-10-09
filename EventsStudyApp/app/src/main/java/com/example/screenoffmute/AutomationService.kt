package com.example.screenoffmute

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.MediaPlayer
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import androidx.core.content.ContextCompat

class AutomationService : Service() {
    private val handler = Handler(android.os.Looper.getMainLooper())
    private var lastWifi = false
    private var wifiMismatchSince = 0L

    // Wi-Fi state can briefly look unavailable while Android refreshes the
    // active network or LinkProperties. Never end Study Mode on the first
    // transient miss. A continuous 45-second mismatch is required before
    // we accept that the user really left/changed networks.
    private companion object {
        const val WIFI_POLL_MS = 10_000L
        const val WIFI_LOSS_GRACE_MS = 45_000L
    }
    private var lastBatteryAlert = 0L
    private var lastBatteryState = -1
    private var batteryPlayer: MediaPlayer? = null

    private val batteryPoll = object : Runnable {
        override fun run() {
            runCatching { checkCurrentBattery() }
            handler.postDelayed(this, 30_000L)
        }
    }

    private val wifiPoll = object : Runnable {
        override fun run() {
            runCatching { evaluateWifi() }
            handler.postDelayed(this, WIFI_POLL_MS)
        }
    }

    private val batteryReceiver = object : BroadcastReceiver() {
        override fun onReceive(c: Context?, i: Intent?) {
            runCatching { handleBattery(i) }
        }
    }

    private val networkCallback = object : ConnectivityManager.NetworkCallback() {
        override fun onAvailable(n: Network) {
            runCatching { evaluateWifi() }
        }

        override fun onLost(n: Network) {
            runCatching { evaluateWifi() }
        }

        override fun onCapabilitiesChanged(n: Network, c: NetworkCapabilities) {
            if (c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) {
                runCatching { evaluateWifi() }
            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        runCatching {
            createChannel()
            startForeground(2001, AutomationNotifier.notification(this, "Events automation active"))
            registerReceivers()
            registerNetwork()
            lastWifi = StudyPrefs.wifiConnected(this)
            evaluateWifi()
            handler.post(wifiPoll)
            handler.post(batteryPoll)
        }.onFailure {
            handler.post(wifiPoll)
        }
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
        // Re-post/update the persistent foreground-service notification whenever
        // Android or the app asks this service to start again. This helps restore
        // the notification after it was dismissed from the notification shade.
        runCatching {
            createChannel()
            startForeground(2001, AutomationNotifier.notification(this, "Events automation active"))
        }.onFailure {
            android.util.Log.e("EventsAutomation", "Could not refresh foreground notification", it)
        }
        runCatching { evaluateWifi() }
        return START_STICKY
    }

    private fun registerReceivers() {
        val sticky = ContextCompat.registerReceiver(
            this,
            batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        handleBattery(sticky)
    }

    private fun registerNetwork() {
        val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val req = NetworkRequest.Builder()
            .addTransportType(NetworkCapabilities.TRANSPORT_WIFI)
            .build()
        runCatching { cm.registerNetworkCallback(req, networkCallback) }
    }

    private fun handleBattery(i: Intent?) {
        if (i?.action != Intent.ACTION_BATTERY_CHANGED) return
        processBattery(
            i.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            i.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
            i.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            i.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        )
    }

    private fun checkCurrentBattery() {
        if (!EventPrefs.isBatteryEnabled(this)) {
            lastBatteryAlert = 0L
            lastBatteryState = -1
            return
        }
        val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED)) ?: return
        processBattery(
            intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1),
            intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100),
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1),
            intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0)
        )
    }

    private fun processBattery(level: Int, scale: Int, status: Int, plugged: Int) {
        if (!EventPrefs.isBatteryEnabled(this) || level < 0) {
            lastBatteryAlert = 0L
            lastBatteryState = -1
            return
        }

        val pct = if (scale > 0) level * 100 / scale else level
        // The high-level reminder must depend on the charger actually being
        // connected. BATTERY_STATUS_FULL can remain after the cable is removed,
        // so use the plugged flag as well.
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            (status == BatteryManager.BATTERY_STATUS_FULL && plugged != 0)
        val high = EventPrefs.batteryHigh(this)
        val low = EventPrefs.batteryLow(this)
        val state = when {
            charging && pct >= high -> 1
            !charging && pct < low -> 2
            else -> 0
        }

        // A change between charging/disconnected/threshold states starts a new
        // reminder cycle. It never carries the old cycle's timer across states.
        if (state != lastBatteryState) {
            lastBatteryState = state
            lastBatteryAlert = 0L
        }

        if (state == 0) return

        val interval = if (state == 1) {
            EventPrefs.batteryHighInterval(this) * 60_000L
        } else {
            EventPrefs.batteryLowInterval(this) * 60_000L
        }
        val now = System.currentTimeMillis()
        if (lastBatteryAlert == 0L || now - lastBatteryAlert >= interval) {
            if (state == 1) {
                speakBattery("Energised, Enough of charging")
            } else {
                speakBattery("I am thirsty, Please connect the charger")
            }
            lastBatteryAlert = now
        }
    }

    private fun speakBattery(message: String) {
        // Battery alerts use bundled audio rather than the phone's TTS/media
        // stream. This keeps the reminder audible even when Study Mode has
        // set STREAM_MUSIC to 0. The audio is routed through the alarm usage.
        val resId = when (message) {
            "Energised, Enough of charging" -> R.raw.battery_high
            else -> R.raw.battery_low
        }
        runCatching {
            batteryPlayer?.let { old ->
                runCatching { if (old.isPlaying) old.stop() }
                runCatching { old.release() }
            }
            val player = MediaPlayer()
            player.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ALARM)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build()
            )
            player.setOnCompletionListener { mp ->
                runCatching { mp.release() }
                if (batteryPlayer === mp) batteryPlayer = null
            }
            player.setOnErrorListener { mp, _, _ ->
                runCatching { mp.release() }
                if (batteryPlayer === mp) batteryPlayer = null
                playAlarmFallback()
                true
            }
            val afd = resources.openRawResourceFd(resId)
                ?: throw IllegalStateException("Battery reminder audio missing")
            afd.use { descriptor ->
                player.setDataSource(
                    descriptor.fileDescriptor,
                    descriptor.startOffset,
                    descriptor.length
                )
            }
            player.prepare()
            batteryPlayer = player
            player.start()
        }.onFailure { playAlarmFallback() }
    }

    private fun playAlarmFallback() {
        runCatching {
            val tone = ToneGenerator(AudioManager.STREAM_ALARM, 90)
            tone.startTone(ToneGenerator.TONE_PROP_BEEP2, 2000)
            handler.postDelayed({ runCatching { tone.stopTone(); tone.release() } }, 2100)
        }
    }

    private fun evaluateWifi() {
        if (!StudyPrefs.isAutoWifiEnabled(this)) {
            wifiMismatchSince = 0L
            if (StudyPrefs.isActive(this) || lastWifi || StudyPrefs.wifiConnected(this)) {
                lastWifi = false
                StudyManager.wifiState(this, false)
            } else {
                lastWifi = false
            }
            return
        }

        val target = StudyPrefs.wifiFingerprint(this)
        if (target.isBlank()) {
            wifiMismatchSince = 0L
            return
        }

        val snapshot = WifiIdentity.current(this)

        // Android can momentarily return no active Wi-Fi/LinkProperties during
        // DHCP renewal, network validation, or a Wi-Fi stack refresh. Treat
        // that as UNKNOWN, not as a real disconnect.
        if (snapshot == null) {
            handleWifiMismatchOrUnknown()
            return
        }

        val matchedStable = snapshot.fingerprint == target
        val matchedLegacy = snapshot.legacyFingerprint.isNotBlank() &&
            snapshot.legacyFingerprint == target
        val connected = matchedStable || matchedLegacy

        if (connected) {
            wifiMismatchSince = 0L

            // Automatically migrate a v10 saved fingerprint to the newer
            // stable identity without making the user save the Wi-Fi again.
            if (matchedLegacy && !matchedStable) {
                StudyPrefs.setWifiFingerprint(this, snapshot.fingerprint)
                StudyPrefs.setWifiFingerprintDescription(this, snapshot.description)
            }

            val wasActive = StudyPrefs.isActive(this)
            val wasConnected = StudyPrefs.wifiConnected(this) || lastWifi
            lastWifi = true
            StudyPrefs.setWifiConnected(this, true)

            if (!wasActive || !wasConnected) {
                // activate() now applies media/silent entry actions only when
                // this is a genuinely new Study Mode session.
                StudyManager.wifiState(this, true)
                post("Study-hall Wi-Fi connected — Study Mode active")
            } else {
                // Re-apply only Silent Mode on its configured 30-minute check.
                // Never reset media volume while the user remains in the hall.
                StudyManager.reapplySilentMode(this)
            }
            return
        }

        handleWifiMismatchOrUnknown()
    }

    private fun handleWifiMismatchOrUnknown() {
        val active = StudyPrefs.isActive(this) || lastWifi || StudyPrefs.wifiConnected(this)
        if (!active) {
            wifiMismatchSince = 0L
            lastWifi = false
            return
        }

        val now = android.os.SystemClock.elapsedRealtime()
        if (wifiMismatchSince == 0L) wifiMismatchSince = now

        if (now - wifiMismatchSince < WIFI_LOSS_GRACE_MS) return

        wifiMismatchSince = 0L
        lastWifi = false
        StudyManager.wifiState(this, false)
        // StudyManager owns the Study Mode OFF notification so the event gets
        // one normal dismissible end notification rather than duplicates.
    }

    private fun createChannel() {
        AutomationNotifier.ensureChannel(this)
    }

    private fun post(text: String) {
        AutomationNotifier.post(this, text)
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        // Keep Events running in the background when the user swipes it away from Recents.
        // This intentionally remains enabled. Force Stop is still different and stops the app.
        runCatching {
            val am = getSystemService(ALARM_SERVICE) as android.app.AlarmManager
            val restartIntent = Intent(this, ServiceRestartReceiver::class.java)
            val pi = android.app.PendingIntent.getBroadcast(
                this,
                9182,
                restartIntent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE
            )
            am.setAndAllowWhileIdle(
                android.app.AlarmManager.ELAPSED_REALTIME_WAKEUP,
                android.os.SystemClock.elapsedRealtime() + 1500L,
                pi
            )
        }
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        handler.removeCallbacks(wifiPoll)
        handler.removeCallbacks(batteryPoll)
        runCatching { batteryPlayer?.stop() }
        runCatching { batteryPlayer?.release() }
        batteryPlayer = null
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
