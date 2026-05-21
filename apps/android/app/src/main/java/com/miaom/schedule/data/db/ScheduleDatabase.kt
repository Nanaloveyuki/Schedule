package com.miaom.schedule.data.db

import androidx.room.Database
import androidx.room.RoomDatabase
import com.miaom.schedule.data.db.dao.CourseDao
import com.miaom.schedule.data.db.dao.ReminderTaskDao
import com.miaom.schedule.data.db.dao.TimeSlotDao
import com.miaom.schedule.data.db.entity.CourseEntity
import com.miaom.schedule.data.db.entity.ReminderTaskEntity
import com.miaom.schedule.data.db.entity.TimeSlotEntity

@Database(
    entities = [CourseEntity::class, TimeSlotEntity::class, ReminderTaskEntity::class],
    version = 2,
    exportSchema = false
)
abstract class ScheduleDatabase : RoomDatabase() {
    abstract fun courseDao(): CourseDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun reminderTaskDao(): ReminderTaskDao
}
