package com.miaom.schedule.data.mapper

import com.miaom.schedule.data.db.entity.CourseEntity
import com.miaom.schedule.data.db.entity.ReminderTaskEntity
import com.miaom.schedule.data.db.entity.TimeSlotEntity
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.TimeSlot

fun CourseEntity.toDomain(): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    slotId = slotId
)

fun Course.toEntity(): CourseEntity = CourseEntity(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    slotId = slotId
)

fun TimeSlotEntity.toDomain(): TimeSlot = TimeSlot(
    id = id,
    label = label,
    startTime = startTime,
    endTime = endTime
)

fun TimeSlot.toEntity(): TimeSlotEntity = TimeSlotEntity(
    id = id,
    label = label,
    startTime = startTime,
    endTime = endTime
)

fun ReminderTaskEntity.toDomain(): ReminderTask = ReminderTask(
    id = id,
    courseId = courseId,
    minutesBefore = minutesBefore,
    channel = ReminderChannel.valueOf(channel),
    exact = exact,
    enabled = enabled
)

fun ReminderTask.toEntity(): ReminderTaskEntity = ReminderTaskEntity(
    id = id,
    courseId = courseId,
    minutesBefore = minutesBefore,
    channel = channel.name,
    exact = exact,
    enabled = enabled
)
