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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
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
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.displayLabel
import com.miaom.schedule.domain.model.weekRuleDisplayLabel
import com.miaom.schedule.ui.component.CourseColorEditor
import com.miaom.schedule.ui.component.CourseColorEditorMode
import com.miaom.schedule.ui.component.CourseColorEditorState
import com.miaom.schedule.ui.component.EditorHistoryActions
import com.miaom.schedule.ui.component.EditorSectionCard
import com.miaom.schedule.ui.component.TimeSlotFormFields
import com.miaom.schedule.ui.viewmodel.CourseEditorViewModel
import com.miaom.schedule.ui.viewmodel.TimeSlotEditorViewModel

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditorScreen(onBack: () -> Unit) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val repository = appContainer.scheduleRepository
    val viewModel: CourseEditorViewModel = viewModel(
        factory = CourseEditorViewModel.factory(
            repository = repository,
            scheduleStore = appContainer.scheduleStore
        )
    )
    val timeSlotEditorViewModel: TimeSlotEditorViewModel = viewModel(
        factory = TimeSlotEditorViewModel.factory(
            repository = repository,
            scheduleStore = appContainer.scheduleStore
        )
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val slots = uiState.slots
    val slotMap = slots.associateBy { it.id }

    var editingCourseId by remember { mutableStateOf<String?>(null) }
    var name by remember { mutableStateOf("") }
    var teacher by remember { mutableStateOf("") }
    var location by remember { mutableStateOf("") }
    var dayOfWeek by remember { mutableIntStateOf(1) }
    var slotId by remember { mutableStateOf("") }
    var slotExpanded by remember { mutableStateOf(false) }
    var weekParity by remember { mutableStateOf(WeekParity.Every) }
    var useTimeOverride by remember { mutableStateOf(false) }
    var overrideStartTime by remember { mutableStateOf("") }
    var overrideEndTime by remember { mutableStateOf("") }
    var colorEditorMode by remember { mutableStateOf(CourseColorEditorMode.Hsv) }
    var courseColorState by remember { mutableStateOf(CourseColorEditorState()) }
    var week1MondayDate by remember(uiState.weekConfig.week1MondayDate) { mutableStateOf(uiState.weekConfig.week1MondayDate) }
    var isCreateSlotSheetVisible by remember { mutableStateOf(false) }
    var newSlotLabel by remember { mutableStateOf("") }
    var newSlotStartTime by remember { mutableStateOf("") }
    var newSlotEndTime by remember { mutableStateOf("") }

    val selectedSlot = slotMap[slotId]
    val canCreateSlot = newSlotLabel.isNotBlank() && newSlotStartTime.isNotBlank() && newSlotEndTime.isNotBlank()
    val canSave = name.isNotBlank() && slotId.isNotBlank() && (!useTimeOverride || (overrideStartTime.isNotBlank() && overrideEndTime.isNotBlank()))

    fun resetCourseForm() {
        editingCourseId = null
        name = ""
        teacher = ""
        location = ""
        dayOfWeek = 1
        slotId = ""
        slotExpanded = false
        weekParity = WeekParity.Every
        useTimeOverride = false
        overrideStartTime = ""
        overrideEndTime = ""
        colorEditorMode = CourseColorEditorMode.Hsv
        courseColorState = CourseColorEditorState()
    }

    fun resetInlineTimeSlotForm() {
        newSlotLabel = ""
        newSlotStartTime = ""
        newSlotEndTime = ""
    }

    LaunchedEffect(uiState.courses, editingCourseId) {
        if (editingCourseId != null && uiState.courses.none { it.id == editingCourseId }) {
            resetCourseForm()
        }
    }

    LaunchedEffect(uiState.suggestedSlotId, slotMap.keys) {
        val suggestedSlotId = uiState.suggestedSlotId ?: return@LaunchedEffect
        if (slotMap.containsKey(suggestedSlotId)) {
            slotId = suggestedSlotId
            slotExpanded = false
            viewModel.onSuggestedSlotConsumed(suggestedSlotId)
        }
    }

    if (isCreateSlotSheetVisible) {
        ModalBottomSheet(
            onDismissRequest = {
                isCreateSlotSheetVisible = false
                resetInlineTimeSlotForm()
            }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(text = "新建时间段模板", style = MaterialTheme.typography.titleLarge)
                TimeSlotFormFields(
                    label = newSlotLabel,
                    onLabelChange = { newSlotLabel = it },
                    startTime = newSlotStartTime,
                    onStartTimeChange = { newSlotStartTime = it },
                    endTime = newSlotEndTime,
                    onEndTimeChange = { newSlotEndTime = it }
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            isCreateSlotSheetVisible = false
                            resetInlineTimeSlotForm()
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("取消")
                    }
                    Button(
                        onClick = {
                            timeSlotEditorViewModel.saveTimeSlot(
                                slotId = null,
                                label = newSlotLabel.trim(),
                                startTime = newSlotStartTime.trim(),
                                endTime = newSlotEndTime.trim()
                            )
                            isCreateSlotSheetVisible = false
                            slotExpanded = false
                            resetInlineTimeSlotForm()
                        },
                        modifier = Modifier.weight(1f),
                        enabled = canCreateSlot
                    ) {
                        Text("保存并带回")
                    }
                }
            }
        }
    }

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
                EditorHistoryActions(
                    canUndo = uiState.undoState.canUndo,
                    canRedo = uiState.undoState.canRedo,
                    onUndo = viewModel::undo,
                    onRedo = viewModel::redo
                )
            }

            item {
                EditorSectionCard(
                    title = "周设置"
                ) {
                    OutlinedTextField(
                        value = week1MondayDate,
                        onValueChange = {
                            week1MondayDate = it
                            viewModel.updateWeek1MondayDate(it)
                        },
                        label = { Text("第1周周一日期") },
                        supportingText = { Text("YYYY-MM-DD") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }

            item {
                EditorSectionCard(
                    title = if (editingCourseId == null) "基本信息" else "编辑课程"
                ) {
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
                }
            }

            item {
                EditorSectionCard(
                    title = "排课规则"
                ) {
                    OutlinedTextField(
                        value = dayOfWeek.toString(),
                        onValueChange = { dayOfWeek = it.toIntOrNull()?.coerceIn(1, 7) ?: 1 },
                        label = { Text("星期") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        supportingText = { Text("1-7 对应周一到周日") }
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        WeekParity.entries.forEach { parity ->
                            FilterChip(
                                selected = weekParity == parity,
                                onClick = { weekParity = parity },
                                label = { Text(parity.displayLabel()) }
                            )
                        }
                    }
                    ExposedDropdownMenuBox(
                        expanded = slotExpanded && slots.isNotEmpty(),
                        onExpandedChange = { expanded ->
                            if (slots.isNotEmpty()) {
                                slotExpanded = expanded
                            }
                        }
                    ) {
                        OutlinedTextField(
                            value = selectedSlot?.let { "${it.label} ${it.startTime}-${it.endTime}" }.orEmpty(),
                            onValueChange = {},
                            readOnly = true,
                            label = { Text("时间段模板") },
                            placeholder = { Text(if (slots.isEmpty()) "暂无模板" else "请选择模板") },
                            trailingIcon = {
                                ExposedDropdownMenuDefaults.TrailingIcon(expanded = slotExpanded && slots.isNotEmpty())
                            },
                            modifier = Modifier
                                .menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable)
                                .fillMaxWidth(),
                            singleLine = true,
                            supportingText = {
                                Text(if (slots.isEmpty()) "先新建模板。" else "默认使用模板时间。")
                            }
                        )
                        DropdownMenu(
                            expanded = slotExpanded && slots.isNotEmpty(),
                            onDismissRequest = { slotExpanded = false }
                        ) {
                            slots.forEach { slot ->
                                DropdownMenuItem(
                                    text = { Text("${slot.label} ${slot.startTime}-${slot.endTime}") },
                                    onClick = {
                                        slotId = slot.id
                                        slotExpanded = false
                                    },
                                    contentPadding = ExposedDropdownMenuDefaults.ItemContentPadding
                                )
                            }
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(
                            onClick = { isCreateSlotSheetVisible = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("新建模板")
                        }
                        if (slots.isNotEmpty()) {
                            Button(
                                onClick = { slotExpanded = true },
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("选择模板")
                            }
                        }
                    }
                }
            }

            item {
                EditorSectionCard(
                    title = "单独设置时间"
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("启用单独时间", style = MaterialTheme.typography.titleSmall)
                            Text(
                                text = "关闭后使用模板时间。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = useTimeOverride,
                            onCheckedChange = { checked ->
                                useTimeOverride = checked
                                if (!checked) {
                                    overrideStartTime = ""
                                    overrideEndTime = ""
                                }
                            }
                        )
                    }
                    if (useTimeOverride) {
                        OutlinedTextField(
                            value = overrideStartTime,
                            onValueChange = { overrideStartTime = it },
                            label = { Text("覆盖开始时间") },
                            placeholder = { Text("08:10") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = overrideEndTime,
                            onValueChange = { overrideEndTime = it },
                            label = { Text("覆盖结束时间") },
                            placeholder = { Text("08:55") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                    }
                }
            }

            item {
                EditorSectionCard(
                    title = "课程颜色"
                ) {
                    CourseColorEditor(
                        state = courseColorState,
                        mode = colorEditorMode,
                        onModeChange = { colorEditorMode = it },
                        onStateChange = { courseColorState = it }
                    )
                }
            }

            item {
                EditorSectionCard(
                    title = "当前安排"
                ) {
                    Text(
                        text = buildCurrentSummary(
                            dayOfWeek = dayOfWeek,
                            weekParity = weekParity,
                            selectedSlot = selectedSlot,
                            useTimeOverride = useTimeOverride,
                            overrideStartTime = overrideStartTime,
                            overrideEndTime = overrideEndTime
                        ),
                        style = MaterialTheme.typography.titleSmall
                    )
                }
            }

            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Button(
                        onClick = {
                            viewModel.saveCourse(
                                courseId = editingCourseId,
                                name = name,
                                teacher = teacher,
                                location = location,
                                dayOfWeek = dayOfWeek,
                                slotId = slotId,
                                weekParity = weekParity,
                                overrideStartTime = if (useTimeOverride) overrideStartTime else "",
                                overrideEndTime = if (useTimeOverride) overrideEndTime else "",
                                useThemeDefaults = courseColorState.useThemeDefaults,
                                backgroundColorArgb = courseColorState.backgroundColorArgb,
                                textColorArgb = courseColorState.textColorArgb,
                                borderColorArgb = courseColorState.borderColorArgb
                            )
                            resetCourseForm()
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = canSave
                    ) {
                        Text(if (editingCourseId == null) "保存并继续添加" else "保存课程修改")
                    }
                    if (editingCourseId != null) {
                        TextButton(onClick = ::resetCourseForm) {
                            Text("取消编辑")
                        }
                    }
                }
            }

            item {
                Text("已保存课程", style = MaterialTheme.typography.titleMedium)
            }

            items(uiState.courses, key = { it.id }) { course ->
                CourseSummaryCard(
                    course = course,
                    onEdit = {
                        editingCourseId = course.id
                        name = course.name
                        teacher = course.teacher
                        location = course.location
                        dayOfWeek = course.dayOfWeek
                        slotId = course.slotId
                        slotExpanded = false
                        weekParity = course.weekParity
                        useTimeOverride = course.overrideStartTime.isNotBlank() || course.overrideEndTime.isNotBlank()
                        overrideStartTime = course.overrideStartTime
                        overrideEndTime = course.overrideEndTime
                        courseColorState = CourseColorEditorState(
                            useThemeDefaults = course.useThemeDefaults,
                            backgroundColorArgb = course.backgroundColorArgb,
                            textColorArgb = course.textColorArgb,
                            borderColorArgb = course.borderColorArgb
                        )
                    },
                    onDelete = { viewModel.deleteCourse(course.id) }
                )
            }
        }
    }
}

@Composable
private fun CourseSummaryCard(
    course: Course,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    Card {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(course.name, style = MaterialTheme.typography.titleMedium)
            Text("教师：${course.teacher.ifBlank { "未填写" }}")
            Text("地点：${course.location.ifBlank { "未填写" }}")
            Text("${weekdayLabels.getOrElse(course.dayOfWeek - 1) { "星期 ${course.dayOfWeek}" }} · ${weekRuleDisplayLabel(course.weekParity, course.weekNumbers)}")
            Text(
                if (course.effectiveStartTime.isNotBlank() && course.effectiveEndTime.isNotBlank()) {
                    "时间：${course.effectiveStartTime} - ${course.effectiveEndTime}" +
                        if (course.overrideStartTime.isNotBlank() || course.overrideEndTime.isNotBlank()) "（课程覆盖）" else ""
                } else {
                    "时间段 ${course.slotId}"
                }
            )
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

private fun buildCurrentSummary(
    dayOfWeek: Int,
    weekParity: WeekParity,
    selectedSlot: TimeSlot?,
    useTimeOverride: Boolean,
    overrideStartTime: String,
    overrideEndTime: String
): String {
    val weekday = weekdayLabels.getOrElse(dayOfWeek - 1) { "星期 $dayOfWeek" }
    val templateText = selectedSlot?.let { "${it.label} ${it.startTime}-${it.endTime}" } ?: "未选择模板"
    val timeText = if (useTimeOverride) {
        "${overrideStartTime.ifBlank { "--:--" }}-${overrideEndTime.ifBlank { "--:--" }}"
    } else {
        selectedSlot?.let { "${it.startTime}-${it.endTime}" } ?: "待定"
    }
    return "$weekday · ${weekParity.displayLabel()} · $templateText · $timeText"
}
