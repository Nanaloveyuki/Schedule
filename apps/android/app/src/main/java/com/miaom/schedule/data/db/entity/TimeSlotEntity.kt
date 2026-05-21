package com.miaom.schedule.data.db.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "time_slots")
data class TimeSlotEntity(
    @PrimaryKey val id: String,
    val label: String,
    val startTime: String,
    val endTime: String
)

