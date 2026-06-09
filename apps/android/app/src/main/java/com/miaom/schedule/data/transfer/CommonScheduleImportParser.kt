package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.CourseColorStyle
import com.miaom.schedule.domain.model.CourseEntry
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.TimeSlotTemplate
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.normalized
import java.util.UUID

data class CommonScheduleImportResult(
    val document: ScheduleDocument,
    val detectedFormat: String,
    val importedCourseCount: Int,
    val importedTimeSlotCount: Int,
    val warnings: List<String>
)

object CommonScheduleImportParser {
    private data class ParsedCourseRow(
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

    private data class ParsedTable(
        val formatLabel: String,
        val rows: List<ParsedCourseRow>,
        val warnings: List<String>
    )

    private data class HeaderMapping(
        val course: Int,
        val dayOfWeek: Int,
        val slot: Int? = null,
        val startPeriod: Int? = null,
        val endPeriod: Int? = null,
        val startTime: Int? = null,
        val endTime: Int? = null,
        val timeRange: Int? = null,
        val teacher: Int? = null,
        val location: Int? = null,
        val weeks: Int? = null,
        val startWeek: Int? = null,
        val endWeek: Int? = null,
        val weekType: Int? = null
    )

    private val courseAliases = setOf(
        "课程", "课程名称",
        "course", "coursename", "course_name", "coursenm",
        "lessonname", "lesson_name", "kcmc",
        "name"
    )
    private val dayAliases = setOf(
        "星期", "星期几", "周几",
        "weekday", "day", "dayofweek", "weekindex",
        "skxq", "xqj", "xqjmc"
    )
    private val slotAliases = setOf("节次", "节", "课节", "上课节次", "section", "period", "slot", "timeslot", "time_slot")
    private val startPeriodAliases = setOf("开始节", "开始节次", "开始节数", "起始节", "起始节次", "startperiod", "startsection", "startslot", "startnode", "beginnode", "fromnode", "nodestart", "ksjc")
    private val endPeriodAliases = setOf("结束节", "结束节次", "结束节数", "终止节", "终止节次", "endperiod", "endsection", "endslot", "endnode", "finishnode", "tonode", "nodeend", "jsjc")
    private val startAliases = setOf("开始时间", "上课时间", "开始", "start", "starttime")
    private val endAliases = setOf("结束时间", "下课时间", "结束", "end", "endtime")
    private val rangeAliases = setOf("时间", "时间段", "起止时间", "上课时间段", "timerange", "time")
    private val teacherAliases = setOf(
        "教师", "老师", "任课教师", "授课教师",
        "teacher", "teachername", "teacher_name", "teacherlist", "teacher_list",
        "teacherinfos", "teachernames", "lecturer", "instructor", "person", "jsmc"
    )
    private val locationAliases = setOf(
        "地点", "教室", "位置", "上课地点", "授课地点",
        "location", "locationname", "location_name", "locationlist",
        "classroom", "classroomname", "classroom_name", "classroomlist",
        "room", "roomname", "room_name", "roomlist", "room_list",
        "place", "address", "position", "jsap", "skdd"
    )
    private val weeksAliases = setOf("周次", "周数", "单双周", "周安排", "weeks", "week", "weekrange", "week_range", "weektext", "week_text", "weekparity", "weekremark", "weekdesc", "weekdescription", "weeknum", "zcd", "teachingweek")
    private val startWeekAliases = setOf(
        "开始周", "开始周次", "起始周", "起始周次", "教学开始周",
        "startweek", "weekstart", "fromweek", "beginweek"
    )
    private val endWeekAliases = setOf(
        "结束周", "结束周次", "终止周", "终止周次", "教学结束周",
        "endweek", "weekend", "toweek", "finishweek"
    )
    private val weekTypeAliases = setOf(
        "单双周", "周类型", "周安排", "周属性", "weektype", "weekmode", "weekparity", "week_type"
    )

    fun parse(rawText: String, currentDocument: ScheduleDocument): CommonScheduleImportResult {
        val text = rawText
            .replace("\uFEFF", "")
            .replace("\r\n", "\n")
            .trim()
        require(text.isNotBlank()) { "没有可导入的课表文本。" }

        val parsedTable = parseMarkdownTable(text)
            ?: parseDelimitedTable(text)
            ?: parseLooseDelimitedRows(text)
            ?: parseWhitespaceRows(text)
            ?: throw IllegalArgumentException(
                "无法识别这段课表文本。请使用带表头的 CSV/TSV/Markdown 表格，或按“周几, 节次, 课程, 教师, 地点”的顺序整理。"
            )

        require(parsedTable.rows.isNotEmpty()) { "没有找到可导入的课程行。" }

        val slotIdByKey = linkedMapOf<String, String>()
        val timeSlots = mutableListOf<TimeSlotTemplate>()
        val courseEntries = parsedTable.rows.map { row ->
            val resolvedSlotLabel = row.slotLabel.ifBlank {
                when {
                    row.startTime.isNotBlank() && row.endTime.isNotBlank() -> "${row.startTime}-${row.endTime}"
                    row.startTime.isNotBlank() -> row.startTime
                    else -> "未命名节次"
                }
            }
            val slotKey = buildString {
                append(resolvedSlotLabel)
                append('|')
                append(row.startTime)
                append('|')
                append(row.endTime)
            }
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
            detectedFormat = parsedTable.formatLabel,
            importedCourseCount = courseEntries.size,
            importedTimeSlotCount = timeSlots.size,
            warnings = parsedTable.warnings
        )
    }

    internal fun looksLikeStructuredHeaderRow(columns: List<String>): Boolean =
        resolveHeaderMapping(columns) != null

    private fun parseMarkdownTable(text: String): ParsedTable? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        val candidateLines = lines.filter { it.contains('|') }
        if (candidateLines.size < 3) return null

        val headerColumns = splitMarkdownRow(candidateLines.first())
        val mapping = resolveHeaderMapping(headerColumns) ?: return null
        val dataLines = candidateLines.drop(1)
            .filterNot(::isMarkdownSeparatorRow)
        val rows = mutableListOf<ParsedCourseRow>()
        val warnings = mutableListOf<String>()

        dataLines.forEachIndexed { index, line ->
            parseStructuredRow(splitMarkdownRow(line), mapping)?.let(rows::add)
                ?: warnings.add("跳过第 ${index + 1} 行，缺少课程名或星期信息。")
        }

        return ParsedTable(
            formatLabel = "Markdown 表格",
            rows = rows,
            warnings = warnings
        )
    }

