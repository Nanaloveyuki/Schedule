package com.miaom.schedule.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "reminder_tasks")
data class ReminderTaskEntity(
    @PrimaryKey val id: String,
    val courseId: String,
    val minutesBefore: Int,
    val channel: String,
    val exact: Boolean,
    val enabled: Boolean
)
