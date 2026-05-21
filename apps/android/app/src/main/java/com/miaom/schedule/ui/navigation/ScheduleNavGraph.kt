package com.miaom.schedule.ui.navigation

import androidx.compose.runtime.Composable
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.miaom.schedule.ui.screen.CourseEditorScreen
import com.miaom.schedule.ui.screen.ScheduleHomeScreen
import com.miaom.schedule.ui.screen.ScheduleOverviewScreen
import com.miaom.schedule.ui.screen.TaskSettingsScreen
import com.miaom.schedule.ui.screen.TimeSlotEditorScreen

private object Routes {
    const val Home = "home"
    const val Overview = "overview"
    const val Courses = "courses"
    const val TimeSlots = "time-slots"
    const val Tasks = "tasks"
}

@Composable
fun ScheduleNavGraph() {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = Routes.Home) {
        composable(Routes.Home) {
            ScheduleHomeScreen(
                onOpenOverview = { navController.navigate(Routes.Overview) },
                onOpenCourses = { navController.navigate(Routes.Courses) },
                onOpenTimeSlots = { navController.navigate(Routes.TimeSlots) },
                onOpenTasks = { navController.navigate(Routes.Tasks) }
            )
        }
        composable(Routes.Overview) {
            ScheduleOverviewScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Courses) {
            CourseEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.TimeSlots) {
            TimeSlotEditorScreen(onBack = { navController.popBackStack() })
        }
        composable(Routes.Tasks) {
            TaskSettingsScreen(onBack = { navController.popBackStack() })
        }
    }
}
