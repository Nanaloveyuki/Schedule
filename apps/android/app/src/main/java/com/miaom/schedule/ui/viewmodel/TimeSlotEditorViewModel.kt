package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.toTimeSlots
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimeSlotEditorUiState(
    val slots: List<TimeSlot> = emptyList(),
    val undoState: UndoState = UndoState()
)

class TimeSlotEditorViewModel(
    private val repository: ScheduleRepository,
    private val scheduleStore: ScheduleStore
) : ViewModel() {
    val uiState: StateFlow<TimeSlotEditorUiState> = combine(
        scheduleStore.document.map { it.toTimeSlots() },
        scheduleStore.undoState
    ) { slots, undoState ->
        TimeSlotEditorUiState(slots = slots, undoState = undoState)
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeSlotEditorUiState())

    fun saveTimeSlot(slotId: String?, label: String, startTime: String, endTime: String) {
        viewModelScope.launch {
            val existingId = slotId?.takeIf { it.isNotBlank() }
            if (existingId == null) {
                scheduleStore.createTimeSlotAndReturnId(
                    label = label,
                    startTime = startTime,
                    endTime = endTime
                )
            } else {
                repository.upsertTimeSlot(
                    TimeSlot(
                        id = existingId,
                        label = label,
                        startTime = startTime,
                        endTime = endTime
                    )
                )
            }
        }
    }

    fun deleteTimeSlot(slotId: String) {
        viewModelScope.launch {
            repository.deleteTimeSlot(slotId)
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

    companion object {
        fun factory(
            repository: ScheduleRepository,
            scheduleStore: ScheduleStore
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimeSlotEditorViewModel(repository, scheduleStore) as T
                }
            }
    }
}
