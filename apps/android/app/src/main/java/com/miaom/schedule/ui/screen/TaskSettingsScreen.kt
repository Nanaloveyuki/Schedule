package com.miaom.schedule.ui.screen

import android.Manifest
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.ui.component.EditorHistoryActions
import com.miaom.schedule.ui.component.EditorInlineNote
import com.miaom.schedule.ui.component.EditorSectionCard
import com.miaom.schedule.ui.component.EditorSwitchRow
import com.miaom.schedule.ui.viewmodel.TaskSettingsViewModel

private val taskWeekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TaskSettingsScreen(onBack: () -> Unit) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ScheduleApplication).appContainer
    val repository = appContainer.scheduleRepository
    val viewModel: TaskSettingsViewModel = viewModel(
        factory = TaskSettingsViewModel.factory(
            repository = repository,
            scheduleStore = appContainer.scheduleStore,
            reminderOrchestrator = appContainer.reminderOrchestrator
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val courses by repository.observeCourses().collectAsStateWithLifecycle(emptyList())
    val courseMap = courses.associateBy { it.id }
    val capabilities = uiState.capabilitySnapshot

    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        viewModel.refreshReminderState()
    }
    val calendarPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions()
    ) {
        viewModel.refreshReminderState()
    }

    var editingTaskId by remember { mutableStateOf<String?>(null) }
    var courseId by remember { mutableStateOf("") }
    var courseExpanded by remember { mutableStateOf(false) }
    var minutesBefore by remember { mutableIntStateOf(10) }
    var channel by remember { mutableStateOf(ReminderChannel.InAppNotification) }
    var channelExpanded by remember { mutableStateOf(false) }
    var exact by remember { mutableStateOf(false) }
    var enabled by remember { mutableStateOf(true) }
    val selectedCourse = courseMap[courseId]
    val canSave = courseId.isNotBlank()
    val notificationPermissionRequired = Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
    val notificationReady = if (notificationPermissionRequired) capabilities.notificationPermissionGranted else true
    val exactAlarmSupported = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val exactAlarmEffectiveForCurrentChannel = channel == ReminderChannel.InAppNotification

    fun resetForm() {
        editingTaskId = null
        courseId = ""
        courseExpanded = false
        minutesBefore = 10
        channel = ReminderChannel.InAppNotification
        channelExpanded = false
        exact = false
        enabled = true
    }

    fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !capabilities.notificationPermissionGranted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    fun requestCalendarPermissionsIfNeeded() {
        if (!capabilities.calendarPermissionsGranted) {
            calendarPermissionLauncher.launch(
                arrayOf(
                    Manifest.permission.READ_CALENDAR,
                    Manifest.permission.WRITE_CALENDAR
                )
            )
        }
    }

    fun openExactAlarmSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            context.startActivity(
                Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM).apply {
                    data = android.net.Uri.parse("package:${context.packageName}")
                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                }
            )
        }
    }

    LaunchedEffect(uiState.tasks, editingTaskId) {
        if (editingTaskId != null && uiState.tasks.none { it.id == editingTaskId }) {
            resetForm()
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshReminderState()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("提醒设置") },
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
                EditorHistoryActions(
                    canUndo = uiState.undoState.canUndo,
                    canRedo = uiState.undoState.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo
                )
            }

            item {
                EditorSectionCard(
                    title = "权限与能力",
                    subtitle = "提醒通道会严格按你当前选择的能力来工作。"
                ) {
                    EditorInlineNote(
                        text = if (notificationReady) {
                            if (notificationPermissionRequired) "应用内通知已准备好。" else "当前系统版本不需要单独授予通知权限。"
                        } else {
                            "应用内通知还未获得通知权限。"
                        }
                    )
                    OutlinedButton(
                        onClick = ::requestNotificationPermissionIfNeeded,
                        enabled = notificationPermissionRequired && !capabilities.notificationPermissionGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开启通知权限")
                    }

                    EditorInlineNote(
                        text = if (capabilities.calendarPermissionsGranted) {
                            if (capabilities.writableCalendarAvailable) {
                                "系统日历可用，保存后会把未来课程事件同步到 ${capabilities.writableCalendarName ?: "默认日历"}。"
                            } else {
                                "已获得日历权限，但设备上暂未发现可写日历。"
                            }
                        } else {
                            "系统日历事件还未获得日历访问权限。"
                        }
                    )
                    OutlinedButton(
                        onClick = ::requestCalendarPermissionsIfNeeded,
                        enabled = !capabilities.calendarPermissionsGranted,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("开启日历权限")
                    }

                    if (exactAlarmSupported) {
                        EditorInlineNote(
                            text = if (capabilities.exactAlarmPermissionGranted) {
                                "精确定时已可用，仅对应用内通知生效。"
                            } else {
                                "精确定时暂未允许；只有应用内通知在启用精确定时后才需要到系统设置中开启。"
                            }
                        )
                        OutlinedButton(
                            onClick = ::openExactAlarmSettings,
                            enabled = !capabilities.exactAlarmPermissionGranted,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("开启精确定时")
                        }
                    }
                }
            }

            item {
                EditorSectionCard(
                    title = "提醒对象",
                    subtitle = if (editingTaskId == null) "为已有课程选择提醒对象。" else "正在修改已保存提醒。"
                ) {
                    ExposedDropdownMenuBox(
                        expanded = courseExpanded && courses.isNotEmpty(),
                        onExpandedChange = { expanded ->
                            if (courses.isNotEmpty()) {
                                courseExpanded = expanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedCourse?.let(::formatCourseSelectionLabel).orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            enabled = courses.isNotEmpty(),
                            label = { Text("课程") },
                            placeholder = { Text(if (courses.isEmpty()) "暂无可选课程" else "请选择") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(
                                    expanded = courseExpanded && courses.isNotEmpty()
                                )
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(
                                    when {
                                        courses.isEmpty() -> "还没有课程，请先到“课程编辑”页新增课程。"
                                        selectedCourse != null -> formatCourseSelectionSupportText(selectedCourse)
                                        else -> "请选择要绑定提醒的课程。"
                                    }
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = courseExpanded && courses.isNotEmpty(),
                            onDismissRequest = { courseExpanded = false }
                        ) {
                            courses.forEach { course ->
                                DropdownMenuItem(
                                    text = { Text(formatCourseSelectionLabel(course)) },
                                    onClick = {
                                        courseId = course.id
                                        courseExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                }
            }

            item {
                EditorSectionCard(
                    title = "提醒时机",
                    subtitle = "设置提前时间、启用状态和定时方式。"
                ) {
                    OutlinedTextField(
                        value = minutesBefore.toString(),
                        onValueChange = { minutesBefore = it.toIntOrNull()?.coerceAtLeast(0) ?: 10 },
                        label = { Text("提前分钟数") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("课程开始前多久触发提醒。") }
                    )
                    EditorSwitchRow(
                        title = "启用提醒",
                        description = "关闭后保留这条提醒配置，但不会参与调度。",
                        checked = enabled,
                        onCheckedChange = { enabled = it }
                    )
                    EditorSwitchRow(
                        title = "精确定时",
                        description = if (exactAlarmEffectiveForCurrentChannel) {
                            "允许时会按精确闹钟安排应用内通知；不允许时会退回普通调度。"
                        } else {
                            "系统日历事件不使用精确定时，这里会保留为关闭。"
                        },
                        checked = exact,
                        onCheckedChange = { exact = if (exactAlarmEffectiveForCurrentChannel) it else false },
                        enabled = exactAlarmEffectiveForCurrentChannel
                    )
                }
            }

            item {
                EditorSectionCard(
                    title = "提醒方式",
                    subtitle = "选择提醒的正式通道，系统不会替你擅自切换。"
                ) {
                    ExposedDropdownMenuBox(
                        expanded = channelExpanded,
                        onExpandedChange = { expanded -> channelExpanded = expanded }
                    ) {
                        OutlinedTextField(
                            value = channel.displayLabel(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("提醒方式") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = channelExpanded)
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text("展开后选择正式使用的提醒通道。")
                            }
                        )
                        DropdownMenu(
                            expanded = channelExpanded,
                            onDismissRequest = { channelExpanded = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("应用内通知") },
                                onClick = {
                                    channel = ReminderChannel.InAppNotification
                                    channelExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                            DropdownMenuItem(
                                text = { Text("系统日历事件") },
                                onClick = {
                                    channel = ReminderChannel.SystemCalendar
                                    exact = false
                                    channelExpanded = false
                                },
                                contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                            )
                        }
                    }
                    EditorInlineNote(
                        text = when (channel) {
                            ReminderChannel.InAppNotification -> if (notificationReady) {
                                "保存后会安排应用内通知。"
                            } else {
                                "保存后会保留这条规则，获得通知权限后再开始调度。"
                            }

                            ReminderChannel.SystemCalendar -> if (capabilities.calendarPermissionsGranted && capabilities.writableCalendarAvailable) {
                                "保存后会按当前规则同步未来课程事件到系统日历。"
                            } else {
                                "保存后会保留这条规则，待日历可用后再写入系统日历。"
                            }
                        }
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveTask(
                                taskId = editingTaskId,
                                courseId = courseId,
                                minutesBefore = minutesBefore,
                                channel = channel,
                                exact = exact,
                                enabled = enabled
                            )
                            resetForm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSave
                    ) {
                        Text(if (editingTaskId == null) "保存提醒任务" else "保存提醒修改")
                    }
                    if (editingTaskId != null) {
                        TextButton(onClick = ::resetForm) {
                            Text("取消编辑")
                        }
                    }
                }
            }

            item {
                Text("已保存提醒", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.tasks, key = { it.id }) { task ->
                TaskSummaryCard(
                    task = task,
                    courseName = courseMap[task.courseId]?.name ?: "课程 ${task.courseId}",
                    runtimeSummary = uiState.runtimeTaskStates[task.id]?.summary,
                    onEdit = {
                        editingTaskId = task.id
                        courseId = task.courseId
                        courseExpanded = false
                        minutesBefore = task.minutesBefore
                        channel = task.channel
                        channelExpanded = false
                        exact = task.exact && task.channel == ReminderChannel.InAppNotification
                        enabled = task.enabled
                    },
                    onDelete = { viewModel.deleteTask(task.id) }
                )
            }
        }
    }
}

private fun ReminderChannel.displayLabel(): String = when (this) {
    ReminderChannel.InAppNotification -> "应用内通知"
    ReminderChannel.SystemCalendar -> "系统日历事件"
}

private fun formatCourseSelectionLabel(course: Course): String {
    val weekday = taskWeekdayLabels.getOrElse(course.dayOfWeek - 1) { "周${course.dayOfWeek}" }
    val time = when {
        course.effectiveStartTime.isNotBlank() && course.effectiveEndTime.isNotBlank() -> {
            "${course.effectiveStartTime}-${course.effectiveEndTime}"
        }

        else -> "时间待补充"
    }
    return "${course.name} · $weekday · $time"
}

private fun formatCourseSelectionSupportText(course: Course): String {
    val teacherOrLocation = course.teacher.ifBlank { course.location }.ifBlank { "未填写教师和地点" }
    return "当前课程：$teacherOrLocation"
}

@Composable
private fun TaskSummaryCard(
    task: ReminderTask,
    courseName: String,
    runtimeSummary: String?,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(courseName, style = MaterialTheme.typography.titleMedium)
            Text("提前 ${task.minutesBefore} 分钟")
            Text(
                "${if (task.channel == ReminderChannel.InAppNotification) "应用内通知" else "系统日历事件"} · " +
                    "${if (task.exact) "精确定时" else "普通定时"} · ${if (task.enabled) "已启用" else "已关闭"}"
            )
            if (!runtimeSummary.isNullOrBlank()) {
                EditorInlineNote(runtimeSummary)
            }
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                TextButton(onClick = onEdit) {
                    Text("编辑")
                }
                TextButton(onClick = onDelete) {
                    Text("删除")
                }
            }
        }
    }
}
