package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.domain.model.BackgroundMode
import com.miaom.schedule.domain.model.BuiltInFontOption
import com.miaom.schedule.domain.model.Course
import com.miaom.schedule.domain.model.CourseTemplatePresetSnapshot
import com.miaom.schedule.domain.model.GridSizingConfig
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.ThemeConfig
import com.miaom.schedule.domain.model.ThemePresetSnapshot
import com.miaom.schedule.domain.model.TimeSlot
import com.miaom.schedule.domain.model.UserCourseTemplatePreset
import com.miaom.schedule.domain.model.UserThemePreset
import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.applySnapshot
import com.miaom.schedule.domain.model.toCourses
import com.miaom.schedule.domain.model.toEntry
import com.miaom.schedule.domain.model.toPresetSnapshot
import com.miaom.schedule.domain.model.toTimeSlots
import com.miaom.schedule.domain.model.toTemplatePresetSnapshot
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class CourseTemplatePresetDraft(
    val sourceCourseId: String = "",
    val name: String = "",
    val note: String = ""
)

data class PresetsUiState(
    val document: ScheduleDocument = ScheduleDocument(),
    val builtInThemePresets: List<BuiltInThemePreset> = builtInThemePresets(),
    val selectedBuiltInPresetId: String = "campus-breeze",
    val selectedUserThemePresetId: String? = null,
    val lastAppliedCourseTemplateId: String? = null,
    val courses: List<Course> = emptyList(),
    val slots: List<TimeSlot> = emptyList(),
    val courseTemplateDraft: CourseTemplatePresetDraft = CourseTemplatePresetDraft()
) {
    val themeConfig: ThemeConfig get() = document.themeConfig
}

class PresetsViewModel(
    private val scheduleStore: ScheduleStore
) : ViewModel() {
    private val selectedUserThemePresetId = MutableStateFlow<String?>(null)
    private val lastAppliedCourseTemplateId = MutableStateFlow<String?>(null)

    val uiState: StateFlow<PresetsUiState> = combine(
        scheduleStore.document,
        selectedUserThemePresetId,
        lastAppliedCourseTemplateId
    ) { document, selectedUserPresetId, lastAppliedTemplateId ->
            PresetsUiState(
                document = document,
                selectedBuiltInPresetId = document.themeConfig.selectedBuiltInPresetId,
                courses = document.toCourses(),
                slots = document.toTimeSlots(),
                selectedUserThemePresetId = selectedUserPresetId,
                lastAppliedCourseTemplateId = lastAppliedTemplateId
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PresetsUiState())

    fun applyBuiltInThemePreset(presetId: String) {
        val preset = builtInThemePresets().firstOrNull { it.id == presetId } ?: return
        selectedUserThemePresetId.value = null
        updateThemeConfig { config -> config.applySnapshot(preset.snapshot, selectedBuiltInPresetId = preset.id) }
    }

    fun saveCurrentThemePreset(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        updateThemeConfig { config ->
            config.copy(
                userPresets = config.userPresets + UserThemePreset(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    note = "来自当前主题配置",
                    snapshot = config.toPresetSnapshot()
                )
            )
        }
    }

    fun applyUserThemePreset(presetId: String) {
        selectedUserThemePresetId.value = presetId
        updateThemeConfig { config ->
            val preset = config.userPresets.firstOrNull { it.id == presetId } ?: return@updateThemeConfig config
            config.applySnapshot(preset.snapshot)
        }
    }

    fun saveCourseTemplatePreset(courseId: String, presetName: String, note: String) {
        val document = uiState.value.document
        val course = document.toCourses().firstOrNull { it.id == courseId } ?: return
        val templateLabel = document.toTimeSlots().firstOrNull { it.id == course.slotId }?.label.orEmpty()
        val trimmedName = presetName.trim().ifBlank { course.name }

        viewModelScope.launch {
            scheduleStore.edit { current ->
                current.copy(
                    courseTemplatePresets = current.courseTemplatePresets + UserCourseTemplatePreset(
                        id = UUID.randomUUID().toString(),
                        name = trimmedName,
                        note = note.trim(),
                        snapshot = course.toTemplatePresetSnapshot(templateLabel)
                    )
                )
            }
        }
    }

    fun applyCourseTemplatePreset(presetId: String) {
        val current = uiState.value.document
        val preset = current.courseTemplatePresets.firstOrNull { it.id == presetId } ?: return
        val snapshot = preset.snapshot
        val slotId = snapshot.preferredTimeSlotTemplateId.takeIf { preferredId ->
            current.timeSlotTemplates.any { it.id == preferredId }
        } ?: current.timeSlotTemplates.firstOrNull()?.id.orEmpty()
        if (slotId.isBlank()) return

        viewModelScope.launch {
            scheduleStore.edit { document ->
                document.copy(
                    courseEntries = document.courseEntries + Course(
                        id = UUID.randomUUID().toString(),
                        name = snapshot.courseName,
                        teacher = snapshot.teacher,
                        location = snapshot.location,
                        dayOfWeek = 1,
                        slotId = slotId,
                        weekParity = snapshot.weekParity,
                        weekNumbers = snapshot.weekNumbers,
                        overrideStartTime = snapshot.timeOverride?.startTime.orEmpty(),
                        overrideEndTime = snapshot.timeOverride?.endTime.orEmpty(),
                        useThemeDefaults = snapshot.colorStyle.useThemeDefaults,
                        backgroundColorArgb = snapshot.colorStyle.backgroundColorArgb,
                        textColorArgb = snapshot.colorStyle.textColorArgb,
                        borderColorArgb = snapshot.colorStyle.borderColorArgb
                    ).toEntry()
                )
            }
            lastAppliedCourseTemplateId.value = presetId
        }
    }

    private fun updateThemeConfig(transform: (ThemeConfig) -> ThemeConfig) {
        viewModelScope.launch {
            scheduleStore.edit { document ->
                document.copy(themeConfig = transform(document.themeConfig))
            }
        }
    }

    companion object {
        fun factory(scheduleStore: ScheduleStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return PresetsViewModel(scheduleStore) as T
                }
            }
    }
}

