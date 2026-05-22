package com.miaom.schedule.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.Course

private val shellWeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleSectionScreen(
    onOpenOverview: () -> Unit,
    onOpenCourses: () -> Unit,
    onOpenTimeSlots: () -> Unit,
    onOpenTasks: () -> Unit
) {
    val repository = (LocalContext.current.applicationContext as ScheduleApplication)
        .appContainer
        .scheduleRepository
    val courses by repository.observeCourses().collectAsStateWithLifecycle(emptyList())
    val slots by repository.observeTimeSlots().collectAsStateWithLifecycle(emptyList())
    val tasks by repository.observeReminderTasks().collectAsStateWithLifecycle(emptyList())

    val slotMap = slots.associateBy { it.id }
    val previewCourses = courses
        .sortedWith(
            compareBy<Course> { it.dayOfWeek }
                .thenBy { slotMap[it.slotId]?.startTime ?: "99:99" }
                .thenBy { it.name }
        )
        .take(6)

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("课表") })
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
                SectionCard(
                    title = "本周总览",
                    body = "已记录 ${courses.size} 门课程、${slots.size} 个时间段、${tasks.size} 条提醒规则。"
                ) {
                    Button(
                        onClick = onOpenOverview,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("查看完整课表")
                    }
                }
            }

            if (previewCourses.isEmpty()) {
                item {
                    SectionCard(
                        title = "还没有课程安排",
                        body = "先添加课程和时间段，再查看整周安排。"
                    ) {
                        Button(
                            onClick = onOpenCourses,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("前往课程编辑")
                        }
                    }
                }
            } else {
                item {
                    Text(
                        text = "近期安排",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                }

                items(previewCourses, key = { it.id }) { course ->
                    val slot = slotMap[course.slotId]
                    Card(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(course.name, style = MaterialTheme.typography.titleMedium)
                            Text(
                                text = "${shellWeekdayLabels.getOrElse(course.dayOfWeek - 1) { "周${course.dayOfWeek}" }} · " +
                                    (slot?.let { "${it.label} ${it.startTime}-${it.endTime}" }
                                        ?: "时间待定"),
                                style = MaterialTheme.typography.bodyMedium
                            )
                            Text(
                                text = "${course.teacher.ifBlank { "教师未填" }} · ${course.location.ifBlank { "地点未填" }}",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            item {
                SectionCard(
                    title = "常用入口",
                    body = "继续编辑课程、时间段和提醒。"
                ) {
                    Button(
                        onClick = onOpenCourses,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("课程编辑")
                    }
                    OutlinedButton(
                        onClick = onOpenTimeSlots,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("时间段编辑")
                    }
                    OutlinedButton(
                        onClick = onOpenTasks,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("定时任务设置")
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditSectionScreen(
    onOpenCourses: () -> Unit,
    onOpenTimeSlots: () -> Unit,
    onOpenTasks: () -> Unit
) {
    val repository = (LocalContext.current.applicationContext as ScheduleApplication)
        .appContainer
        .scheduleRepository
    val isTwoColumn = LocalConfiguration.current.screenWidthDp >= 700
    val courses by repository.observeCourses().collectAsStateWithLifecycle(emptyList())
    val slots by repository.observeTimeSlots().collectAsStateWithLifecycle(emptyList())
    val tasks by repository.observeReminderTasks().collectAsStateWithLifecycle(emptyList())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("编辑") })
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
                SectionCard(
                    title = "当前数据",
                    body = "当前已有 ${courses.size} 门课程、${slots.size} 个时间段、${tasks.size} 条提醒任务。"
                )
            }

            item {
                if (isTwoColumn) {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ManagementEntryCard(
                                title = "课程编辑",
                                body = "课程名称、教师、地点和上课规则。",
                                buttonLabel = "进入课程编辑",
                                onClick = onOpenCourses,
                                modifier = Modifier.weight(1f)
                            )
                            ManagementEntryCard(
                                title = "时间段编辑",
                                body = "节次名称与上下课时间。",
                                buttonLabel = "进入时间段编辑",
                                onClick = onOpenTimeSlots,
                                modifier = Modifier.weight(1f)
                            )
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            ManagementEntryCard(
                                title = "定时任务设置",
                                body = "提醒方式、提前时间和启用状态。",
                                buttonLabel = "进入定时任务设置",
                                onClick = onOpenTasks,
                                modifier = Modifier.weight(1f)
                            )
                            Spacer(modifier = Modifier.weight(1f))
                        }
                    }
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(16.dp)) {
                        ManagementEntryCard(
                            title = "课程编辑",
                            body = "课程名称、教师、地点和上课规则。",
                            buttonLabel = "进入课程编辑",
                            onClick = onOpenCourses
                        )
                        ManagementEntryCard(
                            title = "时间段编辑",
                            body = "节次名称与上下课时间。",
                            buttonLabel = "进入时间段编辑",
                            onClick = onOpenTimeSlots
                        )
                        ManagementEntryCard(
                            title = "定时任务设置",
                            body = "提醒方式、提前时间和启用状态。",
                            buttonLabel = "进入定时任务设置",
                            onClick = onOpenTasks
                        )
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlaceholderSectionScreen(
    title: String,
    summary: String,
    detail: String,
    primaryActionLabel: String,
    onPrimaryAction: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(title = { Text(title) })
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
                SectionCard(
                    title = title,
                    body = summary
                )
            }

            item {
                SectionCard(
                    title = "使用方式",
                    body = detail
                ) {
                    Button(
                        onClick = onPrimaryAction,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(primaryActionLabel)
                    }
                }
            }
        }
    }
}

@Composable
private fun ManagementEntryCard(
    title: String,
    body: String,
    buttonLabel: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    SectionCard(
        title = title,
        body = body,
        modifier = modifier
    ) {
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(buttonLabel)
        }
    }
}

@Composable
private fun SectionCard(
    title: String,
    body: String,
    modifier: Modifier = Modifier,
    content: @Composable (ColumnScope.() -> Unit)? = null
) {
    Card(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
            content?.invoke(this)
        }
    }
}
