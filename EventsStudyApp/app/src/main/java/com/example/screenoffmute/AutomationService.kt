package com.example.screenoffmute

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.speech.tts.TextToSpeech
import java.util.Locale
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
    private var lastBatteryAlert = 0L
    private var lastBatteryState = -1
    private var tts: TextToSpeech? = null

    private val batteryPoll = object : Runnable {
        override fun run() {
            runCatching { checkCurrentBattery() }
            handler.postDelayed(this, 30_000L)
        }
    }

    private val wifiPoll = object : Runnable {
        override fun run() {
            runCatching { evaluateWifi() }
            handler.postDelayed(this, 15_000L)
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
            evaluateWifi()
            initTts()
            handler.post(wifiPoll)
            handler.post(batteryPoll)
        }.onFailure {
            handler.post(wifiPoll)
        }
    }

    override fun onStartCommand(i: Intent?, flags: Int, startId: Int): Int {
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
            i.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
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
            intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1)
        )
    }

    private fun processBattery(level: Int, scale: Int, status: Int) {
        if (!EventPrefs.isBatteryEnabled(this) || level < 0) {
            lastBatteryAlert = 0L
            lastBatteryState = -1
            return
        }

        val pct = if (scale > 0) level * 100 / scale else level
        val charging = status == BatteryManager.BATTERY_STATUS_CHARGING ||
            status == BatteryManager.BATTERY_STATUS_FULL
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

    private fun initTts() {
        if (tts != null) return
        runCatching {
            tts = TextToSpeech(applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    tts?.language = Locale.US
                    tts?.setSpeechRate(0.95f)
                }
            }
        }
    }

    private fun speakBattery(message: String) {
        runCatching {
            initTts()
            val engine = tts
            if (engine != null) {
                engine.speak(
                    message,
                    TextToSpeech.QUEUE_FLUSH,
                    null,
                    "events_battery_${System.currentTimeMillis()}"
                )
            } else {
                playAlarmFallback()
            }
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
            if (lastWifi) StudyManager.wifiState(this, false)
            lastWifi = false
            return
        }

        val target = StudyPrefs.wifiFingerprint(this)
        val current = WifiIdentity.current(this)?.fingerprint ?: ""
        val connected = target.isNotBlank() && current == target

        if (connected != lastWifi || StudyPrefs.wifiConnected(this) != connected) {
            lastWifi = connected
            StudyManager.wifiState(this, connected)
            post(
                if (connected)
                    "Study-hall Wi-Fi connected — Study Mode active"
                else
                    "Study-hall Wi-Fi changed/disconnected — previous sound settings restored"
            )
        } else if (connected && StudyPrefs.isActive(this)) {
            // Re-apply only Silent Mode after the user grants access.
            // Do NOT reset media volume while the user remains in the hall.
            StudyManager.reapplySilentMode(this)
        } else if (!connected && StudyPrefs.isActive(this)) {
            StudyManager.wifiState(this, false)
        }
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
        runCatching { tts?.stop(); tts?.shutdown() }
        tts = null
        runCatching { unregisterReceiver(batteryReceiver) }
        runCatching {
            val cm = getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        }
        super.onDestroy()
    }

    override fun onBind(i: Intent?): IBinder? = null
}
