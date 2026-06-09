package com.miaom.schedule.platform.calendar

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CalendarScheduleHeuristicsTest {
    @Test
    fun `accepts recurring course style event`() {
        assertTrue(
            CalendarScheduleHeuristics.looksLikeScheduleEvent(
                title = "高等数学",
                description = "教师: 张老师",
                location = "A101",
                calendarName = "我的课程表",
                recurrenceRule = "FREQ=WEEKLY;BYDAY=MO"
            )
        )
    }

    @Test
    fun `accepts course event without course keyword when teacher and classroom exist`() {
        assertTrue(
            CalendarScheduleHeuristics.looksLikeScheduleEvent(
                title = "数据结构",
                description = "王老师",
                location = "教学楼302",
                calendarName = "学校日历",
                recurrenceRule = ""
            )
        )
    }

    @Test
    fun `rejects reminder like event`() {
        assertFalse(
            CalendarScheduleHeuristics.looksLikeScheduleEvent(
                title = "信用卡还款提醒",
                description = "本月账单",
                location = "",
                calendarName = "提醒事项",
                recurrenceRule = ""
            )
        )
    }

    @Test
    fun `rejects generic meeting`() {
        assertFalse(
            CalendarScheduleHeuristics.looksLikeScheduleEvent(
                title = "项目组会",
                description = "周会讨论",
                location = "会议室 A101",
                calendarName = "工作",
                recurrenceRule = "FREQ=WEEKLY;BYDAY=MO"
            )
        )
    }
}
