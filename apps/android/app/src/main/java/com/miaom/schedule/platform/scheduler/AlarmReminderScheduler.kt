package com.miaom.schedule.platform.scheduler

import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build

class AlarmReminderScheduler(
    context: Context
) : ReminderScheduler {
    private val appContext = context.applicationContext
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val preferences = appContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    override fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val notificationManager = appContext.getSystemService(NotificationManager::class.java)
        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESCRIPTION
        }
        notificationManager.createNotificationChannel(channel)
    }

    override suspend fun schedule(reminder: ScheduledAppReminder) {
        val pendingIntent = buildAlarmPendingIntent(reminder)
        alarmManager.cancel(pendingIntent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (reminder.exact && canUseExactAlarms()) {
                alarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
            } else {
                alarmManager.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
            }
        } else if (reminder.exact && Build.VERSION.SDK_INT >= Build.VERSION_CODES.KITKAT) {
            alarmManager.setExact(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
        } else {
            alarmManager.set(AlarmManager.RTC_WAKEUP, reminder.triggerAtMillis, pendingIntent)
        }
    }

    override suspend fun cancel(taskId: String) {
        alarmManager.cancel(buildAlarmPendingIntent(taskId))
    }

    override suspend fun reconcileTrackedTasks(activeTaskIds: Set<String>) {
        val previousIds = preferences.getStringSet(KEY_TRACKED_TASK_IDS, emptySet()).orEmpty()
        previousIds.filterNot(activeTaskIds::contains).forEach { staleTaskId ->
            alarmManager.cancel(buildAlarmPendingIntent(staleTaskId))
        }
        preferences.edit().putStringSet(KEY_TRACKED_TASK_IDS, activeTaskIds).apply()
    }

    private fun buildAlarmPendingIntent(reminder: ScheduledAppReminder): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, reminder.taskId)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_ID, reminder.courseId)
            putExtra(ReminderAlarmReceiver.EXTRA_COURSE_NAME, reminder.courseName)
            putExtra(ReminderAlarmReceiver.EXTRA_LOCATION, reminder.location)
            putExtra(ReminderAlarmReceiver.EXTRA_TEACHER, reminder.teacher)
            putExtra(ReminderAlarmReceiver.EXTRA_SUMMARY_TEXT, reminder.summaryText)
            putExtra(ReminderAlarmReceiver.EXTRA_DETAIL_TEXT, reminder.detailText)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(reminder.taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun buildAlarmPendingIntent(taskId: String): PendingIntent {
        val intent = Intent(appContext, ReminderAlarmReceiver::class.java).apply {
            action = ReminderAlarmReceiver.ACTION_TRIGGER_REMINDER
            putExtra(ReminderAlarmReceiver.EXTRA_TASK_ID, taskId)
        }
        return PendingIntent.getBroadcast(
            appContext,
            requestCodeFor(taskId),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
    }

    private fun canUseExactAlarms(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
    }

    companion object {
        const val CHANNEL_ID = "schedule_reminders"
        const val CHANNEL_NAME = "课程提醒"
        const val CHANNEL_DESCRIPTION = "上课前的应用内提醒通知"

        private const val PREFS_NAME = "reminder_alarm_sync"
        private const val KEY_TRACKED_TASK_IDS = "tracked_task_ids"

        fun requestCodeFor(taskId: String): Int = 10_000 + taskId.hashCode()
    }
}
