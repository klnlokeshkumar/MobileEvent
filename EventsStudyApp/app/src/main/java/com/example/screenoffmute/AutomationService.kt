package com.example.screenoffmute

import android.app.Service
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.media.AudioManager
import android.media.ToneGenerator
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.BatteryManager
import android.os.Handler
import android.os.IBinder
import android.os.PowerManager
import androidx.core.content.ContextCompat

class AutomationService:Service(){
    private val handler=Handler(android.os.Looper.getMainLooper())
    private var screenOff=false
    private var lastWifi=false
    private var lastBatteryAlert=0L

    private val mute=Runnable{
        runCatching{
            if(screenOff && EventPrefs.isScreenMuteEnabled(this)){
                val a=getSystemService(AUDIO_SERVICE) as AudioManager
                a.setStreamVolume(AudioManager.STREAM_MUSIC,0,0)
                post("Screen off for 1 minute — media volume 0")
            }
        }.onFailure{post("Screen mute failed — service still running")}
    }

    private val wifiPoll=object:Runnable{
        override fun run(){
            runCatching{evaluateWifi()}
            handler.postDelayed(this,15_000L)
        }
    }

    private val screenReceiver=object:BroadcastReceiver(){
        override fun onReceive(c:Context?,i:Intent?){
            runCatching{
                when(i?.action){
                    Intent.ACTION_SCREEN_OFF->{
                        screenOff=true
                        handler.removeCallbacks(mute)
                        if(EventPrefs.isScreenMuteEnabled(this@AutomationService))
                            handler.postDelayed(mute,EventPrefs.delayMs(this@AutomationService))
                        post("Screen off — media will mute in 1 minute")
                    }
                    Intent.ACTION_SCREEN_ON->{
                        screenOff=false
                        handler.removeCallbacks(mute)
                        post("Automation service active")
                    }
                }
            }
        }
    }

    private val batteryReceiver=object:BroadcastReceiver(){
        override fun onReceive(c:Context?,i:Intent?){runCatching{handleBattery(i)}}
    }

