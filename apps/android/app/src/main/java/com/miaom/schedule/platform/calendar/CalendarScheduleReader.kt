package com.miaom.schedule.platform.calendar

import android.content.Context
import android.provider.CalendarContract
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

data class CalendarImportSource(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val ownerAccount: String,
    val eventCountHint: Int
)

data class CalendarScheduleImportResult(
    val icsText: String,
    val importedEventCount: Int,
    val matchedCalendarCount: Int
)

class CalendarScheduleReader(
    context: Context
) {
    private val contentResolver = context.applicationContext.contentResolver

    fun listVisibleCalendars(
        lookBackDays: Long = 14,
        lookAheadDays: Long = 140
    ): List<CalendarImportSource> {
        val eventCountByCalendar = queryCandidateEventCountByCalendar(lookBackDays, lookAheadDays)
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.OWNER_ACCOUNT,
            CalendarContract.Calendars.VISIBLE
        )
        val selection = "${CalendarContract.Calendars.VISIBLE}=1"
        val cursor = contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} COLLATE NOCASE ASC"
        ) ?: throw IllegalArgumentException("无法读取系统日历列表。")

        return buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val id = it.getLong(0)
                    val displayName = it.getString(1).orEmpty().trim()
                    val accountName = it.getString(2).orEmpty().trim()
                    val ownerAccount = it.getString(3).orEmpty().trim()
                    val eventCountHint = eventCountByCalendar[id] ?: 0
                    if (displayName.isBlank() || eventCountHint <= 0) continue
                    add(
                        CalendarImportSource(
                            id = id,
                            displayName = displayName,
                            accountName = accountName,
                            ownerAccount = ownerAccount,
                            eventCountHint = eventCountHint
                        )
                    )
                }
            }
        }
    }

    fun exportVisibleEventsAsIcs(
        selectedCalendarIds: Set<Long> = emptySet(),
        lookBackDays: Long = 14,
        lookAheadDays: Long = 140
    ): CalendarScheduleImportResult {
        val zoneId = ZoneId.systemDefault()
        val startMillis = Instant.now().minusSeconds(lookBackDays * 24 * 60 * 60).toEpochMilli()
        val endMillis = Instant.now().plusSeconds(lookAheadDays * 24 * 60 * 60).toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.DELETED,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.CALENDAR_ID
        )
        val selectionParts = mutableListOf(
            "${CalendarContract.Events.DELETED}=0",
            "${CalendarContract.Events.DTSTART}>=?",
            "${CalendarContract.Events.DTSTART}<=?",
            "${CalendarContract.Events.VISIBLE}=1"
        )
        val selectionArgs = mutableListOf(startMillis.toString(), endMillis.toString())
        if (selectedCalendarIds.isNotEmpty()) {
            selectionParts += "${CalendarContract.Events.CALENDAR_ID} IN (${selectedCalendarIds.joinToString(",") { "?" }})"
            selectionArgs += selectedCalendarIds.map(Long::toString)
        }

        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selectionParts.joinToString(" AND "),
            selectionArgs.toTypedArray(),
            "${CalendarContract.Events.DTSTART} ASC"
        ) ?: throw IllegalArgumentException("无法读取系统日历事件。")

        val matchedCalendarIds = linkedSetOf<Long>()
        val events = buildList {
            cursor.use {
                while (it.moveToNext()) {
                    val title = it.getString(0).orEmpty().trim()
                    val location = it.getString(1).orEmpty().trim()
                    val description = it.getString(2).orEmpty().trim()
                    val startAt = it.getLong(3)
                    val endAt = it.getLong(4)
                    val rrule = it.getString(5).orEmpty().trim()
                    val isDeleted = it.getInt(6) != 0
                    val allDay = it.getInt(7) != 0
                    val calendarName = it.getString(8).orEmpty().trim()
                    val calendarId = it.getLong(9)

                    if (isDeleted || allDay || title.isBlank() || startAt <= 0L || endAt <= startAt) continue
                    if (!CalendarScheduleHeuristics.looksLikeScheduleEvent(title, description, location, calendarName, rrule)) continue

                    matchedCalendarIds += calendarId
                    add(
                        buildIcsEvent(
                            summary = title,
                            location = location,
                            description = description,
                            startAt = startAt,
                            endAt = endAt,
                            recurrenceRule = rrule,
                            zoneId = zoneId
                        )
                    )
                }
            }
        }

        require(events.isNotEmpty()) { "系统日历中没有找到可导入的课程事件。" }

        return CalendarScheduleImportResult(
            icsText = buildString {
                appendLine("BEGIN:VCALENDAR")
                appendLine("VERSION:2.0")
                events.forEach { append(it) }
                append("END:VCALENDAR")
            },
            importedEventCount = events.size,
            matchedCalendarCount = matchedCalendarIds.size
        )
    }

    private fun queryCandidateEventCountByCalendar(
        lookBackDays: Long,
        lookAheadDays: Long
    ): Map<Long, Int> {
        val startMillis = Instant.now().minusSeconds(lookBackDays * 24 * 60 * 60).toEpochMilli()
        val endMillis = Instant.now().plusSeconds(lookAheadDays * 24 * 60 * 60).toEpochMilli()
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.RRULE,
            CalendarContract.Events.CALENDAR_DISPLAY_NAME,
            CalendarContract.Events.DELETED,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND
        )
        val selection = buildString {
            append("${CalendarContract.Events.DELETED}=0")
            append(" AND ${CalendarContract.Events.DTSTART}>=?")
            append(" AND ${CalendarContract.Events.DTSTART}<=?")
            append(" AND ${CalendarContract.Events.VISIBLE}=1")
        }
        val selectionArgs = arrayOf(startMillis.toString(), endMillis.toString())
        val cursor = contentResolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            null
        ) ?: return emptyMap()

        return buildMap {
            cursor.use {
                while (it.moveToNext()) {
                    val calendarId = it.getLong(0)
                    val title = it.getString(1).orEmpty().trim()
                    val location = it.getString(2).orEmpty().trim()
                    val description = it.getString(3).orEmpty().trim()
                    val rrule = it.getString(4).orEmpty().trim()
                    val calendarName = it.getString(5).orEmpty().trim()
                    val isDeleted = it.getInt(6) != 0
                    val allDay = it.getInt(7) != 0
                    val startAt = it.getLong(8)
                    val endAt = it.getLong(9)

                    if (isDeleted || allDay || title.isBlank() || startAt <= 0L || endAt <= startAt) continue
                    if (!CalendarScheduleHeuristics.looksLikeScheduleEvent(title, description, location, calendarName, rrule)) continue
                    put(calendarId, (get(calendarId) ?: 0) + 1)
                }
            }
        }
    }

    private fun buildIcsEvent(
        summary: String,
        location: String,
        description: String,
        startAt: Long,
        endAt: Long,
        recurrenceRule: String,
        zoneId: ZoneId
    ): String {
        val start = LocalDateTime.ofInstant(Instant.ofEpochMilli(startAt), zoneId)
        val end = LocalDateTime.ofInstant(Instant.ofEpochMilli(endAt), zoneId)
        return buildString {
            appendLine("BEGIN:VEVENT")
            appendLine("SUMMARY:${escapeIcsText(summary)}")
            appendLine("DTSTART:${start.format(ICS_DATE_TIME_FORMATTER)}")
            appendLine("DTEND:${end.format(ICS_DATE_TIME_FORMATTER)}")
            if (location.isNotBlank()) appendLine("LOCATION:${escapeIcsText(location)}")
            if (description.isNotBlank()) appendLine("DESCRIPTION:${escapeIcsText(description)}")
            if (recurrenceRule.isNotBlank()) appendLine("RRULE:${escapeIcsText(recurrenceRule)}")
            appendLine("END:VEVENT")
        }
    }

    private fun escapeIcsText(value: String): String {
        return value
            .replace("\\", "\\\\")
            .replace("\n", "\\n")
            .replace(",", "\\,")
            .replace(";", "\\;")
    }

    private companion object {
        val ICS_DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    }
}
