package com.example.screenoffmute

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.widget.RemoteViews
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit

class ReminderWidgetProvider : AppWidgetProvider() {
    override fun onUpdate(c: Context, m: AppWidgetManager, ids: IntArray) {
        ReminderStore.ensureDefaults(c)
        ids.forEach { update(c, m, it) }
    }

    override fun onDeleted(c: Context, ids: IntArray) {
        ids.forEach { ReminderStore.clearWidget(c, it) }
    }

    companion object {
        fun refreshAll(c: Context) {
            val m = AppWidgetManager.getInstance(c)
            val ids = m.getAppWidgetIds(ComponentName(c, ReminderWidgetProvider::class.java))
            ids.forEach { update(c, m, it) }
        }

        private fun update(c: Context, m: AppWidgetManager, id: Int) {
            ReminderStore.ensureDefaults(c)
            val selectedId = ReminderStore.widgetEntry(c, id)
            val r = selectedId?.let { ReminderStore.byId(c, it) } ?: ReminderStore.all(c).firstOrNull()
            val v = RemoteViews(c.packageName, R.layout.widget_reminder)
            val dark = ReminderStore.widgetTheme(c, id) == "dark"

            v.setInt(
                R.id.widget_root,
                "setBackgroundResource",
                if (dark) R.drawable.widget_bg_dark else R.drawable.widget_bg_light
            )
            v.setTextColor(R.id.widget_title, Color.parseColor(if (dark) "#FFFFFF" else "#111111"))
            v.setTextColor(R.id.widget_date, Color.parseColor(if (dark) "#B8C0CC" else "#555555"))
            v.setTextColor(R.id.widget_days, Color.parseColor(if (dark) "#64B5F6" else "#1565C0"))
            v.setTextColor(R.id.widget_note, Color.parseColor(if (dark) "#D7DCE4" else "#333333"))

            if (r == null) {
                v.setTextViewText(R.id.widget_title, "Add a reminder")
                v.setTextViewText(R.id.widget_date, "No date set")
                v.setTextViewText(R.id.widget_days, "—")
                v.setTextViewText(R.id.widget_note, "Open Events to add one")
            } else {
                val days = ChronoUnit.DAYS.between(LocalDate.now(), r.date)
                val text = when {
                    days > 0 -> "$days days left"
                    days == 0L -> "Today"
                    else -> "${-days} days overdue"
                }
                v.setTextViewText(R.id.widget_title, r.name)
                v.setTextViewText(
                    R.id.widget_date,
                    "Date: ${r.date.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))}"
                )
                v.setTextViewText(R.id.widget_days, text)
                v.setTextViewText(R.id.widget_note, if (r.note.isBlank()) "" else r.note)
            }

            val editIntent = Intent(c, ReminderEditActivity::class.java).apply {
                putExtra(ReminderEditActivity.EXTRA_REMINDER_ID, r?.id ?: "")
                putExtra(ReminderEditActivity.EXTRA_WIDGET_ID, id)
            }
            val pi = PendingIntent.getActivity(
                c,
                50000 + id,
                editIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            v.setOnClickPendingIntent(R.id.widget_root, pi)
            m.updateAppWidget(id, v)
        }
    }
}
