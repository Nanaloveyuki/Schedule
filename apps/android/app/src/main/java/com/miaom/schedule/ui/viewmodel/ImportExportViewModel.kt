package com.miaom.schedule.ui.viewmodel

import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.transfer.ImportDraftInbox
import com.miaom.schedule.data.transfer.PendingImportDraft
import com.miaom.schedule.data.transfer.ScheduleImportCoordinator
import com.miaom.schedule.data.transfer.ScheduleImportSniffer
import com.miaom.schedule.data.transfer.ScheduleTextImportKind
import com.miaom.schedule.data.transfer.SchedulePackCodec
import com.miaom.schedule.data.transfer.SchedulePackExport
import com.miaom.schedule.data.transfer.ScheduleImportExecutionResult
import com.miaom.schedule.domain.model.ExportTransport
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.platform.calendar.CalendarImportSource
import com.miaom.schedule.platform.calendar.CalendarScheduleReader
import com.miaom.schedule.platform.network.RemoteScheduleFetcher
import com.miaom.schedule.platform.ocr.OcrScheduleImporter
import com.miaom.schedule.platform.share.mergePendingImageDrafts
import com.miaom.schedule.platform.share.ShareImportSupport
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ImportExportUiState(
    val defaultExportMethod: ExportTransport = ExportTransport.FilePack,
    val rememberDefaultExportMethod: Boolean = true,
    val lastStatus: String = "",
    val lastImportWarnings: List<String> = emptyList(),
    val rawRecognizedOcrText: String = "",
    val lastRecognizedOcrText: String = "",
    val lastParsedOcrText: String = "",
    val stagedImportText: String = "",
    val stagedImportSourceLabel: String = "",
    val availableCalendarSources: List<CalendarImportSource> = emptyList(),
    val selectedCalendarSourceIds: Set<Long> = emptySet(),
    val showCalendarSourcePicker: Boolean = false,
    val isImportingFromOcr: Boolean = false,
    val isImportingFromLink: Boolean = false,
    val isImportingFromCalendar: Boolean = false,
    val latestClipboardPayload: String = "",
    val latestExport: SchedulePackExport? = null,
    val currentDocument: ScheduleDocument = ScheduleDocument()
)

