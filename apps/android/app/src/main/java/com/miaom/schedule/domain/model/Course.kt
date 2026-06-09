package com.miaom.schedule.domain.model

data class Course(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val slotId: String,
    val weekParity: WeekParity = WeekParity.Every,
    val weekNumbers: List<Int> = emptyList(),
    val overrideStartTime: String = "",
    val overrideEndTime: String = "",
    val useThemeDefaults: Boolean = true,
    val backgroundColorArgb: Int = 0xFFDBEAFE.toInt(),
    val textColorArgb: Int = 0xFF102A43.toInt(),
    val borderColorArgb: Int = 0xFF6B8BB3.toInt(),
    val effectiveStartTime: String = overrideStartTime,
    val effectiveEndTime: String = overrideEndTime
)
