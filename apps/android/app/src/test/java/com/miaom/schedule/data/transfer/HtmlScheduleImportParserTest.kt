package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Test

class HtmlScheduleImportParserTest {
    @Test
    fun `parse html schedule table with headers`() {
        val input = """
            <html>
            <body>
            <table>
              <tr><th>周几</th><th>时间段</th><th>课程</th><th>教师</th><th>地点</th><th>周次</th></tr>
              <tr><td>周一</td><td>08:00-09:35</td><td>高等数学</td><td>张老师</td><td>A101</td><td>单周</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 表格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse html schedule table when title row appears before headers`() {
        val input = """
            <html>
            <body>
            <table>
              <tr><th colspan="6">2026-2027 学年第一学期课表</th></tr>
              <tr><th>周几</th><th>时间段</th><th>课程</th><th>教师</th><th>地点</th><th>周次</th></tr>
              <tr><td>周一</td><td>08:00-09:35</td><td>高等数学</td><td>张老师</td><td>A101</td><td>单周</td></tr>
            </table>
            </body>
            </html>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 表格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
    }

    @Test
    fun `parse html grid schedule table`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>单周</td>
                <td>大学英语<br/>李老师<br/>B202</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", result.document.courseEntries.first().teacher)
        assertEquals("A101", result.document.courseEntries.first().location)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse html grid schedule table when title row appears before weekday header`() {
        val input = """
            <table>
              <tr><th colspan="3">学生个人课表</th></tr>
              <tr><th>节次</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>单周</td>
                <td>大学英语<br/>李老师<br/>B202</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
    }

    @Test
    fun `parse html grid schedule table with rowspan slot labels`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td rowspan="2">1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101</td>
                <td>大学英语<br/>李老师<br/>B202</td>
              </tr>
              <tr>
                <td>线性代数<br/>王老师<br/>A102</td>
                <td>数据结构<br/>赵老师<br/>B203</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(4, result.importedCourseCount)
        assertEquals(4, result.document.courseEntries.size)
        assertEquals(4, result.document.courseEntries.count { it.timeSlotTemplateId == result.document.courseEntries.first().timeSlotTemplateId })
    }

    @Test
    fun `parse html grid schedule table with colspan course cell`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周二</th><th>周三</th></tr>
              <tr>
                <td>3-4节<br/>10:10-11:45</td>
                <td colspan="2">程序设计<br/>刘老师<br/>机房201<br/>每周</td>
                <td>体育<br/>陈老师<br/>操场</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(3, result.importedCourseCount)
        assertEquals(2, result.document.courseEntries.count { it.name == "程序设计" })
        assertEquals(WeekParity.Every, result.document.courseEntries.first { it.name == "程序设计" }.weekParity)
    }

    @Test
    fun `parse html grid schedule with explicit week range`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse html grid schedule with bare week tokens`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th><th>周三</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8</td>
                <td>大学英语<br/>李老师<br/>B202<br/>2-16双</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse html grid schedule with separate period and time columns`() {
        val input = """
            <table>
              <tr><th>节次</th><th>时间</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td>1-2节</td>
                <td>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8周</td>
                <td>大学英语<br/>李老师<br/>B202</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse html grid schedule with bracketed period range labels`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>[01-02]</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals((1..8).toList(), entry.weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse html grid schedule with three leading helper columns`() {
        val input = """
            <table>
              <tr><th>时段</th><th>节次</th><th>时间</th><th>周一</th><th>周二</th></tr>
              <tr>
                <td>上午</td>
                <td>1-2节</td>
                <td>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8周</td>
                <td>大学英语<br/>李老师<br/>B202</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
    }

    @Test
    fun `parse html grid schedule with multiple courses inside one cell`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>
                  高等数学<br/>张老师<br/>A101<br/>1-8周<br/>
                  大学英语<br/>李老师<br/>B202<br/>9-16双周
                </td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse html grid schedule with single line course sentence inside cell`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学 张老师 HGX507 1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师", entry.teacher)
        assertEquals("HGX507", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse html grid schedule with single line course sentence and multiple teachers`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学 张老师 李老师 HGX507 江湾楼204 1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("HGX507 / 江湾楼204", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse html grid schedule with multiple single line courses inside one cell`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学 张老师 HGX507 1-8周 大学英语 李老师 江湾楼204 9-16双周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("HGX507", coursesByName.getValue("高等数学").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("李老师", coursesByName.getValue("大学英语").teacher)
        assertEquals("江湾楼204", coursesByName.getValue("大学英语").location)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse html grid schedule with labeled single line course sentence`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>课程:高等数学 星期:周一 时间:08:00-09:35 教师:张老师 地点:HGX507 周次:1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师", entry.teacher)
        assertEquals("HGX507", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse html grid schedule with labeled single line course sentence and multiple teachers locations`() {
        val input = """
            <table>
              <tr><th>节次</th><th>周一</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>课程 高等数学 星期 周一 时间 08:00-09:35 教师 张老师 李老师 地点 HGX507 江湾楼204 周次 1-8周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", entry.name)
        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("HGX507 / 江湾楼204", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse html grid schedule with two row weekday header`() {
        val input = """
            <table>
              <tr><th>节次</th><th colspan="2">星期</th></tr>
              <tr><th>时间</th><th>一</th><th>二</th></tr>
              <tr>
                <td>1-2节<br/>08:00-09:35</td>
                <td>高等数学<br/>张老师<br/>A101<br/>1-8周</td>
                <td>大学英语<br/>李老师<br/>B202<br/>9-16周</td>
              </tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课表网格", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals(1, coursesByName.getValue("高等数学").dayOfWeek)
        assertEquals(2, coursesByName.getValue("大学英语").dayOfWeek)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals((9..16).toList(), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse html positional course rows without explicit headers`() {
        val input = """
            <table>
              <tr><td>1</td><td>高等数学</td><td>张老师</td><td>1-8周 星期一 1-2节 HGX507</td></tr>
              <tr><td>2</td><td>大学英语</td><td>李老师</td><td>9-16双周 星期三 5-6节 江湾楼204</td></tr>
            </table>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("HTML 课程行", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("张老师", coursesByName.getValue("高等数学").teacher)
        assertEquals("HGX507", coursesByName.getValue("高等数学").location)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse html course cards from aischedule style page`() {
        val input = """
            <html>
            <body>
              <div class="course-content">
                <div class="name"><p class="content">离散数学</p></div>
                <div class="course-item-list">
                  <div class="time"><p class="content">第[1-8]周 周一 1-2节 08:00~09:35</p></div>
                  <div class="address"><p class="content">HGX507</p></div>
                  <div class="teacher"><p class="content">张老师</p></div>
                </div>
                <div class="course-item-list">
                  <div class="time"><p class="content">第[9-16]双周 周三 5-6节 14:30~16:00</p></div>
                  <div class="address"><p class="content">江湾楼204</p></div>
                  <div class="teacher"><p class="content">李老师</p></div>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("HTML 课程卡片", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("离散数学", result.document.courseEntries.first().name)
        assertEquals("HGX507", result.document.courseEntries.first().location)
        assertEquals("张老师", result.document.courseEntries.first().teacher)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.last().weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
    }

    @Test
    fun `parse html course cards with bracketed period slots`() {
        val input = """
            <html>
            <body>
              <div class="course-content">
                <div class="name"><p class="content">离散数学</p></div>
                <div class="course-item-list">
                  <div class="time"><p class="content">第[1-8]周 周一 [01-02] 08:00~09:35</p></div>
                  <div class="address"><p class="content">HGX507</p></div>
                  <div class="teacher"><p class="content">张老师</p></div>
                </div>
              </div>
            </body>
            </html>
        """.trimIndent()

        val result = HtmlScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("HTML 课程卡片", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("离散数学", entry.name)
        assertEquals("HGX507", entry.location)
        assertEquals("张老师", entry.teacher)
        assertEquals((1..8).toList(), entry.weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
    }
}
