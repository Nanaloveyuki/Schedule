package com.miaom.schedule.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.Course

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleOverviewScreen(onBack: () -> Unit) {
    val repository = (LocalContext.current.applicationContext as ScheduleApplication)
        .appContainer
        .scheduleRepository
    val courses by repository.observeCourses().collectAsStateWithLifecycle(emptyList())
    val slots by repository.observeTimeSlots().collectAsStateWithLifecycle(emptyList())

    val slotMap = slots.associateBy { it.id }
    val groupedCourses = courses.groupBy { it.dayOfWeek }.toSortedMap()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表查看") },
                actions = {
                    TextButton(onClick = onBack) {
                        Text("返回")
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text("本周概览", style = MaterialTheme.typography.titleLarge)
                        Text("共 ${courses.size} 门课程，${slots.size} 个时间段。")
                    }
                }
            }

            if (courses.isEmpty()) {
                item {
                    Card {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("还没有课程", style = MaterialTheme.typography.titleMedium)
                            Text("先去“课程编辑”和“时间段编辑”页录入数据。")
                        }
                    }
                }
            } else {
                groupedCourses.forEach { (dayOfWeek, dayCourses) ->
                    item {
                        Text(
                            text = weekdayLabels.getOrElse(dayOfWeek - 1) { "周$dayOfWeek" },
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.padding(top = 8.dp)
                        )
                    }
                    items(
                        items = dayCourses.sortedWith(
                            compareBy<Course> { slotMap[it.slotId]?.startTime ?: "99:99" }
                                .thenBy { it.name }
                        ),
                        key = { it.id }
                    ) { course ->
                        val slot = slotMap[course.slotId]
                        Card {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Text(course.name, style = MaterialTheme.typography.titleMedium)
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text("教师：${course.teacher.ifBlank { "未填写" }}")
                                    Text("地点：${course.location.ifBlank { "未填写" }}")
                                }
                                Text(
                                    if (slot != null) {
                                        "${slot.label} · ${slot.startTime} - ${slot.endTime}"
                                    } else {
                                        "时间段 ${course.slotId} 未配置"
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
