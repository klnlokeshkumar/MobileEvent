package com.example.screenoffmute

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Reminder(val id:String,val name:String,val date:LocalDate)
object ReminderStore{
    private const val PREFS="reminders";private const val KEY="list";private const val WIDGET_PREFIX="widget_"
    private fun p(c:Context)=c.getSharedPreferences(PREFS,Context.MODE_PRIVATE)
    fun all(c:Context):List<Reminder>{val a=JSONArray(p(c).getString(KEY,"[]")?:"[]");val r=mutableListOf<Reminder>();for(i in 0 until a.length()){val o=a.optJSONObject(i)?:continue;val id=o.optString("id");val n=o.optString("name");val d=o.optString("date");if(id.isNotBlank()&&n.isNotBlank()&&d.isNotBlank())runCatching{r+=Reminder(id,n,LocalDate.parse(d))}};return r}
    fun ensureDefaults(c:Context){if(all(c).isEmpty())add(c,"SSC CGL",LocalDate.of(2026,10,28))}
    fun add(c:Context,name:String,date:LocalDate):Reminder{val item=Reminder(UUID.randomUUID().toString(),name,date);val list=all(c).toMutableList();list+=item;save(c,list);ReminderWidgetProvider.refreshAll(c);return item}
    fun byId(c:Context,id:String)=all(c).firstOrNull{it.id==id}
    fun setWidgetEntry(c:Context,id:Int,reminderId:String)=p(c).edit().putString(WIDGET_PREFIX+id,reminderId).apply()
    fun widgetEntry(c:Context,id:Int)=p(c).getString(WIDGET_PREFIX+id,null)
    fun clearWidget(c:Context,id:Int)=p(c).edit().remove(WIDGET_PREFIX+id).apply()
    private fun save(c:Context,list:List<Reminder>){val a=JSONArray();list.forEach{a.put(JSONObject().apply{put("id",it.id);put("name",it.name);put("date",it.date.toString())})};p(c).edit().putString(KEY,a.toString()).apply()}
}
