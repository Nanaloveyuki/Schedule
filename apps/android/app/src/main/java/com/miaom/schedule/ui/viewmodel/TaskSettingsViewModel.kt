package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderTask
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TaskSettingsUiState(
    val tasks: List<ReminderTask> = emptyList()
)

class TaskSettingsViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {
    val uiState: StateFlow<TaskSettingsUiState> = repository.observeReminderTasks()
        .map { tasks -> TaskSettingsUiState(tasks = tasks) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskSettingsUiState())

    fun saveTask(
        courseId: String,
        minutesBefore: Int,
        channel: ReminderChannel,
        exact: Boolean,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            repository.upsertReminderTask(
                ReminderTask(
                    id = UUID.randomUUID().toString(),
                    courseId = courseId,
                    minutesBefore = minutesBefore,
                    channel = channel,
                    exact = exact,
                    enabled = enabled
                )
            )
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteReminderTask(taskId)
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskSettingsViewModel(repository) as T
                }
            }
    }
}
