package com.miaom.schedule.domain.model

import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlin.math.min

private const val SCHEDULE_DOCUMENT_CURRENT_VERSION = 4
private val DEFAULT_WEEK1_MONDAY: LocalDate = LocalDate.of(2026, 2, 23)

enum class WeekParity {
    Every,
    Odd,
    Even
}

enum class BackgroundMode {
    Solid,
    SolidBlur,
    Image,
    ImageBlur
}

enum class BackgroundImageDisplayMode {
    Fill,
    Fit,
    Stretch,
    Crop
}

enum class BuiltInFontOption {
    SystemSans,
    Serif,
    Rounded,
    Monospace
}

enum class ExportTransport {
    FilePack,
    ClipboardPack
}

data class CourseTimeOverride(
    val startTime: String = "",
    val endTime: String = ""
)

data class CourseColorStyle(
    val useThemeDefaults: Boolean = true,
    val backgroundColorArgb: Int = 0xFFDBEAFE.toInt(),
    val textColorArgb: Int = 0xFF102A43.toInt(),
    val borderColorArgb: Int = 0xFF6B8BB3.toInt()
)

data class GridSizingConfig(
    val gridMinCellWidthDp: Float = 112f,
    val gridMaxCellWidthDp: Float = 168f,
    val gridMinCellHeightDp: Float = 108f,
    val gridMaxCellHeightDp: Float = 156f,
    val compactMode: Boolean = false,
    val adaptiveSizing: Boolean = true
)

data class ThemeColorTokens(
    val primaryHex: String = "#1D7A85",
    val secondaryHex: String = "#58708A",
    val tertiaryHex: String = "#8A6E9E",
    val backgroundHex: String = "#F7F4EC",
    val surfaceHex: String = "#FFFDF8",
    val surfaceVariantHex: String = "#DFE8EA",
    val onSurfaceHex: String = "#12202F",
    val outlineHex: String = "#7B8A90"
)

data class BackgroundConfig(
    val mode: String = BackgroundMode.Solid.name,
    val solidColorHex: String = ThemeColorTokens().backgroundHex,
    val imageReference: String = "",
    val blurRadiusDp: Float = 18f,
    val imageDisplayMode: String = BackgroundImageDisplayMode.Crop.name
)

data class FontConfig(
    val builtInFontId: String = BuiltInFontOption.SystemSans.name,
    val customFontLabel: String = "",
    val customFontPath: String = "",
    val preferCustomFont: Boolean = false
)

data class ThemePresetSnapshot(
    val themeMode: String = "system",
    val useDynamicColor: Boolean = true,
    val accentColorHex: String = ThemeColorTokens().primaryHex,
    val colorTokens: ThemeColorTokens = ThemeColorTokens(),
    val background: BackgroundConfig = BackgroundConfig(),
    val font: FontConfig = FontConfig(),
    val gridSizing: GridSizingConfig = GridSizingConfig()
)

data class UserThemePreset(
    val id: String,
    val name: String,
    val group: String = "用户预设",
    val note: String = "",
    val snapshot: ThemePresetSnapshot = ThemePresetSnapshot()
)

data class CourseTemplatePresetSnapshot(
    val courseName: String = "",
    val teacher: String = "",
    val location: String = "",
    val preferredTimeSlotTemplateId: String = "",
    val preferredTimeSlotLabel: String = "",
    val weekParity: WeekParity = WeekParity.Every,
    val weekNumbers: List<Int> = emptyList(),
    val timeOverride: CourseTimeOverride? = null,
    val colorStyle: CourseColorStyle = CourseColorStyle()
)

data class UserCourseTemplatePreset(
    val id: String,
    val name: String,
    val note: String = "",
    val snapshot: CourseTemplatePresetSnapshot = CourseTemplatePresetSnapshot()
)

data class TransferConfig(
    val defaultExportMethod: String = ExportTransport.FilePack.name,
    val rememberDefaultExportMethod: Boolean = true
)

