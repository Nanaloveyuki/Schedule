package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.toReminderTasks
import com.miaom.schedule.platform.scheduler.ReminderCapabilitySnapshot
import com.miaom.schedule.platform.scheduler.ReminderOrchestrator
import com.miaom.schedule.platform.scheduler.ReminderTaskRuntimeState
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TaskSettingsUiState(
    val tasks: List<ReminderTask> = emptyList(),
    val undoState: UndoState = UndoState(),
    val capabilitySnapshot: ReminderCapabilitySnapshot = ReminderCapabilitySnapshot(),
    val runtimeTaskStates: Map<String, ReminderTaskRuntimeState> = emptyMap()
)

class TaskSettingsViewModel(
    private val repository: ScheduleRepository,
    private val scheduleStore: ScheduleStore,
    private val reminderOrchestrator: ReminderOrchestrator
) : ViewModel() {
    private val reminderTasksFlow = scheduleStore.document.map { it.toReminderTasks() }

    val uiState: StateFlow<TaskSettingsUiState> = combine(
        reminderTasksFlow,
        scheduleStore.undoState,
        reminderOrchestrator.syncState
    ) { tasks, undoState, syncState ->
        TaskSettingsUiState(
            tasks = tasks,
            undoState = undoState,
            capabilitySnapshot = syncState.capabilities,
            runtimeTaskStates = syncState.taskStates
        )
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TaskSettingsUiState())

    fun saveTask(
        taskId: String?,
        courseId: String,
        minutesBefore: Int,
        channel: ReminderChannel,
        exact: Boolean,
        enabled: Boolean
    ) {
        viewModelScope.launch {
            repository.upsertReminderTask(
                ReminderTask(
                    id = taskId?.takeIf { it.isNotBlank() } ?: UUID.randomUUID().toString(),
                    courseId = courseId,
                    minutesBefore = minutesBefore,
                    channel = channel,
                    exact = exact,
                    enabled = enabled
                )
            )
            reminderOrchestrator.requestSync()
        }
    }

    fun deleteTask(taskId: String) {
        viewModelScope.launch {
            repository.deleteReminderTask(taskId)
            reminderOrchestrator.requestSync()
        }
    }

    fun undo() {
        viewModelScope.launch {
            scheduleStore.undo()
            reminderOrchestrator.requestSync()
        }
    }

    fun redo() {
        viewModelScope.launch {
            scheduleStore.redo()
            reminderOrchestrator.requestSync()
        }
    }

    fun refreshReminderState() {
        reminderOrchestrator.requestSync()
    }

    companion object {
        fun factory(
            repository: ScheduleRepository,
            scheduleStore: ScheduleStore,
            reminderOrchestrator: ReminderOrchestrator
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TaskSettingsViewModel(repository, scheduleStore, reminderOrchestrator) as T
                }
            }
    }
}
