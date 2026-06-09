package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

class XlsxScheduleImportParserTest {
    @Test
    fun `parse xlsx structured schedule table`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("课程名称", "星期", "开始节次", "结束节次", "教师", "教室", "周安排", "高等数学", "周一", "1", "2", "张老师", "A101", "单周"),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="s"><v>4</v></c>
                      <c r="F1" t="s"><v>5</v></c>
                      <c r="G1" t="s"><v>6</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>7</v></c>
                      <c r="B2" t="s"><v>8</v></c>
                      <c r="C2" t="s"><v>9</v></c>
                      <c r="D2" t="s"><v>10</v></c>
                      <c r="E2" t="s"><v>11</v></c>
                      <c r="F2" t="s"><v>12</v></c>
                      <c r="G2" t="s"><v>13</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse xlsx structured schedule table with title row before headers`() {
        val bytes = buildXlsx(
            sharedStrings = listOf(
                "2026-2027 学年第一学期课表",
                "课程名称", "星期", "开始节次", "结束节次", "教师", "教室", "周安排",
                "高等数学", "周一", "1", "2", "张老师", "A101", "单周"
            ),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>1</v></c>
                      <c r="B2" t="s"><v>2</v></c>
                      <c r="C2" t="s"><v>3</v></c>
                      <c r="D2" t="s"><v>4</v></c>
                      <c r="E2" t="s"><v>5</v></c>
                      <c r="F2" t="s"><v>6</v></c>
                      <c r="G2" t="s"><v>7</v></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="s"><v>8</v></c>
                      <c r="B3" t="s"><v>9</v></c>
                      <c r="C3" t="s"><v>10</v></c>
                      <c r="D3" t="s"><v>11</v></c>
                      <c r="E3" t="s"><v>12</v></c>
                      <c r="F3" t="s"><v>13</v></c>
                      <c r="G3" t="s"><v>14</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(1, result.importedCourseCount)
        assertEquals("高等数学", result.document.courseEntries.first().name)
    }

    @Test
    fun `parse xlsx grid schedule with merged cell`() {
        val bytes = buildXlsx(
            sharedStrings = listOf("节次", "周一", "周二", "周三", "3-4节\n10:10-11:45", "程序设计\n刘老师\n机房201\n每周", "体育\n陈老师\n操场"),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>4</v></c>
                      <c r="B2" t="s"><v>5</v></c>
                      <c r="D2" t="s"><v>6</v></c>
                    </row>
                  </sheetData>
                  <mergeCells count="1">
                    <mergeCell ref="B2:C2"/>
                  </mergeCells>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(3, result.importedCourseCount)
        assertEquals(2, result.document.courseEntries.count { it.name == "程序设计" })
        assertTrue(result.document.courseEntries.any { it.name == "体育" })
    }

    @Test
    fun `parse xlsx grid schedule with separate period and time columns`() {
        val bytes = buildXlsx(
            sharedStrings = listOf(
                "节次",
                "时间",
                "周一",
                "周二",
                "1-2节",
                "08:00-09:35",
                "高等数学\n张老师\nA101\n1-8周",
                "大学英语\n李老师\nB202"
            ),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>4</v></c>
                      <c r="B2" t="s"><v>5</v></c>
                      <c r="C2" t="s"><v>6</v></c>
                      <c r="D2" t="s"><v>7</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
    }

    @Test
    fun `parse xlsx grid schedule with three leading helper columns`() {
        val bytes = buildXlsx(
            sharedStrings = listOf(
                "时段",
                "节次",
                "时间",
                "周一",
                "周二",
                "上午",
                "1-2节",
                "08:00-09:35",
                "高等数学\n张老师\nA101\n1-8周",
                "大学英语\n李老师\nB202"
            ),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>2</v></c>
                      <c r="D1" t="s"><v>3</v></c>
                      <c r="E1" t="s"><v>4</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>5</v></c>
                      <c r="B2" t="s"><v>6</v></c>
                      <c r="C2" t="s"><v>7</v></c>
                      <c r="D2" t="s"><v>8</v></c>
                      <c r="E2" t="s"><v>9</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("08:00", result.document.timeSlotTemplates.first().startTime)
        assertEquals("09:35", result.document.timeSlotTemplates.first().endTime)
    }

    @Test
    fun `parse xlsx grid schedule with multiple courses inside one cell`() {
        val bytes = buildXlsx(
            sharedStrings = listOf(
                "节次",
                "周一",
                "1-2节\n08:00-09:35",
                "高等数学\n张老师\nA101\n1-8周\n大学英语\n李老师\nB202\n9-16双周"
            ),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>2</v></c>
                      <c r="B2" t="s"><v>3</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse xlsx grid schedule with two row weekday header`() {
        val bytes = buildXlsx(
            sharedStrings = listOf(
                "节次",
                "星期",
                "时间",
                "一",
                "二",
                "1-2节\n08:00-09:35",
                "高等数学\n张老师\nA101\n1-8周",
                "大学英语\n李老师\nB202\n9-16周"
            ),
            sheetXml = """
                <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
                  <sheetData>
                    <row r="1">
                      <c r="A1" t="s"><v>0</v></c>
                      <c r="B1" t="s"><v>1</v></c>
                      <c r="C1" t="s"><v>1</v></c>
                    </row>
                    <row r="2">
                      <c r="A2" t="s"><v>2</v></c>
                      <c r="B2" t="s"><v>3</v></c>
                      <c r="C2" t="s"><v>4</v></c>
                    </row>
                    <row r="3">
                      <c r="A3" t="s"><v>5</v></c>
                      <c r="B3" t="s"><v>6</v></c>
                      <c r="C3" t="s"><v>7</v></c>
                    </row>
                  </sheetData>
                </worksheet>
            """.trimIndent()
        )

        val result = XlsxScheduleImportParser.parse(bytes, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals("Excel 课表", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals(1, coursesByName.getValue("高等数学").dayOfWeek)
        assertEquals(2, coursesByName.getValue("大学英语").dayOfWeek)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals((9..16).toList(), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `detect xlsx by zip contents`() {
        val bytes = buildXlsx(sharedStrings = emptyList(), sheetXml = """
            <worksheet xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main">
              <sheetData />
            </worksheet>
        """.trimIndent())

        assertTrue(XlsxScheduleImportParser.looksLikeXlsx(bytes, null))
    }

    private fun buildXlsx(sharedStrings: List<String>, sheetXml: String): ByteArray {
        val workbookXml = """
            <workbook xmlns="http://schemas.openxmlformats.org/spreadsheetml/2006/main" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">
              <sheets>
                <sheet name="课表" sheetId="1" r:id="rId1"/>
              </sheets>
            </workbook>
        """.trimIndent()
        val workbookRelsXml = buildString {
            append("<Relationships xmlns=\"http://schemas.openxmlformats.org/package/2006/relationships\">")
            append("<Relationship Id=\"rId1\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet\" Target=\"worksheets/sheet1.xml\"/>")
            if (sharedStrings.isNotEmpty()) {
                append("<Relationship Id=\"rId2\" Type=\"http://schemas.openxmlformats.org/officeDocument/2006/relationships/sharedStrings\" Target=\"sharedStrings.xml\"/>")
            }
            append("</Relationships>")
        }
        val contentTypesXml = buildString {
            append("<Types xmlns=\"http://schemas.openxmlformats.org/package/2006/content-types\">")
            append("<Default Extension=\"rels\" ContentType=\"application/vnd.openxmlformats-package.relationships+xml\"/>")
            append("<Default Extension=\"xml\" ContentType=\"application/xml\"/>")
            append("<Override PartName=\"/xl/workbook.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml\"/>")
            append("<Override PartName=\"/xl/worksheets/sheet1.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml\"/>")
            if (sharedStrings.isNotEmpty()) {
                append("<Override PartName=\"/xl/sharedStrings.xml\" ContentType=\"application/vnd.openxmlformats-officedocument.spreadsheetml.sharedStrings+xml\"/>")
            }
            append("</Types>")
        }
        val rootRelsXml = """
            <Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">
              <Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>
            </Relationships>
        """.trimIndent()
        val sharedStringsXml = buildString {
            append("<sst xmlns=\"http://schemas.openxmlformats.org/spreadsheetml/2006/main\" count=\"")
            append(sharedStrings.size)
            append("\" uniqueCount=\"")
            append(sharedStrings.size)
            append("\">")
            sharedStrings.forEach { value ->
                append("<si><t xml:space=\"preserve\">")
                append(escapeXml(value))
                append("</t></si>")
            }
            append("</sst>")
        }

        return ByteArrayOutputStream().use { output ->
            ZipOutputStream(output).use { zip ->
                putZipEntry(zip, "[Content_Types].xml", contentTypesXml)
                putZipEntry(zip, "_rels/.rels", rootRelsXml)
                putZipEntry(zip, "xl/workbook.xml", workbookXml)
                putZipEntry(zip, "xl/_rels/workbook.xml.rels", workbookRelsXml)
                putZipEntry(zip, "xl/worksheets/sheet1.xml", sheetXml)
                if (sharedStrings.isNotEmpty()) {
                    putZipEntry(zip, "xl/sharedStrings.xml", sharedStringsXml)
                }
            }
            output.toByteArray()
        }
    }

    private fun putZipEntry(zip: ZipOutputStream, name: String, content: String) {
        zip.putNextEntry(ZipEntry(name))
        zip.write(content.toByteArray(Charsets.UTF_8))
        zip.closeEntry()
    }

    private fun escapeXml(value: String): String {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }
}