data class WeekConfig(
    val firstDayOfWeek: Int = 1,
    val teachingDays: List<Int> = listOf(1, 2, 3, 4, 5),
    val week1MondayDate: String = DEFAULT_WEEK1_MONDAY.toString()
) {
    fun resolvedWeek1Monday(): LocalDate = parseIsoLocalDateOrNull(week1MondayDate) ?: DEFAULT_WEEK1_MONDAY

    fun weekIndexFor(date: LocalDate): Int {
        val daysBetween = ChronoUnit.DAYS.between(resolvedWeek1Monday(), date)
        val weekOffset = floorDiv(daysBetween, 7)
        return max(1, weekOffset.toInt() + 1)
    }

    fun parityFor(date: LocalDate): WeekParity {
        return if (weekIndexFor(date) % 2 == 0) WeekParity.Even else WeekParity.Odd
    }
}

data class TimeSlotTemplate(
    val id: String,
    val label: String,
    val startTime: String,
    val endTime: String,
    val order: Int = 0,
    val enabled: Boolean = true
)

data class CourseEntry(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val timeSlotTemplateId: String,
    val weekParity: WeekParity = WeekParity.Every,
    val weekNumbers: List<Int> = emptyList(),
    val timeOverride: CourseTimeOverride? = null,
    val colorStyle: CourseColorStyle = CourseColorStyle()
) {
    fun effectiveStartTime(template: TimeSlotTemplate?): String =
        timeOverride?.startTime?.takeIf { it.isNotBlank() } ?: template?.startTime.orEmpty()

    fun effectiveEndTime(template: TimeSlotTemplate?): String =
        timeOverride?.endTime?.takeIf { it.isNotBlank() } ?: template?.endTime.orEmpty()
}

data class ReminderRule(
    val id: String,
    val courseEntryId: String,
    val minutesBefore: Int,
    val channel: ReminderChannel,
    val exact: Boolean,
    val enabled: Boolean
)

data class ThemeConfig(
    val themeMode: String = "system",
    val useDynamicColor: Boolean = true,
    val accentColorHex: String = "",
    val colorTokens: ThemeColorTokens = ThemeColorTokens(),
    val background: BackgroundConfig = BackgroundConfig(),
    val font: FontConfig = FontConfig(),
    val selectedBuiltInPresetId: String = "campus-breeze",
    val userPresets: List<UserThemePreset> = emptyList(),
    val gridSizing: GridSizingConfig = GridSizingConfig()
)

data class ScheduleDocument(
    val version: Int = SCHEDULE_DOCUMENT_CURRENT_VERSION,
    val weekConfig: WeekConfig = WeekConfig(),
    val timeSlotTemplates: List<TimeSlotTemplate> = emptyList(),
    val courseEntries: List<CourseEntry> = emptyList(),
    val courseTemplatePresets: List<UserCourseTemplatePreset> = emptyList(),
    val reminderRules: List<ReminderRule> = emptyList(),
    val themeConfig: ThemeConfig = ThemeConfig(),
    val transferConfig: TransferConfig = TransferConfig(),
    val updatedAtEpochMillis: Long = 0L
) {
    companion object {
        const val CURRENT_VERSION = SCHEDULE_DOCUMENT_CURRENT_VERSION
    }
}

data class ScheduleLayoutMetrics(
    val cellWidthDp: Float,
    val cellHeightDp: Float,
    val labelColumnWidthDp: Float
)

data class SchedulePresentationCourse(
    val id: String,
    val name: String,
    val teacher: String,
    val location: String,
    val dayOfWeek: Int,
    val slotId: String,
    val slotLabel: String,
    val startTime: String,
    val endTime: String,
    val weekParity: WeekParity,
    val weekNumbers: List<Int>,
    val hasTimeOverride: Boolean,
    val useThemeDefaults: Boolean,
    val backgroundColorArgb: Int,
    val textColorArgb: Int,
    val borderColorArgb: Int
)

fun TimeSlotTemplate.toTimeSlot(): TimeSlot = TimeSlot(
    id = id,
    label = label,
    startTime = startTime,
    endTime = endTime
)

fun TimeSlot.toTemplate(order: Int = 0): TimeSlotTemplate = TimeSlotTemplate(
    id = id,
    label = label,
    startTime = startTime,
    endTime = endTime,
    order = order
)

