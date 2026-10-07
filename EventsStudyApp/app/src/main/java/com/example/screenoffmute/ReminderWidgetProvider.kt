package com.example.screenoffmute

import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ReminderWidgetProvider:AppWidgetProvider(){
    override fun onUpdate(c:Context,m:AppWidgetManager,ids:IntArray){ReminderStore.ensureDefaults(c);ids.forEach{update(c,m,it)}}
    override fun onDeleted(c:Context,ids:IntArray){ids.forEach{ReminderStore.clearWidget(c,it)}}
    companion object{
        fun refreshAll(c:Context){val m=AppWidgetManager.getInstance(c);val ids=m.getAppWidgetIds(ComponentName(c,ReminderWidgetProvider::class.java));ids.forEach{update(c,m,it)}}
        private fun update(c:Context,m:AppWidgetManager,id:Int){
            val v=RemoteViews(c.packageName,R.layout.widget_reminder);val r=ReminderStore.widgetEntry(c,id)?.let{ReminderStore.byId(c,it)}?:ReminderStore.all(c).firstOrNull()
            if(r==null){v.setTextViewText(R.id.widget_title,"Add a reminder");v.setTextViewText(R.id.widget_days,"—");v.setTextViewText(R.id.widget_date,"Open Events to add one")}
            else{val days=ChronoUnit.DAYS.between(LocalDate.now(),r.date);val text=when{days>0->"$days days left";days==0L->"Today";else->"${-days} days overdue"};v.setTextViewText(R.id.widget_title,r.name);v.setTextViewText(R.id.widget_days,text);v.setTextViewText(R.id.widget_date,r.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy")))}
            m.updateAppWidget(id,v)
        }
    }
}
