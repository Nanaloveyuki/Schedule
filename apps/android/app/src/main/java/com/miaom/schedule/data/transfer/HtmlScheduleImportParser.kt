package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument

object HtmlScheduleImportParser {
    private data class CourseCardRecord(
        val courseName: String,
        val teacher: String,
        val location: String,
        val dayOfWeek: Int,
        val slotText: String,
        val weekToken: String
    )

    private data class HtmlTable(
        val rows: List<List<String>>
    )

    private data class HtmlCell(
        val text: String,
        val rowSpan: Int,
        val colSpan: Int
    )

    private data class ActiveSpan(
        val text: String,
        var remainingRows: Int
    )

    private data class GridCourseCell(
        val courseName: String,
        val teacher: String,
        val location: String,
        val weekToken: String
    )

    private val tableRegex = Regex("(?is)<table\\b.*?</table>")
    private val rowRegex = Regex("(?is)<tr\\b.*?</tr>")
    private val cellRegex = Regex("(?is)<(t[dh])\\b([^>]*)>(.*?)</\\1>")
    private val scriptStyleRegex = Regex("(?is)<(script|style)\\b.*?</\\1>")
    private val lineBreakRegex = Regex("(?i)<br\\s*/?>")
    private val closingBlockRegex = Regex("(?i)</(p|div|li|section|article|h[1-6])>")
    private val tagRegex = Regex("(?is)<[^>]+>")
    private val courseContentClassRegex = Regex("(?i)class=['\"][^'\"]*course-content[^'\"]*['\"]")
    private val courseItemListClassRegex = Regex("(?i)class=['\"][^'\"]*course-item-list[^'\"]*['\"]")
    private val cardNameClassRegex = Regex("(?i)class=['\"][^'\"]*name[^'\"]*['\"]")
    private val cardTimeClassRegex = Regex("(?i)class=['\"][^'\"]*time[^'\"]*['\"]")
    private val cardAddressClassRegex = Regex("(?i)class=['\"][^'\"]*address[^'\"]*['\"]")
    private val cardTeacherClassRegex = Regex("(?i)class=['\"][^'\"]*teacher[^'\"]*['\"]")

    fun parse(rawHtml: String, currentDocument: ScheduleDocument): CommonScheduleImportResult {
        val html = rawHtml.replace("\uFEFF", "").trim()
        require(ScheduleImportSniffer.isHtmlDocument(html)) { "这不是可识别的 HTML 课表内容。" }

        val tables = extractTables(html)

        for (table in tables) {
            parseStructuredTable(table, currentDocument)?.let { return it }
            parseGridTable(table, currentDocument)?.let { return it }
            parsePositionalRowTable(table, currentDocument)?.let { return it }
        }

        parseCourseCardHtml(html, currentDocument)?.let { return it }

        throw IllegalArgumentException(
            "HTML 中没有找到可导入的课表内容。建议导出包含周几、节次或时间、课程名称的网页表格，或保存课程卡片列表页面。"
        )
    }

    private fun extractTables(html: String): List<HtmlTable> {
        val sanitized = scriptStyleRegex.replace(html, " ")
        val tableBlocks = tableRegex.findAll(sanitized).map { it.value }.toList()
        val candidates = if (tableBlocks.isNotEmpty()) tableBlocks else listOf(sanitized)
        return candidates.mapNotNull(::parseTable).filter { it.rows.isNotEmpty() }
    }

    private fun parseTable(tableHtml: String): HtmlTable? {
        val activeSpans = mutableMapOf<Int, ActiveSpan>()
        val rows = rowRegex.findAll(tableHtml)
            .map { rowMatch ->
                expandTableRow(rowMatch.value, activeSpans)
            }
            .filter { it.isNotEmpty() && it.any { cell -> cell.isNotBlank() } }
            .toList()
        return rows.takeIf { it.isNotEmpty() }?.let(::HtmlTable)
    }

