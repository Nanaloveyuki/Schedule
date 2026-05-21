package com.miaom.schedule.ui.screen

import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.ui.viewmodel.CourseEditorViewModel

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val repository = appContainer.scheduleRepository
    val viewModel: CourseEditorViewModel = viewModel(
        factory = CourseEditorViewModel.factory(repository)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slots by repository.observeTimeSlots().collectAsStateWithLifecycle(emptyList())
    val slotMap = slots.associateBy { it.id }

    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableIntStateOf(1) }
    var slotId by remember { mutableStateOf("") }
    var slotExpanded by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课程编辑") },
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
                        Text("新增课程", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("课程名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = teacher,
                            onValueChange = { teacher = it },
                            label = { Text("教师") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = location,
                            onValueChange = { location = it },
                            label = { Text("地点") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            OutlinedTextField(
                                value = dayOfWeek.toString(),
                                onValueChange = { dayOfWeek = it.toIntOrNull()?.coerceIn(1, 7) ?: 1 },
                                label = { Text("星期") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Box(modifier = Modifier.weight(1f)) {
                                OutlinedTextField(
                                    value = slotMap[slotId]?.let { "${it.label} ${it.startTime}-${it.endTime}" } ?: "",
                                    onValueChange = {},
                                    readOnly = true,
                                    label = { Text("时间段") },
                                    placeholder = { Text("请选择") },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .pointerInput(slots, slotId) {
                                            detectTapGestures(onTap = {
                                                if (slots.isNotEmpty()) {
                                                    slotExpanded = true
                                                }
                                            })
                                        },
                                    singleLine = true
                                )
                                DropdownMenu(
                                    expanded = slotExpanded,
                                    onDismissRequest = { slotExpanded = false },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    slots.forEach { slot ->
                                        DropdownMenuItem(
                                            text = { Text("${slot.label} ${slot.startTime}-${slot.endTime}") },
                                            onClick = {
                                                slotId = slot.id
                                                slotExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
                        }
                        if (slots.isEmpty()) {
                            Text(
                                text = "还没有时间段，请先到“时间段编辑”页新增时间段。",
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                        Button(
                            onClick = {
                                if (name.isNotBlank() && slotId.isNotBlank()) {
                                    viewModel.saveCourse(name, teacher, location, dayOfWeek, slotId)
                                    name = ""
                                    teacher = ""
                                    location = ""
                                    dayOfWeek = 1
                                    slotId = ""
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("保存课程")
                        }
                    }
                }
            }

            item {
                Text("已保存课程", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.courses, key = { it.id }) { course ->
                Card {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(course.name, style = MaterialTheme.typography.titleMedium)
                        Text("教师：${course.teacher.ifBlank { "未填写" }}")
                        Text("地点：${course.location.ifBlank { "未填写" }}")
                        Text(
                            "${weekdayLabels.getOrElse(course.dayOfWeek - 1) { "星期 ${course.dayOfWeek}" }} · " +
                                (slotMap[course.slotId]?.let { "${it.label} ${it.startTime}-${it.endTime}" }
                                    ?: "时间段 ${course.slotId}")
                        )
                        TextButton(onClick = { viewModel.deleteCourse(course.id) }) {
                            Text("删除")
                        }
                    }
                }
            }
        }
    }
}
