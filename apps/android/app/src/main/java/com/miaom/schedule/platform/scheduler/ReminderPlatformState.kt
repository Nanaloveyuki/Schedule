package com.miaom.schedule.platform.scheduler

data class ReminderCapabilitySnapshot(
    val notificationPermissionGranted: Boolean = true,
    val calendarPermissionsGranted: Boolean = false,
    val exactAlarmPermissionGranted: Boolean = true,
    val writableCalendarAvailable: Boolean = false,
    val writableCalendarName: String? = null
)

data class ReminderTaskRuntimeState(
    val taskId: String,
    val active: Boolean,
    val summary: String
)

data class ReminderSyncSnapshot(
    val capabilities: ReminderCapabilitySnapshot = ReminderCapabilitySnapshot(),
    val taskStates: Map<String, ReminderTaskRuntimeState> = emptyMap()
)