private fun builtInThemePresets(): List<BuiltInThemePreset> = listOf(
    BuiltInThemePreset(
        id = "campus-breeze",
        group = "校园",
        name = "校园清风",
        description = "米白背景配青绿色主色，适合日常课程查看。",
        snapshot = ThemePresetSnapshot()
    ),
    BuiltInThemePreset(
        id = "library-paper",
        group = "阅读",
        name = "纸页书架",
        description = "偏暖背景和深灰文字，适合长时间阅读列表。",
        snapshot = ThemePresetSnapshot(
            accentColorHex = "#8A5A44",
            colorTokens = ThemeConfig().colorTokens.copy(
                primaryHex = "#8A5A44",
                secondaryHex = "#6E7C63",
                tertiaryHex = "#A0755E",
                backgroundHex = "#F4EFE5",
                surfaceHex = "#FFF8F0",
                surfaceVariantHex = "#E4DBCE",
                onSurfaceHex = "#2A221D",
                outlineHex = "#8F8175"
            ),
            background = ThemeConfig().background.copy(mode = BackgroundMode.Solid.name),
            font = ThemeConfig().font.copy(builtInFontId = BuiltInFontOption.Serif.name)
        )
    ),
    BuiltInThemePreset(
        id = "studio-grid",
        group = "效率",
        name = "工作台网格",
        description = "对比更清晰，搭配更紧凑的课表单元格。",
        snapshot = ThemePresetSnapshot(
            accentColorHex = "#345BA8",
            colorTokens = ThemeConfig().colorTokens.copy(
                primaryHex = "#345BA8",
                secondaryHex = "#507A61",
                tertiaryHex = "#7A5E9C",
                backgroundHex = "#F3F6FB",
                surfaceHex = "#FCFDFF",
                surfaceVariantHex = "#DDE5F0",
                onSurfaceHex = "#1C2430",
                outlineHex = "#718096"
            ),
            background = ThemeConfig().background.copy(mode = BackgroundMode.SolidBlur.name, solidColorHex = "#F3F6FB"),
            font = ThemeConfig().font.copy(builtInFontId = BuiltInFontOption.Rounded.name),
            gridSizing = GridSizingConfig(
                gridMinCellWidthDp = 104f,
                gridMaxCellWidthDp = 152f,
                gridMinCellHeightDp = 96f,
                gridMaxCellHeightDp = 140f,
                compactMode = false,
                adaptiveSizing = true
            )
        )
    )
)
