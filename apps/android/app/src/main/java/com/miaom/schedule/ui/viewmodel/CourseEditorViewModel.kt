package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.domain.model.Course
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CourseEditorUiState(
    val courses: List<Course> = emptyList()
)

class CourseEditorViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {
    val uiState: StateFlow<CourseEditorUiState> = repository.observeCourses()
        .map { courses -> CourseEditorUiState(courses = courses) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CourseEditorUiState())

    fun saveCourse(
        name: String,
        teacher: String,
        location: String,
        dayOfWeek: Int,
        slotId: String
    ) {
        viewModelScope.launch {
            repository.upsertCourse(
                Course(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    teacher = teacher,
                    location = location,
                    dayOfWeek = dayOfWeek,
                    slotId = slotId
                )
            )
        }
    }

    fun deleteCourse(courseId: String) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return CourseEditorViewModel(repository) as T
                }
            }
    }
}

