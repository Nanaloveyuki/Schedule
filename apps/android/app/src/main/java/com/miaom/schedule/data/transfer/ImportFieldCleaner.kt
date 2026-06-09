package com.miaom.schedule.data.transfer

internal object ImportFieldCleaner {
    private val courseLabelRegex = Regex("^(课程|课程名称|course|coursename|course_name)[:：]?$", RegexOption.IGNORE_CASE)
    private val dayLabelRegex = Regex("^(星期|星期几|周几|weekday|day|dayofweek)[:：]?$", RegexOption.IGNORE_CASE)
    private val slotLabelRegex = Regex("^(节次|节|课节|上课节次|section|period|slot|timeslot|time_slot)[:：]?$", RegexOption.IGNORE_CASE)
    private val timeLabelRegex = Regex("^(时间|时间段|起止时间|上课时间段|timerange|time)[:：]?$", RegexOption.IGNORE_CASE)
    private val weekLabelRegex = Regex("^(周次|周数|周安排|weeks|week|weekrange|weektext|weekremark|weekdesc|weekdescription|weeknum|zcd|teachingweek)[:：]?$", RegexOption.IGNORE_CASE)
    private val teacherLabelRegex = Regex("^(教师|老师|teacher|任课教师|授课教师)[:：]?$", RegexOption.IGNORE_CASE)
    private val locationLabelRegex = Regex("^(地点|教室|位置|上课地点|授课地点)[:：]?$")

    fun teacher(value: String): String {
        return normalize(value)
            .replace(Regex("^(教师|老师|teacher|任课教师|授课教师)[:：]?", RegexOption.IGNORE_CASE), "")
            .trim()
    }

    fun teachers(values: Iterable<String>): String = mergeValues(values, ::teacher)

    fun location(value: String): String {
        return normalize(value)
            .replace(Regex("^(地点|教室|位置|上课地点|授课地点)[:：]?"), "")
            .trim()
    }

    fun locations(values: Iterable<String>): String = mergeValues(values, ::location)

    fun consumeCourseTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isCourseLabelToken) { normalize(it) }

    fun consumeDayTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isDayLabelToken) { normalize(it) }

    fun consumeSlotTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isSlotLabelToken) { normalize(it) }

    fun consumeTimeTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isTimeLabelToken) { normalize(it) }

    fun consumeWeekTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isWeekLabelToken) { normalize(it) }

    fun consumeTeacherTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isTeacherLabelToken, ::teacher)

    fun consumeLocationTokens(tokens: List<String>): Pair<String, Set<Int>> = consumeLabeledValueTokens(tokens, ::isLocationLabelToken, ::location)

    private fun mergeValues(values: Iterable<String>, cleaner: (String) -> String): String {
        return values
            .flatMap(::splitCompositeValues)
            .map(cleaner)
            .map(String::trim)
            .filter { it.isNotBlank() }
            .distinct()
            .joinToString(" / ")
    }

    private fun consumeLabeledValueTokens(
        tokens: List<String>,
        isLabelToken: (String) -> Boolean,
        cleaner: (String) -> String
    ): Pair<String, Set<Int>> {
        val consumedIndices = linkedSetOf<Int>()
        val values = mutableListOf<String>()
        var index = 0
        while (index < tokens.size) {
            val token = tokens[index]
            val inlineValue = extractInlineLabeledValue(token, isLabelToken)
            if (inlineValue != null) {
                consumedIndices += index
                values += inlineValue
                index += 1
                continue
            }

            if (!isLabelToken(token)) {
                index += 1
                continue
            }

            consumedIndices += index
            val nextIndex = index + 1
            val nextToken = tokens.getOrNull(nextIndex).orEmpty()
            if (nextToken.isNotBlank()) {
                consumedIndices += nextIndex
                values += nextToken
                index += 2
            } else {
                index += 1
            }
        }

        return mergeValues(values, cleaner) to consumedIndices
    }

    private fun extractInlineLabeledValue(value: String, isLabelToken: (String) -> Boolean): String? {
        val normalized = normalize(value)
        val separatorIndex = normalized.indexOfFirst { it == ':' || it == '：' }
        if (separatorIndex <= 0 || separatorIndex >= normalized.lastIndex) return null
        val label = normalized.substring(0, separatorIndex + 1)
        val extracted = normalized.substring(separatorIndex + 1).trim()
        if (extracted.isBlank() || !isLabelToken(label)) return null
        return extracted
    }

    private fun splitCompositeValues(value: String): List<String> {
        val normalized = normalize(value)
        if (normalized.isBlank()) return emptyList()
        return normalized
            .split(Regex("\\s*(?:/|／|,|，|、|;|；|&|＆)\\s*"))
            .map(String::trim)
            .filter { it.isNotBlank() }
    }

    private fun isTeacherLabelToken(value: String): Boolean = teacherLabelRegex.matches(normalize(value))

    private fun isLocationLabelToken(value: String): Boolean = locationLabelRegex.matches(normalize(value))

    private fun isCourseLabelToken(value: String): Boolean = courseLabelRegex.matches(normalize(value))

    private fun isDayLabelToken(value: String): Boolean = dayLabelRegex.matches(normalize(value))

    private fun isSlotLabelToken(value: String): Boolean = slotLabelRegex.matches(normalize(value))

    private fun isTimeLabelToken(value: String): Boolean = timeLabelRegex.matches(normalize(value))

    private fun isWeekLabelToken(value: String): Boolean = weekLabelRegex.matches(normalize(value))

    private fun normalize(value: String): String =
        value.replace('：', ':').replace(Regex("\\s+"), " ").trim()
}
