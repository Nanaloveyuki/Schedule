package com.miaom.schedule.domain.model

data class TimeSlot(
    val id: String,
    val label: String,
    val startTime: String,
    val endTime: String
)