fun CourseEntry.toCourse(template: TimeSlotTemplate? = null): Course = Course(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    slotId = timeSlotTemplateId,
    weekParity = weekParity,
    weekNumbers = weekNumbers,
    overrideStartTime = timeOverride?.startTime.orEmpty(),
    overrideEndTime = timeOverride?.endTime.orEmpty(),
    useThemeDefaults = colorStyle.useThemeDefaults,
    backgroundColorArgb = colorStyle.backgroundColorArgb,
    textColorArgb = colorStyle.textColorArgb,
    borderColorArgb = colorStyle.borderColorArgb,
    effectiveStartTime = effectiveStartTime(template),
    effectiveEndTime = effectiveEndTime(template)
)

fun Course.toEntry(): CourseEntry = CourseEntry(
    id = id,
    name = name,
    teacher = teacher,
    location = location,
    dayOfWeek = dayOfWeek,
    timeSlotTemplateId = slotId,
    weekParity = weekParity,
    weekNumbers = weekNumbers,
    timeOverride = overrideStartTime.takeIf { it.isNotBlank() || overrideEndTime.isNotBlank() }?.let {
        CourseTimeOverride(
            startTime = overrideStartTime,
            endTime = overrideEndTime
        )
    },
    colorStyle = CourseColorStyle(
        useThemeDefaults = useThemeDefaults,
        backgroundColorArgb = backgroundColorArgb,
        textColorArgb = textColorArgb,
        borderColorArgb = borderColorArgb
    )
)

fun ReminderRule.toReminderTask(): ReminderTask = ReminderTask(
    id = id,
    courseId = courseEntryId,
    minutesBefore = minutesBefore,
    channel = channel,
    exact = exact,
    enabled = enabled
)

fun ReminderTask.toRule(): ReminderRule = ReminderRule(
    id = id,
    courseEntryId = courseId,
    minutesBefore = minutesBefore,
    channel = channel,
    exact = exact,
    enabled = enabled
)

fun ScheduleDocument.toCourses(): List<Course> {
    val templatesById = timeSlotTemplates.associateBy { it.id }
    return courseEntries
        .map { entry -> entry.toCourse(templatesById[entry.timeSlotTemplateId]) }
        .sortedWith(
            compareBy<Course> { it.dayOfWeek }
                .thenBy { it.effectiveStartTime.ifBlank { "99:99" } }
                .thenBy { it.slotId }
                .thenBy { it.name }
        )
}

fun ScheduleDocument.toTimeSlots(): List<TimeSlot> = timeSlotTemplates
    .map { it.toTimeSlot() }
    .sortedWith(compareBy<TimeSlot> { it.startTime }.thenBy { it.endTime }.thenBy { it.label })

fun ScheduleDocument.toReminderTasks(): List<ReminderTask> = reminderRules
    .map { it.toReminderTask() }
    .sortedWith(compareByDescending<ReminderTask> { it.enabled }.thenBy { it.minutesBefore }.thenBy { it.courseId })

fun ScheduleDocument.toPresentationCourses(): List<SchedulePresentationCourse> {
    val templatesById = timeSlotTemplates.associateBy { it.id }
    return courseEntries.map { entry ->
        val template = templatesById[entry.timeSlotTemplateId]
        SchedulePresentationCourse(
            id = entry.id,
            name = entry.name,
            teacher = entry.teacher,
            location = entry.location,
            dayOfWeek = entry.dayOfWeek,
            slotId = entry.timeSlotTemplateId,
            slotLabel = template?.label ?: "时间段待补充",
            startTime = entry.effectiveStartTime(template),
            endTime = entry.effectiveEndTime(template),
            weekParity = entry.weekParity,
            weekNumbers = entry.weekNumbers,
            hasTimeOverride = entry.timeOverride != null,
            useThemeDefaults = entry.colorStyle.useThemeDefaults,
            backgroundColorArgb = entry.colorStyle.backgroundColorArgb,
            textColorArgb = entry.colorStyle.textColorArgb,
            borderColorArgb = entry.colorStyle.borderColorArgb
        )
    }.sortedWith(
        compareBy<SchedulePresentationCourse> { it.dayOfWeek }
            .thenBy { it.startTime.ifBlank { "99:99" } }
            .thenBy { it.endTime.ifBlank { "99:99" } }
            .thenBy { it.name }
    )
}

