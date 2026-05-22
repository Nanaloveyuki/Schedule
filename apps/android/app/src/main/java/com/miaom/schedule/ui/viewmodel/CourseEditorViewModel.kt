package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.GridSizingConfig
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.WeekConfig
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.ensureReadableTextColor
import com.miaom.schedule.domain.model.resolveReadableTextColor
import com.miaom.schedule.domain.model.toCourses
import com.miaom.schedule.domain.model.toTimeSlots
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CourseEditorUiState(
    val courses: List<Course> = emptyList(),
    val slots: List<TimeSlot> = emptyList(),
    val suggestedSlotId: String? = null,
    val weekConfig: WeekConfig = WeekConfig(),
    val gridSizing: GridSizingConfig = GridSizingConfig(),
    val undoState: UndoState = UndoState()
)

class CourseEditorViewModel(
    private val repository: ScheduleRepository,
    private val scheduleStore: ScheduleStore
) : ViewModel() {
    private val documentFlow = scheduleStore.document

    val uiState: StateFlow<CourseEditorUiState> = combine(
        documentFlow,
        scheduleStore.pendingCreatedTimeSlotId,
        scheduleStore.undoState
    ) { document, pendingCreatedTimeSlotId, undoState ->
        val slots = document.toTimeSlots()
        val suggestedSlotId = pendingCreatedTimeSlotId?.takeIf { createdId ->
            slots.any { it.id == createdId }
        }
        CourseEditorUiState(
            courses = document.toCourses(),
            slots = slots,
            suggestedSlotId = suggestedSlotId,
            weekConfig = document.weekConfig,
            gridSizing = document.themeConfig.gridSizing,
            undoState = undoState
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseEditorUiState())

    fun onSuggestedSlotConsumed(slotId: String) {
        scheduleStore.markCreatedTimeSlotHandled(slotId)
    }

    fun saveCourse(
        courseId: String?,
        name: String,
        teacher: String,
        location: String,
        dayOfWeek: Int,
        slotId: String,
        weekParity: WeekParity,
        overrideStartTime: String,
        overrideEndTime: String,
        useThemeDefaults: Boolean,
        backgroundColorArgb: Int,
        textColorArgb: Int,
        borderColorArgb: Int
    ) {
        viewModelScope.launch {
            val resolvedCourseId = courseId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString()
            val resolvedTextColor = if (useThemeDefaults) {
                resolveReadableTextColor(backgroundColorArgb)
            } else {
                ensureReadableTextColor(backgroundColorArgb, textColorArgb)
            }
            repository.upsertCourse(
                Course(
                    id = resolvedCourseId,
                    name = name,
                    teacher = teacher,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    slotId = slotId,
                    weekParity = weekParity,
                    overrideStartTime = overrideStartTime.trim(),
                    overrideEndTime = overrideEndTime.trim(),
                    useThemeDefaults = useThemeDefaults,
                    backgroundColorArgb = backgroundColorArgb,
                    textColorArgb = resolvedTextColor,
                    borderColorArgb = borderColorArgb
                )
            )
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
        }
    }

    fun undo() {
        viewModelScope.launch {
            scheduleStore.undo()
        }
    }

    fun redo() {
        viewModelScope.launch {
            scheduleStore.redo()
        }
    }

    fun updateWeek1MondayDate(date: String) {
        viewModelScope.launch {
            scheduleStore.edit { document ->
                document.copy(
                    weekConfig = document.weekConfig.copy(week1MondayDate = date.trim())
                )
            }
        }
    }

    companion object {
        fun factory(
            repository: ScheduleRepository,
            scheduleStore: ScheduleStore
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CourseEditorViewModel(repository, scheduleStore) as T
                }
            }
    }
}
