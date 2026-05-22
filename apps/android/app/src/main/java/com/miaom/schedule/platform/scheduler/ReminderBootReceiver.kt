package com.miaom.schedule.platform.scheduler

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.miaom.schedule.ScheduleApplication

class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            Intent.ACTION_BOOT_COMPLETED,
            Intent.ACTION_MY_PACKAGE_REPLACED -> {
                (context.applicationContext as? ScheduleApplication)
                    ?.appContainer
                    ?.reminderOrchestrator
                    ?.requestSync()
            }
        }
    }
}