    private val networkCallback=object:ConnectivityManager.NetworkCallback(){
        override fun onAvailable(n:Network){runCatching{evaluateWifi()}}
        override fun onLost(n:Network){runCatching{evaluateWifi()}}
        override fun onCapabilitiesChanged(n:Network,c:NetworkCapabilities){
            if(c.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)) runCatching{evaluateWifi()}
        }
    }

    override fun onCreate(){
        super.onCreate()
        runCatching{
            createChannel()
            startForeground(2001,AutomationNotifier.notification(this,"Automation service active"))
            registerReceivers()
            registerNetwork()
            val pm=getSystemService(POWER_SERVICE) as PowerManager
            screenOff=!pm.isInteractive
            if(screenOff && EventPrefs.isScreenMuteEnabled(this))
                handler.postDelayed(mute,EventPrefs.delayMs(this))
            evaluateWifi()
            handler.post(wifiPoll)
        }.onFailure{e->
            post("Service started with limited features: ${e::class.java.simpleName}")
            handler.post(wifiPoll)
        }
    }

    override fun onStartCommand(i:Intent?,flags:Int,startId:Int):Int{
        runCatching{
            evaluateWifi()
            val pm=getSystemService(POWER_SERVICE) as PowerManager
            screenOff=!pm.isInteractive
            if(screenOff && EventPrefs.isScreenMuteEnabled(this)){
                handler.removeCallbacks(mute)
                handler.postDelayed(mute,EventPrefs.delayMs(this))
            }else handler.removeCallbacks(mute)
        }.onFailure{post("Automation is running; one check failed")}
        return START_STICKY
    }

    private fun registerReceivers(){
        ContextCompat.registerReceiver(
            this,screenReceiver,
            IntentFilter().apply{
                addAction(Intent.ACTION_SCREEN_OFF)
                addAction(Intent.ACTION_SCREEN_ON)
            },
            ContextCompat.RECEIVER_EXPORTED
        )
        val sticky=ContextCompat.registerReceiver(
            this,batteryReceiver,
            IntentFilter(Intent.ACTION_BATTERY_CHANGED),
            ContextCompat.RECEIVER_EXPORTED
        )
        handleBattery(sticky)
    }

    private fun registerNetwork(){
        val cm=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
        val req=NetworkRequest.Builder().addTransportType(NetworkCapabilities.TRANSPORT_WIFI).build()
        runCatching{cm.registerNetworkCallback(req,networkCallback)}
            .onFailure{post("Wi-Fi callback unavailable — using 15-second checking")}
    }

    private fun handleBattery(i:Intent?){
        if(i?.action!=Intent.ACTION_BATTERY_CHANGED||!EventPrefs.isBatteryEnabled(this))return
        val level=i.getIntExtra(BatteryManager.EXTRA_LEVEL,-1)
        val scale=i.getIntExtra(BatteryManager.EXTRA_SCALE,100)
        val pct=if(scale>0)level*100/scale else level
        val st=i.getIntExtra(BatteryManager.EXTRA_STATUS,-1)
        val charging=st==BatteryManager.BATTERY_STATUS_CHARGING||st==BatteryManager.BATTERY_STATUS_FULL
        val threshold=EventPrefs.batteryThreshold(this)
        if(pct>=threshold){
            val interval=if(charging)120_000L else 300_000L
            val now=System.currentTimeMillis()
            if(lastBatteryAlert==0L||now-lastBatteryAlert>=interval){
                alertBattery()
                lastBatteryAlert=now
            }
        }else{
            lastBatteryAlert=0L
        }
    }

    private fun alertBattery(){
        runCatching{
            val t=ToneGenerator(AudioManager.STREAM_ALARM,90)
            t.startTone(ToneGenerator.TONE_PROP_BEEP2,2000)
            handler.postDelayed({runCatching{t.stopTone();t.release()}},2100)
            val v=getSystemService(VIBRATOR_SERVICE) as android.os.Vibrator
            if(v.hasVibrator()) v.vibrate(android.os.VibrationEffect.createOneShot(2000,android.os.VibrationEffect.DEFAULT_AMPLITUDE))
        }.onFailure{post("Battery alert unavailable")}
    }

    private fun evaluateWifi(){
        if(!StudyPrefs.isAutoWifiEnabled(this)){
            if(lastWifi) StudyManager.wifiState(this,false)
            lastWifi=false
            return
        }

        val target=StudyPrefs.wifiFingerprint(this)
        val current=WifiIdentity.current(this)?.fingerprint ?: ""
        val connected=target.isNotBlank() && current==target

        if(connected!=lastWifi || StudyPrefs.wifiConnected(this)!=connected){
            lastWifi=connected
            StudyManager.wifiState(this,connected)
            post(
                if(connected)
                    "Study-hall Wi-Fi connected — Study Mode active"
                else
                    "Study-hall Wi-Fi changed/disconnected — previous sound settings restored"
            )
        }else if(connected && StudyPrefs.isActive(this)){
            // Re-apply after the user grants Silent Mode access in Android settings.
            StudyManager.applyFeatureSettings(this)
        }else if(!connected && StudyPrefs.isActive(this)){
            StudyManager.wifiState(this,false)
        }
    }

    private fun createChannel(){
        AutomationNotifier.ensureChannel(this)
    }

    private fun post(text:String){
        AutomationNotifier.post(this,text)
    }

    override fun onTaskRemoved(rootIntent:Intent?){
        // Keep the automation alive when the user swipes Events away from Recents.
        // This does NOT run when the user explicitly force-stops the app.
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
        post("Events automation continues after removing it from Recents")
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy(){
        handler.removeCallbacks(mute)
        handler.removeCallbacks(wifiPoll)
        runCatching{unregisterReceiver(screenReceiver)}
        runCatching{unregisterReceiver(batteryReceiver)}
        runCatching{
            val cm=getSystemService(CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.unregisterNetworkCallback(networkCallback)
        }
        super.onDestroy()
    }

    override fun onBind(i:Intent?):IBinder?=null
}
