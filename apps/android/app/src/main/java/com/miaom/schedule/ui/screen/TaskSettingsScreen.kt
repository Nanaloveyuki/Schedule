package com.miaom.schedule.ui.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.ui.viewmodel.TaskSettingsViewModel

private val taskWeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSettingsScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val repository = appContainer.scheduleRepository
    val viewModel: TaskSettingsViewModel = viewModel(
        factory = TaskSettingsViewModel.factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courses by repository.observeCourses().collectAsStateWithLifecycle(emptyList())
    val courseMap = courses.associateBy { it.id }

    var courseId by remember { mutableStateOf("") }
    var courseExpanded by remember { mutableStateOf(false) }
    var minutesBefore by remember { mutableIntStateOf(10) }
    var channel by remember { mutableStateOf(ReminderChannel.InAppNotification) }
    var channelExpanded by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("定时任务设置") },
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
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("新增提醒任务", style = MaterialTheme.typography.titleMedium)
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = courseMap[courseId]?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("课程") },
                                placeholder = { Text("请选择") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(courses, courseId) {
                                        detectTapGestures(onTap = {
                                            if (courses.isNotEmpty()) {
                                                courseExpanded = true
                                            }
                                        })
                                    },
                                singleLine = true
                            )
                            DropdownMenu(
                                expanded = courseExpanded,
                                onDismissRequest = { courseExpanded = false },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                courses.forEach { course ->
                                    DropdownMenuItem(
                                        text = {
                                            Text(
                                                "${course.name} · ${course.teacher.ifBlank { taskWeekdayLabels.getOrElse(course.dayOfWeek - 1) { "周${course.dayOfWeek}" } }}"
                                            )
                                        },
                                        onClick = {
                                            courseId = course.id
                                            courseExpanded = false
                                        }
                                    )
                                }
                            }
                        }
                        if (courses.isEmpty()) {
                            Text(
                                text = "还没有课程，请先到“课程编辑”页新增课程。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        OutlinedTextField(
                            value = minutesBefore.toString(),
                            onValueChange = { minutesBefore = it.toIntOrNull() ?: 10 },
                            label = { Text("提前分钟数") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Box(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = when (channel) {
                                    ReminderChannel.InAppNotification -> "应用内通知"
                                    ReminderChannel.SystemCalendar -> "系统日历事件"
                                },
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("提醒方式") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .pointerInput(Unit) {
                                        detectTapGestures(onTap = { channelExpanded = true })
                                    }
                            )
                            DropdownMenu(
                                expanded = channelExpanded,
                                onDismissRequest = { channelExpanded = false },
                                modifier = Modifier
                                    .fillMaxWidth()
                            )
                            {
                                DropdownMenuItem(
                                    text = { Text("应用内通知") },
                                    onClick = {
                                        channel = ReminderChannel.InAppNotification
                                        channelExpanded = false
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text("系统日历事件") },
                                    onClick = {
                                        channel = ReminderChannel.SystemCalendar
                                        channelExpanded = false
                                    }
                                )
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("精确定时")
                                Switch(checked = exact, onCheckedChange = { exact = it })
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text("启用")
                                Switch(checked = enabled, onCheckedChange = { enabled = it })
                            }
                        }
                        Text(
                            text = if (channel == ReminderChannel.SystemCalendar) {
                                "系统日历事件模式后续会在获得日历权限后创建系统日程。"
                            } else {
                                "应用内通知模式后续会通过应用自身通知渠道提醒。"
                            },
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = {
                                if (courseId.isNotBlank()) {
                                    viewModel.saveTask(courseId, minutesBefore, channel, exact, enabled)
                                    courseId = ""
                                    minutesBefore = 10
                                    channel = ReminderChannel.InAppNotification
                                    exact = false
                                    enabled = true
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存提醒任务")
                        }
                    }
                }
            }

            item {
                Text("已保存任务", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.tasks, key = { it.id }) { task ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(
                            courseMap[task.courseId]?.name ?: "课程 ${task.courseId}",
                            style = MaterialTheme.typography.titleMedium
                        )
                        Text("提前 ${task.minutesBefore} 分钟")
                        Text(
                            "${if (task.channel == ReminderChannel.InAppNotification) "应用内通知" else "系统日历事件"} · " +
                                "${if (task.exact) "精确定时" else "普通定时"} · ${if (task.enabled) "已启用" else "已关闭"}"
                        )
                        TextButton(onClick = { viewModel.deleteTask(task.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
