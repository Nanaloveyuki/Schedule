package com.miaom.schedule.data.state

import com.miaom.schedule.domain.model.ScheduleDocument

enum class EditorCommandTarget {
    Course,
    TimeSlot,
    ReminderTask
}

enum class EditorCommandAction {
    Create,
    Update,
    Delete
}

interface EditorCommand {
    val target: EditorCommandTarget
    val action: EditorCommandAction
    val label: String

    fun apply(document: ScheduleDocument): ScheduleDocument

    fun undo(document: ScheduleDocument): ScheduleDocument
}

data class SnapshotEditorCommand(
    override val target: EditorCommandTarget,
    override val action: EditorCommandAction,
    override val label: String,
    val before: ScheduleDocument,
    val after: ScheduleDocument
) : EditorCommand {
    override fun apply(document: ScheduleDocument): ScheduleDocument = after

    override fun undo(document: ScheduleDocument): ScheduleDocument = before
}