    private fun expandTableRow(
        rowHtml: String,
        activeSpans: MutableMap<Int, ActiveSpan>
    ): List<String> {
        val rowValues = mutableListOf<String>()
        var columnIndex = 0

        cellRegex.findAll(rowHtml).forEach { cellMatch ->
            while (appendActiveSpanIfPresent(rowValues, activeSpans, columnIndex)) {
                columnIndex += 1
            }

            val cell = HtmlCell(
                text = toPlainText(cellMatch.groupValues[3]),
                rowSpan = parseSpanValue(cellMatch.groupValues[2], "rowspan"),
                colSpan = parseSpanValue(cellMatch.groupValues[2], "colspan")
            )

            repeat(cell.colSpan) { offset ->
                rowValues += cell.text
                if (cell.rowSpan > 1) {
                    activeSpans[columnIndex + offset] = ActiveSpan(
                        text = cell.text,
                        remainingRows = cell.rowSpan - 1
                    )
                }
            }
            columnIndex += cell.colSpan
        }

        while (appendActiveSpanIfPresent(rowValues, activeSpans, columnIndex)) {
            columnIndex += 1
        }

        return rowValues
    }

    private fun appendActiveSpanIfPresent(
        rowValues: MutableList<String>,
        activeSpans: MutableMap<Int, ActiveSpan>,
        columnIndex: Int
    ): Boolean {
        val span = activeSpans[columnIndex] ?: return false
        rowValues += span.text
        span.remainingRows -= 1
        if (span.remainingRows <= 0) {
            activeSpans.remove(columnIndex)
        }
        return true
    }

    private fun parseSpanValue(attributes: String, attributeName: String): Int {
        val match = Regex("(?i)$attributeName\\s*=\\s*['\"]?(\\d+)").find(attributes)
        return match?.groupValues?.getOrNull(1)?.toIntOrNull()?.coerceAtLeast(1) ?: 1
    }

    private fun parseStructuredTable(
        table: HtmlTable,
        currentDocument: ScheduleDocument
    ): CommonScheduleImportResult? {
        if (table.rows.size < 2) return null
        val headerIndex = table.rows.indexOfFirst(CommonScheduleImportParser::looksLikeStructuredHeaderRow)
        if (headerIndex < 0 || headerIndex >= table.rows.lastIndex) return null

        val tsv = table.rows.drop(headerIndex).joinToString("\n") { row ->
            row.joinToString("\t") { cell -> cell.replace("\n", " ").replace(Regex("\\s{2,}"), " ").trim() }
        }

        return runCatching {
            CommonScheduleImportParser.parse(tsv, currentDocument).copy(detectedFormat = "HTML 表格")
        }.getOrNull()
    }

    private fun parseGridTable(
        table: HtmlTable,
        currentDocument: ScheduleDocument
    ): CommonScheduleImportResult? {
        val headerIndex = table.rows.indexOfFirst(::looksLikeGridHeaderRow)
        if (headerIndex < 0 || headerIndex >= table.rows.lastIndex) return null
        val header = table.rows[headerIndex]
        if (header.size < 2) return null

        val leadingSlotColumns = detectLeadingSlotColumns(header)
        val dayColumns = header.drop(leadingSlotColumns).map(::parseGridHeaderDayOfWeek)
        if (dayColumns.none { it != null }) return null

        val looseRows = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        table.rows.drop(headerIndex + 1).forEachIndexed { rowIndex, row ->
            if (row.size <= leadingSlotColumns) return@forEachIndexed

            val slotText = row.take(leadingSlotColumns)
                .joinToString(" ") { it.replace("\n", " ").replace(Regex("\\s{2,}"), " ").trim() }
                .replace(Regex("\\s{2,}"), " ")
                .trim()
            if (slotText.isBlank() || parseDayOfWeek(slotText) != null) return@forEachIndexed

            row.drop(leadingSlotColumns).forEachIndexed { columnIndex, cellText ->
                val dayOfWeek = dayColumns.getOrNull(columnIndex) ?: return@forEachIndexed
                val segments = splitCourseSegments(cellText)
                if (segments.isEmpty()) return@forEachIndexed

                segments.forEach { segment ->
                    val parsedCells = parseGridCells(segment)
                    if (parsedCells.isEmpty()) {
                        warnings += "跳过第 ${rowIndex + 2} 行第 ${columnIndex + 2} 列，未识别出课程信息。"
                    } else {
                        parsedCells.forEach { parsedCell ->
                            looseRows += listOf(
                                dayLabel(dayOfWeek),
                                slotText,
                                parsedCell.courseName,
                                parsedCell.teacher,
                                parsedCell.location,
                                parsedCell.weekToken
                            ).joinToString("\t")
                        }
                    }
                }
            }
        }

        if (looseRows.isEmpty()) return null

        return runCatching {
            val result = CommonScheduleImportParser.parse(looseRows.joinToString("\n"), currentDocument)
            result.copy(
                detectedFormat = "HTML 课表网格",
                warnings = result.warnings + warnings
            )
        }.getOrNull()
    }

