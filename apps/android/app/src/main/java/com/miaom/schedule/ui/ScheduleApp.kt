package com.miaom.schedule.ui

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.ui.navigation.ScheduleNavGraph
import com.miaom.schedule.ui.theme.ScheduleThemeBackground
import com.miaom.schedule.ui.theme.ScheduleTheme

@Composable
fun ScheduleApp() {
    val scheduleStore = (LocalContext.current.applicationContext as ScheduleApplication)
        .appContainer
        .scheduleStore
    val document = scheduleStore.document.collectAsStateWithLifecycle().value

    ScheduleTheme(themeConfig = document.themeConfig) {
        Box(modifier = Modifier.fillMaxSize()) {
            ScheduleThemeBackground(
                themeConfig = document.themeConfig,
                modifier = Modifier.fillMaxSize()
            )
            Surface(
                modifier = Modifier.fillMaxSize(),
                color = MaterialTheme.colorScheme.background
            ) {
                ScheduleNavGraph()
            }
        }
    }
}
