package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class JsonScheduleImportParserTest {
    @Test
    fun `parse json schedule array with node fields`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "教师:张老师",
                  "room": "地点:A101",
                  "dayOfWeek": 1,
                  "startNode": 1,
                  "endNode": 2,
                  "weekType": "单周"
                },
                {
                  "name": "数据结构",
                  "teacher": "王老师",
                  "location": "实验楼201",
                  "weekday": "周三",
                  "time": "08:00-09:35"
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals("JSON 课表", result.detectedFormat)
        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("张老师", result.document.courseEntries.first().teacher)
        assertEquals("A101", result.document.courseEntries.first().location)
        assertEquals(WeekParity.Odd, result.document.courseEntries.first().weekParity)
        assertEquals("数据结构", result.document.courseEntries.last().name)
    }

    @Test
    fun `parse top level json array with start and end times`() {
        val input = """
            [
              {
                "title": "大学英语",
                "lecturer": "李老师",
                "classroom": "B202",
                "day": "星期五",
                "start_time": "13:30",
                "end_time": "15:05",
                "weeks": "双周"
              }
            ]
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(1, result.importedCourseCount)
        assertEquals("13:30", result.document.timeSlotTemplates.first().startTime)
        assertEquals("15:05", result.document.timeSlotTemplates.first().endTime)
        assertEquals(WeekParity.Even, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse nested json schedule list with legacy aliases and week warning`() {
        val input = """
            {
              "data": {
                "list": [
                  {
                    "kcmc": "线性代数",
                    "jsmc": "陈老师",
                    "jsap": "教学楼 B301",
                    "xqj": 2,
                    "ksjc": 3,
                    "jsjc": 4,
                    "zcd": "1-8周"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(1, result.importedCourseCount)
        assertEquals("线性代数", result.document.courseEntries.first().name)
        assertEquals("陈老师", result.document.courseEntries.first().teacher)
        assertEquals("3-4 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertTrue(result.warnings.isEmpty())
    }

    @Test
    fun `parse fudan style lessons json with schedule text segments`() {
        val input = """
            {
              "lessons": [
                {
                  "course": {
                    "nameZh": "离散数学"
                  },
                  "scheduleText": {
                    "dateTimePlacePersonText": {
                      "textZh": "1-8周 星期一 1~2节 HGX507 张老师; \n9-16双周 星期三 5~6节 江湾楼204 李老师"
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(2, result.importedCourseCount)
        assertEquals("离散数学", result.document.courseEntries.first().name)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals("HGX507", result.document.courseEntries.first().location)
        assertEquals("张老师", result.document.courseEntries.first().teacher)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.last().weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
    }

    @Test
    fun `parse aischedule style adapter json array`() {
        val input = """
            [
              {
                "name": "离散数学",
                "teacher": "张老师",
                "position": "HGX507",
                "day": 1,
                "weeks": [1, 2, 3, 4, 5, 6, 7, 8],
                "sections": [
                  { "section": 1 },
                  { "section": 2 }
                ]
              },
              {
                "name": "程序设计",
                "teacher": "李老师",
                "position": "机房201",
                "day": "星期三",
                "weeks": [
                  { "week": 10 },
                  { "week": 12 },
                  { "week": 14 },
                  { "week": 16 }
                ],
                "sections": [5, 6]
              }
            ]
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(2, result.importedCourseCount)
        assertEquals("离散数学", result.document.courseEntries.first().name)
        assertEquals("HGX507", result.document.courseEntries.first().location)
        assertEquals((1..8).toList(), result.document.courseEntries.first().weekNumbers)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals(listOf(10, 12, 14, 16), result.document.courseEntries.last().weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
    }

    @Test
    fun `parse school api style courseInfos with split week fields`() {
        val input = """
            {
              "data": {
                "courseInfos": [
                  {
                    "courseName": "操作系统",
                    "teacherName": "王老师",
                    "roomName": "理教楼302",
                    "weekIndex": 2,
                    "startSection": 1,
                    "endSection": 2,
                    "startWeek": 1,
                    "endWeek": 16,
                    "weekType": "双周"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(1, result.importedCourseCount)
        assertEquals("操作系统", result.document.courseEntries.first().name)
        assertEquals("理教楼302", result.document.courseEntries.first().location)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result.document.courseEntries.first().weekNumbers)
        assertEquals(WeekParity.Even, result.document.courseEntries.first().weekParity)
    }

    @Test
    fun `parse school api style course list with teacher and room objects`() {
        val input = """
            {
              "result": {
                "courseList": [
                  {
                    "name": "数据库系统",
                    "teachers": [
                      { "name": "陈老师" },
                      { "name": "刘老师" }
                    ],
                    "rooms": [
                      { "name": "实验楼501" },
                      { "name": "机房201" }
                    ],
                    "day_of_week": "周五",
                    "sections": [7, 8],
                    "weekRange": "3-12单周"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())

        assertEquals(1, result.importedCourseCount)
        assertEquals("数据库系统", result.document.courseEntries.first().name)
        assertEquals("陈老师 / 刘老师", result.document.courseEntries.first().teacher)
        assertEquals("实验楼501 / 机房201", result.document.courseEntries.first().location)
        assertEquals("7-8 节", result.document.timeSlotTemplates.first().label)
        assertEquals(listOf(3, 5, 7, 9, 11), result.document.courseEntries.first().weekNumbers)
    }

    @Test
    fun `parse school api style course list with short teacher and room object aliases`() {
        val input = """
            {
              "data": {
                "courseList": [
                  {
                    "name": "概率论",
                    "teachers": [
                      { "xm": "张老师" },
                      { "xm": "李老师" }
                    ],
                    "rooms": [
                      { "mc": "理教楼302" },
                      { "mc": "实验中心501" }
                    ],
                    "day_of_week": "周二",
                    "sections": [1, 2],
                    "weekRange": "1-8周"
                  }
                ]
              }
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals(1, result.importedCourseCount)
        assertEquals("概率论", entry.name)
        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("理教楼302 / 实验中心501", entry.location)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse json schedule array with delimited teacher and location strings`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "教师:张老师/李老师",
                  "roomName": "地点:A101、B102",
                  "dayOfWeek": 1,
                  "startNode": 1,
                  "endNode": 2,
                  "weeks": "1-8周"
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val entry = result.document.courseEntries.first()

        assertEquals("张老师 / 李老师", entry.teacher)
        assertEquals("A101 / B102", entry.location)
        assertEquals((1..8).toList(), entry.weekNumbers)
    }

    @Test
    fun `parse json schedule array with weeks expressed as strings and text objects`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "张老师",
                  "room": "A101",
                  "dayOfWeek": 1,
                  "startNode": 1,
                  "endNode": 2,
                  "weeks": ["1-8"]
                },
                {
                  "courseName": "大学英语",
                  "teacherName": "李老师",
                  "room": "B202",
                  "dayOfWeek": 3,
                  "startNode": 5,
                  "endNode": 6,
                  "weeks": [
                    { "text": "9-16双周" }
                  ]
                },
                {
                  "courseName": "程序设计",
                  "teacherName": "王老师",
                  "room": "机房201",
                  "dayOfWeek": 5,
                  "startNode": 7,
                  "endNode": 8,
                  "weeks": ["单周"]
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals(3, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
        assertTrue(coursesByName.getValue("程序设计").weekNumbers.isEmpty())
        assertEquals(WeekParity.Odd, coursesByName.getValue("程序设计").weekParity)
    }

    @Test
    fun `parse json schedule array with sections expressed as strings and text objects`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "张老师",
                  "room": "A101",
                  "dayOfWeek": 1,
                  "sections": ["[01-02]"],
                  "weeks": "1-8周"
                },
                {
                  "courseName": "大学英语",
                  "teacherName": "李老师",
                  "room": "B202",
                  "dayOfWeek": 3,
                  "sections": [
                    { "text": "5-6节" }
                  ],
                  "weeks": "9-16双周"
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse json schedule array with weeks expressed as objects`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "张老师",
                  "room": "A101",
                  "dayOfWeek": 1,
                  "startNode": 1,
                  "endNode": 2,
                  "weeks": {
                    "text": "1-8周"
                  }
                },
                {
                  "courseName": "大学英语",
                  "teacherName": "李老师",
                  "room": "B202",
                  "dayOfWeek": 3,
                  "startNode": 5,
                  "endNode": 6,
                  "weeks": {
                    "startWeek": 2,
                    "endWeek": 16,
                    "weekType": "双周"
                  }
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importedCourseCount)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
        assertEquals(WeekParity.Even, coursesByName.getValue("大学英语").weekParity)
    }

    @Test
    fun `parse json schedule array with sections expressed as objects`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "张老师",
                  "room": "A101",
                  "dayOfWeek": 1,
                  "sections": {
                    "text": "[01-02]"
                  },
                  "weeks": "1-8周"
                },
                {
                  "courseName": "大学英语",
                  "teacherName": "李老师",
                  "room": "B202",
                  "dayOfWeek": 3,
                  "sections": {
                    "startSection": 5,
                    "endSection": 6
                  },
                  "weeks": "9-16双周"
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
        assertEquals(listOf(10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
    }

    @Test
    fun `parse json schedule array with nested schedule fields`() {
        val input = """
            {
              "courses": [
                {
                  "courseName": "高等数学",
                  "teacherName": "张老师",
                  "room": "A101",
                  "schedule": {
                    "day": "周一",
                    "weeks": {
                      "text": "1-8周"
                    },
                    "sections": {
                      "text": "[01-02]"
                    }
                  }
                },
                {
                  "courseName": "大学英语",
                  "teacherName": "李老师",
                  "room": "B202",
                  "time": {
                    "weekday": "星期三",
                    "weeks": {
                      "startWeek": 2,
                      "endWeek": 16,
                      "weekType": "双周"
                    },
                    "sections": {
                      "startSection": 5,
                      "endSection": 6
                    }
                  }
                }
              ]
            }
        """.trimIndent()

        val result = JsonScheduleImportParser.parse(input, ScheduleDocument())
        val coursesByName = result.document.courseEntries.associateBy { it.name }

        assertEquals(2, result.importedCourseCount)
        assertEquals("1-2 节", result.document.timeSlotTemplates.first().label)
        assertEquals((1..8).toList(), coursesByName.getValue("高等数学").weekNumbers)
        assertEquals("5-6 节", result.document.timeSlotTemplates.last().label)
        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), coursesByName.getValue("大学英语").weekNumbers)
        assertEquals(WeekParity.Even, coursesByName.getValue("大学英语").weekParity)
    }

    @Test
    fun `sniffer recognizes supported json schedule array`() {
        val input = """
            [
              {
                "title": "大学英语",
                "lecturer": "李老师",
                "classroom": "B202",
                "day": "星期五",
                "start_time": "13:30",
                "end_time": "15:05"
              }
            ]
        """.trimIndent()

        assertEquals(ScheduleTextImportKind.JsonSchedule, ScheduleImportSniffer.detectTextPayload(input))
    }
}