    private fun looksLikeGridHeaderRow(row: List<String>): Boolean {
        if (row.size < 2) return false
        return row.drop(1).map(::parseGridHeaderDayOfWeek).any { it != null } ||
            row.drop(2).map(::parseGridHeaderDayOfWeek).any { it != null } ||
            row.drop(3).map(::parseGridHeaderDayOfWeek).any { it != null }
    }

    private fun detectLeadingSlotColumns(header: List<String>): Int {
        if (header.size < 2) return 1
        val firstDayIndex = header.indexOfFirst { parseGridHeaderDayOfWeek(it) != null }
        return when {
            firstDayIndex in 1..3 -> firstDayIndex
            else -> 1
        }
    }

    private fun parseGridHeaderDayOfWeek(text: String): Int? {
        parseDayOfWeek(text)?.let { return it }
        return when (cleanCellValue(text)) {
            "一" -> 1
            "二" -> 2
            "三" -> 3
            "四" -> 4
            "五" -> 5
            "六" -> 6
            "日", "天" -> 7
            else -> null
        }
    }

    private fun parsePositionalRowTable(
        table: HtmlTable,
        currentDocument: ScheduleDocument
    ): CommonScheduleImportResult? {
        if (table.rows.isEmpty()) return null

        val looseRows = mutableListOf<String>()
        val warnings = mutableListOf<String>()

        table.rows.forEachIndexed { index, row ->
            val parsed = parsePositionalCourseRow(row)
            if (parsed != null) {
                looseRows += parsed
            } else if (row.any { it.isNotBlank() } && row.any { cell -> parseDayOfWeek(cell) != null || looksLikeWeekInfo(cell) }) {
                warnings += "跳过第 ${index + 1} 行，未识别出课程名、星期和节次的固定列网页行。"
            }
        }

        if (looseRows.isEmpty()) return null

        return runCatching {
            val result = CommonScheduleImportParser.parse(looseRows.joinToString("\n"), currentDocument)
            result.copy(
                detectedFormat = "HTML 课程行",
                warnings = result.warnings + warnings
            )
        }.getOrNull()
    }

    private fun parsePositionalCourseRow(row: List<String>): String? {
        val normalizedCells = row
            .map(::cleanCellValue)
            .filter { it.isNotBlank() && !looksLikeEmptyCell(it) }
        if (normalizedCells.size < 3) return null

        val scheduleIndex = normalizedCells.indexOfFirst(::looksLikeScheduleSummaryCell)
        if (scheduleIndex < 0) return null

        val schedule = parseScheduleSummaryCell(normalizedCells[scheduleIndex]) ?: return null
        val teacherIndex = normalizedCells.indices.firstOrNull { index ->
            index != scheduleIndex && looksLikeTeacher(normalizedCells[index])
        }
        val courseIndex = normalizedCells.indices
            .filter { index -> index != scheduleIndex && index != teacherIndex }
            .filterNot { index -> looksLikeWeekInfo(normalizedCells[index]) || looksLikeLocation(normalizedCells[index]) }
            .lastOrNull { index ->
                val value = normalizedCells[index]
                value.length in 2..40 && !value.all(Char::isDigit)
            } ?: return null

        val courseName = normalizedCells[courseIndex]
        val fallbackLocation = normalizedCells.indices
            .filter { it != scheduleIndex && it != teacherIndex && it != courseIndex }
            .map(normalizedCells::get)
            .firstOrNull(::looksLikeLocation)
            .orEmpty()

        return listOf(
            dayLabel(schedule.dayOfWeek),
            schedule.slotText,
            courseName,
            teacherIndex?.let(normalizedCells::get).orEmpty(),
            schedule.location.ifBlank { fallbackLocation },
            schedule.weekToken
        ).joinToString("\t")
    }

    private data class ScheduleSummaryCell(
        val dayOfWeek: Int,
        val slotText: String,
        val location: String,
        val weekToken: String
    )

    private fun looksLikeScheduleSummaryCell(text: String): Boolean {
        val normalized = cleanCellValue(text)
        val hasDay = parseDayOfWeek(normalized) != null ||
            Regex("(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天])").containsMatchIn(normalized)
        val hasSlot = Regex("(?:第?\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\s*节|(?<!第)\\[\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\](?!周))").containsMatchIn(normalized) ||
            Regex("\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}").containsMatchIn(normalized)
        return hasDay && hasSlot
    }

