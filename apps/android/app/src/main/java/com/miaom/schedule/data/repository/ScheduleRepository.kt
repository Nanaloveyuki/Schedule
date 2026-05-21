package com.miaom.schedule.data.repository

import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow

interface ScheduleRepository {
    fun observeCourses(): Flow<List<Course>>
    fun observeTimeSlots(): Flow<List<TimeSlot>>
    fun observeReminderTasks(): Flow<List<ReminderTask>>

    suspend fun upsertCourse(course: Course)
    suspend fun deleteCourse(courseId: String)

    suspend fun upsertTimeSlot(slot: TimeSlot)
    suspend fun deleteTimeSlot(slotId: String)

    suspend fun upsertReminderTask(task: ReminderTask)
    suspend fun deleteReminderTask(taskId: String)
}