fun GridSizingConfig.resolveMetrics(availableWidthDp: Float): ScheduleLayoutMetrics {
    val labelColumnWidthDp = 112f
    val spacingDp = 8f
    val dayColumns = 7
    val usableWidth = max(availableWidthDp - labelColumnWidthDp - spacingDp * dayColumns, 0f)
    val preferredCellWidth = if (adaptiveSizing) usableWidth / dayColumns else gridMaxCellWidthDp
    val resolvedCellWidth = preferredCellWidth
        .coerceIn(gridMinCellWidthDp, gridMaxCellWidthDp)
    val widthRatio = if (gridMaxCellWidthDp <= 0f) 1f else resolvedCellWidth / gridMaxCellWidthDp
    val interpolatedHeight = gridMinCellHeightDp + (gridMaxCellHeightDp - gridMinCellHeightDp) * widthRatio
    val resolvedCellHeight = if (compactMode) {
        gridMinCellHeightDp
    } else {
        interpolatedHeight.coerceIn(gridMinCellHeightDp, gridMaxCellHeightDp)
    }
    return ScheduleLayoutMetrics(
        cellWidthDp = resolvedCellWidth,
        cellHeightDp = resolvedCellHeight,
        labelColumnWidthDp = labelColumnWidthDp
    )
}

fun WeekParity.displayLabel(): String = when (this) {
    WeekParity.Every -> "每周"
    WeekParity.Odd -> "单周"
    WeekParity.Even -> "双周"
}

fun WeekParity.shortLabel(): String = when (this) {
    WeekParity.Every -> "每周"
    WeekParity.Odd -> "单"
    WeekParity.Even -> "双"
}

fun ThemeConfig.toPresetSnapshot(): ThemePresetSnapshot = ThemePresetSnapshot(
    themeMode = themeMode,
    useDynamicColor = useDynamicColor,
    accentColorHex = accentColorHex,
    colorTokens = colorTokens,
    background = background,
    font = font,
    gridSizing = gridSizing
)

fun ThemeConfig.applySnapshot(
    snapshot: ThemePresetSnapshot,
    selectedBuiltInPresetId: String = this.selectedBuiltInPresetId
): ThemeConfig = copy(
    themeMode = snapshot.themeMode,
    useDynamicColor = snapshot.useDynamicColor,
    accentColorHex = snapshot.accentColorHex,
    colorTokens = snapshot.colorTokens,
    background = snapshot.background,
    font = snapshot.font,
    selectedBuiltInPresetId = selectedBuiltInPresetId,
    gridSizing = snapshot.gridSizing
)

fun Course.toTemplatePresetSnapshot(
    preferredTimeSlotLabel: String = ""
): CourseTemplatePresetSnapshot = CourseTemplatePresetSnapshot(
    courseName = name,
    teacher = teacher,
    location = location,
    preferredTimeSlotTemplateId = slotId,
    preferredTimeSlotLabel = preferredTimeSlotLabel,
    weekParity = weekParity,
    weekNumbers = weekNumbers,
    timeOverride = overrideStartTime.takeIf { it.isNotBlank() || overrideEndTime.isNotBlank() }?.let {
        CourseTimeOverride(
            startTime = overrideStartTime,
            endTime = overrideEndTime
        )
    },
    colorStyle = CourseColorStyle(
        useThemeDefaults = useThemeDefaults,
        backgroundColorArgb = backgroundColorArgb,
        textColorArgb = textColorArgb,
        borderColorArgb = borderColorArgb
    )
)