    private fun parseScheduleSummaryCell(text: String): ScheduleSummaryCell? {
        val tokens = cleanCellValue(text)
            .replace(Regex("([;；])"), " ")
            .split(Regex("\\s+"))
            .map(::cleanCellValue)
            .filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val dayIndex = tokens.indexOfFirst { parseDayOfWeek(it) != null }
        if (dayIndex < 0) return null
        val dayOfWeek = parseDayOfWeek(tokens[dayIndex]) ?: return null
        val slotIndex = tokens.indices.firstOrNull { index -> index > dayIndex && looksLikeSlotToken(tokens[index]) } ?: -1
        if (slotIndex < 0) return null

        val weekToken = tokens.firstOrNull(::looksLikeWeekInfo).orEmpty()
        val location = tokens.indices
            .filter { it != dayIndex && it != slotIndex }
            .map(tokens::get)
            .filterNot(::looksLikeWeekInfo)
            .joinToString(" ")
            .trim()

        return ScheduleSummaryCell(
            dayOfWeek = dayOfWeek,
            slotText = tokens[slotIndex],
            location = location,
            weekToken = weekToken
        )
    }

    private fun looksLikeSlotToken(text: String): Boolean {
        val normalized = cleanCellValue(text)
        return Regex("^(?:第?\\d{1,2}(?:\\s*[-~至]\\s*\\d{1,2})?\\s*节|\\[\\d{1,2}(?:\\s*[-~至]\\s*\\d{1,2})?\\])$").matches(normalized) ||
            Regex("^\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}$").matches(normalized)
    }

    private fun parseCourseCardHtml(
        rawHtml: String,
        currentDocument: ScheduleDocument
    ): CommonScheduleImportResult? {
        val sanitizedHtml = scriptStyleRegex.replace(rawHtml, " ")
        val courseBlocks = extractElementsByClass(sanitizedHtml, courseContentClassRegex)
        if (courseBlocks.isEmpty()) return null

        val records = mutableListOf<CourseCardRecord>()
        val warnings = mutableListOf<String>()
        courseBlocks.forEach { blockHtml ->
            val courseName = extractElementsByClass(blockHtml, cardNameClassRegex)
                .firstOrNull()
                ?.let(::toPlainText)
                ?.lineSequence()
                ?.map(String::trim)
                ?.firstOrNull { it.isNotBlank() }
                .orEmpty()

            val courseItems = extractElementsByClass(blockHtml, courseItemListClassRegex)
            courseItems.forEachIndexed { index, itemHtml ->
                val parsed = parseCourseCardItem(courseName, itemHtml)
                if (parsed != null) {
                    records += parsed
                } else {
                    warnings += "跳过课程卡片“${courseName.ifBlank { "未命名课程" }}”的第 ${index + 1} 条记录，未识别出周几、节次和课程名。"
                }
            }
        }

        if (records.isEmpty()) return null

        val looseRows = records.map { record ->
            listOf(
                dayLabel(record.dayOfWeek),
                record.slotText,
                record.courseName,
                record.teacher,
                record.location,
                record.weekToken
            ).joinToString("\t")
        }

        return runCatching {
            val result = CommonScheduleImportParser.parse(looseRows.joinToString("\n"), currentDocument)
            result.copy(
                detectedFormat = "HTML 课程卡片",
                warnings = result.warnings + warnings
            )
        }.getOrNull()
    }

    private fun parseCourseCardItem(courseName: String, itemHtml: String): CourseCardRecord? {
        val timeText = extractElementsByClass(itemHtml, cardTimeClassRegex)
            .firstOrNull()
            ?.let(::toPlainText)
            .orEmpty()
        val teacherText = extractElementsByClass(itemHtml, cardTeacherClassRegex)
            .firstOrNull()
            ?.let(::toPlainText)
            .orEmpty()
        val locationText = extractElementsByClass(itemHtml, cardAddressClassRegex)
            .firstOrNull()
            ?.let(::toPlainText)
            .orEmpty()
        val resolvedCourseName = courseName.trim().ifBlank {
            toPlainText(itemHtml).lineSequence()
                .map(String::trim)
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        }
        val dayOfWeek = extractCourseCardDayOfWeek(timeText) ?: return null
        val slotText = extractCourseCardSlotText(timeText) ?: return null
        if (resolvedCourseName.isBlank()) return null

        return CourseCardRecord(
            courseName = resolvedCourseName,
            teacher = ImportFieldCleaner.teachers(listOf(teacherText)),
            location = ImportFieldCleaner.locations(listOf(locationText)),
            dayOfWeek = dayOfWeek,
            slotText = slotText,
            weekToken = extractCourseCardWeekToken(timeText)
        )
    }

