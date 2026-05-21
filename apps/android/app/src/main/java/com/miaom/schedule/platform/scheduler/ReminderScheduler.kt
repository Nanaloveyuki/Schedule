package com.miaom.schedule.platform.scheduler

import com.miaom.schedule.domain.model.ReminderTask

interface ReminderScheduler {
    suspend fun schedule(task: ReminderTask)
    suspend fun cancel(taskId: String)
}

