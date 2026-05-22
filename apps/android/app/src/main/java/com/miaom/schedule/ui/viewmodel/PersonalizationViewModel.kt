package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.domain.model.BackgroundMode
import com.miaom.schedule.domain.model.BuiltInFontOption
import com.miaom.schedule.domain.model.GridSizingConfig
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.domain.model.ThemeConfig
import com.miaom.schedule.domain.model.ThemePresetSnapshot
import com.miaom.schedule.domain.model.UserThemePreset
import com.miaom.schedule.domain.model.applySnapshot
import com.miaom.schedule.domain.model.toPresetSnapshot
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.util.UUID

data class PersonalizationUiState(
    val document: ScheduleDocument = ScheduleDocument(),
    val builtInPresets: List<BuiltInThemePreset> = builtInThemePresets(),
    val selectedPresetId: String = "campus-breeze"
) {
    val themeConfig: ThemeConfig get() = document.themeConfig
}

data class BuiltInThemePreset(
    val id: String,
    val group: String,
    val name: String,
    val description: String,
    val snapshot: ThemePresetSnapshot
)

class PersonalizationViewModel(
    private val scheduleStore: ScheduleStore
) : ViewModel() {
    val uiState: StateFlow<PersonalizationUiState> = scheduleStore.document
        .map { document ->
            PersonalizationUiState(
                document = document,
                selectedPresetId = document.themeConfig.selectedBuiltInPresetId
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonalizationUiState())

    fun updateThemeMode(themeMode: String) = updateThemeConfig { it.copy(themeMode = themeMode) }

    fun updateDynamicColor(enabled: Boolean) = updateThemeConfig { it.copy(useDynamicColor = enabled) }

    fun updateAccentColor(hex: String) = updateThemeConfig { it.copy(accentColorHex = hex, colorTokens = it.colorTokens.copy(primaryHex = hex)) }

    fun updateColorToken(tokenKey: String, value: String) = updateThemeConfig { config ->
        val tokens = config.colorTokens
        config.copy(
            colorTokens = when (tokenKey) {
                "primary" -> tokens.copy(primaryHex = value)
                "secondary" -> tokens.copy(secondaryHex = value)
                "tertiary" -> tokens.copy(tertiaryHex = value)
                "background" -> tokens.copy(backgroundHex = value)
                "surface" -> tokens.copy(surfaceHex = value)
                "surfaceVariant" -> tokens.copy(surfaceVariantHex = value)
                "onSurface" -> tokens.copy(onSurfaceHex = value)
                "outline" -> tokens.copy(outlineHex = value)
                else -> tokens
            },
            accentColorHex = if (tokenKey == "primary") value else config.accentColorHex
        )
    }

    fun updateBackgroundMode(mode: BackgroundMode) = updateThemeConfig { it.copy(background = it.background.copy(mode = mode.name)) }

    fun updateBackgroundColor(hex: String) = updateThemeConfig { it.copy(background = it.background.copy(solidColorHex = hex)) }

    fun updateBackgroundImageReference(reference: String) =
        updateThemeConfig { it.copy(background = it.background.copy(imageReference = reference)) }

    fun updateBackgroundBlurRadius(value: Float) =
        updateThemeConfig { it.copy(background = it.background.copy(blurRadiusDp = value)) }

    fun updateBuiltInFont(font: BuiltInFontOption) =
        updateThemeConfig { it.copy(font = it.font.copy(builtInFontId = font.name)) }

    fun updatePreferCustomFont(enabled: Boolean) =
        updateThemeConfig { it.copy(font = it.font.copy(preferCustomFont = enabled)) }

    fun updateCustomFontLabel(label: String) =
        updateThemeConfig { it.copy(font = it.font.copy(customFontLabel = label)) }

    fun updateCustomFontPath(path: String) =
        updateThemeConfig { it.copy(font = it.font.copy(customFontPath = path)) }

    fun updateGridSizing(transform: (GridSizingConfig) -> GridSizingConfig) =
        updateThemeConfig { it.copy(gridSizing = transform(it.gridSizing)) }

    fun applyBuiltInPreset(presetId: String) {
        val preset = builtInThemePresets().firstOrNull { it.id == presetId } ?: return
        updateThemeConfig { config ->
            config.applySnapshot(preset.snapshot, selectedBuiltInPresetId = preset.id)
        }
    }

    fun saveCurrentAsUserPreset(name: String) {
        val trimmedName = name.trim()
        if (trimmedName.isBlank()) return
        updateThemeConfig { config ->
            config.copy(
                userPresets = config.userPresets + UserThemePreset(
                    id = UUID.randomUUID().toString(),
                    name = trimmedName,
                    note = "",
                    snapshot = config.toPresetSnapshot()
                )
            )
        }
    }

    fun applyUserPreset(presetId: String) {
        updateThemeConfig { config ->
            val preset = config.userPresets.firstOrNull { it.id == presetId } ?: return@updateThemeConfig config
            config.applySnapshot(preset.snapshot)
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
                    return PersonalizationViewModel(scheduleStore) as T
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
            )
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