    private fun extractElementsByClass(html: String, classRegex: Regex): List<String> {
        val matches = mutableListOf<String>()
        val lowerHtml = html.lowercase()
        var searchIndex = 0
        while (true) {
            val classMatch = classRegex.find(html, searchIndex) ?: break
            val tagStart = html.lastIndexOf('<', classMatch.range.first)
            if (tagStart < 0) {
                searchIndex = classMatch.range.last + 1
                continue
            }

            val openTagEnd = html.indexOf('>', classMatch.range.last)
            if (openTagEnd < 0) break
            val openTag = html.substring(tagStart, openTagEnd + 1)
            val tagName = Regex("<\\s*([a-zA-Z0-9]+)").find(openTag)?.groupValues?.getOrNull(1)?.lowercase()
            if (tagName.isNullOrBlank()) {
                searchIndex = openTagEnd + 1
                continue
            }

            val closeTag = "</$tagName"
            var cursor = openTagEnd + 1
            var depth = 1
            while (depth > 0) {
                val nextOpen = Regex("<\\s*$tagName(?:\\s|>)", RegexOption.IGNORE_CASE).find(html, cursor)
                val nextClose = lowerHtml.indexOf(closeTag, cursor)
                if (nextClose < 0) {
                    depth = 0
                    cursor = html.length
                    break
                }

                if (nextOpen != null && nextOpen.range.first < nextClose) {
                    depth += 1
                    cursor = nextOpen.range.last + 1
                } else {
                    depth -= 1
                    cursor = nextClose + closeTag.length
                    if (depth == 0) {
                        val closeEnd = html.indexOf('>', nextClose)
                        if (closeEnd > 0) {
                            matches += html.substring(tagStart, closeEnd + 1)
                            searchIndex = closeEnd + 1
                        } else {
                            matches += html.substring(tagStart)
                            searchIndex = html.length
                        }
                    }
                }
            }

            if (searchIndex <= classMatch.range.last) {
                searchIndex = classMatch.range.last + 1
            }
        }
        return matches
    }

    private fun extractCourseCardDayOfWeek(timeText: String): Int? {
        return Regex("(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天])")
            .find(timeText)
            ?.value
            ?.let(::parseDayOfWeek)
    }

    private fun extractCourseCardSlotText(timeText: String): String? {
        val periodRange = Regex("第?\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\s*节")
            .find(timeText)
            ?.value
            ?.replace('~', '-')
            ?.replace('至', '-')
            ?.replace(Regex("\\s+"), "")
        if (!periodRange.isNullOrBlank()) return periodRange

        val bracketPeriodRange = Regex("(?<!第)\\[\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\](?!周)")
            .find(timeText)
            ?.value
            ?.replace('~', '-')
            ?.replace('至', '-')
            ?.replace(Regex("\\s+"), "")
        if (!bracketPeriodRange.isNullOrBlank()) return bracketPeriodRange

        return Regex("\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}")
            .find(timeText)
            ?.value
            ?.replace('：', ':')
            ?.replace('.', ':')
            ?.replace('~', '-')
            ?.replace('至', '-')
            ?.replace(Regex("\\s+"), "")
            ?.takeIf { it.isNotBlank() }
    }

    private fun extractCourseCardWeekToken(timeText: String): String {
        val normalized = cleanCellValue(timeText)
        val dayMatch = Regex("(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天])")
            .find(normalized)
        val weekSource = dayMatch?.range?.first?.takeIf { it > 0 }?.let { normalized.substring(0, it) } ?: normalized
        return Regex("第?\\[?\\d+(?:\\s*[-~至]\\s*\\d+)?\\]?周?(?:单周|双周)?|单周|双周|每周")
            .findAll(weekSource)
            .map { it.value.replace(Regex("\\s+"), "") }
            .toList()
            .joinToString(" ")
    }

    private fun splitCourseSegments(text: String): List<String> {
        val normalized = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        if (normalized.isBlank() || looksLikeEmptyCell(normalized)) return emptyList()
        return normalized.split(Regex("\\n\\s*\\n+"))
            .map { it.trim() }
            .filter { it.isNotBlank() && !looksLikeEmptyCell(it) }
            .flatMap(::splitMergedCourseBlocks)
    }

