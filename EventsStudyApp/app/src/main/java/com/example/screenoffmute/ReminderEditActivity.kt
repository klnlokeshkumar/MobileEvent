package com.example.screenoffmute

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.appwidget.AppWidgetManager
import android.os.Bundle
import android.view.ViewGroup
import android.widget.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

class ReminderEditActivity : Activity() {
    companion object {
        const val EXTRA_REMINDER_ID = "reminder_id"
        const val EXTRA_WIDGET_ID = "widget_id"
    }

    private lateinit var titleInput: EditText
    private lateinit var noteInput: EditText
    private lateinit var dateButton: Button
    private var selectedDate = LocalDate.now()
    private var reminderId: String? = null
    private var widgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var widgetThemeGroup: RadioGroup? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        reminderId = intent.getStringExtra(EXTRA_REMINDER_ID)?.takeIf { it.isNotBlank() }
        widgetId = intent.getIntExtra(EXTRA_WIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID)

        val existing = reminderId?.let { ReminderStore.byId(this, it) }
        if (existing != null) selectedDate = existing.date

        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(28, 28, 28, 28)
        }

        root.addView(TextView(this).apply {
            text = if (existing == null) "New Reminder" else "Edit Reminder"
            textSize = 26f
        }, lp())

        root.addView(TextView(this).apply {
            text = "Title"
            textSize = 15f
        }, lp())
        titleInput = EditText(this).apply {
            hint = "e.g. SSC CGL"
            setSingleLine(true)
            setText(existing?.name ?: "")
        }
        root.addView(titleInput, lp())

        root.addView(TextView(this).apply {
            text = "Date"
            textSize = 15f
        }, lp())
        dateButton = Button(this).apply {
            setOnClickListener { pickDate() }
        }
        root.addView(dateButton, lp())
        updateDateButton()

        root.addView(TextView(this).apply {
            text = "Note"
            textSize = 15f
        }, lp())
        noteInput = EditText(this).apply {
            hint = "Optional note for the widget"
            minLines = 3
            gravity = android.view.Gravity.TOP
            setText(existing?.note ?: "")
        }
        root.addView(noteInput, lp())

        
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            root.addView(TextView(this).apply {
                text = "Widget theme"
                textSize = 15f
            }, lp())
            widgetThemeGroup = RadioGroup(this).apply {
                orientation = RadioGroup.HORIZONTAL
            }
            val light = RadioButton(this).apply {
                text = "Light"
                id = android.R.id.button1
            }
            val dark = RadioButton(this).apply {
                text = "Dark"
                id = android.R.id.button2
            }
            widgetThemeGroup!!.addView(light)
            widgetThemeGroup!!.addView(dark)
            if (ReminderStore.widgetTheme(this, widgetId) == "dark") dark.isChecked = true else light.isChecked = true
            root.addView(widgetThemeGroup, lp())
        }

        root.addView(Button(this).apply {
            text = "SAVE"
            setOnClickListener { save() }
        }, lp())

        if (existing != null) {
            root.addView(Button(this).apply {
                text = "DELETE REMINDER"
                setOnClickListener {
                    AlertDialog.Builder(this@ReminderEditActivity)
                        .setTitle("Delete reminder?")
                        .setMessage("This removes the reminder. Any widget using it will show the first remaining reminder.")
                        .setNegativeButton("CANCEL", null)
                        .setPositiveButton("DELETE") { _, _ ->
                            ReminderStore.delete(this@ReminderEditActivity, existing.id)
                            finish()
                        }
                        .show()
                }
            }, lp())
        }

        setContentView(ScrollView(this).apply { addView(root) })
    }

    private fun pickDate() {
        DatePickerDialog(
            this,
            { _, y, m, d ->
                selectedDate = LocalDate.of(y, m + 1, d)
                updateDateButton()
            },
            selectedDate.year,
            selectedDate.monthValue - 1,
            selectedDate.dayOfMonth
        ).show()
    }

    private fun updateDateButton() {
        dateButton.text = selectedDate.format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
    }

    private fun save() {
        val name = titleInput.text.toString().trim()
        if (name.isBlank()) {
            titleInput.error = "Enter a title"
            return
        }
        val note = noteInput.text.toString().trim()
        val id = reminderId
        if (id == null) {
            val created = ReminderStore.add(this, name, selectedDate, note)
            if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                ReminderStore.setWidgetEntry(this, widgetId, created.id)
            }
        } else {
            ReminderStore.update(this, id, name, selectedDate, note)
        }
        if (widgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            widgetThemeGroup?.let { group ->
                val selected = group.checkedRadioButtonId
                ReminderStore.setWidgetTheme(this, widgetId, if (selected == android.R.id.button2) "dark" else "light")
            }
            ReminderWidgetProvider.refreshAll(this)
        }
        Toast.makeText(this, "Reminder saved", Toast.LENGTH_SHORT).show()
        finish()
    }

    private fun lp() = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT
    ).apply { bottomMargin = 14 }
}
