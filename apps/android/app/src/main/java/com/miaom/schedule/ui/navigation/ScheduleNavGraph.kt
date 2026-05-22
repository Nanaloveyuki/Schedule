package com.miaom.schedule.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Style
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.miaom.schedule.ui.screen.CourseEditorScreen
import com.miaom.schedule.ui.screen.EditSectionScreen
import com.miaom.schedule.ui.screen.PersonalizationScreen
import com.miaom.schedule.ui.screen.PresetsScreen
import com.miaom.schedule.ui.screen.ScheduleOverviewScreen
import com.miaom.schedule.ui.screen.SettingsScreen
import com.miaom.schedule.ui.screen.TaskSettingsScreen
import com.miaom.schedule.ui.screen.TimeSlotEditorScreen

private object Routes {
    const val Schedule = "schedule"
    const val Overview = "overview"
    const val Edit = "edit"
    const val Courses = "courses"
    const val TimeSlots = "time-slots"
    const val Tasks = "tasks"
    const val Presets = "presets"
    const val Personalization = "personalization"
    const val Settings = "settings"
}

@Composable
fun ScheduleNavGraph() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val selectedTopLevelRoute = topLevelRouteFor(backStackEntry?.destination?.route)

    val topLevelDestinations = listOf(
        AppShellDestination(route = Routes.Schedule, label = "课表", icon = Icons.Filled.DateRange),
        AppShellDestination(route = Routes.Edit, label = "编辑", icon = Icons.Filled.EditNote),
        AppShellDestination(route = Routes.Presets, label = "预设", icon = Icons.Filled.Style),
        AppShellDestination(route = Routes.Personalization, label = "个性化", icon = Icons.Filled.Palette),
        AppShellDestination(route = Routes.Settings, label = "设置", icon = Icons.Filled.Settings)
    )

    ScheduleAdaptiveShell(
        destinations = topLevelDestinations,
        selectedRoute = selectedTopLevelRoute,
        onNavigateToTopLevel = { route ->
            navController.navigate(route) {
                popUpTo(navController.graph.findStartDestination().id) {
                    saveState = true
                }
                launchSingleTop = true
                restoreState = true
            }
        }
    ) { modifier ->
        NavHost(
            navController = navController,
            startDestination = Routes.Schedule,
            modifier = modifier
        ) {
            composable(Routes.Schedule) {
                ScheduleOverviewScreen()
            }
            composable(Routes.Overview) {
                ScheduleOverviewScreen(onBack = { navController.popBackStack() })
            }
            composable(Routes.Edit) {
                EditSectionScreen(
                    onOpenCourses = { navController.navigate(Routes.Courses) },
                    onOpenTimeSlots = { navController.navigate(Routes.TimeSlots) },
                    onOpenTasks = { navController.navigate(Routes.Tasks) }
                )
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
            composable(Routes.Presets) {
                PresetsScreen()
            }
            composable(Routes.Personalization) {
                PersonalizationScreen()
            }
            composable(Routes.Settings) {
                SettingsScreen()
            }
        }
    }
}

private fun topLevelRouteFor(route: String?): String {
    return when (route) {
        Routes.Schedule,
        Routes.Overview -> Routes.Schedule

        Routes.Edit,
        Routes.Courses,
        Routes.TimeSlots,
        Routes.Tasks -> Routes.Edit

        Routes.Presets -> Routes.Presets
        Routes.Personalization -> Routes.Personalization
        Routes.Settings -> Routes.Settings
        else -> Routes.Schedule
    }
}