    private fun splitMergedCourseBlocks(text: String): List<String> {
        val lines = text.lines()
            .map(::cleanCellValue)
            .filter { it.isNotBlank() && !looksLikeEmptyCell(it) }
        if (lines.isEmpty()) return emptyList()
        if (lines.size == 1) return listOf(lines.first())

        val segments = mutableListOf<MutableList<String>>()
        var current = mutableListOf<String>()
        lines.forEachIndexed { index, line ->
            if (index > 0 && shouldStartNewCourseBlock(current, lines.drop(index))) {
                segments += current
                current = mutableListOf()
            }
            current += line
        }
        if (current.isNotEmpty()) {
            segments += current
        }
        return segments.map { it.joinToString("\n") }
    }

    private fun shouldStartNewCourseBlock(currentLines: List<String>, remainingLines: List<String>): Boolean {
        if (currentLines.isEmpty()) return false
        val nextLine = remainingLines.firstOrNull().orEmpty()
        if (!looksLikeCourseNameLine(nextLine)) return false
        if (currentLines.none(::looksLikeCourseNameLine)) return false
        if (remainingLines.drop(1).none { looksLikeTeacher(it) || looksLikeLocation(it) || looksLikeWeekInfo(it) }) return false

        val lastLine = currentLines.lastOrNull().orEmpty()
        return looksLikeWeekInfo(lastLine) ||
            looksLikeTeacher(lastLine) ||
            looksLikeLocation(lastLine) ||
            currentLines.count(::looksLikeCourseNameLine) >= 2
    }

    private fun parseGridCells(text: String): List<GridCourseCell> {
        val normalized = text.replace("\r\n", "\n").replace('\r', '\n').trim()
        if (!normalized.contains('\n')) {
            parseSingleLineGridCells(normalized)?.let { return it }
        }
        return splitMergedCourseBlocks(text).mapNotNull(::parseGridCell)
    }

    private fun parseGridCell(text: String): GridCourseCell? {
        val lines = text.lines()
            .map { it.trim() }
            .filter { it.isNotBlank() && !looksLikeEmptyCell(it) }
        if (lines.isEmpty()) return null

        if (lines.size == 1) {
            parseSingleLineGridCell(lines.first())?.let { return it }
        }

        val courseName = lines.firstOrNull {
            !looksLikeWeekInfo(it) && !looksLikeTeacher(it) && !looksLikeLocationLabelOnly(it)
        }?.let(::cleanCellValue) ?: return null
        if (courseName.isBlank()) return null

        val remaining = lines.dropWhile { cleanCellValue(it) != courseName }
            .drop(1)
            .map(::cleanCellValue)
            .filter { it.isNotBlank() }

        var teacher = remaining.firstOrNull(::looksLikeTeacher)?.let(ImportFieldCleaner::teacher).orEmpty()
        var location = remaining.firstOrNull(::looksLikeLocation)?.let(ImportFieldCleaner::location).orEmpty()
        val weekToken = remaining.firstOrNull(::looksLikeWeekInfo).orEmpty()

        if (teacher.isBlank()) {
            teacher = remaining.firstOrNull {
                !looksLikeWeekInfo(it) && !looksLikeLocation(it)
            }?.let(ImportFieldCleaner::teacher).orEmpty()
        }

        if (location.isBlank()) {
            location = remaining.firstOrNull {
                !looksLikeWeekInfo(it) && ImportFieldCleaner.teacher(it) != teacher
            }?.let(ImportFieldCleaner::location).orEmpty()
        }

        return GridCourseCell(
            courseName = courseName,
            teacher = teacher,
            location = if (location == teacher) "" else location,
            weekToken = weekToken
        )
    }

