package com.miaom.schedule.data.transfer

import org.junit.Assert.assertEquals
import org.junit.Test
import java.nio.charset.Charset

class ScheduleTextDecoderTest {
    @Test
    fun `decode utf8 text by default`() {
        val bytes = "周一,08:00-09:35,高等数学".toByteArray(Charsets.UTF_8)

        val result = ScheduleTextDecoder.decode(bytes)

        assertEquals("周一,08:00-09:35,高等数学", result)
    }

    @Test
    fun `fallback to gb18030 for non utf8 text`() {
        val bytes = "周二,10:10-11:45,大学英语".toByteArray(Charset.forName("GB18030"))

        val result = ScheduleTextDecoder.decode(bytes)

        assertEquals("周二,10:10-11:45,大学英语", result)
    }

    @Test
    fun `detect utf16le text without bom`() {
        val bytes = "周三\t08:00-09:35\t高等数学".toByteArray(Charsets.UTF_16LE)

        val result = ScheduleTextDecoder.decode(bytes)

        assertEquals("周三\t08:00-09:35\t高等数学", result)
    }

    @Test
    fun `detect utf16be text without bom`() {
        val bytes = "周四,13:30-15:05,大学英语".toByteArray(Charsets.UTF_16BE)

        val result = ScheduleTextDecoder.decode(bytes)

        assertEquals("周四,13:30-15:05,大学英语", result)
    }
}
