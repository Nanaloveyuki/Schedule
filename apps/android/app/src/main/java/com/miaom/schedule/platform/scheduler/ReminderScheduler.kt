package com.miaom.schedule.platform.scheduler

data class ScheduledAppReminder(
    val taskId: String,
    val courseId: String,
    val courseName: String,
    val location: String,
    val teacher: String,
    val triggerAtMillis: Long,
    val exact: Boolean,
    val summaryText: String,
    val detailText: String
)

interface ReminderScheduler {
    fun ensureNotificationChannel()

    suspend fun schedule(reminder: ScheduledAppReminder)

    suspend fun cancel(taskId: String)

    suspend fun reconcileTrackedTasks(activeTaskIds: Set<String>)
}