    private fun parseSingleLineGridCell(text: String): GridCourseCell? {
        val tokens = cleanCellValue(text)
            .split(Regex("\\s+"))
            .map(::cleanCellValue)
            .filter { it.isNotBlank() }
        if (tokens.size < 2) return null

        val (labeledCourse, consumedCourseIndices) = ImportFieldCleaner.consumeCourseTokens(tokens)
        val (_, consumedDayIndices) = ImportFieldCleaner.consumeDayTokens(tokens)
        val (_, consumedSlotIndices) = ImportFieldCleaner.consumeSlotTokens(tokens)
        val (_, consumedTimeIndices) = ImportFieldCleaner.consumeTimeTokens(tokens)
        val (labeledWeeks, consumedWeekIndices) = ImportFieldCleaner.consumeWeekTokens(tokens)
        val (labeledTeachers, consumedTeacherIndices) = ImportFieldCleaner.consumeTeacherTokens(tokens)
        val (labeledLocations, consumedLocationIndices) = ImportFieldCleaner.consumeLocationTokens(tokens)
        val consumedLabelIndices = consumedCourseIndices + consumedDayIndices + consumedSlotIndices + consumedTimeIndices + consumedWeekIndices + consumedTeacherIndices + consumedLocationIndices

        val weekIndex = tokens.indices.lastOrNull { index ->
            index !in consumedLabelIndices && looksLikeWeekInfo(tokens[index])
        } ?: -1
        val teacherIndex = tokens.indices.firstOrNull { index ->
            index != weekIndex && index !in consumedLabelIndices && looksLikeTeacher(tokens[index])
        } ?: -1
        val locationIndex = tokens.indices.firstOrNull { index ->
            index != weekIndex && index !in consumedLabelIndices && looksLikeLocation(tokens[index])
        } ?: -1
        val courseEndExclusive = listOf(teacherIndex, locationIndex, weekIndex)
            .filter { it >= 0 }
            .minOrNull() ?: tokens.size
        if (courseEndExclusive <= 0 && labeledCourse.isBlank()) return null

        val courseName = labeledCourse.ifBlank {
            tokens.take(courseEndExclusive)
                .filterIndexed { index, _ -> index !in consumedLabelIndices }
                .joinToString(" ")
                .trim()
        }.takeIf { it.isNotBlank() } ?: return null

        val teacherIndices = tokens.indices.filter { index ->
            index != weekIndex && index !in consumedLabelIndices && looksLikeTeacher(tokens[index])
        }
        val teacher = ImportFieldCleaner.teachers(listOf(labeledTeachers) + teacherIndices.map(tokens::get))
        val locationIndices = tokens.indices.filter { index ->
            index != weekIndex && index !in consumedLabelIndices && looksLikeLocation(tokens[index])
        }
        val location = when {
            labeledLocations.isNotBlank() -> ImportFieldCleaner.locations(listOf(labeledLocations) + locationIndices.map(tokens::get))
            locationIndices.isNotEmpty() -> ImportFieldCleaner.locations(locationIndices.map(tokens::get))
            teacherIndices.isNotEmpty() && weekIndex > teacherIndices.last() + 1 -> ImportFieldCleaner.locations(
                tokens.subList(teacherIndices.last() + 1, weekIndex)
            )
            labeledTeachers.isNotBlank() && weekIndex > 0 -> ImportFieldCleaner.locations(
                tokens.subList(0, weekIndex).filterIndexed { index, _ ->
                    index !in consumedLabelIndices && !looksLikeTeacher(tokens[index])
                }
            )
            else -> ""
        }
        val weekToken = labeledWeeks.ifBlank {
            weekIndex.takeIf { it >= 0 }
            ?.let(tokens::get)
            .orEmpty()
        }

        return GridCourseCell(
            courseName = courseName,
            teacher = teacher,
            location = if (location == teacher) "" else location,
            weekToken = weekToken
        )
    }

    private fun parseSingleLineGridCells(text: String): List<GridCourseCell>? {
        val tokens = cleanCellValue(text)
            .split(Regex("\\s+"))
            .map(::cleanCellValue)
            .filter { it.isNotBlank() }
        if (tokens.size < 4) return null

        val segments = mutableListOf<List<String>>()
        var current = mutableListOf<String>()
        tokens.forEachIndexed { index, token ->
            current += token
            if (!looksLikeWeekInfo(token)) return@forEachIndexed
            val remaining = tokens.drop(index + 1)
            if (remaining.isEmpty()) return@forEachIndexed
            val nextToken = remaining.firstOrNull().orEmpty()
            if (!looksLikeCourseNameLine(nextToken)) return@forEachIndexed
            if (remaining.drop(1).none { looksLikeTeacher(it) || looksLikeLocation(it) || looksLikeWeekInfo(it) }) return@forEachIndexed
            segments += current.toList()
            current = mutableListOf()
        }
        if (current.isNotEmpty()) {
            segments += current.toList()
        }

        if (segments.size < 2) return null
        val parsed = segments.mapNotNull { segment ->
            parseSingleLineGridCell(segment.joinToString(" "))
        }
        return parsed.takeIf { it.size == segments.size }
    }

