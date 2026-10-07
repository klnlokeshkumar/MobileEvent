package com.example.screenoffmute

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent
import android.widget.Toast

class StudyAccessibilityService:AccessibilityService(){
    private var lastPackage=""
    private var lastAt=0L

    override fun onServiceConnected(){
        super.onServiceConnected()
        serviceInfo = serviceInfo.apply {
            eventTypes =
                AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED or
                AccessibilityEvent.TYPE_WINDOWS_CHANGED or
                AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED
            notificationTimeout=75
        }
    }

    override fun onAccessibilityEvent(event:AccessibilityEvent?){
        if(!StudyPrefs.isActive(this) || !StudyPrefs.anti(this)) return

        val pkg=event?.packageName?.toString() ?: return
        if(pkg==packageName) return
        if(pkg !in StudyPrefs.apps(this)) return

        val now=System.currentTimeMillis()
        if(pkg==lastPackage && now-lastAt<700) return

        lastPackage=pkg
        lastAt=now

        val blocked=performGlobalAction(GLOBAL_ACTION_HOME)
        if(blocked){
            Toast.makeText(this,"Blocked during Study Mode",Toast.LENGTH_SHORT).show()
        }
    }

    override fun onInterrupt(){}
}
