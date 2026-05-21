package com.miaom.schedule.domain.model

data class Course(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val slotId: String
)

