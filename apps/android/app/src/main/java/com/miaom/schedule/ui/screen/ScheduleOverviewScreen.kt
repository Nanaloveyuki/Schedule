package com.miaom.schedule.ui.screen

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.focusable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isShiftPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.GridSizingConfig
import com.miaom.schedule.domain.model.ScheduleLayoutMetrics
import com.miaom.schedule.domain.model.SchedulePresentationCourse
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.displayLabel
import com.miaom.schedule.domain.model.ensureReadableTextColor
import com.miaom.schedule.domain.model.resolveMetrics
import com.miaom.schedule.domain.model.shortLabel
import com.miaom.schedule.domain.model.toCourse
import com.miaom.schedule.domain.model.toPresentationCourses
import com.miaom.schedule.domain.model.toTimeSlots
import com.miaom.schedule.ui.interaction.CourseClipboardMode
import com.miaom.schedule.ui.interaction.ScheduleCellTarget
import com.miaom.schedule.ui.interaction.ScheduleCourseActionManager
import java.time.LocalDate
import java.util.UUID
import kotlinx.coroutines.launch

private val weekdayLabels = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private enum class ScheduleViewMode(val label: String) {
    Week("周视图"),
    List("列表视图")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleOverviewScreen(onBack: (() -> Unit)? = null) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val scheduleStore = appContainer.scheduleStore
    val repository = appContainer.scheduleRepository
    val document by scheduleStore.document.collectAsStateWithLifecycle()
    val undoState by scheduleStore.undoState.collectAsStateWithLifecycle()
    val slots = remember(document) { document.toTimeSlots() }
    val courses = remember(document) { document.toPresentationCourses() }
    val templatesById = remember(document) { document.timeSlotTemplates.associateBy { it.id } }
    val courseMap = remember(document, templatesById) {
        document.courseEntries.associate { entry -> entry.id to entry.toCourse(templatesById[entry.timeSlotTemplateId]) }
    }
    val presentationCourseMap = remember(courses) { courses.associateBy { it.id } }
    val weekConfig = document.weekConfig
    val gridSizing = document.themeConfig.gridSizing
    val currentWeekIndex = remember(weekConfig.week1MondayDate) { weekConfig.weekIndexFor(LocalDate.now()) }
    val currentParityLabel = remember(weekConfig.week1MondayDate) { weekConfig.parityFor(LocalDate.now()).shortLabel() }
    val todayCourseCount = remember(courses) { courses.count { it.dayOfWeek == LocalDate.now().dayOfWeek.value } }
    var viewMode by remember { mutableStateOf(ScheduleViewMode.Week) }
    var detailCourse by remember { mutableStateOf<SchedulePresentationCourse?>(null) }
    val actionManager = remember { ScheduleCourseActionManager() }
    val scope = rememberCoroutineScope()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(courses, slots) {
        actionManager.syncSelection(
            existingCourseIds = courses.map { it.id }.toSet(),
            existingSlotIds = slots.map { it.id }.toSet()
        )
    }

    LaunchedEffect(Unit) {
        focusRequester.requestFocus()
    }

    fun pasteInto(target: ScheduleCellTarget) {
        val snapshot = actionManager.clipboard ?: return
        if (!actionManager.canPasteInto(target)) return
        scope.launch {
            val source = snapshot.course
            val pastedCourse = when (snapshot.mode) {
                CourseClipboardMode.Copy -> source.copy(
                    id = UUID.randomUUID().toString(),
                    dayOfWeek = target.dayOfWeek,
                    slotId = target.slotId
                )

                CourseClipboardMode.Cut -> source.copy(
                    dayOfWeek = target.dayOfWeek,
                    slotId = target.slotId
                )
            }
            repository.upsertCourse(pastedCourse)
            actionManager.onPasteCompleted(target, pastedCourse.id)
        }
    }

    fun handleShortcut(key: Key, shiftPressed: Boolean): Boolean {
        when (key) {
            Key.C -> {
                val selectedCourse = actionManager.selectedCourseId?.let(courseMap::get) ?: return false
                actionManager.copy(selectedCourse)
                return true
            }

            Key.X -> {
                val selectedCourse = actionManager.selectedCourseId?.let(courseMap::get) ?: return false
                actionManager.cut(selectedCourse)
                return true
            }

            Key.V -> {
                val selectedTarget = actionManager.selectedCellTarget ?: return false
                if (!actionManager.canPasteInto(selectedTarget)) return false
                pasteInto(selectedTarget)
                return true
            }

            Key.Z -> {
                scope.launch {
                    if (shiftPressed) scheduleStore.redo() else scheduleStore.undo()
                }
                return true
            }

            Key.Y -> {
                scope.launch { scheduleStore.redo() }
                return true
            }

            else -> return false
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("课表") },
                actions = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) {
                            Text("返回")
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 10.dp, vertical = 6.dp)
                .focusRequester(focusRequester)
                .focusable()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown || !event.isCtrlPressed) {
                        false
                    } else {
                        handleShortcut(event.key, event.isShiftPressed)
                    }
                },
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            ScheduleOverviewHeader(
                todayCourseCount = todayCourseCount,
                currentMode = viewMode,
                currentWeekIndex = currentWeekIndex,
                currentParityLabel = currentParityLabel,
                helperSummary = actionManager.clipboard?.let { snapshot ->
                    val prefix = if (snapshot.mode == CourseClipboardMode.Copy) "已复制" else "已剪切"
                    "$prefix：${snapshot.course.name}"
                },
                onModeSelected = { viewMode = it }
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f, fill = true)
            ) {
                when {
                    courses.isEmpty() -> EmptyScheduleState(
                        title = "还没有课程安排",
                        message = "先添加课程和时间段。",
                        modifier = Modifier.fillMaxSize()
                    )

                    slots.isEmpty() -> EmptyScheduleState(
                        title = "还没有可用时间段模板",
                        message = "先添加时间段。",
                        modifier = Modifier.fillMaxSize()
                    )

                    viewMode == ScheduleViewMode.Week -> WeekScheduleView(
                        slots = slots,
                        courses = courses,
                        gridSizing = gridSizing,
                        actionManager = actionManager,
                        courseMap = courseMap,
                        presentationCourseMap = presentationCourseMap,
                        onShowCourseDetail = { detailCourse = it },
                        onPasteInto = ::pasteInto,
                        modifier = Modifier.fillMaxSize()
                    )

                    else -> ListScheduleView(
                        courses = courses,
                        modifier = Modifier.fillMaxSize()
                    )
                }
            }
        }

        detailCourse?.let { course ->
            CourseDetailDialog(
                item = course,
                onDismiss = { detailCourse = null }
            )
        }
    }
}

