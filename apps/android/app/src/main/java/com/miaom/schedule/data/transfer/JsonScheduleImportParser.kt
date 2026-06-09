package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.CourseColorStyle
import com.miaom.schedule.domain.model.CourseEntry
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.TimeSlotTemplate
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.normalized
import com.google.gson.JsonArray
import com.google.gson.JsonElement
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.util.UUID

object JsonScheduleImportParser {
    private data class ParsedCourseRecord(
        val courseName: String,
        val teacher: String,
        val location: String,
        val dayOfWeek: Int,
        val slotLabel: String,
        val startTime: String,
        val endTime: String,
        val weekParity: WeekParity,
        val weekNumbers: List<Int>
    )

    private data class ExtractedCourseRecords(
        val records: List<ParsedCourseRecord>,
        val warnings: List<String>
    )

    private data class ParsedCourseArray(
        val records: List<ParsedCourseRecord>,
        val warnings: List<String>,
        val skippedCount: Int
    )

    private data class ParsedCourseObject(
        val record: ParsedCourseRecord?,
        val warnings: List<String> = emptyList()
    )

    private data class ParsedLessonTextObject(
        val records: List<ParsedCourseRecord>,
        val warnings: List<String> = emptyList()
    )

    internal fun looksLikeSupportedPayload(rawJson: String): Boolean {
        val normalizedText = rawJson.trim()
        if (normalizedText.isBlank()) return false
        return extractCourseRecords(normalizedText).records.isNotEmpty()
    }

    fun parse(rawJson: String, currentDocument: ScheduleDocument): CommonScheduleImportResult {
        val normalizedText = rawJson.trim()
        require(normalizedText.isNotBlank()) { "没有可导入的 JSON 课表内容。" }

        val extracted = extractCourseRecords(normalizedText)
        val records = extracted.records
        require(records.isNotEmpty()) { "JSON 中没有找到可导入的课程数组。" }

        val slotIdByKey = linkedMapOf<String, String>()
        val timeSlots = mutableListOf<TimeSlotTemplate>()
        val courseEntries = records.map { row ->
            val resolvedSlotLabel = row.slotLabel.ifBlank {
                when {
                    row.startTime.isNotBlank() && row.endTime.isNotBlank() -> "${row.startTime}-${row.endTime}"
                    row.startTime.isNotBlank() -> row.startTime
                    else -> "未命名节次"
                }
            }
            val slotKey = "${resolvedSlotLabel}|${row.startTime}|${row.endTime}"
            val slotId = slotIdByKey.getOrPut(slotKey) {
                val id = UUID.randomUUID().toString()
                timeSlots += TimeSlotTemplate(
                    id = id,
                    label = resolvedSlotLabel,
                    startTime = row.startTime,
                    endTime = row.endTime,
                    order = timeSlots.size,
                    enabled = true
                )
                id
            }

            CourseEntry(
                id = UUID.randomUUID().toString(),
                name = row.courseName,
                teacher = row.teacher,
                location = row.location,
                dayOfWeek = row.dayOfWeek,
                timeSlotTemplateId = slotId,
                weekParity = row.weekParity,
                weekNumbers = row.weekNumbers,
                colorStyle = CourseColorStyle()
            )
        }

        val importedDocument = currentDocument.copy(
            timeSlotTemplates = timeSlots,
            courseEntries = courseEntries,
            reminderRules = emptyList()
        ).normalized(updatedAtEpochMillis = System.currentTimeMillis())

        return CommonScheduleImportResult(
            document = importedDocument,
            detectedFormat = "JSON 课表",
            importedCourseCount = courseEntries.size,
            importedTimeSlotCount = timeSlots.size,
            warnings = extracted.warnings
        )
    }

