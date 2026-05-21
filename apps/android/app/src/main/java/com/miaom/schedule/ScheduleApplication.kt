package com.miaom.schedule

import android.app.Application
import com.miaom.schedule.di.AppContainer

class ScheduleApplication : Application() {
    lateinit var appContainer: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        appContainer = AppContainer(this)
    }
}

