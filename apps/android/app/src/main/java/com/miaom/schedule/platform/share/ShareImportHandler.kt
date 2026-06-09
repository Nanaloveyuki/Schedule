package com.miaom.schedule.platform.share

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.net.Uri
import com.miaom.schedule.data.repository.ScheduleStore
import com.miaom.schedule.data.transfer.ImportDraftInbox
import com.miaom.schedule.data.transfer.PendingImportDraft
import com.miaom.schedule.data.transfer.ScheduleImportCoordinator
import com.miaom.schedule.platform.ocr.OcrScheduleImporter

class ShareImportHandler(
    context: Context,
    private val importCoordinator: ScheduleImportCoordinator,
    private val ocrScheduleImporter: OcrScheduleImporter,
    private val scheduleStore: ScheduleStore,
    private val importDraftInbox: ImportDraftInbox
) {
    private val appContext = context.applicationContext

    suspend fun handleIntent(intent: Intent): String? {
        return when (intent.action) {
            Intent.ACTION_SEND -> handleSendIntent(intent)
            Intent.ACTION_SEND_MULTIPLE -> handleSendMultipleIntent(intent)
            Intent.ACTION_VIEW -> handleViewIntent(intent)
            else -> null
        }
    }

    private suspend fun handleSendMultipleIntent(intent: Intent): String? {
        val uris = intent.getParcelableArrayListExtra(Intent.EXTRA_STREAM, Uri::class.java).orEmpty()
        if (uris.isEmpty()) return null
        val resolver = appContext.contentResolver
        val typedUris = uris.map { uri ->
            uri to resolver.getType(uri).orEmpty().ifBlank {
                intent.type.orEmpty()
            }.ifBlank {
                ShareImportSupport.guessContentTypeFromPathSegment(uri.lastPathSegment)
            }
        }

        return when (classifyMultiSharePayload(typedUris.map { (uri, contentType) ->
            SharePayloadDescriptor(lastPathSegment = uri.lastPathSegment, contentType = contentType)
        })) {
            MultiShareKind.AllImages -> {
            uris.forEach { uri -> persistReadPermission(uri, intent.flags) }
            val drafts = uris.map { uri -> buildImageDraft(uri) }
            importDraftInbox.stage(mergePendingImageDrafts(drafts))
                "已识别 ${drafts.size} 张课表图片，请在导入页确认后再导入。"
            }
            MultiShareKind.SingleNonImage,
            MultiShareKind.SingleImage -> {
                val importedStatuses = buildList {
                    typedUris.forEach { (uri, contentType) ->
                        persistReadPermission(uri, intent.flags)
                        importFromUri(uri, contentType)?.takeIf { it.isNotBlank() }?.let(::add)
                    }
                }
                importedStatuses.lastOrNull()
            }
            MultiShareKind.Mixed,
            MultiShareKind.MultipleNonImages -> {
                throw IllegalArgumentException(
                    "暂不支持一次分享多种课表文件或多个非图片课表文件，请改为逐个导入，或只分享多张课表图片进行 OCR 合并。"
                )
            }
        }
    }

    private suspend fun handleSendIntent(intent: Intent): String? {
        val streamUri = intent.getParcelableExtra(Intent.EXTRA_STREAM, Uri::class.java)
        if (streamUri != null) {
            persistReadPermission(streamUri, intent.flags)
            return importFromUri(streamUri, intent.type)
        }

        intent.clipData?.let { clipData ->
            val importedStatuses = buildList {
                for (index in 0 until clipData.itemCount) {
                    val itemUri = clipData.getItemAt(index)?.uri ?: continue
                    persistReadPermission(itemUri, intent.flags)
                    importFromUri(itemUri, intent.type)?.takeIf { it.isNotBlank() }?.let(::add)
                }
            }
            if (importedStatuses.isNotEmpty()) {
                return importedStatuses.last()
            }
        }

        val text = intent.getStringExtra(Intent.EXTRA_TEXT)
            ?.takeIf { it.isNotBlank() }
            ?: intent.clipData?.getItemAt(0)?.coerceToText(appContext)?.toString()?.takeIf { it.isNotBlank() }
            ?: return null
        return importCoordinator.importTextPayload(text).statusMessage
    }

    private suspend fun handleViewIntent(intent: Intent): String? {
        val dataUri = intent.data ?: return null
        return if (ShareImportSupport.isRemoteUriScheme(dataUri.scheme)) {
            importCoordinator.importRemoteUrl(dataUri.toString()).statusMessage
        } else {
            persistReadPermission(dataUri, intent.flags)
            importFromUri(dataUri, intent.type)
        }
    }

    private suspend fun importFromUri(uri: Uri, fallbackContentType: String? = null): String? {
        val resolver = appContext.contentResolver
        val contentType = resolver.getType(uri).orEmpty().ifBlank {
            fallbackContentType.orEmpty()
        }.ifBlank {
            ShareImportSupport.guessContentTypeFromPathSegment(uri.lastPathSegment)
        }

        if (ShareImportSupport.isImagePayload(contentType, uri.lastPathSegment)) {
            importDraftInbox.stage(buildImageDraft(uri))
            return "已识别分享的课表图片，请在导入页确认后再导入。"
        }

        val bytes = resolver.openInputStream(uri)?.use { it.readBytes() }
            ?: throw IllegalArgumentException("无法读取分享的课表文件。")
        return importCoordinator.importBytes(bytes, contentType, fromFile = true).statusMessage
    }

    private suspend fun buildImageDraft(uri: Uri): PendingImportDraft {
        val result = ocrScheduleImporter.importFromImage(uri, scheduleStore.document.value)
        val sourceName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "课表图片"
        return PendingImportDraft(
            sourceLabel = sourceName,
            statusMessage = imageDraftStatusMessage(result.importResult.importedCourseCount),
            stagedImportText = result.parsedText.ifBlank { result.displayRecognizedText.ifBlank { result.rawRecognizedText } },
            rawRecognizedText = result.rawRecognizedText,
            displayRecognizedText = result.displayRecognizedText,
            parsedText = result.parsedText,
            warnings = result.importResult.warnings
        )
    }

    private fun imageDraftStatusMessage(importedCourseCount: Int): String {
        return if (importedCourseCount > 0) {
            "OCR 已识别出文本，请确认或修改后再导入。"
        } else {
            "OCR 已识别出文本，但还不能自动整理成完整课表。请先修改预览文本后再导入。"
        }
    }

    private fun persistReadPermission(uri: Uri, intentFlags: Int) {
        if (uri.scheme != ContentResolver.SCHEME_CONTENT) return
        val takeFlags = intentFlags and (Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION)
        if (takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION == 0) return
        runCatching {
            appContext.contentResolver.takePersistableUriPermission(
                uri,
                takeFlags and Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }
    }
}

internal enum class MultiShareKind {
    SingleImage,
    SingleNonImage,
    AllImages,
    Mixed,
    MultipleNonImages
}

internal data class SharePayloadDescriptor(
    val lastPathSegment: String?,
    val contentType: String
)

internal fun classifyMultiSharePayload(payloads: List<SharePayloadDescriptor>): MultiShareKind {
    if (payloads.isEmpty()) return MultiShareKind.MultipleNonImages
    val imageCount = payloads.count { payload ->
        ShareImportSupport.isImagePayload(payload.contentType, payload.lastPathSegment)
    }
    return when {
        payloads.size == 1 && imageCount == 1 -> MultiShareKind.SingleImage
        payloads.size == 1 -> MultiShareKind.SingleNonImage
        imageCount == payloads.size -> MultiShareKind.AllImages
        imageCount == 0 -> MultiShareKind.MultipleNonImages
        else -> MultiShareKind.Mixed
    }
}

internal fun mergePendingImageDrafts(drafts: List<PendingImportDraft>): PendingImportDraft {
    require(drafts.isNotEmpty()) { "没有可合并的 OCR 草稿。" }
    if (drafts.size == 1) return drafts.first()

    val mergedImported = drafts.any { draft ->
        draft.statusMessage.contains("请确认或修改后再导入") && !draft.statusMessage.contains("还不能自动整理")
    }
    return PendingImportDraft(
        sourceLabel = "分享的课表图片(${drafts.size}张)",
        statusMessage = if (mergedImported) {
            "已识别 ${drafts.size} 张课表图片，请确认或修改后再导入。"
        } else {
            "已识别 ${drafts.size} 张课表图片，但还不能自动整理成完整课表。请先修改预览文本后再导入。"
        },
        stagedImportText = drafts.joinToString("\n") { it.stagedImportText.trim() }.trim(),
        rawRecognizedText = drafts.joinToString("\n\n") { it.rawRecognizedText.trim() }.trim(),
        displayRecognizedText = drafts.joinToString("\n\n") { it.displayRecognizedText.trim() }.trim(),
        parsedText = drafts.joinToString("\n") { it.parsedText.trim().ifBlank { it.stagedImportText.trim() } }.trim(),
        warnings = drafts.flatMap { it.warnings }.distinct()
    )
}
