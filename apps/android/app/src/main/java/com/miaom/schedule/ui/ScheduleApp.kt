package com.miaom.schedule.ui

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.miaom.schedule.ui.navigation.ScheduleNavGraph

@Composable
fun ScheduleApp() {
    Surface(modifier = Modifier.fillMaxSize()) {
        ScheduleNavGraph()
    }
}

