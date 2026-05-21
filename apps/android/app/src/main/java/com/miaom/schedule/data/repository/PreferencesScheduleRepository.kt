package com.miaom.schedule.data.repository

import android.content.Context
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.TimeSlot
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import org.json.JSONArray
import org.json.JSONObject

class PreferencesScheduleRepository(context: Context) : ScheduleRepository {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val coursesFlow = MutableStateFlow(loadCourses())
    private val timeSlotsFlow = MutableStateFlow(loadTimeSlots())
    private val reminderTasksFlow = MutableStateFlow(loadReminderTasks())

    override fun observeCourses(): Flow<List<Course>> = coursesFlow.asStateFlow()

    override fun observeTimeSlots(): Flow<List<TimeSlot>> = timeSlotsFlow.asStateFlow()

    override fun observeReminderTasks(): Flow<List<ReminderTask>> = reminderTasksFlow.asStateFlow()

    override suspend fun upsertCourse(course: Course) {
        coursesFlow.update { current ->
            current
                .filterNot { it.id == course.id }
                .plus(course)
                .sortedWith(compareBy<Course> { it.dayOfWeek }.thenBy { it.slotId }.thenBy { it.name })
                .also { persistCourses(it) }
        }
    }

    override suspend fun deleteCourse(courseId: String) {
        coursesFlow.update { current ->
            current.filterNot { it.id == courseId }
                .also { persistCourses(it) }
        }
    }

    override suspend fun upsertTimeSlot(slot: TimeSlot) {
        timeSlotsFlow.update { current ->
            current
                .filterNot { it.id == slot.id }
                .plus(slot)
                .sortedWith(compareBy<TimeSlot> { it.startTime }.thenBy { it.endTime }.thenBy { it.label })
                .also { persistTimeSlots(it) }
        }
    }

    override suspend fun deleteTimeSlot(slotId: String) {
        timeSlotsFlow.update { current ->
            current.filterNot { it.id == slotId }
                .also { persistTimeSlots(it) }
        }
    }

    override suspend fun upsertReminderTask(task: ReminderTask) {
        reminderTasksFlow.update { current ->
            current
                .filterNot { it.id == task.id }
                .plus(task)
                .sortedWith(compareByDescending<ReminderTask> { it.enabled }.thenBy { it.minutesBefore }.thenBy { it.courseId })
                .also { persistReminderTasks(it) }
        }
    }

    override suspend fun deleteReminderTask(taskId: String) {
        reminderTasksFlow.update { current ->
            current.filterNot { it.id == taskId }
                .also { persistReminderTasks(it) }
        }
    }

    private fun loadCourses(): List<Course> = readArray(KEY_COURSES) { item ->
        Course(
            id = item.getString("id"),
            name = item.getString("name"),
            teacher = item.optString("teacher"),
            location = item.optString("location"),
            dayOfWeek = item.optInt("dayOfWeek", 1),
            slotId = item.getString("slotId")
        )
    }.sortedWith(compareBy<Course> { it.dayOfWeek }.thenBy { it.slotId }.thenBy { it.name })

    private fun loadTimeSlots(): List<TimeSlot> = readArray(KEY_TIME_SLOTS) { item ->
        TimeSlot(
            id = item.getString("id"),
            label = item.getString("label"),
            startTime = item.getString("startTime"),
            endTime = item.getString("endTime")
        )
    }.sortedWith(compareBy<TimeSlot> { it.startTime }.thenBy { it.endTime }.thenBy { it.label })

    private fun loadReminderTasks(): List<ReminderTask> = readArray(KEY_REMINDER_TASKS) { item ->
        ReminderTask(
            id = item.getString("id"),
            courseId = item.getString("courseId"),
            minutesBefore = item.optInt("minutesBefore", 10),
            channel = ReminderChannel.valueOf(item.optString("channel", ReminderChannel.InAppNotification.name)),
            exact = item.optBoolean("exact", false),
            enabled = item.optBoolean("enabled", true)
        )
    }.sortedWith(compareByDescending<ReminderTask> { it.enabled }.thenBy { it.minutesBefore }.thenBy { it.courseId })

    private fun persistCourses(courses: List<Course>) {
        persistArray(KEY_COURSES, courses) { course ->
            JSONObject()
                .put("id", course.id)
                .put("name", course.name)
                .put("teacher", course.teacher)
                .put("location", course.location)
                .put("dayOfWeek", course.dayOfWeek)
                .put("slotId", course.slotId)
        }
    }

    private fun persistTimeSlots(slots: List<TimeSlot>) {
        persistArray(KEY_TIME_SLOTS, slots) { slot ->
            JSONObject()
                .put("id", slot.id)
                .put("label", slot.label)
                .put("startTime", slot.startTime)
                .put("endTime", slot.endTime)
        }
    }

    private fun persistReminderTasks(tasks: List<ReminderTask>) {
        persistArray(KEY_REMINDER_TASKS, tasks) { task ->
            JSONObject()
                .put("id", task.id)
                .put("courseId", task.courseId)
                .put("minutesBefore", task.minutesBefore)
                .put("channel", task.channel.name)
                .put("exact", task.exact)
                .put("enabled", task.enabled)
        }
    }

    private fun <T> readArray(key: String, mapper: (JSONObject) -> T): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList(jsonArray.length()) {
                for (index in 0 until jsonArray.length()) {
                    add(mapper(jsonArray.getJSONObject(index)))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun <T> persistArray(key: String, items: List<T>, serializer: (T) -> JSONObject) {
        val jsonArray = JSONArray()
        items.forEach { item -> jsonArray.put(serializer(item)) }
        preferences.edit().putString(key, jsonArray.toString()).apply()
    }

    private companion object {
        const val PREFS_NAME = "schedule_prefs"
        const val KEY_COURSES = "courses"
        const val KEY_TIME_SLOTS = "time_slots"
        const val KEY_REMINDER_TASKS = "reminder_tasks"
    }
}
