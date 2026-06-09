package com.miaom.schedule.platform.ocr

import com.miaom.schedule.data.transfer.CommonScheduleImportResult
import com.miaom.schedule.data.transfer.CommonScheduleImportParser
import com.miaom.schedule.domain.model.ScheduleDocument
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class OcrScheduleImporterTest {
    @Test
    fun `build result falls back parsed text to original when normalized parse fails`() {
        val result = buildOcrImportResult(
            recognizedText = "raw text",
            currentDocument = ScheduleDocument(),
            normalizer = { "normalized text" },
            parser = { text, document ->
                when (text) {
                    "normalized text" -> throw IllegalArgumentException("normalized failed")
                    "raw text" -> CommonScheduleImportResult(
                        document = document,
                        detectedFormat = "OCR 文本",
                        importedCourseCount = 1,
                        importedTimeSlotCount = 1,
                        warnings = emptyList()
                    )
                    else -> throw IllegalArgumentException("unexpected text")
                }
            }
        )

        assertEquals("raw text", result.rawRecognizedText)
        assertEquals("normalized text", result.displayRecognizedText)
        assertEquals("raw text", result.parsedText)
        assertEquals("OCR 文本", result.importResult.detectedFormat)
    }

    @Test
    fun `build result keeps editable text when all parse attempts fail`() {
        val result = buildOcrImportResult(
            recognizedText = "周一 1-2节 高等数学",
            currentDocument = ScheduleDocument(),
            normalizer = { "周一 1-2节 高等数学" },
            parser = { _, _ -> throw IllegalArgumentException("parse failed") }
        )

        assertEquals("周一 1-2节 高等数学", result.parsedText)
        assertEquals(0, result.importResult.importedCourseCount)
        assertEquals("OCR 文本", result.importResult.detectedFormat)
        assertTrue(result.importResult.warnings.isNotEmpty())
    }

    @Test
    fun `build result imports ocr text with leading ordinal weekday lines`() {
        val recognizedText = """
            1 周一
            1-2节
            高等数学
            张老师
            A101
            1-8周
            2. 周三
            5-6节
            大学英语
            李老师
            B202
            9-16双周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val coursesByName = result.importResult.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importResult.importedCourseCount)
        assertEquals(
            """
                周一 1-2节 高等数学 张老师 A101 1-8周
                周三 5-6节 大学英语 李老师 B202 9-16双周
            """.trimIndent(),
            result.displayRecognizedText
        )
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `build result imports ocr text with inline field labels and time range`() {
        val recognizedText = """
            课程：高等数学
            星期：周一
            时间：08：00－09：35
            教师：张老师
            地点：A101
            周次：1-8周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val entry = result.importResult.document.courseEntries.first()

        assertEquals(1, result.importResult.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师", entry.teacher)
        assertEquals("A101", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
        assertEquals("08:00", result.importResult.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.importResult.document.timeSlotTemplates.first().endTime)
    }

    @Test
    fun `build result imports ocr text with glued label value lines`() {
        val recognizedText = """
            高等数学
            星期周一
            时间08：00－09：35
            教师张老师
            地点A101
            周次1-8周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val entry = result.importResult.document.courseEntries.first()

        assertEquals(1, result.importResult.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师", entry.teacher)
        assertEquals("A101", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
        assertEquals("08:00", result.importResult.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.importResult.document.timeSlotTemplates.first().endTime)
    }

    @Test
    fun `build result imports sunday variant ocr rows`() {
        val recognizedText = """
            周天第１－２节
            高等数学
            张老师
            A１０１
            １－８周
            星期天08：00－09：35
            大学英语
            李老师
            B２０２
            双周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val coursesByName = result.importResult.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importResult.importedCourseCount)
        assertEquals(7, coursesByName.getValue("高等数学").dayOfWeek)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(7, coursesByName.getValue("大学英语").dayOfWeek)
        assertEquals("08:00", result.importResult.document.timeSlotTemplates.last().startTime)
        assertEquals("09:35", result.importResult.document.timeSlotTemplates.last().endTime)
    }

    @Test
    fun `build result imports ocr text with split period week and time tokens`() {
        val recognizedText = """
            周一第１ ２节
            高等数学
            张老师
            A１０１
            １ ８周
            周三08 00-09 35
            大学英语
            李老师
            B２０２
            双周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val coursesByName = result.importResult.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importResult.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("1-2 节", result.importResult.document.timeSlotTemplates.first().label)
        assertEquals("08:00", result.importResult.document.timeSlotTemplates.last().startTime)
        assertEquals("09:35", result.importResult.document.timeSlotTemplates.last().endTime)
        assertEquals(com.miaom.schedule.domain.model.WeekParity.Even, coursesByName.getValue("大学英语").weekParity)
    }

    @Test
    fun `build result imports digit weekday ocr rows`() {
        val recognizedText = """
            周1第１－２节
            高等数学
            张老师
            A１０１
            单周
            星期708：00－09：35
            大学英语
            李老师
            B２０２
            双周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val coursesByName = result.importResult.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importResult.importedCourseCount)
        assertEquals(1, coursesByName.getValue("高等数学").dayOfWeek)
        assertEquals(7, coursesByName.getValue("大学英语").dayOfWeek)
        assertEquals(com.miaom.schedule.domain.model.WeekParity.Odd, coursesByName.getValue("高等数学").weekParity)
        assertEquals(com.miaom.schedule.domain.model.WeekParity.Even, coursesByName.getValue("大学英语").weekParity)
        assertEquals("08:00", result.importResult.document.timeSlotTemplates.last().startTime)
        assertEquals("09:35", result.importResult.document.timeSlotTemplates.last().endTime)
    }

    @Test
    fun `build result imports split discrete week lists from ocr`() {
        val recognizedText = """
            周一1-2节
            高等数学
            张老师
            A101
            1 3 5 7周
            周三3-4节
            大学英语
            李老师
            B202
            2 4 6 8周
        """.trimIndent()

        val result = buildOcrImportResult(
            recognizedText = recognizedText,
            currentDocument = ScheduleDocument(),
            parser = CommonScheduleImportParser::parse
        )
        val coursesByName = result.importResult.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importResult.importedCourseCount)
        assertEquals(listOf(1, 3, 5, 7), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(2, 4, 6, 8), coursesByName.getValue("大学英语").weekNumbers)
    }
}
