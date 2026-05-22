package com.miaom.schedule.data.repository

import android.content.Context
import com.miaom.schedule.data.state.EditorCommand
import com.miaom.schedule.data.state.EditorCommandAction
import com.miaom.schedule.data.state.EditorCommandTarget
import com.miaom.schedule.data.state.SnapshotEditorCommand
import com.miaom.schedule.data.state.UndoManager
import com.miaom.schedule.data.state.UndoState
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.CourseColorStyle
import com.miaom.schedule.domain.model.CourseEntry
import com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot
import com.miaom.schedule.domain.model.CourseTimeOverride
import com.miaom.schedule.domain.model.FontConfig
import com.miaom.schedule.domain.model.GridSizingConfig
import com.miaom.schedule.domain.model.BackgroundConfig
import com.miaom.schedule.domain.model.ReminderChannel
import com.miaom.schedule.domain.model.ReminderRule
import com.miaom.schedule.domain.model.ReminderTask
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.ThemeConfig
import com.miaom.schedule.domain.model.ThemeColorTokens
import com.miaom.schedule.domain.model.ThemePresetSnapshot
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.TimeSlotTemplate
import com.miaom.schedule.domain.model.TransferConfig
import com.miaom.schedule.domain.model.UserCourseTemplatePreset
import com.miaom.schedule.domain.model.UserThemePreset
import com.miaom.schedule.domain.model.WeekConfig
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.normalized
import com.miaom.schedule.domain.model.toCourses
import com.miaom.schedule.domain.model.toEntry
import com.miaom.schedule.domain.model.toReminderTasks
import com.miaom.schedule.domain.model.toRule
import com.miaom.schedule.domain.model.toTemplate
import com.miaom.schedule.domain.model.toTimeSlots
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

class PreferencesScheduleRepository(context: Context) : ScheduleRepository, ScheduleStore {
    private val preferences = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    private val editMutex = Mutex()
    private val undoManager = UndoManager()
    private val documentState = MutableStateFlow(loadDocument())
    private val pendingCreatedTimeSlotIdState = MutableStateFlow<String?>(null)
    private val undoStateState = MutableStateFlow(UndoState())

    override val document: StateFlow<ScheduleDocument> = documentState.asStateFlow()
    override val pendingCreatedTimeSlotId: StateFlow<String?> = pendingCreatedTimeSlotIdState.asStateFlow()
    override val undoState: StateFlow<UndoState> = undoStateState.asStateFlow()

    override fun observeCourses(): Flow<List<Course>> = document.map { it.toCourses() }

    override fun observeTimeSlots(): Flow<List<TimeSlot>> = document.map { it.toTimeSlots() }

    override fun observeReminderTasks(): Flow<List<ReminderTask>> = document.map { it.toReminderTasks() }

    override suspend fun edit(transform: (ScheduleDocument) -> ScheduleDocument): ScheduleDocument {
        return editMutex.withLock {
            val current = documentState.value
            val updated = transform(current).normalized(updatedAtEpochMillis = System.currentTimeMillis())
            commitDocument(updated)
            updated
        }
    }

    override suspend fun apply(command: EditorCommand): ScheduleDocument {
        return editMutex.withLock {
            val current = documentState.value
            val updated = command.apply(current).normalized(updatedAtEpochMillis = System.currentTimeMillis())
            commitDocument(updated)
            undoManager.record(command)
            syncUndoState()
            updated
        }
    }

    override suspend fun undo(): ScheduleDocument {
        return editMutex.withLock {
            val command = undoManager.takeUndoCommand() ?: return documentState.value
            val reverted = command.undo(documentState.value).normalized(updatedAtEpochMillis = System.currentTimeMillis())
            commitDocument(reverted)
            syncUndoState()
            reverted
        }
    }

    override suspend fun redo(): ScheduleDocument {
        return editMutex.withLock {
            val command = undoManager.takeRedoCommand() ?: return documentState.value
            val redone = command.apply(documentState.value).normalized(updatedAtEpochMillis = System.currentTimeMillis())
            commitDocument(redone)
            syncUndoState()
            redone
        }
    }

    override fun clearHistory() {
        undoManager.clear()
        syncUndoState()
    }