fun ScheduleDocument.normalized(updatedAtEpochMillis: Long = this.updatedAtEpochMillis): ScheduleDocument {
    val normalizedTeachingDays = weekConfig.teachingDays
        .map { it.coerceIn(1, 7) }
        .distinct()
        .ifEmpty { listOf(1, 2, 3, 4, 5) }

    val normalizedThemeMode = when (themeConfig.themeMode.lowercase()) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "system"
    }

    val normalizedColorTokens = themeConfig.colorTokens.normalized(
        primaryFallback = themeConfig.accentColorHex.takeIf { it.isNotBlank() }
            ?: ThemeColorTokens().primaryHex
    )
    val normalizedGridSizing = normalizeGridSizingConfig(themeConfig.gridSizing)
    val normalizedBackground = themeConfig.background.normalized(
        defaultColorHex = normalizedColorTokens.backgroundHex
    )
    val normalizedFont = themeConfig.font.normalized()
    val normalizedUserPresets = themeConfig.userPresets
        .distinctBy { it.id }
        .map { it.normalized() }
        .take(24)
    val normalizedCourseTemplatePresets: List<UserCourseTemplatePreset> = courseTemplatePresets
        .distinctBy { it.id }
        .map { preset -> preset.normalized() }
        .take(36)
    val normalizedTransferConfig = themeConfigToTransferConfigFallback(transferConfig)

    val sortedTemplates = timeSlotTemplates
        .distinctBy { it.id }
        .sortedWith(
            compareBy<TimeSlotTemplate> { it.order }
                .thenBy { it.startTime }
                .thenBy { it.endTime }
                .thenBy { it.label }
        )
        .mapIndexed { index, template ->
            template.copy(
                startTime = normalizeTimeString(template.startTime),
                endTime = normalizeTimeString(template.endTime),
                order = index,
                enabled = template.enabled
            )
        }

    return copy(
        version = ScheduleDocument.CURRENT_VERSION,
        weekConfig = WeekConfig(
            firstDayOfWeek = weekConfig.firstDayOfWeek.coerceIn(1, 7),
            teachingDays = normalizedTeachingDays,
            week1MondayDate = (parseIsoLocalDateOrNull(weekConfig.week1MondayDate) ?: DEFAULT_WEEK1_MONDAY).toString()
        ),
        timeSlotTemplates = sortedTemplates,
        courseEntries = courseEntries
            .distinctBy { it.id }
            .sortedWith(
                compareBy<CourseEntry> { it.dayOfWeek.coerceIn(1, 7) }
                    .thenBy { it.effectiveStartTime(sortedTemplates.associateBy { template -> template.id }[it.timeSlotTemplateId]).ifBlank { "99:99" } }
                    .thenBy { it.name }
            )
            .map { entry ->
                entry.copy(
                    dayOfWeek = entry.dayOfWeek.coerceIn(1, 7),
                    weekNumbers = normalizeWeekNumbers(entry.weekNumbers),
                    timeOverride = entry.timeOverride?.normalizedOrNull(),
                    colorStyle = entry.colorStyle.normalized()
                )
            },
        courseTemplatePresets = normalizedCourseTemplatePresets,
        reminderRules = reminderRules
            .distinctBy { it.id }
            .sortedWith(
                compareByDescending<ReminderRule> { it.enabled }
                    .thenBy { it.minutesBefore }
                    .thenBy { it.courseEntryId }
            )
            .map { rule -> rule.copy(minutesBefore = rule.minutesBefore.coerceAtLeast(0)) },
        themeConfig = themeConfig.copy(
            themeMode = normalizedThemeMode,
            accentColorHex = normalizedColorTokens.primaryHex,
            colorTokens = normalizedColorTokens,
            background = normalizedBackground,
            font = normalizedFont,
            selectedBuiltInPresetId = themeConfig.selectedBuiltInPresetId.ifBlank { "campus-breeze" },
            userPresets = normalizedUserPresets,
            gridSizing = normalizedGridSizing
        ),
        transferConfig = normalizedTransferConfig,
        updatedAtEpochMillis = updatedAtEpochMillis
    )
}

private fun themeConfigToTransferConfigFallback(config: TransferConfig): TransferConfig {
    val resolvedMethod = ExportTransport.entries
        .firstOrNull { it.name.equals(config.defaultExportMethod, ignoreCase = true) }
        ?.name
        ?: ExportTransport.FilePack.name
    return config.copy(defaultExportMethod = resolvedMethod)
}

private fun normalizeGridSizingConfig(sizing: GridSizingConfig): GridSizingConfig {
    val minWidth = sizing.gridMinCellWidthDp.coerceAtLeast(88f)
    val maxWidth = sizing.gridMaxCellWidthDp.coerceAtLeast(minWidth)
    val minHeight = sizing.gridMinCellHeightDp.coerceAtLeast(88f)
    val maxHeight = sizing.gridMaxCellHeightDp.coerceAtLeast(minHeight)
    return sizing.copy(
        gridMinCellWidthDp = minWidth,
        gridMaxCellWidthDp = maxWidth,
        gridMinCellHeightDp = minHeight,
        gridMaxCellHeightDp = maxHeight
    )
}

