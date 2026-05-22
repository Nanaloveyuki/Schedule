package com.miaom.schedule.ui.screen

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.miaom.schedule.ScheduleApplication
import com.miaom.schedule.domain.model.BackgroundImageDisplayMode
import com.miaom.schedule.domain.model.BackgroundMode
import com.miaom.schedule.domain.model.BuiltInFontOption
import com.miaom.schedule.domain.model.ExportTransport
import com.miaom.schedule.domain.model.ThemeConfig
import com.miaom.schedule.domain.model.displayLabel
import com.miaom.schedule.ui.component.EditorExpandableSectionCard
import com.miaom.schedule.ui.component.EditorInlineNote
import com.miaom.schedule.ui.component.EditorSectionCard
import com.miaom.schedule.ui.component.PresetCard
import com.miaom.schedule.ui.component.ThemePreviewCard
import com.miaom.schedule.ui.viewmodel.BuiltInThemePreset
import com.miaom.schedule.ui.viewmodel.ImportExportUiState
import com.miaom.schedule.ui.viewmodel.ImportExportViewModel
import com.miaom.schedule.ui.viewmodel.PersonalizationViewModel
import com.miaom.schedule.ui.viewmodel.PresetsViewModel
import java.io.ByteArrayInputStream

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PersonalizationScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val viewModel: PersonalizationViewModel = viewModel(
        factory = PersonalizationViewModel.factory(appContainer.scheduleStore)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val themeConfig = uiState.themeConfig
    val previewWidthDp = (LocalConfiguration.current.screenWidthDp - 48).coerceAtLeast(260).toFloat()
    var themeExpanded by rememberSaveable { mutableStateOf(true) }
    var backgroundExpanded by rememberSaveable { mutableStateOf(false) }
    var fontExpanded by rememberSaveable { mutableStateOf(false) }
    var sizingExpanded by rememberSaveable { mutableStateOf(false) }
    val backgroundImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            persistReadPermission(context, uri)
            viewModel.applyImportedBackgroundImageReference(uri.toString())
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("个性化") },
                actions = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ThemePreviewCard(
                    themeConfig = themeConfig,
                    previewWidthDp = previewWidthDp,
                    title = "实时预览"
                )
            }
            item {
                EditorExpandableSectionCard(
                    title = "主题",
                    summary = themeSectionSummary(themeConfig),
                    expanded = themeExpanded,
                    onExpandedChange = { themeExpanded = it }
                ) {
                    SettingsSubsection(title = "颜色") {
                        TokenEditorRow("主色", themeConfig.colorTokens.primaryHex) { viewModel.updateColorToken("primary", it) }
                        TokenEditorRow("辅助色", themeConfig.colorTokens.secondaryHex) { viewModel.updateColorToken("secondary", it) }
                        TokenEditorRow("强调色", themeConfig.colorTokens.tertiaryHex) { viewModel.updateColorToken("tertiary", it) }
                    }
                    SettingsSubsection(title = "表面") {
                        TokenEditorRow("背景", themeConfig.colorTokens.backgroundHex) { viewModel.updateColorToken("background", it) }
                        TokenEditorRow("表面", themeConfig.colorTokens.surfaceHex) { viewModel.updateColorToken("surface", it) }
                        TokenEditorRow("轮廓", themeConfig.colorTokens.outlineHex) { viewModel.updateColorToken("outline", it) }
                    }
                }
            }
            item {
                EditorExpandableSectionCard(
                    title = "背景",
                    summary = backgroundSectionSummary(themeConfig),
                    expanded = backgroundExpanded,
                    onExpandedChange = { backgroundExpanded = it }
                ) {
                    SettingsSubsection(title = "背景样式") {
                        FlowChipRow {
                            BackgroundMode.entries.forEach { mode ->
                                FilterChip(
                                    selected = themeConfig.background.mode == mode.name,
                                    onClick = { viewModel.updateBackgroundMode(mode) },
                                    label = { Text(backgroundModeLabel(mode)) }
                                )
                            }
                        }
                        TokenEditorRow("背景纯色", themeConfig.background.solidColorHex) {
                            viewModel.updateBackgroundColor(it)
                        }
                    }
                    SettingsSubsection(title = "图片与模糊") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = themeConfig.background.imageReference,
                                onValueChange = viewModel::updateBackgroundImageReference,
                                label = { Text("图片引用") },
                                placeholder = { Text("content:// 或 /storage/emulated/0/Pictures/schedule.jpg") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )
                            Button(
                                onClick = { backgroundImageLauncher.launch(arrayOf("image/*")) },
                                modifier = Modifier
                                    .widthIn(min = 88.dp)
                                    .fillMaxHeight()
                            ) {
                                Text("导入")
                            }
                        }
                        EditorInlineNote("优先使用系统文件选择器导入图片；也可以继续手动填写路径或 URI。")
                        SettingsSubsection(
                            title = "图片展示方式",
                            description = "切换后会立即应用到当前背景图，并随配置一起保存。"
                        ) {
                            FlowChipRow {
                                BackgroundImageDisplayMode.entries.forEach { displayMode ->
                                    FilterChip(
                                        selected = themeConfig.background.imageDisplayMode == displayMode.name,
                                        onClick = { viewModel.updateBackgroundImageDisplayMode(displayMode) },
                                        label = { Text(backgroundImageDisplayModeLabel(displayMode)) }
                                    )
                                }
                            }
                        }
                        Text("模糊强度 ${themeConfig.background.blurRadiusDp.toInt()} dp")
                        Slider(
                            value = themeConfig.background.blurRadiusDp,
                            onValueChange = viewModel::updateBackgroundBlurRadius,
                            valueRange = 0f..36f
                        )
                    }
                }
            }
            item {
                EditorExpandableSectionCard(
                    title = "字体",
                    summary = fontSectionSummary(themeConfig),
                    expanded = fontExpanded,
                    onExpandedChange = { fontExpanded = it }
                ) {
                    FlowChipRow {
                        BuiltInFontOption.entries.forEach { option ->
                            FilterChip(
                                selected = themeConfig.font.builtInFontId == option.name,
                                onClick = { viewModel.updateBuiltInFont(option) },
                                label = { Text(fontOptionLabel(option)) }
                            )
                        }
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("优先使用自定义字体")
                        }
                        Switch(
                            checked = themeConfig.font.preferCustomFont,
                            onCheckedChange = viewModel::updatePreferCustomFont
                        )
                    }
                    OutlinedTextField(
                        value = themeConfig.font.customFontLabel,
                        onValueChange = viewModel::updateCustomFontLabel,
                        label = { Text("自定义字体名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    OutlinedTextField(
                        value = themeConfig.font.customFontPath,
                        onValueChange = viewModel::updateCustomFontPath,
                        label = { Text("自定义字体路径") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            }
            item {
                EditorExpandableSectionCard(
                    title = "课表尺寸",
                    summary = sizingSectionSummary(themeConfig),
                    expanded = sizingExpanded,
                    onExpandedChange = { sizingExpanded = it }
                ) {
                    GridSizingEditor(
                        minWidth = themeConfig.gridSizing.gridMinCellWidthDp,
                        maxWidth = themeConfig.gridSizing.gridMaxCellWidthDp,
                        minHeight = themeConfig.gridSizing.gridMinCellHeightDp,
                        maxHeight = themeConfig.gridSizing.gridMaxCellHeightDp,
                        adaptive = themeConfig.gridSizing.adaptiveSizing,
                        onMinWidthChange = { value -> viewModel.updateGridSizing { it.copy(gridMinCellWidthDp = value) } },
                        onMaxWidthChange = { value -> viewModel.updateGridSizing { it.copy(gridMaxCellWidthDp = value) } },
                        onMinHeightChange = { value -> viewModel.updateGridSizing { it.copy(gridMinCellHeightDp = value) } },
                        onMaxHeightChange = { value -> viewModel.updateGridSizing { it.copy(gridMaxCellHeightDp = value) } },
                        onAdaptiveChange = { enabled -> viewModel.updateGridSizing { it.copy(adaptiveSizing = enabled) } }
                    )
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun PresetsScreen(onBack: (() -> Unit)? = null) {
    val appContainer = (LocalContext.current.applicationContext as ScheduleApplication).appContainer
    val viewModel: PresetsViewModel = viewModel(
        factory = PresetsViewModel.factory(appContainer.scheduleStore)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val previewWidthDp = (LocalConfiguration.current.screenWidthDp - 48).coerceAtLeast(260).toFloat()
    var newThemePresetName by remember { mutableStateOf("") }
    var newCourseTemplateName by remember { mutableStateOf("") }
    var newCourseTemplateNote by remember { mutableStateOf("") }
    var selectedCourseId by remember(uiState.courses) { mutableStateOf(uiState.courses.firstOrNull()?.id.orEmpty()) }
    var builtInExpanded by rememberSaveable { mutableStateOf(true) }
    var userExpanded by rememberSaveable { mutableStateOf(false) }
    var courseTemplateExpanded by rememberSaveable { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("预设") },
                actions = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            item {
                ThemePreviewCard(
                    themeConfig = uiState.themeConfig,
                    previewWidthDp = previewWidthDp,
                    title = "预设实时预览"
                )
            }
            item {
                EditorExpandableSectionCard(
                    title = "内置主题预设",
                    summary = "共 ${uiState.builtInThemePresets.size} 套",
                    expanded = builtInExpanded,
                    onExpandedChange = { builtInExpanded = it }
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        uiState.builtInThemePresets.groupBy(BuiltInThemePreset::group).forEach { (group, presets) ->
                            SettingsSubsection(title = group) {
                                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                    presets.forEach { preset ->
                                        PresetCard(
                                            title = preset.name,
                                            description = preset.description,
                                            selected = uiState.selectedUserThemePresetId == null && uiState.selectedBuiltInPresetId == preset.id,
                                            onApply = { viewModel.applyBuiltInThemePreset(preset.id) }
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
            item {
                EditorExpandableSectionCard(
                    title = "用户主题预设",
                    summary = if (uiState.themeConfig.userPresets.isEmpty()) "暂无已保存预设" else "已保存 ${uiState.themeConfig.userPresets.size} 套",
                    expanded = userExpanded,
                    onExpandedChange = { userExpanded = it }
                ) {
                    OutlinedTextField(
                        value = newThemePresetName,
                        onValueChange = { newThemePresetName = it },
                        label = { Text("预设名称") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Button(
                        onClick = {
                            viewModel.saveCurrentThemePreset(newThemePresetName)
                            newThemePresetName = ""
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = newThemePresetName.isNotBlank()
                    ) {
                        Text("保存当前主题")
                    }
                    if (uiState.themeConfig.userPresets.isEmpty()) {
                        EditorInlineNote("先保存一套当前样式。")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.themeConfig.userPresets.forEach { preset ->
                                PresetCard(
                                    title = preset.name,
                                    description = preset.note.ifBlank { "主题、背景、字体、尺寸" },
                                    selected = uiState.selectedUserThemePresetId == preset.id,
                                    onApply = { viewModel.applyUserThemePreset(preset.id) }
                                )
                            }
                        }
                    }
                }
            }
            item {
                EditorExpandableSectionCard(
                    title = "课程模板预设",
                    summary = if (uiState.document.courseTemplatePresets.isEmpty()) "暂无课程模板" else "已保存 ${uiState.document.courseTemplatePresets.size} 个",
                    expanded = courseTemplateExpanded,
                    onExpandedChange = { courseTemplateExpanded = it }
                ) {
                    if (uiState.courses.isEmpty()) {
                        EditorInlineNote("先添加一门课程。")
                    } else {
                        FlowChipRow {
                            uiState.courses.forEach { course ->
                                FilterChip(
                                    selected = selectedCourseId == course.id,
                                    onClick = { selectedCourseId = course.id },
                                    label = { Text(course.name) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = newCourseTemplateName,
                            onValueChange = { newCourseTemplateName = it },
                            label = { Text("模板名称") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = newCourseTemplateNote,
                            onValueChange = { newCourseTemplateNote = it },
                            label = { Text("备注") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )
                        Button(
                            onClick = {
                                viewModel.saveCourseTemplatePreset(
                                    courseId = selectedCourseId,
                                    presetName = newCourseTemplateName,
                                    note = newCourseTemplateNote
                                )
                                newCourseTemplateName = ""
                                newCourseTemplateNote = ""
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedCourseId.isNotBlank() && newCourseTemplateName.isNotBlank()
                        ) {
                            Text("保存课程模板")
                        }
                    }
                    if (uiState.document.courseTemplatePresets.isEmpty()) {
                        EditorInlineNote("保存后可直接套用。")
                    } else {
                        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                            uiState.document.courseTemplatePresets.forEach { preset ->
                                val snapshot = preset.snapshot
                                PresetCard(
                                    title = preset.name,
                                    description = buildString {
                                        append(snapshot.courseName)
                                        if (snapshot.preferredTimeSlotLabel.isNotBlank()) {
                                            append(" · ")
                                            append(snapshot.preferredTimeSlotLabel)
                                        }
                                        append(" · ")
                                        append(snapshot.weekParity.displayLabel())
                                        if (preset.note.isNotBlank()) {
                                            append(" · ")
                                            append(preset.note)
                                        }
                                    },
                                    selected = uiState.lastAppliedCourseTemplateId == preset.id,
                                    onApply = { viewModel.applyCourseTemplatePreset(preset.id) }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(onBack: (() -> Unit)? = null) {
    val context = LocalContext.current
    val appContainer = (context.applicationContext as ScheduleApplication).appContainer
    val viewModel: PersonalizationViewModel = viewModel(
        factory = PersonalizationViewModel.factory(appContainer.scheduleStore)
    )
    val importExportViewModel: ImportExportViewModel = viewModel(
        factory = ImportExportViewModel.factory(appContainer.scheduleStore)
    )
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val transferState by importExportViewModel.uiState.collectAsStateWithLifecycle()
    val themeConfig = uiState.themeConfig
    var clipboardImportText by remember { mutableStateOf("") }
    var pendingFileExportBytes by remember { mutableStateOf<ByteArray?>(null) }
    var textImportExpanded by rememberSaveable { mutableStateOf(false) }
    var importExportExpanded by rememberSaveable { mutableStateOf(true) }
    var defaultExportExpanded by rememberSaveable { mutableStateOf(false) }
    var preferencesExpanded by rememberSaveable { mutableStateOf(false) }

    val exportFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/zip")
    ) { uri ->
        val bytes = pendingFileExportBytes ?: transferState.latestExport?.fileBytes ?: return@rememberLauncherForActivityResult
        if (uri != null) {
            context.contentResolver.openOutputStream(uri)?.use { output ->
                output.write(bytes)
            }
        }
        pendingFileExportBytes = null
    }
    val importFileLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                importExportViewModel.importFromFile(ByteArrayInputStream(input.readBytes()))
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                actions = {
                    if (onBack != null) {
                        TextButton(onClick = onBack) { Text("返回") }
                    }
                }
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(innerPadding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            EditorSectionCard(
                title = "当前数据"
            ) {
                EditorInlineNote(transferContentSummary(transferState))
            }
            EditorExpandableSectionCard(
                title = "导入导出",
                summary = transferContentSummary(transferState),
                expanded = importExportExpanded,
                onExpandedChange = { importExportExpanded = it }
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = {
                            val export = importExportViewModel.buildExportNow(context, ExportTransport.FilePack)
                            pendingFileExportBytes = export.fileBytes
                            exportFileLauncher.launch("schedule-${System.currentTimeMillis()}.schedulepack")
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导出文件包")
                    }
                    Button(
                        onClick = {
                            val export = importExportViewModel.buildExportNow(context, ExportTransport.ClipboardPack)
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("课表分享包", export.clipboardText))
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("复制分享文本")
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Button(
                        onClick = { importFileLauncher.launch(arrayOf("application/zip", "*/*")) },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入文件包")
                    }
                    Button(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboardImportText = clipboard.primaryClip?.getItemAt(0)?.coerceToText(context)?.toString().orEmpty()
                            if (clipboardImportText.isNotBlank()) {
                                importExportViewModel.importFromClipboard(clipboardImportText)
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("导入剪贴板")
                    }
                }
                if (!textImportExpanded) {
                    Button(
                        onClick = { textImportExpanded = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("粘贴文本导入")
                    }
                } else {
                    OutlinedTextField(
                        value = clipboardImportText,
                        onValueChange = { clipboardImportText = it },
                        label = { Text("剪贴板文本") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 3
                    )
                    Button(
                        onClick = { importExportViewModel.importFromClipboard(clipboardImportText) },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = clipboardImportText.isNotBlank()
                    ) {
                        Text("从文本导入")
                    }
                }
                if (transferState.lastStatus.isNotBlank()) {
                    EditorInlineNote(transferState.lastStatus)
                }
            }
            EditorExpandableSectionCard(
                title = "默认导出方式",
                summary = exportPreferenceSummary(transferState),
                expanded = defaultExportExpanded,
                onExpandedChange = { defaultExportExpanded = it }
            ) {
                FlowChipRow {
                    ExportTransport.entries.forEach { method ->
                        FilterChip(
                            selected = transferState.defaultExportMethod == method,
                            onClick = { importExportViewModel.updateDefaultExportMethod(method) },
                            label = { Text(exportMethodLabel(method)) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("记住默认方式")
                        Text(
                            text = "关闭后不再记住。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = transferState.rememberDefaultExportMethod,
                        onCheckedChange = importExportViewModel::updateRememberDefault
                    )
                }
            }
            EditorExpandableSectionCard(
                title = "其他偏好",
                summary = preferenceSummary(themeConfig),
                expanded = preferencesExpanded,
                onExpandedChange = { preferencesExpanded = it }
            ) {
                FlowChipRow {
                    listOf("system" to "跟随系统", "light" to "浅色", "dark" to "深色").forEach { (value, label) ->
                        FilterChip(
                            selected = themeConfig.themeMode == value,
                            onClick = { viewModel.updateThemeMode(value) },
                            label = { Text(label) }
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("动态配色")
                        Text(
                            text = "优先使用系统配色。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = themeConfig.useDynamicColor,
                        onCheckedChange = viewModel::updateDynamicColor
                    )
                }
                GridSizingEditor(
                    minWidth = themeConfig.gridSizing.gridMinCellWidthDp,
                    maxWidth = themeConfig.gridSizing.gridMaxCellWidthDp,
                    minHeight = themeConfig.gridSizing.gridMinCellHeightDp,
                    maxHeight = themeConfig.gridSizing.gridMaxCellHeightDp,
                    adaptive = themeConfig.gridSizing.adaptiveSizing,
                    onMinWidthChange = { value -> viewModel.updateGridSizing { it.copy(gridMinCellWidthDp = value) } },
                    onMaxWidthChange = { value -> viewModel.updateGridSizing { it.copy(gridMaxCellWidthDp = value) } },
                    onMinHeightChange = { value -> viewModel.updateGridSizing { it.copy(gridMinCellHeightDp = value) } },
                    onMaxHeightChange = { value -> viewModel.updateGridSizing { it.copy(gridMaxCellHeightDp = value) } },
                    onAdaptiveChange = { enabled -> viewModel.updateGridSizing { it.copy(adaptiveSizing = enabled) } }
                )
                ThemePreviewCard(
                    themeConfig = themeConfig,
                    previewWidthDp = (LocalConfiguration.current.screenWidthDp - 48).coerceAtLeast(260).toFloat(),
                    title = "尺寸预览"
                )
            }
        }
    }
}

@Composable
private fun TokenEditorRow(
    label: String,
    value: String,
    onCommit: (String) -> Unit
) {
    var text by remember(value) { mutableStateOf(value) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it)
        },
        label = { Text(label) },
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun FlowChipRow(content: @Composable () -> Unit) {
    FlowRow(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        content()
    }
}

@Composable
private fun SettingsSubsection(
    title: String,
    description: String? = null,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.34f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleSmall)
            if (!description.isNullOrBlank()) {
                Text(
                    text = description,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            content()
        }
    }
}

private fun themeSectionSummary(themeConfig: ThemeConfig): String =
    "主色 ${themeConfig.colorTokens.primaryHex} · 背景 ${themeConfig.colorTokens.backgroundHex}"

private fun backgroundSectionSummary(themeConfig: ThemeConfig): String {
    val mode = BackgroundMode.entries.firstOrNull { it.name == themeConfig.background.mode } ?: BackgroundMode.Solid
    val source = if (themeConfig.background.imageReference.isBlank()) {
        "纯色 ${themeConfig.background.solidColorHex}"
    } else {
        "已设置图片引用"
    }
    return "${backgroundModeLabel(mode)} · $source"
}

private fun fontSectionSummary(themeConfig: ThemeConfig): String {
    val option = BuiltInFontOption.entries.firstOrNull { it.name == themeConfig.font.builtInFontId }
        ?: BuiltInFontOption.SystemSans
    val customState = if (themeConfig.font.preferCustomFont) "自定义优先" else "内置优先"
    return "${fontOptionLabel(option)} · $customState"
}

private fun sizingSectionSummary(themeConfig: ThemeConfig): String =
    "宽 ${themeConfig.gridSizing.gridMinCellWidthDp.toInt()}-${themeConfig.gridSizing.gridMaxCellWidthDp.toInt()} dp · 高 ${themeConfig.gridSizing.gridMinCellHeightDp.toInt()}-${themeConfig.gridSizing.gridMaxCellHeightDp.toInt()} dp"

private fun transferContentSummary(transferState: ImportExportUiState): String =
    "${transferState.currentDocument.courseEntries.size} 门课程 · ${transferState.currentDocument.timeSlotTemplates.size} 个时间段 · ${transferState.currentDocument.reminderRules.size} 条提醒"

private fun exportPreferenceSummary(transferState: ImportExportUiState): String =
    buildString {
        append("默认 ")
        append(exportMethodLabel(transferState.defaultExportMethod))
        append(if (transferState.rememberDefaultExportMethod) " · 已记住" else " · 不记住")
    }

private fun preferenceSummary(themeConfig: ThemeConfig): String =
    buildString {
        append(themeModeLabel(themeConfig.themeMode))
        append(if (themeConfig.useDynamicColor) " · 动态配色开" else " · 动态配色关")
        append(if (themeConfig.gridSizing.adaptiveSizing) " · 自适应尺寸开" else " · 自适应尺寸关")
    }

private fun themeModeLabel(value: String): String = when (value) {
    "light" -> "浅色"
    "dark" -> "深色"
    else -> "跟随系统"
}

@Composable
private fun GridSizingEditor(
    minWidth: Float,
    maxWidth: Float,
    minHeight: Float,
    maxHeight: Float,
    adaptive: Boolean,
    onMinWidthChange: (Float) -> Unit,
    onMaxWidthChange: (Float) -> Unit,
    onMinHeightChange: (Float) -> Unit,
    onMaxHeightChange: (Float) -> Unit,
    onAdaptiveChange: (Boolean) -> Unit
) {
    SizeField("单元格最小宽度", minWidth, onMinWidthChange)
    SizeField("单元格最大宽度", maxWidth, onMaxWidthChange)
    SizeField("单元格最小高度", minHeight, onMinHeightChange)
    SizeField("单元格最大高度", maxHeight, onMaxHeightChange)
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text("启用自适应缩放")
            Text("按屏幕宽度调整大小。")
        }
        Switch(checked = adaptive, onCheckedChange = onAdaptiveChange)
    }
}

@Composable
private fun SizeField(label: String, value: Float, onCommit: (Float) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toInt().toString()) }
    OutlinedTextField(
        value = text,
        onValueChange = {
            text = it
            onCommit(it.toFloatOrNull() ?: value)
        },
        label = { Text(label) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
        singleLine = true
    )
}

private fun backgroundModeLabel(mode: BackgroundMode): String = when (mode) {
    BackgroundMode.Solid -> "纯色"
    BackgroundMode.SolidBlur -> "纯色+模糊"
    BackgroundMode.Image -> "图片"
    BackgroundMode.ImageBlur -> "图片+模糊"
}

private fun backgroundImageDisplayModeLabel(mode: BackgroundImageDisplayMode): String = when (mode) {
    BackgroundImageDisplayMode.Fill -> "填充"
    BackgroundImageDisplayMode.Fit -> "适应"
    BackgroundImageDisplayMode.Stretch -> "拉伸"
    BackgroundImageDisplayMode.Crop -> "居中裁剪"
}

private fun persistReadPermission(context: Context, uri: Uri) {
    runCatching {
        context.contentResolver.takePersistableUriPermission(
            uri,
            Intent.FLAG_GRANT_READ_URI_PERMISSION
        )
    }
}

private fun fontOptionLabel(option: BuiltInFontOption): String = when (option) {
    BuiltInFontOption.SystemSans -> "系统无衬线"
    BuiltInFontOption.Serif -> "衬线"
    BuiltInFontOption.Rounded -> "圆角"
    BuiltInFontOption.Monospace -> "等宽"
}

private fun exportMethodLabel(method: ExportTransport): String = when (method) {
    ExportTransport.FilePack -> "文件包"
    ExportTransport.ClipboardPack -> "剪贴板文本"
}