    private fun parseDelimitedTable(text: String): ParsedTable? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.size < 2) return null

        val delimiter = detectDelimiter(lines.take(4)) ?: return null
        val headerColumns = splitDelimitedLine(lines.first(), delimiter)
        val mapping = resolveHeaderMapping(headerColumns) ?: return null
        val rows = mutableListOf<ParsedCourseRow>()
        val warnings = mutableListOf<String>()

        lines.drop(1).forEachIndexed { index, line ->
            val columns = splitDelimitedLine(line, delimiter)
            parseStructuredRow(columns, mapping)?.let(rows::add)
                ?: warnings.add("跳过第 ${index + 2} 行，缺少课程名或星期信息。")
        }

        return ParsedTable(
            formatLabel = when (delimiter) {
                '\t' -> "TSV 表格"
                ',' -> "CSV 表格"
                ';' -> "分号表格"
                else -> "表格文本"
            },
            rows = rows,
            warnings = warnings
        )
    }

    private fun parseLooseDelimitedRows(text: String): ParsedTable? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null
        val delimiter = detectDelimiter(lines.take(4), requireHeader = false) ?: return null

        val rows = mutableListOf<ParsedCourseRow>()
        val warnings = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val columns = splitDelimitedLine(line, delimiter)
            val parsedRows = parseLooseRows(columns)
            if (parsedRows.isNotEmpty()) {
                rows += parsedRows
            } else {
                warnings.add("跳过第 ${index + 1} 行，未识别出周几、节次和课程结构。")
            }
        }

        if (rows.isEmpty()) return null
        return ParsedTable(
            formatLabel = "简易分隔文本",
            rows = rows,
            warnings = warnings
        )
    }

    private fun parseWhitespaceRows(text: String): ParsedTable? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return null

        val rows = mutableListOf<ParsedCourseRow>()
        val warnings = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            val columns = splitWhitespaceLine(line)
            if (columns.isEmpty()) return@forEachIndexed
            if (looksLikeHeader(columns)) return@forEachIndexed
            val parsedRows = parseLooseRows(columns)
            if (parsedRows.isNotEmpty()) {
                rows += parsedRows
            } else {
                warnings.add("跳过第 ${index + 1} 行，未识别出 OCR 课表结构。")
            }
        }

        if (rows.isEmpty()) return null
        return ParsedTable(
            formatLabel = "OCR 文本",
            rows = rows,
            warnings = warnings
        )
    }

    private fun resolveHeaderMapping(headers: List<String>): HeaderMapping? {
        val headerCandidates = headers.map(::buildHeaderCandidates)
        val courseIndex = headerCandidates.indexOfFirst { headerMatchesAlias(it, courseAliases) }
        val dayIndex = headerCandidates.indexOfFirst { headerMatchesAlias(it, dayAliases) }
        if (courseIndex < 0 || dayIndex < 0) return null

        val mapping = HeaderMapping(
            course = courseIndex,
            dayOfWeek = dayIndex,
            slot = headerCandidates.indexOfFirst { headerMatchesAlias(it, slotAliases) }.takeIf { it >= 0 },
            startPeriod = headerCandidates.indexOfFirst { headerMatchesAlias(it, startPeriodAliases) }.takeIf { it >= 0 },
            endPeriod = headerCandidates.indexOfFirst { headerMatchesAlias(it, endPeriodAliases) }.takeIf { it >= 0 },
            startTime = headerCandidates.indexOfFirst { headerMatchesAlias(it, startAliases) }.takeIf { it >= 0 },
            endTime = headerCandidates.indexOfFirst { headerMatchesAlias(it, endAliases) }.takeIf { it >= 0 },
            timeRange = headerCandidates.indexOfFirst { headerMatchesAlias(it, rangeAliases) }.takeIf { it >= 0 },
            teacher = headerCandidates.indexOfFirst { headerMatchesAlias(it, teacherAliases) }.takeIf { it >= 0 },
            location = headerCandidates.indexOfFirst { headerMatchesAlias(it, locationAliases) }.takeIf { it >= 0 },
            weeks = headerCandidates.indexOfFirst { headerMatchesAlias(it, weeksAliases) }.takeIf { it >= 0 },
            startWeek = headerCandidates.indexOfFirst { headerMatchesAlias(it, startWeekAliases) }.takeIf { it >= 0 },
            endWeek = headerCandidates.indexOfFirst { headerMatchesAlias(it, endWeekAliases) }.takeIf { it >= 0 },
            weekType = headerCandidates.indexOfFirst { headerMatchesAlias(it, weekTypeAliases) }.takeIf { it >= 0 }
        )

        val hasSlotInfo = mapping.slot != null ||
            mapping.timeRange != null ||
            (mapping.startTime != null && mapping.endTime != null) ||
            mapping.startPeriod != null ||
            mapping.endPeriod != null
        return mapping.takeIf { hasSlotInfo }
    }

    private fun parseStructuredRow(columns: List<String>, mapping: HeaderMapping): ParsedCourseRow? {
        val courseName = columns.getOrNull(mapping.course).orEmpty().trim()
        val dayOfWeek = parseDayOfWeek(columns.getOrNull(mapping.dayOfWeek).orEmpty())
        if (courseName.isBlank() || dayOfWeek == null) return null

        val timeRange = mapping.timeRange?.let(columns::getOrNull).orEmpty()
        val (rangeStart, rangeEnd) = parseTimeRange(timeRange)
        val periodLabel = buildSlotLabelFromPeriod(
            mapping.startPeriod?.let(columns::getOrNull).orEmpty(),
            mapping.endPeriod?.let(columns::getOrNull).orEmpty()
        )
        val startTime = mapping.startTime?.let(columns::getOrNull).orEmpty().let(::normalizeLooseTime).ifBlank { rangeStart }
        val endTime = mapping.endTime?.let(columns::getOrNull).orEmpty().let(::normalizeLooseTime).ifBlank { rangeEnd }
        val slotLabel = mapping.slot?.let(columns::getOrNull).orEmpty().trim().ifBlank {
            periodLabel.ifBlank { buildSlotLabelFromTime(startTime, endTime) }
        }

        val explicitWeeks = mapping.weeks?.let(columns::getOrNull).orEmpty().trim()
        val splitWeekPattern = buildWeekPatternFromColumns(
            startWeekValue = mapping.startWeek?.let(columns::getOrNull).orEmpty(),
            endWeekValue = mapping.endWeek?.let(columns::getOrNull).orEmpty(),
            weekTypeValue = mapping.weekType?.let(columns::getOrNull).orEmpty()
        )
        val combinedWeekPattern = mergeWeekPatternValues(explicitWeeks, splitWeekPattern)
        val weekPattern = WeekPatternParser.parse(
            rawValue = combinedWeekPattern,
            forceWeekNumbers = mapping.weeks != null || mapping.startWeek != null || mapping.endWeek != null
        )

        return ParsedCourseRow(
            courseName = courseName,
            teacher = ImportFieldCleaner.teachers(listOf(mapping.teacher?.let(columns::getOrNull).orEmpty())),
            location = ImportFieldCleaner.locations(listOf(mapping.location?.let(columns::getOrNull).orEmpty())),
            dayOfWeek = dayOfWeek,
            slotLabel = slotLabel,
            startTime = startTime,
            endTime = endTime,
            weekParity = weekPattern.weekParity,
            weekNumbers = weekPattern.weekNumbers
        )
    }

    private fun buildWeekPatternFromColumns(
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

        val weekType = weekTypeValue.trim()
        return buildString {
            append(range)
            append(weekType)
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

    private fun parseLooseRows(columns: List<String>): List<ParsedCourseRow> {
        parseMultiCourseLooseRows(columns)?.let { return it }
        return parseLooseRow(columns)?.let(::listOf) ?: emptyList()
    }

    private fun parseMultiCourseLooseRows(columns: List<String>): List<ParsedCourseRow>? {
        val base = resolveLooseRowBase(columns) ?: return null
        val blocks = splitMultiCourseBlocks(base.remainingTokens)
        if (blocks.size < 2) return null

        val syntheticSlotToken = base.slotLabel.ifBlank {
            buildSlotLabelFromTime(base.startTime, base.endTime)
        }.ifBlank { return null }

        val parsedRows = blocks.mapNotNull { block ->
            parseLooseRow(listOf(dayLabel(base.dayOfWeek), syntheticSlotToken) + block)
        }
        return parsedRows.takeIf { it.size == blocks.size }
    }

    private fun splitMultiCourseBlocks(tokens: List<String>): List<List<String>> {
        if (tokens.size < 6) return listOf(tokens)

        val blocks = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        tokens.forEachIndexed { index, token ->
            current += token
            val remaining = tokens.drop(index + 1)
            if (remaining.isEmpty()) return@forEachIndexed
            if (!isExplicitWeekToken(token)) return@forEachIndexed
            val nextToken = remaining.firstOrNull().orEmpty()
            if (!looksLikePotentialCourseToken(nextToken)) return@forEachIndexed
            if (remaining.drop(1).none { looksLikeTeacherValue(it) || looksLikeLocationValue(it) || isExplicitWeekToken(it) }) return@forEachIndexed
            blocks += current
            current = mutableListOf()
        }

        if (current.isNotEmpty()) {
            blocks += current
        }
        return blocks.map { it.toList() }
    }

    private fun looksLikePotentialCourseToken(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (looksLikeTeacherValue(trimmed) || looksLikeLocationValue(trimmed) || looksLikeSlotOrTime(trimmed) || isExplicitWeekToken(trimmed)) {
            return false
        }
        return trimmed.length in 2..32
    }

    private fun isExplicitWeekToken(value: String): Boolean {
        return WeekPatternParser.looksLikeWeekPattern(value) && (
            WeekPatternParser.parseExplicitParity(value) != null ||
                WeekPatternParser.parse(value).weekNumbers.isNotEmpty()
            )
    }

    private fun parseLooseRow(columns: List<String>): ParsedCourseRow? {
        val base = resolveLooseRowBase(columns) ?: return null
        val (labeledCourse, consumedCourseIndices) = ImportFieldCleaner.consumeCourseTokens(base.remainingTokens)
        val (_, consumedDayIndices) = ImportFieldCleaner.consumeDayTokens(base.remainingTokens)
        val (labeledSlot, consumedSlotIndices) = ImportFieldCleaner.consumeSlotTokens(base.remainingTokens)
        val (labeledWeeks, consumedWeekIndices) = ImportFieldCleaner.consumeWeekTokens(base.remainingTokens)
        val weekIndex = base.remainingTokens.indices.lastOrNull { index ->
            index !in consumedWeekIndices && isExplicitWeekToken(base.remainingTokens[index])
        } ?: -1
        val (labeledTeachers, consumedTeacherIndices) = ImportFieldCleaner.consumeTeacherTokens(base.remainingTokens)
        val (labeledLocations, consumedLocationIndices) = ImportFieldCleaner.consumeLocationTokens(base.remainingTokens)
        val consumedLabelIndices = consumedCourseIndices + consumedDayIndices + consumedSlotIndices + consumedWeekIndices + consumedTeacherIndices + consumedLocationIndices
        val courseIndex = base.remainingTokens.indices.firstOrNull { index ->
            index != weekIndex && index !in consumedLabelIndices && run {
                val value = base.remainingTokens[index]
                value.isNotBlank() && !looksLikeTeacherValue(value) && !looksLikeLocationValue(value)
            }
        } ?: base.remainingTokens.indices.firstOrNull { it != weekIndex && it !in consumedLabelIndices }
        val courseName = labeledCourse.ifBlank { courseIndex?.let(base.remainingTokens::get).orEmpty().trim() }.ifBlank { return null }

        val teacherIndices = base.remainingTokens.indices.filter { index ->
            index != courseIndex && index != weekIndex && index !in consumedLabelIndices && looksLikeTeacherValue(base.remainingTokens[index])
        }
        val weekPattern = labeledWeeks.takeIf { it.isNotBlank() }
            ?.let { WeekPatternParser.parse(it) }
            ?: weekIndex.takeIf { it >= 0 }
            ?.let { WeekPatternParser.parse(base.remainingTokens[it]) }
            ?: ParsedWeekPattern(weekParity = WeekParity.Every, weekNumbers = emptyList())
        val teacher = ImportFieldCleaner.teachers(listOf(labeledTeachers) + teacherIndices.map(base.remainingTokens::get))
        val location = ImportFieldCleaner.locations(
            listOf(labeledLocations) +
            base.remainingTokens.indices
                .filter { it != courseIndex && it !in teacherIndices && it != weekIndex && it !in consumedLabelIndices }
                .map(base.remainingTokens::get)
        )
        return ParsedCourseRow(
            courseName = courseName,
            teacher = teacher,
            location = location,
            dayOfWeek = base.dayOfWeek,
            slotLabel = labeledSlot.ifBlank { base.slotLabel },
            startTime = base.startTime,
            endTime = base.endTime,
            weekParity = weekPattern.weekParity,
            weekNumbers = weekPattern.weekNumbers
        )
    }

    private data class LooseRowBase(
        val dayOfWeek: Int,
        val slotLabel: String,
        val startTime: String,
        val endTime: String,
        val remainingTokens: List<String>,
        val labeledDay: String = "",
        val labeledSlot: String = "",
        val labeledTimeRange: String = ""
    )

    private fun resolveLooseRowBase(columns: List<String>): LooseRowBase? {
        if (columns.size < 3) return null
        val normalizedColumns = columns.map { it.trim() }
        val (labeledDay, consumedDayIndices) = ImportFieldCleaner.consumeDayTokens(normalizedColumns)
        val (labeledSlot, consumedSlotIndices) = ImportFieldCleaner.consumeSlotTokens(normalizedColumns)
        val (labeledTimeRange, consumedTimeIndices) = ImportFieldCleaner.consumeTimeTokens(normalizedColumns)

        val explicitDayIndex = selectLooseRowDayIndex(normalizedColumns)
        val dayIndex = explicitDayIndex.takeIf { it >= 0 }
        val dayOfWeek = labeledDay.takeIf { it.isNotBlank() }?.let(::parseDayOfWeek)
            ?: dayIndex?.let(normalizedColumns::get)?.let(::parseDayOfWeek)
            ?: return null
        val embeddedSlotOrTime = dayIndex?.let(normalizedColumns::get)?.let(::extractEmbeddedSlotOrTime)

        val slotIndex = embeddedSlotOrTime?.let { dayIndex } ?: run {
            val slotCandidates = columns.indices.filter { index ->
                index != dayIndex && index !in consumedSlotIndices && looksLikeSlotOrTime(normalizedColumns[index])
            }
            dayIndex?.let { explicitIndex ->
                slotCandidates.firstOrNull { it > explicitIndex }
            } ?: slotCandidates.firstOrNull()
        }

        val slotOrTime = when {
            labeledSlot.isNotBlank() -> labeledSlot
            labeledTimeRange.isNotBlank() -> labeledTimeRange
            slotIndex == dayIndex -> {
            embeddedSlotOrTime.orEmpty()
            }
            slotIndex != null -> normalizedColumns[slotIndex]
            else -> ""
        }
        if (slotOrTime.isBlank()) return null
        val (startTime, endTime) = parseTimeRange(slotOrTime)
        val periodLabel = buildSlotLabelFromRangeToken(slotOrTime)
        val slotLabel = when {
            startTime.isNotBlank() || endTime.isNotBlank() -> periodLabel.ifBlank {
                buildSlotLabelFromTime(startTime, endTime)
            }
            else -> periodLabel.ifBlank { slotOrTime }
        }
        val remainingTokens = normalizedColumns.indices
            .filter { it != dayIndex && it != slotIndex && it !in consumedDayIndices && it !in consumedSlotIndices && it !in consumedTimeIndices }
            .map(normalizedColumns::get)
            .filter { it.isNotBlank() }
            .let(::stripLeadingRowOrdinalTokens)
        if (remainingTokens.isEmpty()) return null

        return LooseRowBase(
            dayOfWeek = dayOfWeek,
            slotLabel = slotLabel,
            startTime = startTime,
            endTime = endTime,
            remainingTokens = remainingTokens,
            labeledDay = labeledDay,
            labeledSlot = labeledSlot,
            labeledTimeRange = labeledTimeRange
        )
    }

    private fun selectLooseRowDayIndex(columns: List<String>): Int {
        val candidates = columns.indices.filter { parseDayOfWeek(columns[it]) != null }
        if (candidates.isEmpty()) return -1
        return candidates.firstOrNull { looksLikeExplicitDayToken(columns[it]) } ?: candidates.first()
    }

    private fun looksLikeExplicitDayToken(value: String): Boolean {
        val trimmed = value.trim().lowercase()
        return trimmed.contains("周") ||
            trimmed.contains("星期") ||
            trimmed.contains("礼拜") ||
            trimmed in setOf("mon", "monday", "tue", "tuesday", "wed", "wednesday", "thu", "thursday", "fri", "friday", "sat", "saturday", "sun", "sunday")
    }

    private fun stripLeadingRowOrdinalTokens(tokens: List<String>): List<String> {
        val firstContentIndex = tokens.indexOfFirst { !isLooseRowOrdinalToken(it) }
        return when {
            firstContentIndex <= 0 -> tokens
            firstContentIndex < tokens.size -> tokens.drop(firstContentIndex)
            else -> tokens
        }
    }

    private fun isLooseRowOrdinalToken(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return Regex("^(?:no\\.?|#)?\\d{1,3}[.)、]?$", RegexOption.IGNORE_CASE).matches(trimmed) ||
            Regex("^第\\d{1,3}(?:条|行|项)$").matches(trimmed)
    }

    private fun extractEmbeddedSlotOrTime(dayToken: String): String? {
        val candidate = dayToken.trim()
            .replaceFirst(
                Regex("^(周(?:[一二三四五六日天1-7])|星期(?:[一二三四五六日天1-7])|礼拜(?:[一二三四五六日天1-7]))\\s*[:：]?"),
                ""
            )
            .trim()
        return candidate.takeIf { it.isNotBlank() && looksLikeSlotOrTime(it) }
    }

    private fun parseDayOfWeek(rawValue: String): Int? {
        val value = rawValue.trim()
            .replace("星期天", "星期日")
            .replace("周天", "周日")
            .replace("礼拜天", "礼拜日")
            .replace(Regex("周([1-7])"), "周$1")
            .replace(Regex("星期([1-7])"), "星期$1")
            .replace(Regex("礼拜([1-7])"), "礼拜$1")
            .lowercase()
        return when (value) {
            "1", "mon", "monday", "周一", "星期一", "礼拜一" -> 1
            "周1", "星期1", "礼拜1" -> 1
            "2", "tue", "tuesday", "周二", "星期二", "礼拜二" -> 2
            "周2", "星期2", "礼拜2" -> 2
            "3", "wed", "wednesday", "周三", "星期三", "礼拜三" -> 3
            "周3", "星期3", "礼拜3" -> 3
            "4", "thu", "thursday", "周四", "星期四", "礼拜四" -> 4
            "周4", "星期4", "礼拜4" -> 4
            "5", "fri", "friday", "周五", "星期五", "礼拜五" -> 5
            "周5", "星期5", "礼拜5" -> 5
            "6", "sat", "saturday", "周六", "星期六", "礼拜六" -> 6
            "周6", "星期6", "礼拜6" -> 6
            "7", "sun", "sunday", "周日", "星期日", "礼拜日" -> 7
            "周7", "星期7", "礼拜7" -> 7
            else -> when {
                value.contains("周一") || value.contains("星期一") || value.contains("礼拜一") || value.contains("周1") || value.contains("星期1") || value.contains("礼拜1") -> 1
                value.contains("周二") || value.contains("星期二") || value.contains("礼拜二") || value.contains("周2") || value.contains("星期2") || value.contains("礼拜2") -> 2
                value.contains("周三") || value.contains("星期三") || value.contains("礼拜三") || value.contains("周3") || value.contains("星期3") || value.contains("礼拜3") -> 3
                value.contains("周四") || value.contains("星期四") || value.contains("礼拜四") || value.contains("周4") || value.contains("星期4") || value.contains("礼拜4") -> 4
                value.contains("周五") || value.contains("星期五") || value.contains("礼拜五") || value.contains("周5") || value.contains("星期5") || value.contains("礼拜5") -> 5
                value.contains("周六") || value.contains("星期六") || value.contains("礼拜六") || value.contains("周6") || value.contains("星期6") || value.contains("礼拜6") -> 6
                value.contains("周日") || value.contains("星期日") || value.contains("礼拜日") || value.contains("周7") || value.contains("星期7") || value.contains("礼拜7") -> 7
                else -> null
            }
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
        return if (startPeriod == endPeriod) {
            "$startPeriod 节"
        } else {
            "$startPeriod-$endPeriod 节"
        }
    }

    private fun buildSlotLabelFromRangeToken(rawValue: String): String {
        val matches = Regex("(\\d{1,2})").findAll(rawValue).map { it.groupValues[1] }.toList()
        val startPeriod = matches.getOrNull(0)?.toIntOrNull()?.takeIf { it in 1..30 } ?: return ""
        val endPeriod = matches.getOrNull(1)?.toIntOrNull()?.takeIf { it in 1..30 } ?: startPeriod
        return if (startPeriod == endPeriod) {
            "$startPeriod 节"
        } else {
            "$startPeriod-$endPeriod 节"
        }
    }

    private fun parsePeriodNumber(rawValue: String): Int? {
        val match = Regex("(\\d{1,2})").find(rawValue.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 }
    }

    private fun parseWeekNumber(rawValue: String): Int? {
        val match = Regex("(\\d{1,2})").find(rawValue.trim()) ?: return null
        return match.groupValues[1].toIntOrNull()?.takeIf { it in 1..30 }
    }

    private fun looksLikeSlotOrTime(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        if (parseTimeRange(trimmed).first.isNotBlank()) return true
        val compact = trimmed.replace(Regex("\\s+"), "")
        return Regex("^(?:第?\\d{1,2}(?:[-~至]\\d{1,2})?节|\\[?\\d{1,2}(?:[-~至]\\d{1,2})?\\]?)$").matches(compact)
    }

    private fun looksLikeTeacherValue(value: String): Boolean {
        val trimmed = value.trim()
        return trimmed.endsWith("老师") ||
            trimmed.endsWith("教授") ||
            trimmed.endsWith("讲师") ||
            trimmed.startsWith("教师") ||
            trimmed.startsWith("老师") ||
            trimmed.contains("teacher", ignoreCase = true)
    }

    private fun looksLikeLocationValue(value: String): Boolean {
        val trimmed = value.trim()
        if (trimmed.isBlank()) return false
        return trimmed.startsWith("地点") ||
            trimmed.startsWith("教室") ||
            trimmed.contains("楼") ||
            trimmed.contains("室") ||
            trimmed.contains("馆") ||
            trimmed.contains("校区") ||
            trimmed.contains("实验") ||
            Regex("[A-Za-z]-?\\d{2,}").containsMatchIn(trimmed) ||
            Regex("\\d{3,}").containsMatchIn(trimmed)
    }

    private fun detectDelimiter(lines: List<String>, requireHeader: Boolean = true): Char? {
        val candidates = listOf('\t', ',', ';')
        return candidates.firstOrNull { delimiter ->
            val columns = splitDelimitedLine(lines.firstOrNull().orEmpty(), delimiter)
            val enoughColumns = columns.size >= 3
            val headerLike = resolveHeaderMapping(columns) != null
            enoughColumns && (!requireHeader || headerLike)
        }
    }

    private fun splitDelimitedLine(line: String, delimiter: Char): List<String> {
        if (delimiter != ',') {
            return line.split(delimiter).map { it.trim().trim('"') }
        }

        val values = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0
        while (index < line.length) {
            val char = line[index]
            when {
                char == '"' && index + 1 < line.length && line[index + 1] == '"' -> {
                    current.append('"')
                    index += 1
                }
                char == '"' -> inQuotes = !inQuotes
                char == delimiter && !inQuotes -> {
                    values += current.toString().trim()
                    current.clear()
                }
                else -> current.append(char)
            }
            index += 1
        }
        values += current.toString().trim()
        return values
    }

    private fun splitMarkdownRow(line: String): List<String> {
        return line.trim().trim('|').split('|').map { it.trim() }
    }

    private fun splitWhitespaceLine(line: String): List<String> {
        val normalized = line
            .replace('　', ' ')
            .replace(Regex("\\s{2,}"), " ")
            .trim()
        if (normalized.isBlank()) return emptyList()
        return normalized.split(Regex("\\s+")).map { it.trim() }.filter { it.isNotBlank() }
    }

    private fun isMarkdownSeparatorRow(line: String): Boolean {
        val compact = line.replace("|", "").replace(":", "").replace("-", "").trim()
        return compact.isEmpty()
    }

    private fun looksLikeHeader(columns: List<String>): Boolean {
        val headerCandidates = columns.map(::buildHeaderCandidates)
        val hasHeaderAliases = headerCandidates.any { headerMatchesAlias(it, courseAliases) } &&
            headerCandidates.any { headerMatchesAlias(it, dayAliases) }
        if (!hasHeaderAliases) return false

        // Reject data rows that happen to contain field labels alongside real values,
        // such as "课程 高等数学 星期 周一 节次 1-2节" copied from school pages.
        if (columns.any { parseDayOfWeek(it) != null || looksLikeSlotOrTime(it) || isExplicitWeekToken(it) }) {
            return false
        }
        return true
    }

    private fun headerMatchesAlias(candidates: Set<String>, aliases: Set<String>): Boolean {
        return candidates.any(aliases::contains)
    }

    private fun buildHeaderCandidates(value: String): Set<String> {
        val normalized = value.trim()
            .lowercase()
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('，', ',')
            .replace('；', ';')
            .replace('｜', '|')

        val compact = normalizeHeaderToken(normalized)
        val candidates = linkedSetOf<String>()
        if (compact.isNotBlank()) {
            candidates += compact
        }

        Regex("[\\p{L}\\p{N}_-]+")
            .findAll(normalized)
            .map { normalizeHeaderToken(it.value) }
            .filter { it.isNotBlank() }
            .forEach(candidates::add)

        return candidates
    }

    private fun normalizeHeaderToken(value: String): String {
        return value
            .replace("_", "")
            .replace(" ", "")
            .replace("/", "")
            .replace("\\", "")
            .replace(":", "")
            .replace("：", "")
            .replace("-", "")
            .replace("(", "")
            .replace(")", "")
            .replace("[", "")
            .replace("]", "")
            .replace(",", "")
            .replace(";", "")
            .replace("|", "")
    }

    private fun dayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }
}
