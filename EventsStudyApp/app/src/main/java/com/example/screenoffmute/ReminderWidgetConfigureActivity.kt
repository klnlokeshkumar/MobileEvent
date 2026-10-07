package com.example.screenoffmute

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.LinearLayout
import android.widget.Spinner
import android.widget.TextView

class ReminderWidgetConfigureActivity:Activity(){
    override fun onCreate(savedInstanceState:Bundle?){super.onCreate(savedInstanceState);setResult(RESULT_CANCELED);val id=intent?.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,AppWidgetManager.INVALID_APPWIDGET_ID)?:AppWidgetManager.INVALID_APPWIDGET_ID;if(id==AppWidgetManager.INVALID_APPWIDGET_ID){finish();return};ReminderStore.ensureDefaults(this);val items=ReminderStore.all(this);val root=LinearLayout(this).apply{orientation=LinearLayout.VERTICAL;setPadding(30,30,30,30)};root.addView(TextView(this).apply{text="Choose reminder for this widget";textSize=20f},lp());val spin=Spinner(this);spin.adapter=ArrayAdapter(this,android.R.layout.simple_spinner_dropdown_item,items.map{"${it.name} — ${it.date}"});root.addView(spin,lp());root.addView(Button(this).apply{text="Add widget";setOnClickListener{ReminderStore.setWidgetEntry(this@ReminderWidgetConfigureActivity,id,items[spin.selectedItemPosition].id);ReminderWidgetProvider.refreshAll(this@ReminderWidgetConfigureActivity);setResult(RESULT_OK,Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id));finish()}},lp());setContentView(root)}
    private fun lp()=LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,ViewGroup.LayoutParams.WRAP_CONTENT).apply{bottomMargin=14}
}
