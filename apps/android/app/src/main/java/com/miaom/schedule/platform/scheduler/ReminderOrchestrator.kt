package com.miaom.schedule.platform.scheduler

import android.Manifest
import android.app.AlarmManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.domain.model.CourseEntry
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderRule
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.TimeSlotTemplate
import com.miaom.schedule.domain.model.matchesWeekRule
import com.miaom.schedule.domain.model.toReminderTask
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

class ReminderOrchestrator(
    context: Context,
    private val scheduleStore: ScheduleStore,
    private val scheduler: ReminderScheduler = AlarmReminderScheduler(context),
    private val calendarStore: CalendarReminderStore = CalendarReminderStore(context)
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val alarmManager = appContext.getSystemService(AlarmManager::class.java)
    private val _syncState = MutableStateFlow(ReminderSyncSnapshot())

    val syncState: StateFlow<ReminderSyncSnapshot> = _syncState.asStateFlow()

    @Volatile
    private var started = false

    fun start() {
        if (started) return
        started = true
        scheduler.ensureNotificationChannel()
        scope.launch {
            scheduleStore.document.collect { document ->
                sync(document)
            }
        }
    }

    fun requestSync() {
        scope.launch {
            sync(scheduleStore.document.value)
        }
    }

    private suspend fun sync(document: ScheduleDocument) {
        scheduler.ensureNotificationChannel()
        val capabilities = snapshotCapabilities()
        val taskStates = linkedMapOf<String, ReminderTaskRuntimeState>()
        val activeNotificationTaskIds = mutableSetOf<String>()
        val activeCalendarTaskIds = mutableSetOf<String>()
        val coursesById = document.courseEntries.associateBy { it.id }
        val timeSlotsById = document.timeSlotTemplates.associateBy { it.id }
        val now = Instant.now().atZone(ZoneId.systemDefault())

        document.reminderRules.forEach { rule ->
            val courseEntry = coursesById[rule.courseEntryId]
            val timeSlot = courseEntry?.let { timeSlotsById[it.timeSlotTemplateId] }

            if (!rule.enabled) {
                scheduler.cancel(rule.id)
                if (capabilities.calendarPermissionsGranted) {
                    calendarStore.deleteTask(rule.id)
                }
                taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "已关闭，不参与调度")
                return@forEach
            }

            if (courseEntry == null) {
                scheduler.cancel(rule.id)
                if (capabilities.calendarPermissionsGranted) {
                    calendarStore.deleteTask(rule.id)
                }
                taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "关联课程已不存在，请重新选择课程")
                return@forEach
            }

            val effectiveStart = courseEntry.effectiveStartTime(timeSlot)
            val effectiveEnd = courseEntry.effectiveEndTime(timeSlot)
            if (effectiveStart.isBlank() || effectiveEnd.isBlank()) {
                scheduler.cancel(rule.id)
                if (capabilities.calendarPermissionsGranted) {
                    calendarStore.deleteTask(rule.id)
                }
                taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "课程时间不完整，暂时无法安排提醒")
                return@forEach
            }

            when (rule.channel) {
                ReminderChannel.InAppNotification -> {
                    if (capabilities.calendarPermissionsGranted) {
                        calendarStore.deleteTask(rule.id)
                    }
                    if (!capabilities.notificationPermissionGranted) {
                        scheduler.cancel(rule.id)
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "等待通知权限后再安排提醒")
                        return@forEach
                    }
                    if (rule.exact && !capabilities.exactAlarmPermissionGranted) {
                        scheduler.cancel(rule.id)
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "需要允许精确定时，才能按精确时间提醒")
                        return@forEach
                    }

                    val occurrence = nextNotificationOccurrence(document, rule, courseEntry, timeSlot, now.toLocalDate(), now.toInstant().toEpochMilli())
                    if (occurrence == null) {
                        scheduler.cancel(rule.id)
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "近期没有可触发的上课时间")
                    } else {
                        scheduler.schedule(
                            ScheduledAppReminder(
                                taskId = rule.id,
                                courseId = rule.courseEntryId,
                                courseName = courseEntry.name,
                                location = courseEntry.location,
                                teacher = courseEntry.teacher,
                                triggerAtMillis = occurrence.triggerAtMillis,
                                exact = rule.exact,
                                summaryText = courseEntry.name,
                                detailText = occurrence.notificationDetail
                            )
                        )
                        activeNotificationTaskIds += rule.id
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, true, "下次将于 ${formatEpochMillis(occurrence.triggerAtMillis)} 发送应用内通知")
                    }
                }

                ReminderChannel.SystemCalendar -> {
                    scheduler.cancel(rule.id)
                    if (!capabilities.calendarPermissionsGranted) {
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "等待日历授权后再写入系统日历")
                        return@forEach
                    }
                    if (!capabilities.writableCalendarAvailable) {
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "设备上没有可写的系统日历")
                        return@forEach
                    }

                    val occurrences = upcomingCalendarOccurrences(document, rule, courseEntry, timeSlot, now.toLocalDate())
                    if (occurrences.isEmpty()) {
                        calendarStore.deleteTask(rule.id)
                        taskStates[rule.id] = ReminderTaskRuntimeState(rule.id, false, "近期没有可写入日历的上课时间")
                    } else {
                        val syncedCount = calendarStore.syncTask(rule.toReminderTask(), occurrences)
                        activeCalendarTaskIds += rule.id
                        taskStates[rule.id] = ReminderTaskRuntimeState(
                            rule.id,
                            syncedCount > 0,
                            if (syncedCount > 0) "已写入系统日历，当前维护 ${syncedCount} 条后续课程事件" else "日历写入未完成，请检查系统日历可用性"
                        )
                    }
                }
            }
        }

        scheduler.reconcileTrackedTasks(activeNotificationTaskIds)
        if (capabilities.calendarPermissionsGranted) {
            calendarStore.reconcileActiveTasks(activeCalendarTaskIds)
        }

        _syncState.value = ReminderSyncSnapshot(capabilities = capabilities, taskStates = taskStates)
    }

    private fun snapshotCapabilities(): ReminderCapabilitySnapshot {
        val notificationPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED &&
                NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        } else {
            NotificationManagerCompat.from(appContext).areNotificationsEnabled()
        }
        val calendarPermissionsGranted = ContextCompat.checkSelfPermission(appContext, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.WRITE_CALENDAR) == PackageManager.PERMISSION_GRANTED
        val exactAlarmPermissionGranted = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            alarmManager.canScheduleExactAlarms()
        } else {
            true
        }
        val writableCalendarName = if (calendarPermissionsGranted) calendarStore.resolveWritableCalendarName() else null
        return ReminderCapabilitySnapshot(
            notificationPermissionGranted = notificationPermissionGranted,
            calendarPermissionsGranted = calendarPermissionsGranted,
            exactAlarmPermissionGranted = exactAlarmPermissionGranted,
            writableCalendarAvailable = writableCalendarName != null,
            writableCalendarName = writableCalendarName
        )
    }

    private fun nextNotificationOccurrence(
        document: ScheduleDocument,
        rule: ReminderRule,
        courseEntry: CourseEntry,
        timeSlot: TimeSlotTemplate?,
        fromDate: LocalDate,
        nowMillis: Long
    ): PlannedNotificationOccurrence? {
        return computeOccurrences(document, courseEntry, timeSlot, fromDate, 1, nowMillis, rule.minutesBefore)
            .firstOrNull()
            ?.let { occurrence ->
                PlannedNotificationOccurrence(
                    triggerAtMillis = occurrence.triggerAtMillis,
                    notificationDetail = "${occurrence.weekdayLabel} ${occurrence.classTimeLabel} · ${courseEntry.location.ifBlank { courseEntry.teacher.ifBlank { "课程提醒" } }}"
                )
            }
    }

    private fun upcomingCalendarOccurrences(
        document: ScheduleDocument,
        rule: ReminderRule,
        courseEntry: CourseEntry,
        timeSlot: TimeSlotTemplate?,
        fromDate: LocalDate
    ): List<CalendarCourseOccurrence> {
        return computeOccurrences(document, courseEntry, timeSlot, fromDate, 12, null, rule.minutesBefore)
            .map { occurrence ->
                CalendarCourseOccurrence(
                    title = courseEntry.name,
                    description = buildString {
                        append("课表提醒")
                        if (courseEntry.teacher.isNotBlank()) append(" · 教师 ${courseEntry.teacher}")
                        append(" · ${occurrence.weekdayLabel} ${occurrence.classTimeLabel}")
                    },
                    location = courseEntry.location,
                    startAtMillis = occurrence.classStartMillis,
                    endAtMillis = occurrence.classEndMillis,
                    reminderMinutesBefore = rule.minutesBefore
                )
            }
    }

    private fun computeOccurrences(
        document: ScheduleDocument,
        courseEntry: CourseEntry,
        timeSlot: TimeSlotTemplate?,
        fromDate: LocalDate,
        maxOccurrences: Int,
        requireTriggerAfterMillis: Long?,
        reminderMinutesBefore: Int
    ): List<PlannedCourseOccurrence> {
        val startTime = parseLocalTime(courseEntry.effectiveStartTime(timeSlot)) ?: return emptyList()
        val endTime = parseLocalTime(courseEntry.effectiveEndTime(timeSlot)) ?: return emptyList()
        val zoneId = ZoneId.systemDefault()
        val results = mutableListOf<PlannedCourseOccurrence>()
        var cursor = fromDate

        repeat(240) {
            if (cursor.dayOfWeek == dayOfWeekFor(courseEntry.dayOfWeek) && matchesWeekParity(document, courseEntry, cursor)) {
                val classStartAt = LocalDateTime.of(cursor, startTime).atZone(zoneId)
                val classEndAt = LocalDateTime.of(cursor, endTime).atZone(zoneId)
                val triggerAt = classStartAt.minusMinutes(reminderMinutesBefore.toLong())
                val triggerAtMillis = triggerAt.toInstant().toEpochMilli()
                if (requireTriggerAfterMillis == null || triggerAtMillis > requireTriggerAfterMillis) {
                    results += PlannedCourseOccurrence(
                        classStartMillis = classStartAt.toInstant().toEpochMilli(),
                        classEndMillis = classEndAt.toInstant().toEpochMilli(),
                        triggerAtMillis = triggerAtMillis,
                        weekdayLabel = weekdayLabel(courseEntry.dayOfWeek),
                        classTimeLabel = "${formatLocalTime(startTime)}-${formatLocalTime(endTime)}"
                    )
                    if (results.size >= maxOccurrences) return results
                }
            }
            cursor = cursor.plusDays(1)
        }
        return results
    }

    private fun matchesWeekParity(document: ScheduleDocument, courseEntry: CourseEntry, date: LocalDate): Boolean {
        return matchesWeekRule(
            weekParity = courseEntry.weekParity,
            weekNumbers = courseEntry.weekNumbers,
            weekIndex = document.weekConfig.weekIndexFor(date)
        )
    }

    private fun parseLocalTime(value: String): LocalTime? = runCatching { LocalTime.parse(value) }.getOrNull()

    private fun dayOfWeekFor(dayOfWeek: Int): DayOfWeek = DayOfWeek.of(dayOfWeek.coerceIn(1, 7))

    private fun weekdayLabel(dayOfWeek: Int): String = when (dayOfWeek) {
        1 -> "周一"
        2 -> "周二"
        3 -> "周三"
        4 -> "周四"
        5 -> "周五"
        6 -> "周六"
        else -> "周日"
    }

    private fun formatLocalTime(time: LocalTime): String = time.format(TIME_FORMATTER)

    private fun formatEpochMillis(epochMillis: Long): String =
        Instant.ofEpochMilli(epochMillis).atZone(ZoneId.systemDefault()).format(DATE_TIME_FORMATTER)

    private data class PlannedCourseOccurrence(
        val classStartMillis: Long,
        val classEndMillis: Long,
        val triggerAtMillis: Long,
        val weekdayLabel: String,
        val classTimeLabel: String
    )

    private data class PlannedNotificationOccurrence(
        val triggerAtMillis: Long,
        val notificationDetail: String
    )

    private companion object {
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm")
        val TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
    }
}
