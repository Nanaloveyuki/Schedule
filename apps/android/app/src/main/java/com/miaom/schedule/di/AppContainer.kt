package com.miaom.schedule.di

import android.content.Context
import com.miaom.schedule.data.repository.PreferencesScheduleRepository
import com.miaom.schedule.data.repository.ScheduleRepository

class AppContainer(context: Context) {
    val scheduleRepository: ScheduleRepository = PreferencesScheduleRepository(context)
}
