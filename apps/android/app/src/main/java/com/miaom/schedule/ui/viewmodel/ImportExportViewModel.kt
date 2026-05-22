package com.miaom.schedule.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.transfer.SchedulePackCodec
import com.miaom.schedule.data.transfer.SchedulePackExport
import com.miaom.schedule.domain.model.ExportTransport
import com.miaom.schedule.domain.model.ScheduleDocument
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.InputStream

data class ImportExportUiState(
    val defaultExportMethod: ExportTransport = ExportTransport.FilePack,
    val rememberDefaultExportMethod: Boolean = true,
    val lastStatus: String = "",
    val latestClipboardPayload: String = "",
    val latestExport: SchedulePackExport? = null,
    val currentDocument: ScheduleDocument = ScheduleDocument()
)

class ImportExportViewModel(
    private val scheduleStore: ScheduleStore
) : ViewModel() {
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
                "已准备好文件导出内容。"
            } else {
                "已准备好剪贴板分享内容。"
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
                val result = SchedulePackCodec.decodeClipboardPayload(payload.trim())
                val document = scheduleStore.replace(result.document)
                document
            }.onSuccess {
                state.value = state.value.copy(
                    lastStatus = "已从剪贴板导入 ${it.courseEntries.size} 门课程、${it.timeSlotTemplates.size} 个时间段和 ${it.reminderRules.size} 条提醒。"
                )
            }.onFailure {
                state.value = state.value.copy(lastStatus = "剪贴板内容无法识别，请检查后重试。")
            }
        }
    }

    fun importFromFile(inputStream: InputStream) {
        viewModelScope.launch {
            runCatching {
                inputStream.use { stream ->
                    val result = SchedulePackCodec.decodeFilePack(stream)
                    scheduleStore.replace(result.document)
                }
            }.onSuccess {
                state.value = state.value.copy(
                    lastStatus = "已导入 ${it.courseEntries.size} 门课程、${it.timeSlotTemplates.size} 个时间段和 ${it.reminderRules.size} 条提醒。"
                )
            }.onFailure {
                state.value = state.value.copy(lastStatus = "文件内容无法导入，请确认选择了有效的课表包。")
            }
        }
    }

    fun clearStatus() {
        state.value = state.value.copy(lastStatus = "")
    }

    companion object {
        fun factory(scheduleStore: ScheduleStore): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T {
                    return ImportExportViewModel(scheduleStore) as T
                }
            }
    }
}
