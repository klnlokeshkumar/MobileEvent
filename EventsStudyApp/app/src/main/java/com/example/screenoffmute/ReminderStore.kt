package com.example.screenoffmute

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.util.UUID

data class Reminder(
    val id: String,
    val name: String,
    val date: LocalDate,
    val note: String = ""
)

object ReminderStore {
    private const val PREFS = "reminders"
    private const val KEY = "list"
    private const val WIDGET_PREFIX = "widget_"
    private const val WIDGET_THEME_PREFIX = "widget_theme_"

    private fun p(c: Context) = c.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun all(c: Context): List<Reminder> {
        val a = JSONArray(p(c).getString(KEY, "[]") ?: "[]")
        val r = mutableListOf<Reminder>()
        for (i in 0 until a.length()) {
            val o = a.optJSONObject(i) ?: continue
            val id = o.optString("id")
            val n = o.optString("name")
            val d = o.optString("date")
            val note = o.optString("note", "")
            if (id.isNotBlank() && n.isNotBlank() && d.isNotBlank()) {
                runCatching { r += Reminder(id, n, LocalDate.parse(d), note) }
            }
        }
        return r
    }

    fun ensureDefaults(c: Context) {
        if (all(c).isEmpty()) add(c, "SSC CGL", LocalDate.of(2026, 10, 28), "")
    }

    fun add(c: Context, name: String, date: LocalDate, note: String = ""): Reminder {
        val item = Reminder(UUID.randomUUID().toString(), name.trim(), date, note.trim())
        val list = all(c).toMutableList()
        list += item
        save(c, list)
        ReminderWidgetProvider.refreshAll(c)
        return item
    }

    fun update(c: Context, id: String, name: String, date: LocalDate, note: String = ""): Boolean {
        val list = all(c).toMutableList()
        val index = list.indexOfFirst { it.id == id }
        if (index < 0) return false
        list[index] = Reminder(id, name.trim(), date, note.trim())
        save(c, list)
        ReminderWidgetProvider.refreshAll(c)
        return true
    }

    fun delete(c: Context, id: String): Boolean {
        val list = all(c).toMutableList()
        val changed = list.removeAll { it.id == id }
        if (!changed) return false
        save(c, list)
        ReminderWidgetProvider.refreshAll(c)
        return true
    }

    fun byId(c: Context, id: String) = all(c).firstOrNull { it.id == id }

    fun setWidgetEntry(c: Context, id: Int, reminderId: String) =
        p(c).edit().putString(WIDGET_PREFIX + id, reminderId).apply()

    fun widgetEntry(c: Context, id: Int) = p(c).getString(WIDGET_PREFIX + id, null)

    fun setWidgetTheme(c: Context, id: Int, theme: String) =
        p(c).edit().putString(WIDGET_THEME_PREFIX + id, if (theme == "dark") "dark" else "light").apply()

    fun widgetTheme(c: Context, id: Int) =
        p(c).getString(WIDGET_THEME_PREFIX + id, "light") ?: "light"

    fun clearWidget(c: Context, id: Int) = p(c).edit()
        .remove(WIDGET_PREFIX + id)
        .remove(WIDGET_THEME_PREFIX + id)
        .apply()

    private fun save(c: Context, list: List<Reminder>) {
        val a = JSONArray()
        list.forEach {
            a.put(JSONObject().apply {
                put("id", it.id)
                put("name", it.name)
                put("date", it.date.toString())
                put("note", it.note)
            })
        }
        p(c).edit().putString(KEY, a.toString()).apply()
    }
}
