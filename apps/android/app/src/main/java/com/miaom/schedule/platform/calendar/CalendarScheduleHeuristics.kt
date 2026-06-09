package com.miaom.schedule.platform.calendar

internal object CalendarScheduleHeuristics {
    private val negativeKeywords = listOf(
        "生日", "纪念日", "还款", "缴费", "账单", "面试", "会议", "组会",
        "值班", "打卡", "报销", "航班", "火车", "高铁", "酒店", "外卖", "待办", "提醒"
    )
    private val courseKeywords = listOf(
        "课程", "上课", "补课", "调课", "停课", "课表", "教务", "实验", "实训", "自习"
    )
    private val weekdayPattern = Regex("(周[一二三四五六日天])|(星期[一二三四五六日天])|(第\\s*[一二三四五六七八九十\\d]+\\s*节)")
    private val teacherPattern = Regex("(教师|老师|讲师|teacher|lecturer)", RegexOption.IGNORE_CASE)
    private val classroomPattern = Regex("([A-Za-z]{1,4}-?\\d{2,4})|(\\d{2,4}[A-Za-z]?)")

    fun looksLikeScheduleEvent(
        title: String,
        description: String,
        location: String,
        calendarName: String,
        recurrenceRule: String
    ): Boolean {
        val normalizedTitle = normalize(title)
        if (normalizedTitle.isBlank()) return false

        val normalizedDescription = normalize(description)
        val normalizedLocation = normalize(location)
        val normalizedCalendarName = normalize(calendarName)
        val normalizedRecurrenceRule = normalize(recurrenceRule)
        val combined = listOf(
            normalizedTitle,
            normalizedDescription,
            normalizedLocation,
            normalizedCalendarName,
            normalizedRecurrenceRule
        ).joinToString(" ")

        if (negativeKeywords.any { combined.contains(it, ignoreCase = true) }) {
            return false
        }

        var score = 0
        if (courseKeywords.any { normalizedTitle.contains(it, ignoreCase = true) || normalizedDescription.contains(it, ignoreCase = true) }) {
            score += 2
        }
        if (normalizedCalendarName.contains("课", ignoreCase = true) || normalizedCalendarName.contains("教务", ignoreCase = true)) {
            score += 2
        }
        if (normalizedRecurrenceRule.contains("FREQ=WEEKLY", ignoreCase = true) || normalizedRecurrenceRule.contains("BYDAY", ignoreCase = true)) {
            score += 2
        }
        if (teacherPattern.containsMatchIn(normalizedTitle) || teacherPattern.containsMatchIn(normalizedDescription)) {
            score += 2
        }
        if (looksLikeLocation(normalizedLocation) || looksLikeLocation(normalizedDescription)) {
            score += 2
        }
        if (weekdayPattern.containsMatchIn(combined)) {
            score += 1
        }

        return score >= 2
    }

    private fun looksLikeLocation(value: String): Boolean {
        if (value.isBlank()) return false
        return value.contains("楼") ||
            value.contains("教室") ||
            value.contains("机房") ||
            value.contains("实验") ||
            value.contains("馆") ||
            value.contains("校区") ||
            classroomPattern.containsMatchIn(value)
    }

    private fun normalize(value: String): String = value.replace('：', ':').trim()
}