private fun ThemeColorTokens.normalized(primaryFallback: String): ThemeColorTokens = copy(
    primaryHex = normalizeHexColor(primaryHex, primaryFallback),
    secondaryHex = normalizeHexColor(secondaryHex, ThemeColorTokens().secondaryHex),
    tertiaryHex = normalizeHexColor(tertiaryHex, ThemeColorTokens().tertiaryHex),
    backgroundHex = normalizeHexColor(backgroundHex, ThemeColorTokens().backgroundHex),
    surfaceHex = normalizeHexColor(surfaceHex, ThemeColorTokens().surfaceHex),
    surfaceVariantHex = normalizeHexColor(surfaceVariantHex, ThemeColorTokens().surfaceVariantHex),
    onSurfaceHex = normalizeHexColor(onSurfaceHex, ThemeColorTokens().onSurfaceHex),
    outlineHex = normalizeHexColor(outlineHex, ThemeColorTokens().outlineHex)
)

private fun BackgroundConfig.normalized(defaultColorHex: String): BackgroundConfig {
    val resolvedMode = BackgroundMode.entries.firstOrNull { it.name.equals(mode, ignoreCase = true) }
        ?: BackgroundMode.Solid
    val resolvedImageDisplayMode = BackgroundImageDisplayMode.entries.firstOrNull {
        it.name.equals(imageDisplayMode, ignoreCase = true)
    } ?: BackgroundImageDisplayMode.Crop
    return copy(
        mode = resolvedMode.name,
        solidColorHex = normalizeHexColor(solidColorHex, defaultColorHex),
        imageReference = imageReference.trim().take(180),
        blurRadiusDp = blurRadiusDp.coerceIn(0f, 36f),
        imageDisplayMode = resolvedImageDisplayMode.name
    )
}

private fun FontConfig.normalized(): FontConfig {
    val resolvedFontId = BuiltInFontOption.entries
        .firstOrNull { it.name.equals(builtInFontId, ignoreCase = true) }
        ?.name
        ?: BuiltInFontOption.SystemSans.name
    return copy(
        builtInFontId = resolvedFontId,
        customFontLabel = customFontLabel.trim().take(40),
        customFontPath = customFontPath.trim().take(240)
    )
}

private fun ThemePresetSnapshot.normalized(): ThemePresetSnapshot {
    val normalizedThemeMode = when (themeMode.lowercase()) {
        "light" -> "light"
        "dark" -> "dark"
        else -> "system"
    }
    val normalizedColorTokens = colorTokens.normalized(
        primaryFallback = accentColorHex.ifBlank { ThemeColorTokens().primaryHex }
    )
    return copy(
        themeMode = normalizedThemeMode,
        accentColorHex = normalizedColorTokens.primaryHex,
        colorTokens = normalizedColorTokens,
        background = background.normalized(normalizedColorTokens.backgroundHex),
        font = font.normalized(),
        gridSizing = normalizeGridSizingConfig(gridSizing)
    )
}

private fun UserThemePreset.normalized(): UserThemePreset = copy(
    name = name.trim().ifBlank { "我的预设" }.take(32),
    group = group.trim().ifBlank { "用户预设" }.take(24),
    note = note.trim().take(80),
    snapshot = snapshot.normalized()
)

private fun CourseTemplatePresetSnapshot.normalized(): CourseTemplatePresetSnapshot = copy(
    courseName = courseName.trim().ifBlank { "课程模板" }.take(32),
    teacher = teacher.trim().take(24),
    location = location.trim().take(32),
    preferredTimeSlotTemplateId = preferredTimeSlotTemplateId.trim().take(64),
    preferredTimeSlotLabel = preferredTimeSlotLabel.trim().take(24),
    weekNumbers = normalizeWeekNumbers(weekNumbers),
    timeOverride = timeOverride?.normalizedOrNull(),
    colorStyle = colorStyle.normalized()
)

private fun UserCourseTemplatePreset.normalized(): UserCourseTemplatePreset = copy(
    name = name.trim().ifBlank { "课程模板" }.take(32),
    note = note.trim().take(80),
    snapshot = snapshot.normalized()
)

private fun CourseTimeOverride.normalizedOrNull(): CourseTimeOverride? {
    val normalizedStart = normalizeTimeString(startTime)
    val normalizedEnd = normalizeTimeString(endTime)
    if (normalizedStart.isBlank() && normalizedEnd.isBlank()) return null
    return copy(startTime = normalizedStart, endTime = normalizedEnd)
}