@Composable
private fun ScheduleOverviewHeader(
    todayCourseCount: Int,
    currentMode: ScheduleViewMode,
    currentWeekIndex: Int,
    currentParityLabel: String,
    helperSummary: String?,
    onModeSelected: (ScheduleViewMode) -> Unit
) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.26f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OverviewStatusRowItems(
                    items = listOf(
                        "今天 $todayCourseCount 门",
                        "第 $currentWeekIndex 周",
                        "$currentParityLabel 周"
                    )
                )
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Text(
                        text = "视图切换",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ScheduleModeChips(currentMode = currentMode, onModeSelected = onModeSelected)
                    }
                }
            }
            if (!helperSummary.isNullOrBlank()) {
                Text(
                    text = helperSummary,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun OverviewStatusRowItems(items: List<String>) {
    items.forEach { label ->
        Surface(
            shape = RoundedCornerShape(999.dp),
            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.88f)
        ) {
            Text(
                text = label,
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
private fun RowScope.ScheduleModeChips(
    currentMode: ScheduleViewMode,
    onModeSelected: (ScheduleViewMode) -> Unit
) {
    ScheduleViewMode.entries.forEach { mode ->
        FilterChip(
            selected = currentMode == mode,
            onClick = { onModeSelected(mode) },
            label = { Text(mode.label) },
            colors = FilterChipDefaults.filterChipColors(
                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
            )
        )
    }
}

@Composable
private fun WeekScheduleView(
    slots: List<TimeSlot>,
    courses: List<SchedulePresentationCourse>,
    gridSizing: GridSizingConfig,
    actionManager: ScheduleCourseActionManager,
    courseMap: Map<String, Course>,
    presentationCourseMap: Map<String, SchedulePresentationCourse>,
    onShowCourseDetail: (SchedulePresentationCourse) -> Unit,
    onPasteInto: (ScheduleCellTarget) -> Unit,
    modifier: Modifier = Modifier
) {
    val courseGrid = remember(courses) {
        courses.groupBy { it.slotId to it.dayOfWeek }
    }
    val unmatchedCourses = remember(courses, slots) {
        val slotIds = slots.map { it.id }.toSet()
        courses.filterNot { it.slotId in slotIds }
    }

    BoxWithConstraints(modifier = modifier) {
        val metrics = remember(maxWidth, gridSizing) {
            gridSizing.resolveMetrics((maxWidth.value - 16f).coerceAtLeast(0f))
        }

        Surface(
            shape = RoundedCornerShape(20.dp),
            color = MaterialTheme.colorScheme.surfaceContainerLowest,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                .fillMaxSize()
                .horizontalScroll(rememberScrollState())
                .verticalScroll(rememberScrollState())
                .padding(6.dp)
            ) {
                WeekHeaderRow(metrics = metrics)
                slots.forEach { slot ->
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TimeSlotLabelCell(slot = slot, metrics = metrics)
                        for (dayOfWeek in 1..7) {
                            val target = ScheduleCellTarget(dayOfWeek = dayOfWeek, slotId = slot.id)
                            WeekCourseCell(
                                items = courseGrid[slot.id to dayOfWeek].orEmpty(),
                                metrics = metrics,
                                target = target,
                                actionManager = actionManager,
                                courseMap = courseMap,
                                presentationCourseMap = presentationCourseMap,
                                onShowCourseDetail = onShowCourseDetail,
                                onPasteInto = onPasteInto
                            )
                        }
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                }
                if (unmatchedCourses.isNotEmpty()) {
                    MissingSlotNotice(count = unmatchedCourses.size, metrics = metrics)
                }
            }
        }
    }
}

@Composable
private fun WeekHeaderRow(metrics: ScheduleLayoutMetrics) {
    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Surface(
            shape = RoundedCornerShape(16.dp),
            color = MaterialTheme.colorScheme.surfaceVariant,
            modifier = Modifier.width(metrics.labelColumnWidthDp.dp)
        ) {
            Box(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Text("时间", style = MaterialTheme.typography.titleSmall)
            }
        }
        weekdayLabels.forEach { label ->
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceVariant,
                modifier = Modifier.width(metrics.cellWidthDp.dp)
            ) {
                Box(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(label, style = MaterialTheme.typography.titleSmall)
                }
            }
        }
    }
    Spacer(modifier = Modifier.height(4.dp))
}

@Composable
private fun TimeSlotLabelCell(slot: TimeSlot, metrics: ScheduleLayoutMetrics) {
    Surface(
        shape = RoundedCornerShape(18.dp),
        tonalElevation = 1.dp,
        color = MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(metrics.labelColumnWidthDp.dp)
            .height(metrics.cellHeightDp.dp)
            .border(
                width = 1.dp,
                color = MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(slot.label, style = MaterialTheme.typography.titleSmall)
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(slot.startTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(slot.endTime, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekCourseCell(
    items: List<SchedulePresentationCourse>,
    metrics: ScheduleLayoutMetrics,
    target: ScheduleCellTarget,
    actionManager: ScheduleCourseActionManager,
    courseMap: Map<String, Course>,
    presentationCourseMap: Map<String, SchedulePresentationCourse>,
    onShowCourseDetail: (SchedulePresentationCourse) -> Unit,
    onPasteInto: (ScheduleCellTarget) -> Unit
) {
    var menuExpanded by remember(target.dayOfWeek, target.slotId) { mutableStateOf(false) }
    val selectedCell = actionManager.selectedCellTarget == target
    val canPaste = actionManager.canPasteInto(target)

    Surface(
        shape = RoundedCornerShape(18.dp),
        color = if (selectedCell) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f) else MaterialTheme.colorScheme.surface,
        modifier = Modifier
            .width(metrics.cellWidthDp.dp)
            .height(metrics.cellHeightDp.dp)
            .border(
                width = if (selectedCell) 2.dp else 1.dp,
                color = if (selectedCell) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant,
                shape = RoundedCornerShape(18.dp)
            )
            .combinedClickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = {
                    actionManager.selectCell(target)
                    if (items.size == 1) {
                        actionManager.selectCourse(items.first().id, target)
                    } else if (items.isEmpty()) {
                        actionManager.clearSelectedCourse()
                    }
                },
                onLongClick = {
                    actionManager.selectCell(target)
                    if (items.isNotEmpty()) {
                        actionManager.selectCourse(items.first().id, target)
                    }
                    menuExpanded = true
                }
            )
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            if (items.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(10.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (canPaste) "长按粘贴" else "空白",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items.take(2).forEach { item ->
                        CourseBlock(
                            item = item,
                            compact = metrics.cellWidthDp < 128f,
                            cellWidthDp = metrics.cellWidthDp,
                            cellHeightDp = metrics.cellHeightDp,
                            isSelected = actionManager.selectedCourseId == item.id,
                            modifier = Modifier
                                .weight(1f, fill = true)
                                .combinedClickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                    onClick = {
                                        actionManager.selectCourse(item.id, target)
                                        actionManager.selectCell(target)
                                    },
                                    onLongClick = {
                                        actionManager.selectCourse(item.id, target)
                                        actionManager.selectCell(target)
                                        menuExpanded = true
                                    }
                                )
                        )
                    }
                    if (items.size > 2) {
                        Text(
                            text = "还有 ${items.size - 2} 门课程",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }
                }
            }

            DropdownMenu(
                expanded = menuExpanded,
                onDismissRequest = { menuExpanded = false }
            ) {
                val selectedCourse = actionManager.selectedCourseId?.let(courseMap::get)
                val selectedPresentationCourse = actionManager.selectedCourseId?.let(presentationCourseMap::get)
                if (selectedPresentationCourse != null && selectedCourse != null && selectedCourse.slotId == target.slotId && selectedCourse.dayOfWeek == target.dayOfWeek) {
                    DropdownMenuItem(
                        text = { Text("查看详情") },
                        onClick = {
                            onShowCourseDetail(selectedPresentationCourse)
                            menuExpanded = false
                        }
                    )
                }
                if (selectedCourse != null && selectedCourse.slotId == target.slotId && selectedCourse.dayOfWeek == target.dayOfWeek) {
                    DropdownMenuItem(
                        text = { Text("复制") },
                        onClick = {
                            actionManager.copy(selectedCourse)
                            menuExpanded = false
                        }
                    )
                    DropdownMenuItem(
                        text = { Text("剪切") },
                        onClick = {
                            actionManager.cut(selectedCourse)
                            menuExpanded = false
                        }
                    )
                }
                if (canPaste) {
                    DropdownMenuItem(
                        text = { Text(if (actionManager.clipboard?.mode == CourseClipboardMode.Cut) "粘贴到这里" else "复制到这里") },
                        onClick = {
                            onPasteInto(target)
                            menuExpanded = false
                        }
                    )
                }
            }
        }
    }
}

@Composable
private fun ListScheduleView(
    courses: List<SchedulePresentationCourse>,
    modifier: Modifier = Modifier
) {
    val groupedCourses = remember(courses) {
        (1..7).associateWith { dayOfWeek ->
            courses.filter { it.dayOfWeek == dayOfWeek }
                .sortedWith(
                    compareBy<SchedulePresentationCourse> { it.startTime.ifBlank { "99:99" } }
                        .thenBy { it.endTime.ifBlank { "99:99" } }
                        .thenBy { it.name }
                )
        }
    }

    Column(
        modifier = modifier.verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        groupedCourses.forEach { (dayOfWeek, dayCourses) ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = weekdayLabels.getOrElse(dayOfWeek - 1) { "周$dayOfWeek" },
                        style = MaterialTheme.typography.titleMedium
                    )
                    if (dayCourses.isEmpty()) {
                        Text(
                            text = "当天暂无课程",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    } else {
                        dayCourses.forEach { item ->
                            CourseListItem(item = item)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CourseListItem(item: SchedulePresentationCourse) {
    val backgroundColor = Color(item.backgroundColorArgb)
    val textColor = Color(ensureReadableTextColor(item.backgroundColorArgb, item.textColorArgb))
    val borderColor = Color(item.borderColorArgb)
    Surface(
        shape = RoundedCornerShape(20.dp),
        color = if (item.useThemeDefaults) {
            MaterialTheme.colorScheme.surfaceContainerLow
        } else {
            backgroundColor
        },
        modifier = Modifier
            .fillMaxWidth()
            .border(
                width = 1.dp,
                color = if (item.useThemeDefaults) MaterialTheme.colorScheme.outlineVariant else borderColor,
                shape = RoundedCornerShape(20.dp)
            )
    ) {
        Row(
            modifier = Modifier.padding(14.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
            verticalAlignment = Alignment.Top
        ) {
            CourseAccent(color = if (item.useThemeDefaults) MaterialTheme.colorScheme.primary else borderColor)
            Column(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = item.name,
                        style = MaterialTheme.typography.titleMedium,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        color = if (item.useThemeDefaults) MaterialTheme.colorScheme.onSurface else textColor
                    )
                    WeekTag(label = item.weekParity.shortLabel())
                }
                Text(
                    text = "${item.slotLabel} · ${item.startTime} - ${item.endTime}" + if (item.hasTimeOverride) " · 覆盖时间" else "",
                    style = MaterialTheme.typography.bodyMedium,
                    color = if (item.useThemeDefaults) MaterialTheme.colorScheme.onSurfaceVariant else textColor.copy(alpha = 0.92f)
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = "教师：${item.teacher.ifBlank { "未填" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.useThemeDefaults) MaterialTheme.colorScheme.onSurfaceVariant else textColor.copy(alpha = 0.86f),
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = "地点：${item.location.ifBlank { "未填" }}",
                        style = MaterialTheme.typography.bodySmall,
                        color = if (item.useThemeDefaults) MaterialTheme.colorScheme.onSurfaceVariant else textColor.copy(alpha = 0.86f),
                        modifier = Modifier.weight(1f)
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun CourseBlock(
    item: SchedulePresentationCourse,
    compact: Boolean,
    cellWidthDp: Float,
    cellHeightDp: Float,
    isSelected: Boolean,
    modifier: Modifier = Modifier
) {
    val backgroundColor = Color(item.backgroundColorArgb)
    val textColor = Color(ensureReadableTextColor(item.backgroundColorArgb, item.textColorArgb))
    val borderColor = Color(item.borderColorArgb)
    val contentColor = if (item.useThemeDefaults) {
        if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer
    } else {
        textColor
    }
    val secondaryContentColor = if (item.useThemeDefaults) {
        contentColor.copy(alpha = 0.82f)
    } else {
        textColor.copy(alpha = 0.86f)
    }
    val showSeparateTeacherAndLocation = !compact && cellHeightDp >= 128f
    val supportLine = listOfNotNull(
        item.teacher.takeIf { it.isNotBlank() },
        item.location.takeIf { it.isNotBlank() }
    ).joinToString(" · ")
    val useMarquee = !compact && cellWidthDp >= 138f
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = if (item.useThemeDefaults) {
            if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer
        } else {
            backgroundColor
        },
        modifier = modifier
            .fillMaxWidth()
            .border(1.dp, if (item.useThemeDefaults) MaterialTheme.colorScheme.outlineVariant else borderColor, RoundedCornerShape(14.dp))
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CourseAccent(color = if (item.useThemeDefaults) MaterialTheme.colorScheme.primary else borderColor)
                Text(
                    text = item.name,
                    style = if (compact) MaterialTheme.typography.labelLarge else MaterialTheme.typography.titleSmall,
                    color = contentColor,
                    maxLines = if (useMarquee) 1 else 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .weight(1f)
                        .then(if (useMarquee) Modifier.basicMarquee(iterations = Int.MAX_VALUE) else Modifier)
                )
            }
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                WeekTag(label = item.weekParity.shortLabel(), compact = true)
                if (item.hasTimeOverride) {
                    CourseMetaTag(label = "覆盖")
                }
            }
            if (showSeparateTeacherAndLocation) {
                item.teacher.takeIf { it.isNotBlank() }?.let { teacher ->
                    Text(
                        text = teacher,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                item.location.takeIf { it.isNotBlank() }?.let { location ->
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = secondaryContentColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            } else if (supportLine.isNotBlank()) {
                Text(
                    text = supportLine,
                    style = MaterialTheme.typography.bodySmall,
                    color = secondaryContentColor,
                    maxLines = if (compact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
private fun CourseMetaTag(label: String) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.78f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun CourseDetailDialog(
    item: SchedulePresentationCourse,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("关闭")
            }
        },
        title = { Text(item.name) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                CourseDetailRow(label = "单双周", value = item.weekParity.displayLabel())
                CourseDetailRow(label = "教师", value = item.teacher.ifBlank { "未填" })
                CourseDetailRow(label = "地点", value = item.location.ifBlank { "未填" })
                CourseDetailRow(label = "时间段", value = item.slotLabel)
                CourseDetailRow(
                    label = if (item.hasTimeOverride) "覆盖时间" else "上课时间",
                    value = "${item.startTime} - ${item.endTime}"
                )
            }
        }
    )
}

@Composable
private fun CourseDetailRow(label: String, value: String) {
    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurface
        )
    }
}

@Composable
private fun MissingSlotNotice(count: Int, metrics: ScheduleLayoutMetrics) {
    Surface(
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.tertiaryContainer,
        modifier = Modifier.width((metrics.labelColumnWidthDp + metrics.cellWidthDp * 7 + 24f).dp)
    ) {
        Text(
            text = "$count 门课程暂未显示，请补充时间段。",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onTertiaryContainer,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)
        )
    }
}

@Composable
private fun CourseAccent(color: Color = MaterialTheme.colorScheme.primary) {
    Box(
        modifier = Modifier
            .size(width = 10.dp, height = 28.dp)
            .clip(RoundedCornerShape(999.dp))
            .background(color)
    )
}

@Composable
private fun WeekTag(label: String, compact: Boolean = false) {
    Surface(
        shape = CircleShape,
        color = MaterialTheme.colorScheme.primary.copy(alpha = 0.12f)
    ) {
        Text(
            text = label,
            style = if (compact) MaterialTheme.typography.labelSmall else MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

@Composable
private fun EmptyScheduleState(
    title: String,
    message: String,
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier) {
        Card(
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow
            ),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(18.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = message,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
