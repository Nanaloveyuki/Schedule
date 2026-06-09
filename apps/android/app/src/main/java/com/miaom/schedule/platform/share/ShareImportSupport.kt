package com.miaom.schedule.platform.share

import java.util.Locale

internal object ShareImportSupport {
    private val knownExtensions = listOf(
        ".xlsx" to "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        ".xls" to "application/vnd.ms-excel",
        ".csv" to "text/csv",
        ".tsv" to "text/tab-separated-values",
        ".ics" to "text/calendar",
        ".html" to "text/html",
        ".htm" to "text/html",
        ".json" to "application/json",
        ".txt" to "text/plain",
        ".md" to "text/plain",
        ".schedulepack" to "application/zip",
        ".zip" to "application/zip",
        ".png" to "image/png",
        ".jpg" to "image/jpeg",
        ".jpeg" to "image/jpeg",
        ".webp" to "image/webp",
        ".heic" to "image/heic",
        ".heif" to "image/heif",
        ".bmp" to "image/bmp"
    )

    fun isRemoteUriScheme(scheme: String?): Boolean {
        val normalizedScheme = scheme.orEmpty().lowercase(Locale.ROOT)
        return normalizedScheme == "http" || normalizedScheme == "https" || normalizedScheme == "webcal" || normalizedScheme == "webcals"
    }

    fun isImagePayload(contentType: String?, lastPathSegment: String? = null): Boolean {
        val normalizedType = contentType.orEmpty().lowercase(Locale.ROOT)
        if (normalizedType.startsWith("image/")) return true
        return guessContentTypeFromPathSegment(lastPathSegment).startsWith("image/")
    }

    fun guessContentTypeFromPathSegment(lastPathSegment: String?): String {
        val candidate = lastPathSegment.orEmpty().lowercase(Locale.ROOT)
        knownExtensions.firstOrNull { (extension, _) -> candidate.endsWith(extension) }
            ?.let { return it.second }

        val query = candidate.substringAfter('?', "")
        if (query.isBlank()) return ""
        return query.split('&')
            .map { it.substringAfter('=', "") }
            .firstNotNullOfOrNull { encodedValue ->
                val decoded = runCatching {
                    java.net.URLDecoder.decode(encodedValue, Charsets.UTF_8.name())
                }.getOrDefault(encodedValue).lowercase(Locale.ROOT)
                knownExtensions.firstOrNull { (extension, _) -> decoded.endsWith(extension) }?.second
            }
            .orEmpty()
    }
}
