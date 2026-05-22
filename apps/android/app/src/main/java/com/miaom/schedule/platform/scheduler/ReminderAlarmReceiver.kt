package com.miaom.schedule.platform.scheduler

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.miaom.schedule.MainActivity
import com.miaom.schedule.ScheduleApplication

class ReminderAlarmReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != ACTION_TRIGGER_REMINDER) return

        val taskId = intent.getStringExtra(EXTRA_TASK_ID).orEmpty()
        val courseName = intent.getStringExtra(EXTRA_COURSE_NAME).orEmpty()
        val summaryText = intent.getStringExtra(EXTRA_SUMMARY_TEXT).orEmpty().ifBlank { courseName }
        val detailText = intent.getStringExtra(EXTRA_DETAIL_TEXT).orEmpty()

        val contentIntent = PendingIntent.getActivity(
            context,
            AlarmReminderScheduler.requestCodeFor(taskId),
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, AlarmReminderScheduler.CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(summaryText)
            .setContentText(detailText)
            .setStyle(NotificationCompat.BigTextStyle().bigText(detailText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        NotificationManagerCompat.from(context).notify(
            AlarmReminderScheduler.requestCodeFor(taskId),
            notification
        )

        (context.applicationContext as? ScheduleApplication)
            ?.appContainer
            ?.reminderOrchestrator
            ?.requestSync()
    }

    companion object {
        const val ACTION_TRIGGER_REMINDER = "com.miaom.schedule.action.TRIGGER_REMINDER"
        const val EXTRA_TASK_ID = "extra_task_id"
        const val EXTRA_COURSE_ID = "extra_course_id"
        const val EXTRA_COURSE_NAME = "extra_course_name"
        const val EXTRA_LOCATION = "extra_location"
        const val EXTRA_TEACHER = "extra_teacher"
        const val EXTRA_SUMMARY_TEXT = "extra_summary_text"
        const val EXTRA_DETAIL_TEXT = "extra_detail_text"
    }
}
