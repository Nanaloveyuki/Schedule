package com.miaom.schedule.di

import android.content.Context
import com.miaom.schedule.data.repository.PreferencesScheduleRepository
import com.miaom.schedule.data.repository.ScheduleRepository
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.platform.scheduler.ReminderOrchestrator

class AppContainer(context: Context) {
    private val preferencesScheduleRepository = PreferencesScheduleRepository(context)

    val scheduleStore: ScheduleStore = preferencesScheduleRepository
    val scheduleRepository: ScheduleRepository = preferencesScheduleRepository
    val reminderOrchestrator: ReminderOrchestrator = ReminderOrchestrator(
        context = context,
        scheduleStore = scheduleStore
    )
}