class ImportExportViewModel(
    private val scheduleStore: ScheduleStore,
    private val ocrScheduleImporter: OcrScheduleImporter,
    private val remoteScheduleFetcher: RemoteScheduleFetcher,
    private val calendarScheduleReader: CalendarScheduleReader,
    private val importDraftInbox: ImportDraftInbox
) : ViewModel() {
    private val importCoordinator = ScheduleImportCoordinator(scheduleStore, remoteScheduleFetcher)
    private val state = MutableStateFlow(ImportExportUiState())
    val uiState: StateFlow<ImportExportUiState> = state.asStateFlow()

    init {
        viewModelScope.launch {
            scheduleStore.document.collect { document ->
                val method = ExportTransport.entries.firstOrNull {
                    it.name.equals(document.transferConfig.defaultExportMethod, ignoreCase = true)
                } ?: ExportTransport.FilePack
                state.value = state.value.copy(
                    defaultExportMethod = method,
                    rememberDefaultExportMethod = document.transferConfig.rememberDefaultExportMethod,
                    currentDocument = document
                )
            }
        }
        viewModelScope.launch {
            importDraftInbox.draft.collect { draft ->
                draft ?: return@collect
                applyPendingImportDraft(draft)
                importDraftInbox.clearDraft(draft.id)
            }
        }
    }

    fun updateDefaultExportMethod(method: ExportTransport) {
        viewModelScope.launch {
            scheduleStore.edit { document ->
                document.copy(
                    transferConfig = document.transferConfig.copy(defaultExportMethod = method.name)
                )
            }
        }
    }

    fun updateRememberDefault(enabled: Boolean) {
        viewModelScope.launch {
            scheduleStore.edit { document ->
                document.copy(
                    transferConfig = document.transferConfig.copy(rememberDefaultExportMethod = enabled)
                )
            }
        }
    }

    fun buildExportNow(
        context: android.content.Context,
        method: ExportTransport? = null
    ): SchedulePackExport {
        val document = scheduleStore.document.value
        val resolvedMethod = method ?: state.value.defaultExportMethod
        val export = SchedulePackCodec.encode(context, document)
        if (document.transferConfig.rememberDefaultExportMethod) {
            viewModelScope.launch {
                scheduleStore.edit { current ->
                    current.copy(
                        transferConfig = current.transferConfig.copy(defaultExportMethod = resolvedMethod.name)
                    )
                }
            }
        }
        state.value = state.value.copy(
            latestExport = export,
            latestClipboardPayload = export.clipboardText,
            lastStatus = if (resolvedMethod == ExportTransport.FilePack) {
                "文件已准备好。"
            } else {
                "分享文本已准备好。"
            }
        )
        return export
    }

    fun prepareExport(context: android.content.Context, method: ExportTransport? = null) {
        buildExportNow(context, method)
    }

    fun importFromClipboard(payload: String) {
        viewModelScope.launch {
            runCatching {
                importClipboardPayload(payload.trim())
            }.onSuccess {
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = "无法识别这段文本。",
                    lastImportWarnings = emptyList(),
                    lastRecognizedOcrText = "",
                    rawRecognizedOcrText = "",
                    lastParsedOcrText = "",
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun importFromCommonText(payload: String) {
        viewModelScope.launch {
            runCatching {
                importClipboardPayload(payload.trim())
            }.onSuccess {
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法导入这段课表文本。",
                    lastImportWarnings = emptyList(),
                    lastRecognizedOcrText = "",
                    rawRecognizedOcrText = "",
                    lastParsedOcrText = "",
                    stagedImportText = payload,
                    stagedImportSourceLabel = detectSourceLabel(payload),
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun stageCommonImportText(payload: String) {
        state.value = state.value.copy(
            stagedImportText = payload,
            stagedImportSourceLabel = detectSourceLabel(payload)
        )
    }

    fun updateStagedImportText(payload: String) {
        state.value = state.value.copy(stagedImportText = payload)
    }

    fun useRawRecognizedOcrText() {
        val rawText = state.value.rawRecognizedOcrText.trim()
        if (rawText.isBlank()) return
        state.value = state.value.copy(stagedImportText = rawText)
    }

    fun useParsedOcrText() {
        val parsedText = state.value.lastParsedOcrText.trim()
        if (parsedText.isBlank()) return
        state.value = state.value.copy(stagedImportText = parsedText)
    }

    fun importFromStagedText() {
        val payload = state.value.stagedImportText.trim()
        if (payload.isBlank()) {
            state.value = state.value.copy(lastStatus = "没有可导入的文本。")
            return
        }
        viewModelScope.launch {
            runCatching {
                importClipboardPayload(payload)
            }.onSuccess {
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法导入这段课表文本。",
                    lastImportWarnings = emptyList(),
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun importFromOcrImage(uri: Uri) {
        state.value = state.value.copy(
            isImportingFromOcr = true,
            lastStatus = "正在识别图片中的课表...",
            lastImportWarnings = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                val result = ocrScheduleImporter.importFromImage(uri, scheduleStore.document.value)
                result
            }.onSuccess {
                state.value = state.value.copy(
                    lastStatus = if (it.importResult.importedCourseCount > 0) {
                        "OCR 已识别出文本，请确认或修改后再导入。"
                    } else {
                        "OCR 已识别出文本，但还不能自动整理成完整课表。请先修改预览文本后再导入。"
                    },
                    lastImportWarnings = it.importResult.warnings,
                    rawRecognizedOcrText = it.rawRecognizedText,
                    lastRecognizedOcrText = it.displayRecognizedText,
                    lastParsedOcrText = it.parsedText,
                    stagedImportText = it.parsedText,
                    stagedImportSourceLabel = "OCR 识别文本",
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法从图片识别课表。",
                    lastImportWarnings = emptyList(),
                    lastRecognizedOcrText = "",
                    rawRecognizedOcrText = "",
                    lastParsedOcrText = "",
                    stagedImportText = "",
                    stagedImportSourceLabel = "",
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun importFromOcrImages(uris: List<Uri>) {
        val validUris = uris.distinct().filter { uri ->
            ShareImportSupport.isImagePayload(null, uri.lastPathSegment)
        }
        if (validUris.isEmpty()) {
            state.value = state.value.copy(lastStatus = "没有可识别的课表图片。")
            return
        }

        state.value = state.value.copy(
            isImportingFromOcr = true,
            lastStatus = if (validUris.size == 1) {
                "正在识别图片中的课表..."
            } else {
                "正在识别 ${validUris.size} 张课表图片..."
            },
            lastImportWarnings = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                val drafts = validUris.mapIndexed { index, uri ->
                    val result = ocrScheduleImporter.importFromImage(uri, scheduleStore.document.value)
                    PendingImportDraft(
                        sourceLabel = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
                            ?: "课表图片${index + 1}",
                        statusMessage = if (result.importResult.importedCourseCount > 0) {
                            "OCR 已识别出文本，请确认或修改后再导入。"
                        } else {
                            "OCR 已识别出文本，但还不能自动整理成完整课表。请先修改预览文本后再导入。"
                        },
                        stagedImportText = result.parsedText.ifBlank {
                            result.displayRecognizedText.ifBlank { result.rawRecognizedText }
                        },
                        rawRecognizedText = result.rawRecognizedText,
                        displayRecognizedText = result.displayRecognizedText,
                        parsedText = result.parsedText,
                        warnings = result.importResult.warnings
                    )
                }
                mergePendingImageDrafts(drafts)
            }.onSuccess {
                applyPendingImportDraft(it)
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法从图片识别课表。",
                    lastImportWarnings = emptyList(),
                    lastRecognizedOcrText = "",
                    rawRecognizedOcrText = "",
                    lastParsedOcrText = "",
                    stagedImportText = "",
                    stagedImportSourceLabel = "",
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun importFromLink(url: String) {
        val trimmed = url.trim()
        if (trimmed.isBlank()) {
            state.value = state.value.copy(lastStatus = "请输入可用的课表链接。")
            return
        }
        state.value = state.value.copy(
            isImportingFromLink = true,
            lastStatus = "正在下载链接中的课表...",
            lastImportWarnings = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                val result = importCoordinator.importRemoteUrl(trimmed)
                applyExecutionResult(result)
            }.onSuccess {
                state.value = state.value.copy(isImportingFromLink = false)
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法从链接导入课表。",
                    lastImportWarnings = emptyList(),
                    isImportingFromLink = false
                )
            }
        }
    }

    fun importFromSystemCalendar() {
        state.value = state.value.copy(
            isImportingFromCalendar = true,
            lastStatus = "正在读取可导入的系统日历...",
            lastImportWarnings = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                val sources = calendarScheduleReader.listVisibleCalendars()
                require(sources.isNotEmpty()) { "没有找到包含课程事件的可见系统日历。" }
                val selectedIds = state.value.selectedCalendarSourceIds
                    .takeIf { it.isNotEmpty() }
                    ?.intersect(sources.mapTo(linkedSetOf(), CalendarImportSource::id))
                    ?.takeIf { it.isNotEmpty() }
                    ?: sources.mapTo(linkedSetOf(), CalendarImportSource::id)
                state.value = state.value.copy(
                    availableCalendarSources = sources,
                    selectedCalendarSourceIds = selectedIds,
                    showCalendarSourcePicker = true,
                    isImportingFromCalendar = false,
                    lastStatus = "请选择要导入的系统日历。"
                )
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法读取系统日历。",
                    lastImportWarnings = emptyList(),
                    availableCalendarSources = emptyList(),
                    selectedCalendarSourceIds = emptySet(),
                    showCalendarSourcePicker = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun toggleCalendarSourceSelection(calendarId: Long, selected: Boolean) {
        val nextSelectedIds = state.value.selectedCalendarSourceIds.toMutableSet().apply {
            if (selected) add(calendarId) else remove(calendarId)
        }
        state.value = state.value.copy(selectedCalendarSourceIds = nextSelectedIds)
    }

    fun dismissCalendarSourcePicker() {
        state.value = state.value.copy(
            showCalendarSourcePicker = false,
            isImportingFromCalendar = false
        )
    }

    fun confirmImportFromSelectedCalendars() {
        val selectedIds = state.value.selectedCalendarSourceIds
        if (selectedIds.isEmpty()) {
            state.value = state.value.copy(lastStatus = "请至少选择一个系统日历。")
            return
        }
        state.value = state.value.copy(
            isImportingFromCalendar = true,
            lastStatus = "正在读取系统日历中的课程事件...",
            lastImportWarnings = emptyList()
        )
        viewModelScope.launch {
            runCatching {
                val exported = calendarScheduleReader.exportVisibleEventsAsIcs(selectedIds)
                val result = importCoordinator.importTextPayload(exported.icsText)
                applyExecutionResult(
                    result.copy(statusMessage = "已从 ${exported.matchedCalendarCount} 个系统日历导入 ${exported.importedEventCount} 条课程事件。")
                )
            }.onSuccess {
                state.value = state.value.copy(
                    isImportingFromCalendar = false,
                    showCalendarSourcePicker = false
                )
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法从系统日历导入课表。",
                    lastImportWarnings = emptyList(),
                    isImportingFromCalendar = false,
                    showCalendarSourcePicker = false
                )
            }
        }
    }

    fun importFromFile(bytes: ByteArray, contentType: String? = null) {
        viewModelScope.launch {
            runCatching {
                importFileBytes(bytes, contentType)
            }.onSuccess {
                state.value = state.value.copy(
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }.onFailure {
                state.value = state.value.copy(
                    lastStatus = it.message ?: "无法导入这个文件。",
                    lastImportWarnings = emptyList(),
                    lastRecognizedOcrText = "",
                    rawRecognizedOcrText = "",
                    lastParsedOcrText = "",
                    stagedImportText = "",
                    stagedImportSourceLabel = "",
                    isImportingFromOcr = false,
                    isImportingFromLink = false,
                    isImportingFromCalendar = false
                )
            }
        }
    }

    fun importImageFile(uri: Uri, contentType: String? = null) {
        if (!ShareImportSupport.isImagePayload(contentType, uri.lastPathSegment)) {
            state.value = state.value.copy(lastStatus = "这不是可识别的课表图片。")
            return
        }
        importFromOcrImage(uri)
    }

    private fun applyPendingImportDraft(draft: PendingImportDraft) {
        state.value = state.value.copy(
            lastStatus = draft.statusMessage,
            lastImportWarnings = draft.warnings,
            rawRecognizedOcrText = draft.rawRecognizedText,
            lastRecognizedOcrText = draft.displayRecognizedText,
            lastParsedOcrText = draft.parsedText,
            stagedImportText = draft.stagedImportText,
            stagedImportSourceLabel = draft.sourceLabel,
            isImportingFromOcr = false,
            isImportingFromLink = false,
            isImportingFromCalendar = false
        )
    }

    private suspend fun importFileBytes(bytes: ByteArray, contentType: String?): ScheduleDocument {
        val result = importCoordinator.importBytes(bytes, contentType = contentType, fromFile = true)
        return applyExecutionResult(result)
    }

    private suspend fun importClipboardPayload(payload: String): ScheduleDocument {
        return when (ScheduleImportSniffer.detectTextPayload(payload)) {
            ScheduleTextImportKind.SchedulePack,
            ScheduleTextImportKind.Ics,
            ScheduleTextImportKind.JsonSchedule,
            ScheduleTextImportKind.RemoteUrl,
            ScheduleTextImportKind.HtmlSchedule,
            ScheduleTextImportKind.CommonText -> {
                val result = importCoordinator.importTextPayload(payload)
                applyExecutionResult(result)
            }
            ScheduleTextImportKind.Unsupported -> {
                throw IllegalArgumentException("无法识别这段文本。")
            }
        }
    }

    private suspend fun applyExecutionResult(result: ScheduleImportExecutionResult): ScheduleDocument {
        state.value = state.value.copy(
            lastStatus = result.statusMessage,
            lastImportWarnings = result.warnings,
            lastRecognizedOcrText = "",
            rawRecognizedOcrText = "",
            lastParsedOcrText = "",
            stagedImportText = "",
            stagedImportSourceLabel = "",
            availableCalendarSources = state.value.availableCalendarSources,
            selectedCalendarSourceIds = state.value.selectedCalendarSourceIds,
            showCalendarSourcePicker = false,
            isImportingFromOcr = false,
            isImportingFromLink = false,
            isImportingFromCalendar = false
        )
        return result.document
    }

    private fun detectSourceLabel(payload: String): String = when (ScheduleImportSniffer.detectTextPayload(payload)) {
        ScheduleTextImportKind.HtmlSchedule -> "HTML 课表"
        ScheduleTextImportKind.Ics -> "ICS 日历"
        ScheduleTextImportKind.JsonSchedule -> "JSON 课表"
        ScheduleTextImportKind.RemoteUrl -> "课表链接"
        ScheduleTextImportKind.SchedulePack -> "课表分享包"
        ScheduleTextImportKind.CommonText -> "通用课表文本"
        ScheduleTextImportKind.Unsupported -> ""
    }


    fun clearStatus() {
        state.value = state.value.copy(
            lastStatus = "",
            lastImportWarnings = emptyList(),
            lastRecognizedOcrText = "",
            rawRecognizedOcrText = "",
            lastParsedOcrText = "",
            stagedImportText = "",
            stagedImportSourceLabel = "",
            showCalendarSourcePicker = false,
            isImportingFromOcr = false,
            isImportingFromLink = false,
            isImportingFromCalendar = false
        )
    }

    companion object {
        fun factory(
            scheduleStore: ScheduleStore,
            ocrScheduleImporter: OcrScheduleImporter,
            remoteScheduleFetcher: RemoteScheduleFetcher,
            calendarScheduleReader: CalendarScheduleReader,
            importDraftInbox: ImportDraftInbox
        ): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImportExportViewModel(
                        scheduleStore,
                        ocrScheduleImporter,
                        remoteScheduleFetcher,
                        calendarScheduleReader,
                        importDraftInbox
                    ) as T
                }
            }
    }
}