    private fun looksLikeCourseNameLine(text: String): Boolean {
        val normalized = cleanCellValue(text)
        if (normalized.isBlank()) return false
        if (looksLikeWeekInfo(normalized) || looksLikeTeacher(normalized) || looksLikeLocation(normalized) || looksLikeLocationLabelOnly(normalized)) {
            return false
        }
        return normalized.length in 2..32
    }

    private fun toPlainText(cellHtml: String): String {
        val withLineBreaks = cellHtml
            .replace(lineBreakRegex, "\n")
            .replace(closingBlockRegex, "\n")
        return decodeHtmlEntities(tagRegex.replace(withLineBreaks, " "))
            .replace('\u00A0', ' ')
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map { it.replace(Regex("\\s+"), " ").trim() }
            .filter { it.isNotBlank() }
            .joinToString("\n")
    }

    private fun decodeHtmlEntities(value: String): String {
        var decoded = value
            .replace("&nbsp;", " ", ignoreCase = true)
            .replace("&amp;", "&", ignoreCase = true)
            .replace("&lt;", "<", ignoreCase = true)
            .replace("&gt;", ">", ignoreCase = true)
            .replace("&quot;", "\"", ignoreCase = true)
            .replace("&#39;", "'", ignoreCase = true)
            .replace("&apos;", "'", ignoreCase = true)

        decoded = Regex("&#(\\d+);").replace(decoded) { match ->
            match.groupValues[1].toIntOrNull()?.toChar()?.toString() ?: match.value
        }
        decoded = Regex("&#x([0-9a-fA-F]+);").replace(decoded) { match ->
            match.groupValues[1].toIntOrNull(16)?.toChar()?.toString() ?: match.value
        }
        return decoded
    }

    private fun parseDayOfWeek(text: String): Int? = when (
        cleanCellValue(text)
            .replace("星期天", "星期日")
            .replace("周天", "周日")
            .replace("礼拜天", "礼拜日")
            .lowercase()
    ) {
        "1", "mon", "monday", "周一", "星期一", "礼拜一" -> 1
        "2", "tue", "tuesday", "周二", "星期二", "礼拜二" -> 2
        "3", "wed", "wednesday", "周三", "星期三", "礼拜三" -> 3
        "4", "thu", "thursday", "周四", "星期四", "礼拜四" -> 4
        "5", "fri", "friday", "周五", "星期五", "礼拜五" -> 5
        "6", "sat", "saturday", "周六", "星期六", "礼拜六" -> 6
        "7", "sun", "sunday", "周日", "星期日", "礼拜日" -> 7
        else -> null
    }

    private fun looksLikeTeacher(text: String): Boolean {
        val normalized = cleanCellValue(text)
        return normalized.startsWith("教师") ||
            normalized.startsWith("老师") ||
            normalized.contains("teacher", ignoreCase = true) ||
            normalized.endsWith("老师") ||
            normalized.endsWith("教授")
    }

    private fun looksLikeLocation(text: String): Boolean {
        val normalized = cleanCellValue(text)
        if (looksLikeWeekInfo(normalized) || looksLikeTeacher(normalized)) return false
        return normalized.startsWith("地点") ||
            normalized.startsWith("教室") ||
            normalized.contains("楼") ||
            normalized.contains("室") ||
            normalized.contains("馆") ||
            normalized.contains("校区") ||
            normalized.contains("实验") ||
            Regex("[A-Za-z]-?\\d{2,}").containsMatchIn(normalized) ||
            Regex("\\d{3,}").containsMatchIn(normalized)
    }

    private fun looksLikeLocationLabelOnly(text: String): Boolean {
        val normalized = cleanCellValue(text)
        return normalized == "地点" || normalized == "教室" || normalized == "位置"
    }

    private fun looksLikeWeekInfo(text: String): Boolean {
        val normalized = cleanCellValue(text)
        return WeekPatternParser.looksLikeWeekPattern(normalized) && (
            WeekPatternParser.parseExplicitParity(normalized) != null ||
                WeekPatternParser.parse(normalized).weekNumbers.isNotEmpty()
            )
    }

    private fun looksLikeEmptyCell(text: String): Boolean {
        val normalized = cleanCellValue(text)
        return normalized.isBlank() ||
            normalized == "-" ||
            normalized == "--" ||
            normalized == "无" ||
            normalized == "空" ||
            normalized == "未安排"
    }

    private fun cleanCellValue(text: String): String =
        text.replace('：', ':').replace(Regex("\\s+"), " ").trim()

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
