package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class TimeSlotEditorUiState(
    val slots: List<TimeSlot> = emptyList()
)

class TimeSlotEditorViewModel(
    private val repository: ScheduleRepository
) : ViewModel() {
    val uiState: StateFlow<TimeSlotEditorUiState> = repository.observeTimeSlots()
        .map { slots -> TimeSlotEditorUiState(slots = slots) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimeSlotEditorUiState())

    fun saveTimeSlot(label: String, startTime: String, endTime: String) {
        viewModelScope.launch {
            repository.upsertTimeSlot(
                TimeSlot(
                    id = UUID.randomUUID().toString(),
                    label = label,
                    startTime = startTime,
                    endTime = endTime
                )
            )
        }
    }

    fun deleteTimeSlot(slotId: String) {
        viewModelScope.launch {
            repository.deleteTimeSlot(slotId)
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return TimeSlotEditorViewModel(repository) as T
                }
            }
    }
}
