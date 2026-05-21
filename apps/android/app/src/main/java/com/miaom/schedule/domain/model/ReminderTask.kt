package com.miaom.schedule.domain.model

data class ReminderTask(
    val id: String,
    val courseId: String,
    val minutesBefore: Int,
    val channel: ReminderChannel,
    val exact: Boolean,
    val enabled: Boolean
)
