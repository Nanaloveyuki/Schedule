package com.miaom.schedule.data.repository

import com.miaom.schedule.data.state.EditorCommand
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.ScheduleDocument
import kotlinx.coroutines.flow.StateFlow

interface ScheduleStore {
    val document: StateFlow<ScheduleDocument>
    val pendingCreatedTimeSlotId: StateFlow<String?>
    val undoState: StateFlow<UndoState>

    suspend fun edit(transform: (ScheduleDocument) -> ScheduleDocument): ScheduleDocument
    suspend fun apply(command: EditorCommand): ScheduleDocument
    suspend fun undo(): ScheduleDocument
    suspend fun redo(): ScheduleDocument
    fun clearHistory()

    suspend fun createTimeSlotAndReturnId(
        label: String,
        startTime: String,
        endTime: String
    ): String

    fun markCreatedTimeSlotHandled(timeSlotId: String)

    suspend fun replace(document: ScheduleDocument): ScheduleDocument = edit { document }.also { clearHistory() }
}
