package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Test

class IcsScheduleImporterTest {
    @Test
    fun `parse recurring course calendar`() {
        val input = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:高等数学
            DTSTART:20260302T080000
            DTEND:20260302T093500
            LOCATION:地点:A101
            DESCRIPTION:教师:张老师
            RRULE:FREQ=WEEKLY;INTERVAL=2;BYDAY=MO
            END:VEVENT
            BEGIN:VEVENT
            SUMMARY:大学英语
            DTSTART:20260304T133000
            DTEND:20260304T150500
            LOCATION:B202
            DESCRIPTION:教师:李老师
            RRULE:FREQ=WEEKLY;BYDAY=WE
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsScheduleImporter.parse(input, ScheduleDocument())

        assertEquals(2, result.importedCourseCount)
        assertEquals(2, result.importedTimeSlotCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
        assertEquals("张老师", result.document.courseEntries.first().teacher)
        assertEquals("A101", result.document.courseEntries.first().location)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
        assertEquals("2026-03-02", result.document.weekConfig.week1MondayDate)
    }

    @Test
    fun `parse recurring course calendar with delimited teacher and location strings`() {
        val input = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            SUMMARY:数据结构
            DTSTART:20260303T101000
            DTEND:20260303T114500
            LOCATION:地点:A101/B102
            DESCRIPTION:教师:张老师、李老师
            RRULE:FREQ=WEEKLY;BYDAY=TU
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()

        val result = IcsScheduleImporter.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals(1, result.importedCourseCount)
        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("A101 / B102", entry.location)
        assertEquals(WeekParity.Every, entry.weekParity)
    }
}
