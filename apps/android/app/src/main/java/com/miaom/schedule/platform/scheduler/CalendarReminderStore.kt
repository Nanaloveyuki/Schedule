package com.miaom.schedule.platform.scheduler

import android.content.ContentValues
import android.content.Context
import android.provider.CalendarContract
import com.miaom.schedule.domain.model.ReminderTask
import org.json.JSONArray
import java.time.ZoneId

data class CalendarCourseOccurrence(
    val title: String,
    val description: String,
    val location: String,
    val startAtMillis: Long,
    val endAtMillis: Long,
    val reminderMinutesBefore: Int
)

class CalendarReminderStore(
    context: Context
) {
    private val appContext = context.applicationContext
    private val contentResolver = appContext.contentResolver
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    fun resolveWritableCalendarName(): String? = queryWritableCalendar()?.second

    fun hasWritableCalendar(): Boolean = queryWritableCalendar() != null

    fun syncTask(task: ReminderTask, occurrences: List<CalendarCourseOccurrence>): Int {
        val calendar = queryWritableCalendar() ?: return 0
        val existingEventIds = readEventIds(task.id)
        val syncedIds = mutableListOf<Long>()

        occurrences.forEachIndexed { index, occurrence ->
            val existingId = existingEventIds.getOrNull(index)
            val eventId = if (existingId != null && updateEvent(existingId, calendar.first, occurrence)) {
                existingId
            } else {
                insertEvent(calendar.first, occurrence)
            }
            if (eventId != null) {
                replaceReminders(eventId, occurrence.reminderMinutesBefore)
                syncedIds += eventId
            }
        }

        existingEventIds.drop(occurrences.size).forEach(::deleteEvent)
        writeEventIds(task.id, syncedIds)
        return syncedIds.size
    }

    fun deleteTask(taskId: String) {
        readEventIds(taskId).forEach(::deleteEvent)
        preferences.edit().remove(taskId).apply()
    }

    fun reconcileActiveTasks(activeTaskIds: Set<String>) {
        preferences.all.keys.filterNot(activeTaskIds::contains).forEach(::deleteTask)
    }

    private fun queryWritableCalendar(): Pair<Long, String>? {
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1 AND ${CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL}>=${CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR}"
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.IS_PRIMARY} DESC, ${CalendarContract.Calendars._ID} ASC"
        ) ?: return null
        cursor.use {
            if (!it.moveToFirst()) return null
            return it.getLong(0) to it.getString(1)
        }
    }

    private fun insertEvent(calendarId: Long, occurrence: CalendarCourseOccurrence): Long? {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, occurrence.title)
            put(CalendarContract.Events.DESCRIPTION, occurrence.description)
            put(CalendarContract.Events.EVENT_LOCATION, occurrence.location)
            put(CalendarContract.Events.DTSTART, occurrence.startAtMillis)
            put(CalendarContract.Events.DTEND, occurrence.endAtMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val inserted = contentResolver.insert(CalendarContract.Events.CONTENT_URI, values) ?: return null
        return inserted.lastPathSegment?.toLongOrNull()
    }

    private fun updateEvent(eventId: Long, calendarId: Long, occurrence: CalendarCourseOccurrence): Boolean {
        val values = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, occurrence.title)
            put(CalendarContract.Events.DESCRIPTION, occurrence.description)
            put(CalendarContract.Events.EVENT_LOCATION, occurrence.location)
            put(CalendarContract.Events.DTSTART, occurrence.startAtMillis)
            put(CalendarContract.Events.DTEND, occurrence.endAtMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, ZoneId.systemDefault().id)
            put(CalendarContract.Events.HAS_ALARM, 1)
        }
        val rows = contentResolver.update(
            CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build(),
            values,
            null,
            null
        )
        return rows > 0
    }

    private fun replaceReminders(eventId: Long, minutesBefore: Int) {
        contentResolver.delete(
            CalendarContract.Reminders.CONTENT_URI,
            "${CalendarContract.Reminders.EVENT_ID}=?",
            arrayOf(eventId.toString())
        )
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutesBefore)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    private fun deleteEvent(eventId: Long) {
        contentResolver.delete(
            CalendarContract.Events.CONTENT_URI.buildUpon().appendPath(eventId.toString()).build(),
            null,
            null
        )
    }

    private fun readEventIds(taskId: String): List<Long> {
        val raw = preferences.getString(taskId, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList(jsonArray.length()) {
                for (index in 0 until jsonArray.length()) {
                    add(jsonArray.getLong(index))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun writeEventIds(taskId: String, eventIds: List<Long>) {
        val jsonArray = JSONArray().also { array -> eventIds.forEach(array::put) }
        preferences.edit().putString(taskId, jsonArray.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "calendar_reminder_sync"
    }
}
