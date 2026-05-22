package com.miaom.schedule.ui.screen

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaom.schedule.ScheduleApplication

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleHomeScreen(
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

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("课程表") })
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                text = "课程与提醒",
                style = MaterialTheme.typography.headlineMedium
            )
            Text("统一查看课程、时间段和提醒状态，重点确认本周安排是否可直接使用。")

            EntryCard(
                title = "课表查看",
                body = "当前有 ${courses.size} 门课程、${slots.size} 个时间段、${tasks.size} 个提醒任务。",
                onClick = onOpenOverview
            )
            EntryCard(
                title = "课程编辑",
                body = "新增、修改、删除课程。",
                onClick = onOpenCourses
            )
            EntryCard(
                title = "时间段编辑",
                body = "维护节次、上下课时间与模板。",
                onClick = onOpenTimeSlots
            )
            EntryCard(
                title = "定时任务设置",
                body = "配置提醒开关、提前时间与精确定时。",
                onClick = onOpenTasks
            )
        }
    }
}

@Composable
private fun EntryCard(
    title: String,
    body: String,
    onClick: (() -> Unit)? = null
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            Text(body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