private fun CourseColorStyle.normalized(): CourseColorStyle {
    val background = backgroundColorArgb.withOpaqueAlpha()
    val border = borderColorArgb.withOpaqueAlpha()
    val text = if (useThemeDefaults) {
        resolveReadableTextColor(background)
    } else {
        ensureReadableTextColor(background, textColorArgb.withOpaqueAlpha())
    }
    return copy(
        backgroundColorArgb = background,
        borderColorArgb = blendColors(border, background, 0.28f),
        textColorArgb = text
    )
}

fun resolveReadableTextColor(backgroundColorArgb: Int): Int {
    val luminance = calculateRelativeLuminance(backgroundColorArgb.withOpaqueAlpha())
    return if (luminance > 0.42f) 0xFF102A43.toInt() else 0xFFF8FAFC.toInt()
}

fun ensureReadableTextColor(backgroundColorArgb: Int, requestedTextColorArgb: Int): Int {
    val background = backgroundColorArgb.withOpaqueAlpha()
    val requested = requestedTextColorArgb.withOpaqueAlpha()
    val contrast = calculateContrastRatio(background, requested)
    return if (contrast >= 4.2f) requested else resolveReadableTextColor(background)
}

private fun Int.withOpaqueAlpha(): Int = this or (0xFF shl 24)

private fun calculateContrastRatio(backgroundColorArgb: Int, foregroundColorArgb: Int): Float {
    val backgroundLum = calculateRelativeLuminance(backgroundColorArgb)
    val foregroundLum = calculateRelativeLuminance(foregroundColorArgb)
    val lighter = max(backgroundLum, foregroundLum)
    val darker = min(backgroundLum, foregroundLum)
    return (lighter + 0.05f) / (darker + 0.05f)
}

private fun calculateRelativeLuminance(colorArgb: Int): Float {
    fun channel(value: Int): Float {
        val normalized = value / 255f
        return if (normalized <= 0.03928f) normalized / 12.92f else Math.pow(((normalized + 0.055f) / 1.055f).toDouble(), 2.4).toFloat()
    }
    val red = channel((colorArgb shr 16) and 0xFF)
    val green = channel((colorArgb shr 8) and 0xFF)
    val blue = channel(colorArgb and 0xFF)
    return 0.2126f * red + 0.7152f * green + 0.0722f * blue
}

private fun blendColors(first: Int, second: Int, ratio: Float): Int {
    val clampedRatio = ratio.coerceIn(0f, 1f)
    val inverse = 1f - clampedRatio
    val red = (((first shr 16) and 0xFF) * inverse + ((second shr 16) and 0xFF) * clampedRatio).toInt()
    val green = (((first shr 8) and 0xFF) * inverse + ((second shr 8) and 0xFF) * clampedRatio).toInt()
    val blue = (((first) and 0xFF) * inverse + ((second) and 0xFF) * clampedRatio).toInt()
    return (0xFF shl 24) or (red shl 16) or (green shl 8) or blue
}

private fun parseIsoLocalDateOrNull(value: String): LocalDate? = runCatching {
    LocalDate.parse(value)
}.getOrNull()

private fun normalizeTimeString(value: String): String {
    val trimmed = value.trim()
    val segments = trimmed.split(':')
    if (segments.size != 2) return trimmed
    val hour = segments[0].toIntOrNull() ?: return trimmed
    val minute = segments[1].toIntOrNull() ?: return trimmed
    if (hour !in 0..23 || minute !in 0..59) return trimmed
    return "%02d:%02d".format(hour, minute)
}

private fun normalizeHexColor(value: String, fallback: String): String {
    val trimmed = value.trim().uppercase()
    val candidate = if (trimmed.startsWith("#")) trimmed else "#$trimmed"
    return if (candidate.matches(Regex("^#[0-9A-F]{6}$"))) candidate else fallback
}

private fun floorDiv(dividend: Long, divisor: Long): Long {
    var quotient = dividend / divisor
    val remainder = dividend % divisor
    if (remainder != 0L && (dividend xor divisor) < 0) {
        quotient -= 1
    }
    return quotient
}
