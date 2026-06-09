package com.miaom.schedule.data.transfer

import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.domain.model.ScheduleDocument
import com.miaom.schedule.platform.network.RemoteScheduleFetcher

data class ScheduleImportExecutionResult(
    val document: ScheduleDocument,
    val statusMessage: String,
    val warnings: List<String>
)

class ScheduleImportCoordinator(
    private val scheduleStore: ScheduleStore,
    private val remoteScheduleFetcher: RemoteScheduleFetcher
) {
    suspend fun importTextPayload(payload: String): ScheduleImportExecutionResult {
        val trimmed = payload.trim()
        val remoteUrl = ScheduleImportSniffer.extractRemoteUrl(trimmed)
        return when (ScheduleImportSniffer.detectTextPayload(trimmed)) {
            ScheduleTextImportKind.SchedulePack -> {
                val result = SchedulePackCodec.decodeClipboardPayload(trimmed)
                applyDocument(
                    document = result.document,
                    statusMessage = buildPackStatus(result.document),
                    warnings = emptyList()
                )
            }
            ScheduleTextImportKind.Ics -> {
                val result = IcsScheduleImporter.parse(trimmed, scheduleStore.document.value)
                applyDocument(
                    document = result.document,
                    statusMessage = "已从 ICS 日历导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            ScheduleTextImportKind.JsonSchedule -> {
                val result = JsonScheduleImportParser.parse(trimmed, scheduleStore.document.value)
                applyDocument(
                    document = result.document,
                    statusMessage = "已通过 ${result.detectedFormat} 导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            ScheduleTextImportKind.RemoteUrl -> importRemoteUrl(remoteUrl ?: trimmed)
            ScheduleTextImportKind.HtmlSchedule,
            ScheduleTextImportKind.CommonText -> {
                val result = parseTextImport(trimmed)
                applyDocument(
                    document = result.document,
                    statusMessage = "已通过${result.detectedFormat}导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            ScheduleTextImportKind.Unsupported -> throw IllegalArgumentException("无法识别这段文本。")
        }
    }

    suspend fun importRemoteUrl(url: String): ScheduleImportExecutionResult {
        val payload = remoteScheduleFetcher.fetch(url)
        return importBytes(payload.bytes, payload.contentType, fromFile = false)
    }

    suspend fun importBytes(
        bytes: ByteArray,
        contentType: String? = null,
        fromFile: Boolean = true
    ): ScheduleImportExecutionResult {
        if (BinarySpreadsheetImportSupport.isLegacyXls(bytes, contentType)) {
            throw IllegalArgumentException("暂不支持导入旧版 Excel `.xls` 课表，请先另存为 `.xlsx`、CSV 或网页表格后再导入。")
        }

        if (XlsxScheduleImportParser.looksLikeXlsx(bytes, contentType)) {
            val result = XlsxScheduleImportParser.parse(bytes, scheduleStore.document.value)
            return applyDocument(
                document = result.document,
                statusMessage = "已通过 ${result.detectedFormat}${if (fromFile) "文件" else ""}导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                warnings = result.warnings
            )
        }

        val decodedText = ScheduleTextDecoder.decode(bytes, contentType)
        return when {
            ScheduleImportSniffer.isIcs(decodedText) -> {
                val result = IcsScheduleImporter.parse(decodedText, scheduleStore.document.value)
                applyDocument(
                    document = result.document,
                    statusMessage = "已从 ICS 日历导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            ScheduleImportSniffer.isLikelyJsonSchedule(decodedText) -> {
                val result = JsonScheduleImportParser.parse(decodedText, scheduleStore.document.value)
                applyDocument(
                    document = result.document,
                    statusMessage = "已通过 ${result.detectedFormat}${if (fromFile) "文件" else ""}导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            ScheduleImportSniffer.isSchedulePackClipboard(decodedText) -> {
                val result = SchedulePackCodec.decodeClipboardPayload(decodedText.trim())
                applyDocument(
                    document = result.document,
                    statusMessage = buildPackStatus(result.document),
                    warnings = emptyList()
                )
            }
            ScheduleImportSniffer.isHtmlDocument(decodedText) || ScheduleImportSniffer.looksLikeCommonScheduleText(decodedText) -> {
                val result = parseTextImport(decodedText.trim())
                applyDocument(
                    document = result.document,
                    statusMessage = "已通过${result.detectedFormat}${if (fromFile) "文件" else ""}导入 ${result.importedCourseCount} 门课程和 ${result.importedTimeSlotCount} 个时间段。",
                    warnings = result.warnings
                )
            }
            else -> {
                val result = SchedulePackCodec.decodeFilePack(bytes.inputStream())
                applyDocument(
                    document = result.document,
                    statusMessage = buildPackStatus(result.document),
                    warnings = emptyList()
                )
            }
        }
    }

    private fun parseTextImport(payload: String): CommonScheduleImportResult {
        return when {
            ScheduleImportSniffer.isHtmlDocument(payload) -> {
                HtmlScheduleImportParser.parse(payload, scheduleStore.document.value)
            }
            else -> CommonScheduleImportParser.parse(payload, scheduleStore.document.value)
        }
    }

    private suspend fun applyDocument(
        document: ScheduleDocument,
        statusMessage: String,
        warnings: List<String>
    ): ScheduleImportExecutionResult {
        val appliedDocument = scheduleStore.replace(document)
        return ScheduleImportExecutionResult(
            document = appliedDocument,
            statusMessage = statusMessage,
            warnings = warnings
        )
    }

    private fun buildPackStatus(document: ScheduleDocument): String {
        return "已导入 ${document.courseEntries.size} 门课程、${document.timeSlotTemplates.size} 个时间段和 ${document.reminderRules.size} 条提醒。"
    }
}
