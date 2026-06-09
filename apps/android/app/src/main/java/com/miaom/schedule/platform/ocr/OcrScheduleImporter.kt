package com.miaom.schedule.platform.ocr

import android.content.Context
import android.net.Uri
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.chinese.ChineseTextRecognizerOptions
import com.miaom.schedule.data.transfer.CommonScheduleImportParser
import com.miaom.schedule.data.transfer.CommonScheduleImportResult
import com.miaom.schedule.domain.model.ScheduleDocument
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

data class OcrScheduleImportResult(
    val importResult: CommonScheduleImportResult,
    val rawRecognizedText: String,
    val displayRecognizedText: String,
    val parsedText: String
)

class OcrScheduleImporter(
    private val appContext: Context
) {
    suspend fun importFromImage(uri: Uri, currentDocument: ScheduleDocument): OcrScheduleImportResult {
        val image = InputImage.fromFilePath(appContext, uri)
        val recognizer = TextRecognition.getClient(ChineseTextRecognizerOptions.Builder().build())

        val recognizedText = try {
            recognizer.process(image).await().text.trim()
        } finally {
            recognizer.close()
        }

        return buildOcrImportResult(recognizedText, currentDocument)
    }
}

internal fun buildOcrImportResult(
    recognizedText: String,
    currentDocument: ScheduleDocument,
    normalizer: (String) -> String = OcrScheduleTextNormalizer::normalize,
    parser: (String, ScheduleDocument) -> CommonScheduleImportResult = CommonScheduleImportParser::parse
): OcrScheduleImportResult {
    require(recognizedText.isNotBlank()) { "OCR 没有识别出可用文本。" }
    val normalizedText = normalizer(recognizedText)
    val normalizedAttempt = runCatching { parser(normalizedText, currentDocument) }
    val rawAttempt = runCatching { parser(recognizedText, currentDocument) }
    val (importResult, parsedText) = when {
        normalizedAttempt.isSuccess -> normalizedAttempt.getOrThrow() to normalizedText
        rawAttempt.isSuccess -> rawAttempt.getOrThrow() to recognizedText
        else -> {
            val fallbackText = normalizedText.ifBlank { recognizedText }
            CommonScheduleImportResult(
                document = currentDocument,
                detectedFormat = "OCR 文本",
                importedCourseCount = 0,
                importedTimeSlotCount = 0,
                warnings = listOfNotNull(
                    normalizedAttempt.exceptionOrNull()?.message,
                    rawAttempt.exceptionOrNull()?.message
                ).distinct().ifEmpty {
                    listOf("OCR 已识别出文本，但还不能自动拆成完整课表。可先手动修正预览文本，再继续导入。")
                }
            ) to fallbackText
        }
    }
    return OcrScheduleImportResult(
        importResult = importResult,
        rawRecognizedText = recognizedText,
        displayRecognizedText = normalizedText.ifBlank { recognizedText },
        parsedText = parsedText
    )
}

private suspend fun <T> com.google.android.gms.tasks.Task<T>.await(): T =
    suspendCancellableCoroutine { continuation ->
        addOnSuccessListener { result -> continuation.resume(result) }
        addOnFailureListener { error -> continuation.resumeWithException(error) }
        addOnCanceledListener { continuation.cancel() }
    }
