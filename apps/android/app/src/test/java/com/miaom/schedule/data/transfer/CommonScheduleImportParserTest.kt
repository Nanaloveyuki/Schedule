package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CommonScheduleImportParserTest {
    @Test
    fun `parse csv schedule table`() {
        val input = """
            周几,节次,课程,教师,地点,周次
            周一,1-2节,高等数学,张老师,A101,单周
            周三,3-4节,大学英语,李老师,B202,每周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals(2, result.document.courseEntries.size)
        assertEquals("高等数学", result.document.courseEntries[0].name)
        assertEquals(WeekParity.Odd, result.document.courseEntries[0].weekParity)
        assertTrue(result.document.courseEntries[0].weekNumbers.isEmpty())
    }

    @Test
    fun `parse markdown table with time range`() {
        val input = """
            | 周几 | 时间段 | 课程 | 教师 | 地点 |
            | --- | --- | --- | --- | --- |
            | 周二 | 08:00-09:35 | 数据结构 | 王老师 | 实验楼201 |
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("Markdown 表格", result.detectedFormat)
        assertEquals(1, result.importedTimeSlotCount)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
    }

    @Test
    fun `parse loose tab separated rows`() {
        val input = """
            周五	10:10-11:45	线性代数	陈老师	教学楼303
            周五	13:30-15:05	体育	赵老师	操场
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("简易分隔文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `parse whitespace ocr style rows`() {
        val input = """
            周一 08:00-09:35 高等数学 张老师 A101 单周
            周三 13:30-15:05 大学英语 李老师 B202
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse csv schedule table with start and end periods`() {
        val input = """
            课程名称,星期,开始节次,结束节次,教师,教室,周安排
            计算机网络,星期四,3,4,孙老师,实验楼405,双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("3-4 节", result.document.timeSlotTemplates.first().label)
        assertEquals(WeekParity.Even, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse loose separated rows when day column is not first`() {
        val input = """
            高等数学,周一,1-2节,张老师,A101,单周
            数据结构,周三,08:00-09:35,王老师,实验楼201
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("简易分隔文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse whitespace rows with course first and inline weekday`() {
        val input = """
            高等数学 周一 1-2节 张老师 A101 单周
            数据结构 周三 08:00-09:35 王老师 实验楼201
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse whitespace rows when weekday and slot are merged into one token`() {
        val input = """
            高等数学 周一1-2节 张老师 A101 单周
            大学英语 周三13:30-15:05 李老师 B202
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("13:30", result.document.timeSlotTemplates.last().startTime)
        assertEquals("15:05", result.document.timeSlotTemplates.last().endTime)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse whitespace rows with digit weekday tokens`() {
        val input = """
            高等数学 周11-2节 张老师 A101 单周
            大学英语 星期708:00-09:35 李老师 B202 双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals(1, coursesByName.getValue("高等数学").dayOfWeek)
        assertEquals(7, coursesByName.getValue("大学英语").dayOfWeek)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("08:00", result.document.timeSlotTemplates.last().startTime)
        assertEquals(WeekParity.Odd, coursesByName.getValue("高等数学").weekParity)
        assertEquals(WeekParity.Even, coursesByName.getValue("大学英语").weekParity)
    }

    @Test
    fun `parse whitespace rows with bracketed period ranges`() {
        val input = """
            高等数学 周一[01-02] 张老师 A101 单周
            大学英语 周三 [5-6] 李老师 B202 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
        assertEquals(WeekParity.Odd, coursesByName.getValue("高等数学").weekParity)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse whitespace rows with bare week tokens`() {
        val input = """
            周一 1-2节 高等数学 张老师 A101 1-8
            周三 5-6节 大学英语 李老师 B202 2-16双
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse whitespace row with multiple courses in same slot`() {
        val input = """
            周一 1-2节 高等数学 张老师 A101 1-8周 大学英语 李老师 B202 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse whitespace rows strips teacher and location labels`() {
        val input = """
            周一 1-2节 高等数学 教师:张老师 地点:A101 1-8周
            周三 5-6节 大学英语 teacher:李老师 教室:B202 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("A101", coursesByName.getValue("高等数学").location)
        assertEquals("李老师", coursesByName.getValue("大学英语").teacher)
        assertEquals("B202", coursesByName.getValue("大学英语").location)
    }

    @Test
    fun `parse whitespace rows with split label tokens`() {
        val input = """
            周一 1-2节 高等数学 教师 张老师 地点 A101 1-8周
            周三 5-6节 大学英语 teacher 李老师 教室 B202 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("A101", coursesByName.getValue("高等数学").location)
        assertEquals("李老师", coursesByName.getValue("大学英语").teacher)
        assertEquals("B202", coursesByName.getValue("大学英语").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse whitespace rows with full field labels`() {
        val input = """
            课程 高等数学 星期 周一 节次 1-2节 教师 张老师 地点 A101 周次 1-8周
            课程 大学英语 星期 周三 节次 5-6节 教师 李老师 地点 B202 周次 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("A101", coursesByName.getValue("高等数学").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("李老师", coursesByName.getValue("大学英语").teacher)
        assertEquals("B202", coursesByName.getValue("大学英语").location)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse whitespace rows with inline field labels and time range`() {
        val input = """
            课程:高等数学 星期:周一 时间:08:00-09:35 教师:张老师 地点:A101 周次:1-8周
            课程:大学英语 星期:周三 时间:13:30-15:05 教师:李老师 地点:B202 周次:9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("A101", coursesByName.getValue("高等数学").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse whitespace rows with multiple teachers and locations`() {
        val input = """
            周一 1-2节 高等数学 张老师 李老师 A101 B102 1-8周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("A101 / B102", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse whitespace rows with leading ordinal tokens`() {
        val input = """
            1 周一 1-2节 高等数学 张老师 A101 1-8周
            2. 周三 5-6节 大学英语 李老师 B202 9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("OCR 文本", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("A101", coursesByName.getValue("高等数学").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse loose rows preserves period range labels`() {
        val input = """
            周三	5-6节	离散数学	李老师	江湾楼204	9-16双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("简易分隔文本", result.detectedFormat)
        assertEquals("5-6 节", result.document.timeSlotTemplates.first().label)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse loose rows with leading ordinal token`() {
        val input = """
            12,周四,3-4节,数据结构,王老师,理教楼302,1-8周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("简易分隔文本", result.detectedFormat)
        assertEquals("数据结构", entry.name)
        assertEquals("王老师", entry.teacher)
        assertEquals("理教楼302", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse explicit week ranges from structured text`() {
        val input = """
            课程名称,星期,开始节次,结束节次,教师,教室,周安排
            线性代数,星期四,3,4,孙老师,实验楼405,1-8周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(listOf(1, 2, 3, 4, 5, 6, 7, 8), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse wakeup compatible csv exported from school converter`() {
        val input = """
            课程名称,星期,开始节数,结束节数,老师,地点,周数
            高等数学,1,1,2,张老师,A101,1-8
            大学英语,3,3,4,李老师,B202,2-16双
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result.document.courseEntries.last().weekNumbers)
    }

    @Test
    fun `parse csv schedule table with broader teacher and location aliases`() {
        val input = """
            课程名称,星期,开始节次,结束节次,授课教师,上课地点,周次
            编译原理,星期二,5,6,王老师,实验楼201,第[9-16]双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("王老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼201", result.document.courseEntries.first().location)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse csv schedule table strips teacher and location labels`() {
        val input = """
            课程名称,星期,开始节次,结束节次,授课教师,上课地点,周次
            编译原理,星期二,5,6,教师:王老师,地点:实验楼201,第[9-16]双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("王老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼201", result.document.courseEntries.first().location)
    }

    @Test
    fun `parse csv schedule table with decorated header labels`() {
        val input = """
            课程名称（中文）,星期/周几,开始节次,结束节次,授课教师(Teacher),上课地点/教室,周次：
            计算机网络,星期四,3,4,孙老师,实验楼405,1-8周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("计算机网络", result.document.courseEntries.first().name)
        assertEquals("孙老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼405", result.document.courseEntries.first().location)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse csv schedule table with camel case teacher and room headers`() {
        val input = """
            courseName,dayOfWeek,startSection,endSection,teacherName,roomName,weekRange
            编译原理,周二,5,6,王老师,实验楼201,第[9-16]双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("王老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼201", result.document.courseEntries.first().location)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse csv schedule table with flattened node and week text headers`() {
        val input = """
            courseName,dayOfWeek,startNode,endNode,teacherName,locationName,weekText
            操作系统,周五,7,8,陈老师,实验中心501,3-12单周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("操作系统", result.document.courseEntries.first().name)
        assertEquals("陈老师", result.document.courseEntries.first().teacher)
        assertEquals("实验中心501", result.document.courseEntries.first().location)
        assertEquals(listOf(3, 5, 7, 9, 11), result.document.courseEntries.first().weekNumbers)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
        assertEquals("7-8 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse csv schedule table with split week columns`() {
        val input = """
            课程名称,星期,开始节数,结束节数,老师,地点,开始周,结束周,单双周
            操作系统,2,1,2,王老师,理教楼302,1,16,双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("操作系统", result.document.courseEntries.first().name)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
        assertEquals(WeekParity.Even, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse csv schedule table with decorated split week headers`() {
        val input = """
            课程名称(Name),星期/周几,开始节次,结束节次,授课教师(Teacher),上课地点/教室,开始周(StartWeek),结束周(EndWeek),周类型/单双周
            数据库系统,周五,7,8,陈老师,实验中心501,3,12,单周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("数据库系统", result.document.courseEntries.first().name)
        assertEquals(listOf(3, 5, 7, 9, 11), result.document.courseEntries.first().weekNumbers)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse csv schedule table with school raw aliases`() {
        val input = """
            courseNm,xqj,ksjc,jsjc,jsmc,jsap,zcd
            数据结构,3,3,4,教师:李老师,地点:理教楼302,1-8周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("数据结构", result.document.courseEntries.first().name)
        assertEquals(3, result.document.courseEntries.first().dayOfWeek)
        assertEquals("李老师", result.document.courseEntries.first().teacher)
        assertEquals("理教楼302", result.document.courseEntries.first().location)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals("3-4 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse csv schedule table with lesson and split week aliases`() {
        val input = """
            lessonName,xqjmc,startNode,endNode,teacherList,roomList,beginWeek,finishWeek,weekMode
            计算机组成原理,周四,5,6,王老师,实验楼405,2,16,双周
        """.trimIndent()

        val result = CommonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("CSV 表格", result.detectedFormat)
        assertEquals("计算机组成原理", result.document.courseEntries.first().name)
        assertEquals(4, result.document.courseEntries.first().dayOfWeek)
        assertEquals("王老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼405", result.document.courseEntries.first().location)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
        assertEquals(WeekParity.Even, result.document.courseEntries.first().weekParity)
        assertEquals("5-6 节", result.document.timeSlotTemplates.first().label)
    }
}
