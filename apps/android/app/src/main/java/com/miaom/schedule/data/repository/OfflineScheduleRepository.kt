package com.miaom.schedule.data.repository

import com.miaom.schedule.data.db.ScheduleDatabase
import com.miaom.schedule.data.mapper.toDomain
import com.miaom.schedule.data.mapper.toEntity
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

class OfflineScheduleRepository(
    private val database: ScheduleDatabase
) : ScheduleRepository {
    override fun observeCourses(): Flow<List<Course>> =
        database.courseDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeTimeSlots(): Flow<List<TimeSlot>> =
        database.timeSlotDao().observeAll().map { list -> list.map { it.toDomain() } }

    override fun observeReminderTasks(): Flow<List<ReminderTask>> =
        database.reminderTaskDao().observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun upsertCourse(course: Course) {
        database.courseDao().upsert(course.toEntity())
    }

    override suspend fun deleteCourse(courseId: String) {
        database.courseDao().deleteById(courseId)
    }

    override suspend fun upsertTimeSlot(slot: TimeSlot) {
        database.timeSlotDao().upsert(slot.toEntity())
    }

    override suspend fun deleteTimeSlot(slotId: String) {
        database.timeSlotDao().deleteById(slotId)
    }

    override suspend fun upsertReminderTask(task: ReminderTask) {
        database.reminderTaskDao().upsert(task.toEntity())
    }

    override suspend fun deleteReminderTask(taskId: String) {
        database.reminderTaskDao().deleteById(taskId)
    }
}

