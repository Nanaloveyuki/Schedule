package com.miaom.schedule.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Test

class ScheduleImportSnifferTest {
    @Test
    fun `detect aischedule style html fragment as html schedule`() {
        val input = """
            <div class="course-content">
              <div class="name"><p class="content">离散数学</p></div>
              <div class="course-item-list">
                <div class="time"><p class="content">第[1-8]周 周一 1-2节 08:00~09:35</p></div>
                <div class="address"><p class="content">HGX507</p></div>
                <div class="teacher"><p class="content">张老师</p></div>
              </div>
            </div>
        """.trimIndent()

        assertEquals(ScheduleTextImportKind.HtmlSchedule, ScheduleImportSniffer.detectTextPayload(input))
    }

    @Test
    fun `detect ocr style schedule text without explicit zhou keyword as common text`() {
        val input = """
            礼拜一 1-2节 高等数学 张老师 A101
            礼拜三 13:30-15:05 大学英语 李老师 B202
        """.trimIndent()

        assertEquals(ScheduleTextImportKind.CommonText, ScheduleImportSniffer.detectTextPayload(input))
    }

    @Test
    fun `detect browser shared text with embedded remote url`() {
        val input = """
            课表下载链接
            https://example.com/share/file?target=schedule.ics&token=abc
        """.trimIndent()

        assertEquals(ScheduleTextImportKind.RemoteUrl, ScheduleImportSniffer.detectTextPayload(input))
        assertEquals(
            "https://example.com/share/file?target=schedule.ics&token=abc",
            ScheduleImportSniffer.extractRemoteUrl(input)
        )
    }

    @Test
    fun `extract remote url from inline share sentence`() {
        val input = "下载地址：https://example.com/export/schedule.xlsx?sign=123。"

        assertEquals(ScheduleTextImportKind.RemoteUrl, ScheduleImportSniffer.detectTextPayload(input))
        assertEquals(
            "https://example.com/export/schedule.xlsx?sign=123",
            ScheduleImportSniffer.extractRemoteUrl(input)
        )
    }
}
