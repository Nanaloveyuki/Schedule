package com.miaom.schedule.platform.ocr

import org.junit.Assert.assertEquals
import org.junit.Test

class OcrScheduleTextNormalizerTest {
    @Test
    fun `normalize merges multiline ocr rows and full width punctuation`() {
        val input = """
            周一
            第１－２节
            高等数学
            张老师
            A１０１
            １－８周
            周三
            １３：３０－１５：０５
            大学英语
            李老师
            B２０２
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周一 1-2节 高等数学 张老师 A101 1-8周
                周三 13:30-15:05 大学英语 李老师 B202
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize keeps already line based schedule text stable`() {
        val input = """
            周一 08:00-09:35 高等数学 张老师 A101 单周
            周三 13:30-15:05 大学英语 李老师 B202
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(input, normalized)
    }

    @Test
    fun `normalize attaches course name line before weekday row`() {
        val input = """
            高等数学
            周一
            第1-2节
            张老师
            A101
            1-8周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("高等数学 周一 1-2节 张老师 A101 1-8周", normalized)
    }

    @Test
    fun `normalize attaches leading week fragment before weekday row`() {
        val input = """
            1-8周
            周一
            1-2节
            高等数学
            张老师
            A101
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("1-8周 周一 1-2节 高等数学 张老师 A101", normalized)
    }

    @Test
    fun `normalize attaches bare week fragment before weekday row`() {
        val input = """
            1-8
            周一
            1-2节
            高等数学
            张老师
            A101
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("1-8 周一 1-2节 高等数学 张老师 A101", normalized)
    }

    @Test
    fun `normalize converts full width brackets in week fragments`() {
        val input = """
            【1-8】周
            周一
            第１－２节
            高等数学
            张老师
            A１０１
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("[1-8]周 周一 1-2节 高等数学 张老师 A101", normalized)
    }

    @Test
    fun `normalize attaches teacher and location fragments before weekday row`() {
        val input = """
            数据结构
            王老师
            实验楼201
            周三
            13:30-15:05
            双周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("数据结构 王老师 实验楼201 周三 13:30-15:05 双周", normalized)
    }

    @Test
    fun `normalize keeps split teacher and location labels readable for parser`() {
        val input = """
            周一
            1-2节
            高等数学
            教师
            张老师
            地点
            A101
            1-8周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("周一 1-2节 高等数学 教师 张老师 地点 A101 1-8周", normalized)
    }

    @Test
    fun `normalize keeps full field labels readable for parser`() {
        val input = """
            课程
            高等数学
            星期
            周一
            节次
            1-2节
            教师
            张老师
            地点
            A101
            周次
            1-8周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals("课程 高等数学 星期 周一 节次 1-2节 教师 张老师 地点 A101 周次 1-8周", normalized)
    }

    @Test
    fun `normalize keeps inline field labels with time range readable for parser`() {
        val input = """
            课程：高等数学
            星期：周一
            时间：08：00－09：35
            教师：张老师
            地点：A101
            周次：1-8周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            "课程:高等数学 星期:周一 时间:08:00-09:35 教师:张老师 地点:A101 周次:1-8周",
            normalized
        )
    }

    @Test
    fun `normalize splits glued labeled field lines for parser`() {
        val input = """
            课程高等数学
            星期周一
            时间08：00－09：35
            教师张老师
            地点A101
            周次1-8周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            "课程 高等数学 星期 周一 时间 08:00-09:35 教师 张老师 地点 A101 周次 1-8周",
            normalized
        )
    }

    @Test
    fun `normalize keeps merged weekday and slot token stable`() {
        val input = """
            高等数学 周一1-2节 张老师 A101 单周
            大学英语 周三13:30-15:05 李老师 B202
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                高等数学 周一 1-2节 张老师 A101 单周
                大学英语 周三 13:30-15:05 李老师 B202
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize merges multiline multi course block in same slot`() {
        val input = """
            周一
            1-2节
            高等数学
            张老师
            A101
            1-8周
            大学英语
            李老师
            B202
            9-16双周
        """.trimIndent()

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            "周一 1-2节 高等数学 张老师 A101 1-8周 大学英语 李老师 B202 9-16双周",
            normalized
        )
    }

    @Test
    fun `normalize strips leading ordinal tokens before weekday rows`() {
        val input = """
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

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周一 1-2节 高等数学 张老师 A101 1-8周
                周三 5-6节 大学英语 李老师 B202 9-16双周
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize treats sunday variants as row starts`() {
        val input = """
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

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周天 1-2节 高等数学 张老师 A101 1-8周
                星期天 08:00-09:35 大学英语 李老师 B202 双周
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize repairs split period week and time tokens from ocr`() {
        val input = """
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

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周一 1-2节 高等数学 张老师 A101 1-8周
                周三 08:00-09:35 大学英语 李老师 B202 双周
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize treats digit weekday variants as row starts`() {
        val input = """
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

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周1 1-2节 高等数学 张老师 A101 单周
                星期7 08:00-09:35 大学英语 李老师 B202 双周
            """.trimIndent(),
            normalized
        )
    }

    @Test
    fun `normalize repairs split discrete week lists from ocr`() {
        val input = """
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

        val normalized = OcrScheduleTextNormalizer.normalize(input)

        assertEquals(
            """
                周一 1-2节 高等数学 张老师 A101 1,3,5,7周
                周三 3-4节 大学英语 李老师 B202 2,4,6,8周
            """.trimIndent(),
            normalized
        )
    }
}
