package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.CourseColorStyle
import com.miaom.schedule.domain.model.CourseEntry
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.TimeSlotTemplate
import com.miaom.schedule.domain.model.WeekConfig
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.normalized
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.UUID

data class IcsScheduleImportResult(
    val document: ScheduleDocument,
    val importedCourseCount: Int,
    val importedTimeSlotCount: Int,
    val warnings: List<String>
)

object IcsScheduleImporter {
    private data class CalendarEvent(
        val summary: String,
        val location: String,
        val description: String,
        val start: LocalDateTime,
        val end: LocalDateTime,
        val recurrenceRule: String
    )

    fun parse(rawText: String, currentDocument: ScheduleDocument): IcsScheduleImportResult {
        val normalizedText = rawText.replace("\r\n", "\n").replace("\r", "\n")
        require(normalizedText.contains("BEGIN:VCALENDAR")) { "这不是可识别的 ICS 日历文件。" }

        val unfoldedLines = unfoldLines(normalizedText)
        val events = parseEvents(unfoldedLines)
        require(events.isNotEmpty()) { "ICS 文件中没有可导入的课程事件。" }

        val warnings = mutableListOf<String>()
        val sortedEvents = events.sortedBy { it.start }
        val slotMap = linkedMapOf<String, TimeSlotTemplate>()
        val courseEntries = mutableListOf<CourseEntry>()
        val baseMonday = sortedEvents.minOf { it.start.toLocalDate() }.with(java.time.DayOfWeek.MONDAY)

        sortedEvents.forEachIndexed { index, event ->
            if (event.summary.isBlank()) {
                warnings += "跳过第 ${index + 1} 个日历事件，课程名称为空。"
                return@forEachIndexed
            }

            val startTime = event.start.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val endTime = event.end.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
            val slotLabel = "$startTime-$endTime"
            val slotKey = "$slotLabel|$startTime|$endTime"
            val slot = slotMap.getOrPut(slotKey) {
                TimeSlotTemplate(
                    id = UUID.randomUUID().toString(),
                    label = slotLabel,
                    startTime = startTime,
                    endTime = endTime,
                    order = slotMap.size,
                    enabled = true
                )
            }

            courseEntries += CourseEntry(
                id = UUID.randomUUID().toString(),
                name = event.summary.trim(),
                teacher = extractTeacher(event.description),
                location = ImportFieldCleaner.locations(listOf(event.location)),
                dayOfWeek = event.start.dayOfWeek.value,
                timeSlotTemplateId = slot.id,
                weekParity = inferWeekParity(event.start.toLocalDate(), event.recurrenceRule, baseMonday),
                colorStyle = CourseColorStyle()
            )
        }

        require(courseEntries.isNotEmpty()) { "ICS 文件中没有可导入的课程事件。" }

        val document = currentDocument.copy(
            weekConfig = WeekConfig(
                firstDayOfWeek = 1,
                teachingDays = courseEntries.map { it.dayOfWeek }.distinct().sorted(),
                week1MondayDate = baseMonday.toString()
            ),
            timeSlotTemplates = slotMap.values.toList(),
            courseEntries = courseEntries,
            reminderRules = emptyList()
        ).normalized(updatedAtEpochMillis = System.currentTimeMillis())

        return IcsScheduleImportResult(
            document = document,
            importedCourseCount = courseEntries.size,
            importedTimeSlotCount = slotMap.size,
            warnings = warnings
        )
    }

    private fun unfoldLines(text: String): List<String> {
        val result = mutableListOf<String>()
        text.lines().forEach { line ->
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.lastIndex] = result.last() + line.trimStart()
            } else {
                result += line
            }
        }
        return result
    }

    private fun parseEvents(lines: List<String>): List<CalendarEvent> {
        val events = mutableListOf<CalendarEvent>()
        var current = mutableMapOf<String, String>()
        var insideEvent = false

        lines.forEach { rawLine ->
            val line = rawLine.trim()
            when {
                line == "BEGIN:VEVENT" -> {
                    insideEvent = true
                    current = mutableMapOf()
                }
                line == "END:VEVENT" && insideEvent -> {
                    parseEvent(current)?.let(events::add)
                    insideEvent = false
                }
                insideEvent && line.contains(':') -> {
                    val key = line.substringBefore(':').substringBefore(';').uppercase()
                    val value = line.substringAfter(':').trim()
                    current[key] = value
                }
            }
        }
        return events
    }

    private fun parseEvent(fields: Map<String, String>): CalendarEvent? {
        val summary = fields["SUMMARY"].orEmpty()
        val start = parseDateTime(fields["DTSTART"] ?: return null) ?: return null
        val end = parseDateTime(fields["DTEND"] ?: return null) ?: return null
        return CalendarEvent(
            summary = summary,
            location = fields["LOCATION"].orEmpty(),
            description = fields["DESCRIPTION"].orEmpty(),
            start = start,
            end = end,
            recurrenceRule = fields["RRULE"].orEmpty()
        )
    }

    private fun parseDateTime(value: String): LocalDateTime? {
        val normalized = value.removeSuffix("Z")
        return when (normalized.length) {
            8 -> runCatching {
                LocalDate.parse(normalized, DateTimeFormatter.BASIC_ISO_DATE).atStartOfDay()
            }.getOrNull()
            15 -> runCatching {
                LocalDateTime.parse(normalized, DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss"))
            }.getOrNull()
            else -> null
        }
    }

    private fun extractTeacher(description: String): String {
        val normalized = description.replace("\\n", "\n")
        val teacherLine = normalized.lines().firstOrNull {
            it.contains("教师") || it.contains("老师") || it.contains("teacher", ignoreCase = true)
        } ?: return ""
        return ImportFieldCleaner.teachers(
            listOf(
            teacherLine.substringAfter(':', teacherLine.substringAfter('：', teacherLine)).trim()
            )
        )
    }

    private fun inferWeekParity(date: LocalDate, recurrenceRule: String, week1Monday: LocalDate): WeekParity {
        val rule = recurrenceRule.uppercase()
        return when {
            rule.contains("INTERVAL=2") -> {
                val weeks = java.time.temporal.ChronoUnit.WEEKS.between(week1Monday, date)
                if (weeks % 2L == 0L) WeekParity.Odd else WeekParity.Even
            }
            else -> WeekParity.Every
        }
    }
}