    private fun extractCourseRecords(rawJson: String): ExtractedCourseRecords {
        val root = runCatching { JsonParser.parseString(rawJson) }.getOrNull()
            ?: return ExtractedCourseRecords(emptyList(), emptyList())
        when {
            root.isJsonObject -> {
                val rootObject = root.asJsonObject
                val candidatePaths = listOf(
                    "courses",
                    "courseInfos",
                    "courseInfoList",
                    "courseList",
                    "courseEntries",
                    "kbList",
                    "schedule",
                    "data",
                    "list",
                    "rows",
                    "items",
                    "records",
                    "data.courseInfos",
                    "data.courseInfoList",
                    "data.courseList",
                    "data.courses",
                    "data.courseEntries",
                    "data.kbList",
                    "data.schedule",
                    "data.list",
                    "data.rows",
                    "data.items",
                    "result.courseInfos",
                    "result.courseList",
                    "result.courses",
                    "result.courseEntries",
                    "result.schedule",
                    "result.list",
                    "result.rows",
                    "result.data",
                    "result.data.courseInfos",
                    "result.data.courseList",
                    "result.data.courses",
                    "result.data.list",
                    "payload.courseInfos",
                    "payload.courseList",
                    "payload.courses",
                    "payload.list"
                )
                val candidates = candidatePaths.mapNotNull { path -> rootObject.getAsJsonArrayPathOrNull(path) }

                candidates.forEach { array ->
                    val parsed = parseCourseArray(array)
                    if (parsed.records.isNotEmpty()) {
                        return ExtractedCourseRecords(
                            records = parsed.records,
                            warnings = buildWarnings(parsed)
                        )
                    }
                }

                parseLessonTextPayload(rootObject)?.let { parsed ->
                    if (parsed.records.isNotEmpty()) {
                        return ExtractedCourseRecords(
                            records = parsed.records,
                            warnings = buildWarnings(parsed)
                        )
                    }
                }
            }
            root.isJsonArray -> {
                val parsed = parseCourseArray(root.asJsonArray)
                if (parsed.records.isNotEmpty()) {
                    return ExtractedCourseRecords(
                        records = parsed.records,
                        warnings = buildWarnings(parsed)
                    )
                }
            }
        }

        return ExtractedCourseRecords(emptyList(), emptyList())
    }

    private fun buildWarnings(parsed: ParsedCourseArray): List<String> {
        val warnings = linkedSetOf<String>()
        warnings += parsed.warnings
        if (parsed.skippedCount > 0) {
            warnings += "跳过了 ${parsed.skippedCount} 条缺少课程名或星期字段的 JSON 记录。"
        }
        return warnings.toList()
    }

    private fun parseCourseArray(array: JsonArray): ParsedCourseArray {
        val records = mutableListOf<ParsedCourseRecord>()
        val warnings = linkedSetOf<String>()
        var skippedCount = 0
        array.forEach { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val parsed = parseCourseObject(item)
            if (parsed.record != null) {
                records += parsed.record
                warnings += parsed.warnings
            } else {
                skippedCount += 1
            }
        }
        return ParsedCourseArray(
            records = records,
            warnings = warnings.toList(),
            skippedCount = skippedCount
        )
    }

    private fun parseLessonTextPayload(root: JsonObject): ParsedCourseArray? {
        val candidatePaths = listOf(
            "lessons",
            "data.lessons",
            "result.lessons",
            "payload.lessons"
        )
        candidatePaths.forEach { path ->
            val lessons = root.getAsJsonArrayPathOrNull(path) ?: return@forEach
            val parsed = parseLessonArray(lessons)
            if (parsed.records.isNotEmpty()) return parsed
        }
        return null
    }

    private fun parseLessonArray(array: JsonArray): ParsedCourseArray {
        val records = mutableListOf<ParsedCourseRecord>()
        val warnings = linkedSetOf<String>()
        var skippedCount = 0
        array.forEach { element ->
            val item = element.takeIf { it.isJsonObject }?.asJsonObject ?: return@forEach
            val parsed = parseLessonObject(item)
            if (parsed.records.isEmpty()) {
                skippedCount += 1
            } else {
                records += parsed.records
                warnings += parsed.warnings
            }
        }
        return ParsedCourseArray(
            records = records,
            warnings = warnings.toList(),
            skippedCount = skippedCount
        )
    }