    override suspend fun createTimeSlotAndReturnId(
        label: String,
        startTime: String,
        endTime: String
    ): String {
        val newId = UUID.randomUUID().toString()
        val slot = TimeSlot(
            id = newId,
            label = label,
            startTime = startTime,
            endTime = endTime
        )
        upsertTimeSlot(slot)
        pendingCreatedTimeSlotIdState.value = newId
        return newId
    }

    override fun markCreatedTimeSlotHandled(timeSlotId: String) {
        if (pendingCreatedTimeSlotIdState.value == timeSlotId) {
            pendingCreatedTimeSlotIdState.value = null
        }
    }

    override suspend fun upsertCourse(course: Course) {
        val current = documentState.value
        val existing = current.courseEntries.firstOrNull { it.id == course.id }
        val updated = current.copy(
            courseEntries = current.courseEntries
                .filterNot { it.id == course.id }
                .plus(course.toEntry())
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.Course,
                action = if (existing == null) EditorCommandAction.Create else EditorCommandAction.Update,
                label = if (existing == null) "新增课程" else "修改课程",
                before = current,
                after = updated
            )
        )
    }

    override suspend fun deleteCourse(courseId: String) {
        val current = documentState.value
        if (current.courseEntries.none { it.id == courseId }) return
        val updated = current.copy(
            courseEntries = current.courseEntries.filterNot { it.id == courseId },
            reminderRules = current.reminderRules.filterNot { it.courseEntryId == courseId }
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.Course,
                action = EditorCommandAction.Delete,
                label = "删除课程",
                before = current,
                after = updated
            )
        )
    }

    override suspend fun upsertTimeSlot(slot: TimeSlot) {
        val current = documentState.value
        val existing = current.timeSlotTemplates.firstOrNull { it.id == slot.id }
        val existingOrder = existing?.order
        val nextOrder = existingOrder ?: current.timeSlotTemplates.size
        val updated = current.copy(
            timeSlotTemplates = current.timeSlotTemplates
                .filterNot { it.id == slot.id }
                .plus(slot.toTemplate(order = nextOrder))
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.TimeSlot,
                action = if (existing == null) EditorCommandAction.Create else EditorCommandAction.Update,
                label = if (existing == null) "新增时间段" else "修改时间段",
                before = current,
                after = updated
            )
        )
    }

    override suspend fun deleteTimeSlot(slotId: String) {
        val current = documentState.value
        if (current.timeSlotTemplates.none { it.id == slotId }) return
        val updated = current.copy(
            timeSlotTemplates = current.timeSlotTemplates.filterNot { it.id == slotId },
            courseEntries = current.courseEntries.filterNot { it.timeSlotTemplateId == slotId },
            reminderRules = current.reminderRules.filterNot { rule ->
                current.courseEntries.any { it.id == rule.courseEntryId && it.timeSlotTemplateId == slotId }
            }
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.TimeSlot,
                action = EditorCommandAction.Delete,
                label = "删除时间段",
                before = current,
                after = updated
            )
        )
    }

    override suspend fun upsertReminderTask(task: ReminderTask) {
        val current = documentState.value
        val existing = current.reminderRules.firstOrNull { it.id == task.id }
        val updated = current.copy(
            reminderRules = current.reminderRules
                .filterNot { it.id == task.id }
                .plus(task.toRule())
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.ReminderTask,
                action = if (existing == null) EditorCommandAction.Create else EditorCommandAction.Update,
                label = if (existing == null) "新增提醒任务" else "修改提醒任务",
                before = current,
                after = updated
            )
        )
    }

    override suspend fun deleteReminderTask(taskId: String) {
        val current = documentState.value
        if (current.reminderRules.none { it.id == taskId }) return
        val updated = current.copy(
            reminderRules = current.reminderRules.filterNot { it.id == taskId }
        )
        apply(
            SnapshotEditorCommand(
                target = EditorCommandTarget.ReminderTask,
                action = EditorCommandAction.Delete,
                label = "删除提醒任务",
                before = current,
                after = updated
            )
        )
    }

    private fun commitDocument(document: ScheduleDocument) {
        persistDocument(document)
        documentState.value = document
    }

    private fun syncUndoState() {
        undoStateState.value = undoManager.snapshot()
    }

    private fun loadDocument(): ScheduleDocument {
        val persistedDocument = preferences.getString(KEY_DOCUMENT, null)
            ?.let(::parseDocument)
        if (persistedDocument != null) {
            return persistedDocument.normalized(
                updatedAtEpochMillis = persistedDocument.updatedAtEpochMillis.takeIf { it > 0L }
                    ?: System.currentTimeMillis()
            )
        }

        val migratedDocument = ScheduleDocument(
            weekConfig = loadWeekConfig(),
            timeSlotTemplates = loadLegacyTimeSlots().mapIndexed { index, slot -> slot.toTemplate(order = index) },
            courseEntries = loadLegacyCourses().map { it.toEntry() },
            reminderRules = loadLegacyReminderTasks().map { it.toRule() },
            themeConfig = loadThemeConfig(),
            transferConfig = TransferConfig(),
            updatedAtEpochMillis = System.currentTimeMillis()
        ).normalized()

        persistDocument(migratedDocument)
        return migratedDocument
    }

    private fun parseDocument(raw: String): ScheduleDocument? = runCatching {
        val root = JSONObject(raw)
        ScheduleDocument(
            version = root.optInt("version", ScheduleDocument.CURRENT_VERSION),
            weekConfig = parseWeekConfig(root.optJSONObject("weekConfig")),
            timeSlotTemplates = parseArray(root.optJSONArray("timeSlotTemplates")) { item, index ->
                TimeSlotTemplate(
                    id = item.getString("id"),
                    label = item.getString("label"),
                    startTime = item.getString("startTime"),
                    endTime = item.getString("endTime"),
                    order = item.optInt("order", index),
                    enabled = item.optBoolean("enabled", true)
                )
            },
            courseEntries = parseArray(root.optJSONArray("courseEntries")) { item, _ ->
                CourseEntry(
                    id = item.getString("id"),
                    name = item.getString("name"),
                    teacher = item.optString("teacher"),
                    location = item.optString("location"),
                    dayOfWeek = item.optInt("dayOfWeek", 1),
                    timeSlotTemplateId = item.optString("timeSlotTemplateId", item.optString("slotId")),
                    weekParity = parseWeekParity(item.optString("weekParity", WeekParity.Every.name)),
                    timeOverride = parseTimeOverride(item.optJSONObject("timeOverride")),
                    colorStyle = parseCourseColorStyle(item.optJSONObject("colorStyle"))
                )
            },
            courseTemplatePresets = parseCourseTemplatePresets(root.optJSONArray("courseTemplatePresets")),
            reminderRules = parseArray(root.optJSONArray("reminderRules")) { item, _ ->
                ReminderRule(
                    id = item.getString("id"),
                    courseEntryId = item.optString("courseEntryId", item.optString("courseId")),
                    minutesBefore = item.optInt("minutesBefore", 10),
                    channel = parseReminderChannel(item.optString("channel", ReminderChannel.InAppNotification.name)),
                    exact = item.optBoolean("exact", false),
                    enabled = item.optBoolean("enabled", true)
                )
            },
            themeConfig = parseThemeConfig(root.optJSONObject("themeConfig")),
            transferConfig = parseTransferConfig(root.optJSONObject("transferConfig")),
            updatedAtEpochMillis = root.optLong("updatedAtEpochMillis", 0L)
        )
    }.getOrNull()

    private fun loadWeekConfig(): WeekConfig {
        val raw = preferences.getString(KEY_WEEK_CONFIG, null) ?: return WeekConfig()
        return runCatching {
            parseWeekConfig(JSONObject(raw))
        }.getOrDefault(WeekConfig())
    }

    private fun loadThemeConfig(): ThemeConfig {
        val raw = preferences.getString(KEY_THEME_CONFIG, null) ?: return ThemeConfig()
        return runCatching {
            parseThemeConfig(JSONObject(raw))
        }.getOrDefault(ThemeConfig())
    }

    private fun loadLegacyCourses(): List<Course> = readArray(KEY_COURSES) { item, _ ->
        Course(
            id = item.getString("id"),
            name = item.getString("name"),
            teacher = item.optString("teacher"),
            location = item.optString("location"),
            dayOfWeek = item.optInt("dayOfWeek", 1),
            slotId = item.getString("slotId"),
            weekParity = parseWeekParity(item.optString("weekParity", WeekParity.Every.name)),
            overrideStartTime = item.optString("overrideStartTime"),
            overrideEndTime = item.optString("overrideEndTime"),
            useThemeDefaults = item.optBoolean("useThemeDefaults", true),
            backgroundColorArgb = item.optInt("backgroundColorArgb", CourseColorStyle().backgroundColorArgb),
            textColorArgb = item.optInt("textColorArgb", CourseColorStyle().textColorArgb),
            borderColorArgb = item.optInt("borderColorArgb", CourseColorStyle().borderColorArgb)
        )
    }

    private fun loadLegacyTimeSlots(): List<TimeSlot> = readArray(KEY_TIME_SLOTS) { item, _ ->
        TimeSlot(
            id = item.getString("id"),
            label = item.getString("label"),
            startTime = item.getString("startTime"),
            endTime = item.getString("endTime")
        )
    }

    private fun loadLegacyReminderTasks(): List<ReminderTask> = readArray(KEY_REMINDER_TASKS) { item, _ ->
        ReminderTask(
            id = item.getString("id"),
            courseId = item.getString("courseId"),
            minutesBefore = item.optInt("minutesBefore", 10),
            channel = parseReminderChannel(item.optString("channel", ReminderChannel.InAppNotification.name)),
            exact = item.optBoolean("exact", false),
            enabled = item.optBoolean("enabled", true)
        )
    }

    private fun parseWeekConfig(jsonObject: JSONObject?): WeekConfig {
        if (jsonObject == null) return WeekConfig()
        val teachingDays = parseIntArray(jsonObject.optJSONArray("teachingDays"))
        return WeekConfig(
            firstDayOfWeek = jsonObject.optInt("firstDayOfWeek", 1),
            teachingDays = teachingDays.ifEmpty { listOf(1, 2, 3, 4, 5) },
            week1MondayDate = jsonObject.optString("week1MondayDate", WeekConfig().week1MondayDate)
        )
    }

    private fun parseThemeConfig(jsonObject: JSONObject?): ThemeConfig {
        if (jsonObject == null) return ThemeConfig()
        return ThemeConfig(
            themeMode = jsonObject.optString("themeMode", "system"),
            useDynamicColor = jsonObject.optBoolean("useDynamicColor", true),
            accentColorHex = jsonObject.optString("accentColorHex"),
            colorTokens = parseThemeColorTokens(jsonObject.optJSONObject("colorTokens")),
            background = parseBackgroundConfig(jsonObject.optJSONObject("background")),
            font = parseFontConfig(jsonObject.optJSONObject("font")),
            selectedBuiltInPresetId = jsonObject.optString("selectedBuiltInPresetId", "campus-breeze"),
            userPresets = parseUserPresets(jsonObject.optJSONArray("userPresets")),
            gridSizing = parseGridSizing(jsonObject.optJSONObject("gridSizing"))
        )
    }

    private fun parseThemeColorTokens(jsonObject: JSONObject?): ThemeColorTokens {
        if (jsonObject == null) return ThemeColorTokens()
        return ThemeColorTokens(
            primaryHex = jsonObject.optString("primaryHex", ThemeColorTokens().primaryHex),
            secondaryHex = jsonObject.optString("secondaryHex", ThemeColorTokens().secondaryHex),
            tertiaryHex = jsonObject.optString("tertiaryHex", ThemeColorTokens().tertiaryHex),
            backgroundHex = jsonObject.optString("backgroundHex", ThemeColorTokens().backgroundHex),
            surfaceHex = jsonObject.optString("surfaceHex", ThemeColorTokens().surfaceHex),
            surfaceVariantHex = jsonObject.optString("surfaceVariantHex", ThemeColorTokens().surfaceVariantHex),
            onSurfaceHex = jsonObject.optString("onSurfaceHex", ThemeColorTokens().onSurfaceHex),
            outlineHex = jsonObject.optString("outlineHex", ThemeColorTokens().outlineHex)
        )
    }

    private fun parseBackgroundConfig(jsonObject: JSONObject?): BackgroundConfig {
        if (jsonObject == null) return BackgroundConfig()
        return BackgroundConfig(
            mode = jsonObject.optString("mode", BackgroundConfig().mode),
            solidColorHex = jsonObject.optString("solidColorHex", BackgroundConfig().solidColorHex),
            imageReference = jsonObject.optString("imageReference"),
            blurRadiusDp = jsonObject.optDouble("blurRadiusDp", 18.0).toFloat()
        )
    }

    private fun parseFontConfig(jsonObject: JSONObject?): FontConfig {
        if (jsonObject == null) return FontConfig()
        return FontConfig(
            builtInFontId = jsonObject.optString("builtInFontId", FontConfig().builtInFontId),
            customFontLabel = jsonObject.optString("customFontLabel"),
            customFontPath = jsonObject.optString("customFontPath"),
            preferCustomFont = jsonObject.optBoolean("preferCustomFont", false)
        )
    }

    private fun parseUserPresets(jsonArray: JSONArray?): List<UserThemePreset> {
        return parseArray(jsonArray) { item, _ ->
            UserThemePreset(
                id = item.getString("id"),
                name = item.optString("name", "我的预设"),
                group = item.optString("group", "用户预设"),
                note = item.optString("note"),
                snapshot = parseThemePresetSnapshot(item.optJSONObject("snapshot"))
            )
        }
    }

    private fun parseCourseTemplatePresets(jsonArray: JSONArray?): List<UserCourseTemplatePreset> {
        return parseArray(jsonArray) { item, _ ->
            UserCourseTemplatePreset(
                id = item.getString("id"),
                name = item.optString("name", "课程模板"),
                note = item.optString("note"),
                snapshot = parseCourseTemplatePresetSnapshot(item.optJSONObject("snapshot"))
            )
        }
    }

    private fun parseThemePresetSnapshot(jsonObject: JSONObject?): ThemePresetSnapshot {
        if (jsonObject == null) return ThemePresetSnapshot()
        return ThemePresetSnapshot(
            themeMode = jsonObject.optString("themeMode", "system"),
            useDynamicColor = jsonObject.optBoolean("useDynamicColor", true),
            accentColorHex = jsonObject.optString("accentColorHex"),
            colorTokens = parseThemeColorTokens(jsonObject.optJSONObject("colorTokens")),
            background = parseBackgroundConfig(jsonObject.optJSONObject("background")),
            font = parseFontConfig(jsonObject.optJSONObject("font")),
            gridSizing = parseGridSizing(jsonObject.optJSONObject("gridSizing"))
        )
    }

    private fun parseCourseTemplatePresetSnapshot(jsonObject: JSONObject?): CourseTemplatePresetSnapshot {
        if (jsonObject == null) return CourseTemplatePresetSnapshot()
        return CourseTemplatePresetSnapshot(
            courseName = jsonObject.optString("courseName"),
            teacher = jsonObject.optString("teacher"),
            location = jsonObject.optString("location"),
            preferredTimeSlotTemplateId = jsonObject.optString("preferredTimeSlotTemplateId"),
            preferredTimeSlotLabel = jsonObject.optString("preferredTimeSlotLabel"),
            weekParity = parseWeekParity(jsonObject.optString("weekParity", WeekParity.Every.name)),
            timeOverride = parseTimeOverride(jsonObject.optJSONObject("timeOverride")),
            colorStyle = parseCourseColorStyle(jsonObject.optJSONObject("colorStyle"))
        )
    }

    private fun parseTransferConfig(jsonObject: JSONObject?): TransferConfig {
        if (jsonObject == null) return TransferConfig()
        return TransferConfig(
            defaultExportMethod = jsonObject.optString("defaultExportMethod", TransferConfig().defaultExportMethod),
            rememberDefaultExportMethod = jsonObject.optBoolean(
                "rememberDefaultExportMethod",
                TransferConfig().rememberDefaultExportMethod
            )
        )
    }

    private fun parseGridSizing(jsonObject: JSONObject?): GridSizingConfig {
        if (jsonObject == null) return GridSizingConfig()
        return GridSizingConfig(
            gridMinCellWidthDp = jsonObject.optDouble("gridMinCellWidthDp", 112.0).toFloat(),
            gridMaxCellWidthDp = jsonObject.optDouble("gridMaxCellWidthDp", 168.0).toFloat(),
            gridMinCellHeightDp = jsonObject.optDouble("gridMinCellHeightDp", 108.0).toFloat(),
            gridMaxCellHeightDp = jsonObject.optDouble("gridMaxCellHeightDp", 156.0).toFloat(),
            compactMode = jsonObject.optBoolean("compactMode", false),
            adaptiveSizing = jsonObject.optBoolean("adaptiveSizing", true)
        )
    }

    private fun parseTimeOverride(jsonObject: JSONObject?): CourseTimeOverride? {
        if (jsonObject == null) return null
        val startTime = jsonObject.optString("startTime")
        val endTime = jsonObject.optString("endTime")
        if (startTime.isBlank() && endTime.isBlank()) return null
        return CourseTimeOverride(
            startTime = startTime,
            endTime = endTime
        )
    }

    private fun parseCourseColorStyle(jsonObject: JSONObject?): CourseColorStyle {
        if (jsonObject == null) return CourseColorStyle()
        val defaults = CourseColorStyle()
        return CourseColorStyle(
            useThemeDefaults = jsonObject.optBoolean("useThemeDefaults", defaults.useThemeDefaults),
            backgroundColorArgb = jsonObject.optInt("backgroundColorArgb", defaults.backgroundColorArgb),
            textColorArgb = jsonObject.optInt("textColorArgb", defaults.textColorArgb),
            borderColorArgb = jsonObject.optInt("borderColorArgb", defaults.borderColorArgb)
        )
    }

    private fun parseWeekParity(value: String): WeekParity {
        return WeekParity.entries.firstOrNull { it.name.equals(value, ignoreCase = true) } ?: WeekParity.Every
    }

    private fun parseReminderChannel(value: String): ReminderChannel {
        return ReminderChannel.entries.firstOrNull { it.name == value } ?: ReminderChannel.InAppNotification
    }

    private fun parseIntArray(jsonArray: JSONArray?): List<Int> {
        if (jsonArray == null) return emptyList()
        return buildList(jsonArray.length()) {
            for (index in 0 until jsonArray.length()) {
                add(jsonArray.optInt(index, index + 1))
            }
        }
    }

    private fun <T> parseArray(
        jsonArray: JSONArray?,
        mapper: (JSONObject, Int) -> T
    ): List<T> {
        if (jsonArray == null) return emptyList()
        return buildList(jsonArray.length()) {
            for (index in 0 until jsonArray.length()) {
                add(mapper(jsonArray.getJSONObject(index), index))
            }
        }
    }

    private fun <T> readArray(key: String, mapper: (JSONObject, Int) -> T): List<T> {
        val raw = preferences.getString(key, null) ?: return emptyList()
        return runCatching {
            val jsonArray = JSONArray(raw)
            buildList(jsonArray.length()) {
                for (index in 0 until jsonArray.length()) {
                    add(mapper(jsonArray.getJSONObject(index), index))
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun persistDocument(document: ScheduleDocument) {
        val root = JSONObject()
            .put("version", document.version)
            .put("weekConfig", weekConfigToJson(document.weekConfig))
            .put("timeSlotTemplates", JSONArray().also { array ->
                document.timeSlotTemplates.forEach { template ->
                    array.put(
                        JSONObject()
                            .put("id", template.id)
                            .put("label", template.label)
                            .put("startTime", template.startTime)
                            .put("endTime", template.endTime)
                            .put("order", template.order)
                            .put("enabled", template.enabled)
                    )
                }
            })
            .put("courseEntries", JSONArray().also { array ->
                document.courseEntries.forEach { entry ->
                    array.put(
                        JSONObject()
                            .put("id", entry.id)
                            .put("name", entry.name)
                            .put("teacher", entry.teacher)
                            .put("location", entry.location)
                            .put("dayOfWeek", entry.dayOfWeek)
                            .put("timeSlotTemplateId", entry.timeSlotTemplateId)
                            .put("weekParity", entry.weekParity.name)
                            .put(
                                "timeOverride",
                                entry.timeOverride?.let { override ->
                                    JSONObject()
                                        .put("startTime", override.startTime)
                                        .put("endTime", override.endTime)
                                }
                            )
                            .put(
                                "colorStyle",
                                JSONObject()
                                    .put("useThemeDefaults", entry.colorStyle.useThemeDefaults)
                                    .put("backgroundColorArgb", entry.colorStyle.backgroundColorArgb)
                                    .put("textColorArgb", entry.colorStyle.textColorArgb)
                                    .put("borderColorArgb", entry.colorStyle.borderColorArgb)
                            )
                    )
                }
            })
            .put("courseTemplatePresets", JSONArray().also { array ->
                document.courseTemplatePresets.forEach { preset ->
                    array.put(
                        JSONObject()
                            .put("id", preset.id)
                            .put("name", preset.name)
                            .put("note", preset.note)
                            .put("snapshot", courseTemplatePresetSnapshotToJson(preset.snapshot))
                    )
                }
            })
            .put("reminderRules", JSONArray().also { array ->
                document.reminderRules.forEach { rule ->
                    array.put(
                        JSONObject()
                            .put("id", rule.id)
                            .put("courseEntryId", rule.courseEntryId)
                            .put("minutesBefore", rule.minutesBefore)
                            .put("channel", rule.channel.name)
                            .put("exact", rule.exact)
                            .put("enabled", rule.enabled)
                    )
                }
            })
            .put("themeConfig", themeConfigToJson(document.themeConfig))
            .put("transferConfig", transferConfigToJson(document.transferConfig))
            .put("updatedAtEpochMillis", document.updatedAtEpochMillis)

        preferences.edit()
            .putString(KEY_DOCUMENT, root.toString())
            .putString(KEY_WEEK_CONFIG, weekConfigToJson(document.weekConfig).toString())
            .putString(KEY_THEME_CONFIG, themeConfigToJson(document.themeConfig).toString())
            .putString(KEY_COURSES, coursesToLegacyArray(document.courseEntries).toString())
            .putString(KEY_TIME_SLOTS, timeSlotsToLegacyArray(document.timeSlotTemplates).toString())
            .putString(KEY_REMINDER_TASKS, remindersToLegacyArray(document.reminderRules).toString())
            .apply()
    }

    private fun weekConfigToJson(config: WeekConfig): JSONObject = JSONObject()
        .put("firstDayOfWeek", config.firstDayOfWeek)
        .put("week1MondayDate", config.week1MondayDate)
        .put("teachingDays", JSONArray().also { array -> config.teachingDays.forEach(array::put) })

    private fun themeConfigToJson(config: ThemeConfig): JSONObject = JSONObject()
        .put("themeMode", config.themeMode)
        .put("useDynamicColor", config.useDynamicColor)
        .put("accentColorHex", config.accentColorHex)
        .put("colorTokens", themeColorTokensToJson(config.colorTokens))
        .put("background", backgroundConfigToJson(config.background))
        .put("font", fontConfigToJson(config.font))
        .put("selectedBuiltInPresetId", config.selectedBuiltInPresetId)
        .put("userPresets", JSONArray().also { array ->
            config.userPresets.forEach { preset ->
                array.put(
                    JSONObject()
                        .put("id", preset.id)
                        .put("name", preset.name)
                        .put("group", preset.group)
                        .put("note", preset.note)
                        .put("snapshot", themePresetSnapshotToJson(preset.snapshot))
                )
            }
        })
        .put("gridSizing", JSONObject()
            .put("gridMinCellWidthDp", config.gridSizing.gridMinCellWidthDp)
            .put("gridMaxCellWidthDp", config.gridSizing.gridMaxCellWidthDp)
            .put("gridMinCellHeightDp", config.gridSizing.gridMinCellHeightDp)
            .put("gridMaxCellHeightDp", config.gridSizing.gridMaxCellHeightDp)
            .put("compactMode", config.gridSizing.compactMode)
            .put("adaptiveSizing", config.gridSizing.adaptiveSizing)
        )

    private fun themeColorTokensToJson(tokens: ThemeColorTokens): JSONObject = JSONObject()
        .put("primaryHex", tokens.primaryHex)
        .put("secondaryHex", tokens.secondaryHex)
        .put("tertiaryHex", tokens.tertiaryHex)
        .put("backgroundHex", tokens.backgroundHex)
        .put("surfaceHex", tokens.surfaceHex)
        .put("surfaceVariantHex", tokens.surfaceVariantHex)
        .put("onSurfaceHex", tokens.onSurfaceHex)
        .put("outlineHex", tokens.outlineHex)

    private fun backgroundConfigToJson(config: BackgroundConfig): JSONObject = JSONObject()
        .put("mode", config.mode)
        .put("solidColorHex", config.solidColorHex)
        .put("imageReference", config.imageReference)
        .put("blurRadiusDp", config.blurRadiusDp)

    private fun fontConfigToJson(config: FontConfig): JSONObject = JSONObject()
        .put("builtInFontId", config.builtInFontId)
        .put("customFontLabel", config.customFontLabel)
        .put("customFontPath", config.customFontPath)
        .put("preferCustomFont", config.preferCustomFont)

    private fun themePresetSnapshotToJson(snapshot: ThemePresetSnapshot): JSONObject = JSONObject()
        .put("themeMode", snapshot.themeMode)
        .put("useDynamicColor", snapshot.useDynamicColor)
        .put("accentColorHex", snapshot.accentColorHex)
        .put("colorTokens", themeColorTokensToJson(snapshot.colorTokens))
        .put("background", backgroundConfigToJson(snapshot.background))
        .put("font", fontConfigToJson(snapshot.font))
        .put("gridSizing", JSONObject()
            .put("gridMinCellWidthDp", snapshot.gridSizing.gridMinCellWidthDp)
            .put("gridMaxCellWidthDp", snapshot.gridSizing.gridMaxCellWidthDp)
            .put("gridMinCellHeightDp", snapshot.gridSizing.gridMinCellHeightDp)
            .put("gridMaxCellHeightDp", snapshot.gridSizing.gridMaxCellHeightDp)
            .put("compactMode", snapshot.gridSizing.compactMode)
            .put("adaptiveSizing", snapshot.gridSizing.adaptiveSizing)
        )

    private fun courseTemplatePresetSnapshotToJson(snapshot: CourseTemplatePresetSnapshot): JSONObject = JSONObject()
        .put("courseName", snapshot.courseName)
        .put("teacher", snapshot.teacher)
        .put("location", snapshot.location)
        .put("preferredTimeSlotTemplateId", snapshot.preferredTimeSlotTemplateId)
        .put("preferredTimeSlotLabel", snapshot.preferredTimeSlotLabel)
        .put("weekParity", snapshot.weekParity.name)
        .put(
            "timeOverride",
            snapshot.timeOverride?.let { override ->
                JSONObject()
                    .put("startTime", override.startTime)
                    .put("endTime", override.endTime)
            }
        )
        .put(
            "colorStyle",
            JSONObject()
                .put("useThemeDefaults", snapshot.colorStyle.useThemeDefaults)
                .put("backgroundColorArgb", snapshot.colorStyle.backgroundColorArgb)
                .put("textColorArgb", snapshot.colorStyle.textColorArgb)
                .put("borderColorArgb", snapshot.colorStyle.borderColorArgb)
        )

    private fun transferConfigToJson(config: TransferConfig): JSONObject = JSONObject()
        .put("defaultExportMethod", config.defaultExportMethod)
        .put("rememberDefaultExportMethod", config.rememberDefaultExportMethod)

    private fun coursesToLegacyArray(entries: List<CourseEntry>): JSONArray = JSONArray().also { array ->
        entries.forEach { entry ->
            array.put(
                JSONObject()
                    .put("id", entry.id)
                    .put("name", entry.name)
                    .put("teacher", entry.teacher)
                    .put("location", entry.location)
                    .put("dayOfWeek", entry.dayOfWeek)
                    .put("weekParity", entry.weekParity.name)
                    .put("slotId", entry.timeSlotTemplateId)
                    .put("overrideStartTime", entry.timeOverride?.startTime.orEmpty())
                    .put("overrideEndTime", entry.timeOverride?.endTime.orEmpty())
                    .put("useThemeDefaults", entry.colorStyle.useThemeDefaults)
                    .put("backgroundColorArgb", entry.colorStyle.backgroundColorArgb)
                    .put("textColorArgb", entry.colorStyle.textColorArgb)
                    .put("borderColorArgb", entry.colorStyle.borderColorArgb)
            )
        }
    }

    private fun timeSlotsToLegacyArray(templates: List<TimeSlotTemplate>): JSONArray = JSONArray().also { array ->
        templates.forEach { template ->
            array.put(
                JSONObject()
                    .put("id", template.id)
                    .put("label", template.label)
                    .put("startTime", template.startTime)
                    .put("endTime", template.endTime)
            )
        }
    }

    private fun remindersToLegacyArray(rules: List<ReminderRule>): JSONArray = JSONArray().also { array ->
        rules.forEach { rule ->
            array.put(
                JSONObject()
                    .put("id", rule.id)
                    .put("courseId", rule.courseEntryId)
                    .put("minutesBefore", rule.minutesBefore)
                    .put("channel", rule.channel.name)
                    .put("exact", rule.exact)
                    .put("enabled", rule.enabled)
            )
        }
    }

    private companion object {
        const val PREFS_NAME = "schedule_prefs"
        const val KEY_DOCUMENT = "schedule_document"
        const val KEY_WEEK_CONFIG = "week_config"
        const val KEY_THEME_CONFIG = "theme_config"
        const val KEY_COURSES = "courses"
        const val KEY_TIME_SLOTS = "time_slots"
        const val KEY_REMINDER_TASKS = "reminder_tasks"
    }
}