    private fun parseLessonObject(item: JsonObject): ParsedLessonTextObject {
        val courseName = firstString(
            item,
            "course.nameZh",
            "course.name",
            "course.courseName",
            "courseName",
            "nameZh",
            "name",
            "title"
        ).trim()
        val scheduleText = firstString(
            item,
            "scheduleText.dateTimePlacePersonText.textZh",
            "dateTimePlacePersonText.textZh",
            "scheduleText.textZh",
            "scheduleText.text",
            "courseTime"
        )
        if (courseName.isBlank() || scheduleText.isBlank()) {
            return ParsedLessonTextObject(records = emptyList())
        }

        val records = mutableListOf<ParsedCourseRecord>()
        val warnings = mutableListOf<String>()
        splitLessonTextSegments(scheduleText).forEachIndexed { index, segment ->
            val record = parseLessonSegment(courseName, segment)
            if (record != null) {
                records += record
            } else {
                warnings += "跳过课程“$courseName”的第 ${index + 1} 段时间文本，未识别出周次、星期和节次。"
            }
        }

        return ParsedLessonTextObject(
            records = records,
            warnings = warnings
        )
    }

    private fun splitLessonTextSegments(rawValue: String): List<String> {
        return rawValue
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .split(Regex("[;；]+\\s*\\n*"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
    }

    private fun parseLessonSegment(courseName: String, segment: String): ParsedCourseRecord? {
        val tokens = segment.split(Regex("\\s+"))
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (tokens.size < 3) return null

        val dayIndex = tokens.indexOfFirst { parseDayOfWeek(it) != null }
        if (dayIndex !in 1 until tokens.lastIndex) return null

        val dayOfWeek = parseDayOfWeek(tokens[dayIndex]) ?: return null
        val weekToken = tokens.take(dayIndex).joinToString(separator = "")
        val slotToken = tokens.getOrNull(dayIndex + 1).orEmpty()
        if (weekToken.isBlank() || slotToken.isBlank()) return null

        val remainingTokens = tokens.drop(dayIndex + 2).toMutableList()
        val clockRangeToken = remainingTokens.firstOrNull()
            ?.takeIf { parseTimeRange(it).first.isNotBlank() }
        val (startTime, endTime) = clockRangeToken?.let(::parseTimeRange) ?: ("" to "")
        if (clockRangeToken != null) {
            remainingTokens.removeAt(0)
        }

        val teacher = when {
            remainingTokens.size >= 2 -> ImportFieldCleaner.teachers(listOf(remainingTokens.last()))
            else -> ""
        }
        val location = when {
            remainingTokens.size >= 2 -> ImportFieldCleaner.locations(remainingTokens.dropLast(1))
            remainingTokens.size == 1 -> ImportFieldCleaner.locations(listOf(remainingTokens.first()))
            else -> ""
        }
        val weekPattern = WeekPatternParser.parse(weekToken, forceWeekNumbers = true)

        return ParsedCourseRecord(
            courseName = courseName,
            teacher = teacher,
            location = location,
            dayOfWeek = dayOfWeek,
            slotLabel = buildSlotLabelFromRangeToken(slotToken).ifBlank {
                buildSlotLabelFromTime(startTime, endTime).ifBlank { slotToken }
            },
            startTime = startTime,
            endTime = endTime,
            weekParity = weekPattern.weekParity,
            weekNumbers = weekPattern.weekNumbers
        )
    }

    private fun parseCourseObject(item: JsonObject): ParsedCourseObject {
        val courseName = firstString(
            item,
            "courseName",
            "course_title",
            "course_name",
            "courseNm",
            "lessonName",
            "lesson_name",
            "kcmc",
            "course.name",
            "course.title",
            "course.courseName",
            "name",
            "course",
            "title"
        ).trim()
        val dayOfWeek = parseDayOfWeek(
            firstString(
                item,
                "dayOfWeek",
                "weekday",
                "weekDay",
                "day_name",
                "weekdayName",
                "weekIndex",
                "dayOfTheWeek",
                "day_of_week",
                "skxq",
                "xqj",
                "xqjmc",
                "day",
                "time.dayOfWeek",
                "time.weekday",
                "time.day",
                "schedule.dayOfWeek",
                "schedule.weekday",
                "schedule.day",
                "arrange.dayOfWeek",
                "arrange.weekday",
                "arrange.day"
            )
        )
        if (courseName.isBlank() || dayOfWeek == null) return ParsedCourseObject(record = null)

        val teacher = firstString(
            item,
            "teacher",
            "teachers",
            "teacherName",
            "teacher_name",
            "teacherList",
            "teacher_list",
            "teacherInfos",
            "teacherNames",
            "lecturer",
            "instructor",
            "jsmc",
            "teacher.name",
            "instructor.name"
        ).let { ImportFieldCleaner.teachers(listOf(it)) }
        val location = firstString(
            item,
            "location",
            "locations",
            "room",
            "rooms",
            "classroom",
            "place",
            "roomName",
            "roomList",
            "room_list",
            "locationList",
            "classroomList",
            "classroomName",
            "classRoom",
            "address",
            "position",
            "jsap",
            "location.name",
            "room.name"
        ).let { ImportFieldCleaner.locations(listOf(it)) }
        val weekPattern = parseWeekPattern(item)

        val timeRange = firstString(
            item,
            "time",
            "timeRange",
            "time_range",
            "classTime",
            "class_time",
            "timeText",
            "periodText",
            "courseTime",
            "sjdd"
        )
        val (rangeStart, rangeEnd) = parseTimeRange(timeRange)
        val startTime = normalizeLooseTime(
            firstString(
                item,
                "startTime",
                "beginTime",
                "fromTime",
                "start_time",
                "time.start",
                "time.begin"
            )
        )
            .ifBlank { rangeStart }
        val endTime = normalizeLooseTime(
            firstString(
                item,
                "endTime",
                "finishTime",
                "toTime",
                "end_time",
                "time.end",
                "time.finish"
            )
        )
            .ifBlank { rangeEnd }

        val slotLabel = firstString(
            item,
            "slotLabel",
            "section",
            "period",
            "node",
            "classPeriod",
            "periodLabel",
            "sectionName",
            "lessonPeriod",
            "jc"
        ).trim().ifBlank {
            val startPeriod = firstString(
                item,
                "startNode",
                "startSection",
                "startPeriod",
                "beginNode",
                "fromNode",
                "beginSection",
                "fromSection",
                "nodeStart",
                "ksjc"
            )
            val endPeriod = firstString(
                item,
                "endNode",
                "endSection",
                "endPeriod",
                "finishNode",
                "toNode",
                "finishSection",
                "toSection",
                "nodeEnd",
                "jsjc"
            )
            val (sectionStart, sectionEnd) = parseSectionRange(item)
            buildSlotLabelFromPeriod(startPeriod, endPeriod).ifBlank {
                buildSlotLabelFromPeriod(
                    sectionStart?.toString().orEmpty(),
                    sectionEnd?.toString().orEmpty()
                ).ifBlank {
                buildSlotLabelFromRangeToken(timeRange).ifBlank {
                    buildSlotLabelFromTime(startTime, endTime)
                }
                }
            }
        }

        return ParsedCourseObject(
            record = ParsedCourseRecord(
                courseName = courseName,
                teacher = teacher,
                location = location,
                dayOfWeek = dayOfWeek,
                slotLabel = slotLabel,
                startTime = startTime,
                endTime = endTime,
                weekParity = weekPattern.weekParity,
                weekNumbers = weekPattern.weekNumbers
            ),
            warnings = emptyList()
        )
    }

    private fun parseWeekPattern(item: JsonObject): ParsedWeekPattern {
        listOf(
            "weeks",
            "time.weeks",
            "time.weekRange",
            "schedule.weeks",
            "schedule.weekRange",
            "arrange.weeks",
            "arrange.weekRange"
        ).forEach { path ->
            val weekValue = getJsonValueByPath(item, path) ?: return@forEach
            parseWeekPatternFromJsonValue(weekValue)?.let { return it }
        }

        val rawWeekValue = firstString(
            item,
            "weekParity",
            "weekType",
            "week_type",
            "weekText",
            "weekRange",
            "week_range",
            "weekNum",
            "weekRemark",
            "weekDesc",
            "weekDescription",
            "zcd",
            "teachingWeek"
        )
        val splitWeekPattern = buildWeekPatternFromFields(
            startWeekValue = firstString(
                item,
                "startWeek",
                "start_week",
                "weekStart",
                "week_start",
                "beginWeek",
                "fromWeek"
            ),
            endWeekValue = firstString(
                item,
                "endWeek",
                "end_week",
                "weekEnd",
                "week_end",
                "finishWeek",
                "toWeek"
            ),
            weekTypeValue = firstString(
                item,
                "weekType",
                "week_type",
                "weekParity",
                "weekMode"
            )
        )
        val mergedWeekValue = mergeWeekPatternValues(rawWeekValue, splitWeekPattern)
        return WeekPatternParser.parse(
            rawValue = mergedWeekValue,
            forceWeekNumbers = splitWeekPattern.any(Char::isDigit)
        )
    }

    private fun parseWeekPatternFromJsonValue(value: JsonElement): ParsedWeekPattern? {
        return when {
            value.isJsonNull -> null
            value.isJsonPrimitive -> WeekPatternParser.parse(primitiveStringOrNull(value).orEmpty(), forceWeekNumbers = true)
            value.isJsonArray -> {
                val explicitWeeks = mutableListOf<Int>()
                var fallbackParity: WeekParity? = null

                value.asJsonArray.forEach { element ->
                    when {
                        element.isJsonPrimitive -> {
                            val parsed = WeekPatternParser.parse(
                                primitiveStringOrNull(element).orEmpty(),
                                forceWeekNumbers = true
                            )
                            if (parsed.weekNumbers.isNotEmpty()) {
                                explicitWeeks += parsed.weekNumbers
                            } else if (fallbackParity == null && parsed.weekParity != WeekParity.Every) {
                                fallbackParity = parsed.weekParity
                            }
                        }
                        element.isJsonObject -> {
                            val objectValue = element.asJsonObject
                            val numericWeek = firstInt(
                                objectValue,
                                "week",
                                "weekIndex",
                                "index",
                                "value"
                            )
                            if (numericWeek != null) {
                                explicitWeeks += numericWeek
                            } else {
                                val weekText = firstString(
                                    objectValue,
                                    "text",
                                    "label",
                                    "name",
                                    "weekText",
                                    "weekRange",
                                    "week",
                                    "value"
                                )
                                val parsed = WeekPatternParser.parse(weekText, forceWeekNumbers = true)
                                if (parsed.weekNumbers.isNotEmpty()) {
                                    explicitWeeks += parsed.weekNumbers
                                } else if (fallbackParity == null && parsed.weekParity != WeekParity.Every) {
                                    fallbackParity = parsed.weekParity
                                }
                            }
                        }
                    }
                }

                val weeks = explicitWeeks
                    .map { it.coerceAtLeast(1) }
                    .distinct()
                    .sorted()

                when {
                    weeks.isNotEmpty() -> ParsedWeekPattern(
                        weekParity = com.miaom.schedule.domain.model.resolveWeekParity(weeks),
                        weekNumbers = weeks
                    )
                    fallbackParity != null -> ParsedWeekPattern(
                        weekParity = fallbackParity ?: WeekParity.Every,
                        weekNumbers = emptyList()
                    )
                    else -> null
                }
            }
            value.isJsonObject -> parseWeekPatternFromJsonObject(value.asJsonObject)
            else -> null
        }
    }

    private fun parseWeekPatternFromJsonObject(item: JsonObject): ParsedWeekPattern? {
        val numericWeek = firstInt(
            item,
            "week",
            "weekIndex",
            "index",
            "value"
        )
        if (numericWeek != null) {
            val weeks = listOf(numericWeek.coerceAtLeast(1))
            return ParsedWeekPattern(
                weekParity = com.miaom.schedule.domain.model.resolveWeekParity(weeks),
                weekNumbers = weeks
            )
        }

        val weekText = firstString(
            item,
            "text",
            "label",
            "name",
            "weekText",
            "weekRange",
            "week",
            "value"
        )
        val splitWeekPattern = buildWeekPatternFromFields(
            startWeekValue = firstString(
                item,
                "startWeek",
                "start_week",
                "weekStart",
                "week_start",
                "beginWeek",
                "fromWeek"
            ),
            endWeekValue = firstString(
                item,
                "endWeek",
                "end_week",
                "weekEnd",
                "week_end",
                "finishWeek",
                "toWeek"
            ),
            weekTypeValue = firstString(
                item,
                "weekType",
                "week_type",
                "weekParity",
                "weekMode"
            )
        )
        val mergedWeekValue = mergeWeekPatternValues(weekText, splitWeekPattern)
        val parsed = WeekPatternParser.parse(
            rawValue = mergedWeekValue,
            forceWeekNumbers = mergedWeekValue.any(Char::isDigit)
        )
        return parsed.takeIf { it.weekNumbers.isNotEmpty() || it.weekParity != WeekParity.Every }
    }

    private fun parseSectionRange(item: JsonObject): Pair<Int?, Int?> {
        val sectionsValue = listOf(
            "sections",
            "sectionList",
            "time.sections",
            "time.sectionList",
            "schedule.sections",
            "schedule.sectionList",
            "arrange.sections",
            "arrange.sectionList"
        ).firstNotNullOfOrNull { path -> getJsonValueByPath(item, path) } ?: return null to null

        if (sectionsValue.isJsonObject) {
            val sectionNumbers = parseSectionNumbers(sectionsValue.asJsonObject)
            return sectionNumbers.firstOrNull() to sectionNumbers.lastOrNull()
        }

        if (!sectionsValue.isJsonArray) return null to null

        val sectionNumbers = sectionsValue.asJsonArray.flatMap { element ->
            when {
                element.isJsonPrimitive -> parseSectionNumbers(primitiveStringOrNull(element).orEmpty())
                element.isJsonObject -> parseSectionNumbers(element.asJsonObject)
                else -> emptyList()
            }
        }
            .filter { it in 1..30 }
            .distinct()
            .sorted()

        return sectionNumbers.firstOrNull() to sectionNumbers.lastOrNull()
    }

    private fun parseSectionNumbers(item: JsonObject): List<Int> {
        val startSection = firstInt(
            item,
            "startSection",
            "startNode",
            "start",
            "from"
        )
        val endSection = firstInt(
            item,
            "endSection",
            "endNode",
            "end",
            "to"
        )
        return when {
            startSection != null || endSection != null -> listOfNotNull(startSection, endSection)
            else -> {
                val numericSection = firstInt(
                    item,
                    "section",
                    "node",
                    "index",
                    "value"
                )
                if (numericSection != null) {
                    listOf(numericSection)
                } else {
                    parseSectionNumbers(
                        firstString(
                            item,
                            "text",
                            "label",
                            "name",
                            "sectionName",
                            "period",
                            "range",
                            "value"
                        )
                    )
                }
            }
        }
    }

    private fun parseSectionNumbers(rawValue: String): List<Int> {
        val normalized = rawValue.trim()
        if (normalized.isBlank() || normalized.contains(':')) return emptyList()
        return Regex("(\\d{1,2})")
            .findAll(normalized)
            .mapNotNull { it.groupValues.getOrNull(1)?.toIntOrNull() }
            .filter { it in 1..30 }
            .toList()
    }

    private fun firstString(item: JsonObject, vararg keys: String): String {
        keys.forEach { key ->
            val value = getJsonValueByPath(item, key) ?: return@forEach
            extractStringValue(value)?.let { return it }
        }
        return ""
    }

    private fun extractStringValue(value: JsonElement): String? {
        return when {
            value.isJsonNull -> null
            value.isJsonPrimitive -> primitiveStringOrNull(value)
            value.isJsonArray -> value.asJsonArray
                .mapNotNull(::extractStringValue)
                .joinToString(" / ")
                .trim()
                .takeIf { it.isNotBlank() }
            value.isJsonObject -> extractStringFromObject(value.asJsonObject)
            else -> null
        }
    }

    private fun extractStringFromObject(item: JsonObject): String? {
        val preferredKeys = listOf(
            "name",
            "xm",
            "mc",
            "title",
            "text",
            "value",
            "label",
            "teacher",
            "teacherName",
            "realName",
            "displayName",
            "personName",
            "room",
            "roomName",
            "classroom",
            "classroomName",
            "locationName",
            "placeName",
            "location",
            "place",
            "address",
            "position"
        )
        preferredKeys.forEach { key ->
            val child = item.get(key) ?: return@forEach
            extractStringValue(child)?.let { return it }
        }

        if (item.entrySet().size == 1) {
            extractStringValue(item.entrySet().first().value)?.let { return it }
        }
        return null
    }

    private fun buildWeekPatternFromFields(
        startWeekValue: String,
        endWeekValue: String,
        weekTypeValue: String
    ): String {
        val startWeek = parseWeekNumber(startWeekValue)
        val endWeek = parseWeekNumber(endWeekValue)
        val range = when {
            startWeek != null && endWeek != null -> {
                val (start, end) = if (startWeek <= endWeek) startWeek to endWeek else endWeek to startWeek
                "$start-${end}周"
            }
            startWeek != null -> "${startWeek}周"
            endWeek != null -> "${endWeek}周"
            else -> ""
        }

        return buildString {
            append(range)
            append(weekTypeValue.trim())
        }.trim()
    }

    private fun mergeWeekPatternValues(explicitWeeks: String, splitWeekPattern: String): String {
        val explicit = explicitWeeks.trim()
        val split = splitWeekPattern.trim()
        if (explicit.isBlank()) return split
        if (split.isBlank()) return explicit

        val explicitHasDigits = explicit.any(Char::isDigit)
        val splitHasDigits = split.any(Char::isDigit)
        return when {
            explicitHasDigits && !splitHasDigits -> appendWeekParityIfMissing(explicit, split)
            !explicitHasDigits && splitHasDigits -> appendWeekParityIfMissing(split, explicit)
            explicitHasDigits -> appendWeekParityIfMissing(explicit, split)
            else -> explicit
        }
    }

    private fun appendWeekParityIfMissing(base: String, suffixSource: String): String {
        val paritySuffix = extractParityToken(suffixSource)
        if (paritySuffix.isBlank()) return base
        if (extractParityToken(base).isNotBlank()) return base
        return base + paritySuffix
    }

    private fun extractParityToken(value: String): String {
        val normalized = value.trim()
        return when {
            normalized.contains("单") || normalized.contains("odd", ignoreCase = true) -> "单周"
            normalized.contains("双") || normalized.contains("even", ignoreCase = true) -> "双周"
            normalized.contains("每") || normalized.contains("every", ignoreCase = true) || normalized.contains("all", ignoreCase = true) -> "每周"
            else -> ""
        }
    }

    private fun primitiveStringOrNull(value: JsonElement): String? {
        if (!value.isJsonPrimitive) return null
        val primitive = value.asJsonPrimitive
        return when {
            primitive.isString -> primitive.asString.takeIf { it.isNotBlank() }
            primitive.isNumber -> primitive.asNumber.toString()
            primitive.isBoolean -> primitive.asBoolean.toString()
            else -> null
        }
    }

    private fun firstInt(item: JsonObject, vararg keys: String): Int? {
        return firstString(item, *keys).toIntOrNull()
    }

    private fun JsonObject.getAsJsonArrayPathOrNull(path: String): JsonArray? {
        val value = getJsonValueByPath(this, path) ?: return null
        return value.takeIf { it.isJsonArray }?.asJsonArray
    }

    private fun getJsonValueByPath(root: JsonObject, path: String): JsonElement? {
        var current: JsonElement = root
        path.split('.')
            .filter { it.isNotBlank() }
            .forEach { segment ->
                val next = current.takeIf { it.isJsonObject }?.asJsonObject?.get(segment) ?: return null
                current = next
            }
        return current
    }

    private fun parseDayOfWeek(rawValue: String): Int? {
        val trimmed = rawValue.trim()
            .replace("星期天", "星期日")
            .replace("周天", "周日")
            .lowercase()
        return when {
            trimmed.isBlank() -> null
            trimmed == "1" || trimmed == "mon" || trimmed == "monday" || trimmed.contains("周一") || trimmed.contains("星期一") -> 1
            trimmed == "2" || trimmed == "tue" || trimmed == "tuesday" || trimmed.contains("周二") || trimmed.contains("星期二") -> 2
            trimmed == "3" || trimmed == "wed" || trimmed == "wednesday" || trimmed.contains("周三") || trimmed.contains("星期三") -> 3
            trimmed == "4" || trimmed == "thu" || trimmed == "thursday" || trimmed.contains("周四") || trimmed.contains("星期四") -> 4
            trimmed == "5" || trimmed == "fri" || trimmed == "friday" || trimmed.contains("周五") || trimmed.contains("星期五") -> 5
            trimmed == "6" || trimmed == "sat" || trimmed == "saturday" || trimmed.contains("周六") || trimmed.contains("星期六") -> 6
            trimmed == "7" || trimmed == "sun" || trimmed == "sunday" || trimmed.contains("周日") || trimmed.contains("星期日") -> 7
            else -> null
        }
    }

    private fun parseTimeRange(rawValue: String): Pair<String, String> {
        val normalized = rawValue
            .trim()
            .replace('：', ':')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .replace('至', '-')
            .replace('~', '-')
        val match = Regex("(\\d{1,2}[:.]?\\d{2})\\s*-\\s*(\\d{1,2}[:.]?\\d{2})").find(normalized)
            ?: return "" to ""
        return normalizeLooseTime(match.groupValues[1]) to normalizeLooseTime(match.groupValues[2])
    }

    private fun normalizeLooseTime(rawValue: String): String {
        val value = rawValue.trim().replace('：', ':').replace('.', ':')
        val fourDigit = Regex("^(\\d{2})(\\d{2})$").matchEntire(value)
        if (fourDigit != null) {
            return "${fourDigit.groupValues[1]}:${fourDigit.groupValues[2]}"
        }
        val match = Regex("^(\\d{1,2}):(\\d{1,2})$").matchEntire(value) ?: return ""
        val hour = match.groupValues[1].toIntOrNull() ?: return ""
        val minute = match.groupValues[2].toIntOrNull() ?: return ""
        if (hour !in 0..23 || minute !in 0..59) return ""
        return "%02d:%02d".format(hour, minute)
    }

    private fun buildSlotLabelFromTime(startTime: String, endTime: String): String {
        return when {
            startTime.isNotBlank() && endTime.isNotBlank() -> "$startTime-$endTime"
            startTime.isNotBlank() -> startTime
            else -> ""
        }
    }

    private fun buildSlotLabelFromPeriod(startValue: String, endValue: String): String {
        val startPeriod = parsePeriodNumber(startValue) ?: return ""
        val endPeriod = parsePeriodNumber(endValue) ?: startPeriod
        return if (startPeriod == endPeriod) "$startPeriod 节" else "$startPeriod-$endPeriod 节"
    }

    private fun buildSlotLabelFromRangeToken(rawValue: String): String {
        val matches = Regex("(\\d{1,2})").findAll(rawValue).map { it.groupValues[1] }.toList()
        val startPeriod = matches.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..30 } ?: return ""
        val endPeriod = matches.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..30 } ?: startPeriod
        return if (startPeriod == endPeriod) "$startPeriod 节" else "$startPeriod-$endPeriod 节"
    }

    private fun parsePeriodNumber(rawValue: String): Int? {
        val match = Regex("(\\d{1,2})").find(rawValue.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 }
    }

    private fun parseWeekNumber(rawValue: String): Int? {
        val match = Regex("(\\d{1,2})").find(rawValue.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 }
    }
}
