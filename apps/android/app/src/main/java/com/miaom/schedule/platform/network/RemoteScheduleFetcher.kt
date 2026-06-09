package com.miaom.schedule.platform.network

import com.miaom.schedule.data.transfer.ScheduleImportSniffer
import com.miaom.schedule.data.transfer.ScheduleTextDecoder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.net.HttpURLConnection
import java.net.URL

data class RemoteSchedulePayload(
    val requestUrl: String,
    val resolvedUrl: String,
    val contentType: String,
    val bytes: ByteArray
)

class RemoteScheduleFetcher {
    suspend fun fetch(url: String): RemoteSchedulePayload = withContext(Dispatchers.IO) {
        val normalizedUrl = normalizeUrl(url)
        var currentUrl = normalizedUrl
        var redirectCount = 0
        var resolvedPayload: RemoteSchedulePayload? = null

        while (resolvedPayload == null) {
            val connection = (URL(currentUrl).openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = CONNECT_TIMEOUT_MS
                readTimeout = READ_TIMEOUT_MS
                instanceFollowRedirects = false
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Accept", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet,text/calendar,text/html,text/plain,application/zip,*/*")
            }

            try {
                val statusCode = connection.responseCode
                if (statusCode in 300..399) {
                    val location = connection.getHeaderField("Location")?.takeIf { it.isNotBlank() }
                        ?: throw IllegalArgumentException("链接发生重定向，但没有返回新的地址。")
                    currentUrl = URL(URL(currentUrl), location).toString()
                    redirectCount += 1
                    require(redirectCount <= MAX_REDIRECTS) { "链接重定向次数过多。" }
                    continue
                }

                require(statusCode in 200..299) { "下载失败，服务器返回 $statusCode。" }
                val bytes = connection.inputStream.use(::readWithLimit)
                require(bytes.isNotEmpty()) { "链接内容为空。" }
                val effectiveContentType = buildEffectiveContentType(
                    contentType = connection.contentType.orEmpty(),
                    contentDisposition = connection.getHeaderField("Content-Disposition")
                )
                resolveDownloadLinkFromHtml(currentUrl, effectiveContentType, bytes)?.let { nextUrl ->
                    currentUrl = nextUrl
                    redirectCount += 1
                    require(redirectCount <= MAX_REDIRECTS) { "链接重定向次数过多。" }
                    continue
                }
                resolvedPayload = RemoteSchedulePayload(
                    requestUrl = normalizedUrl,
                    resolvedUrl = currentUrl,
                    contentType = effectiveContentType,
                    bytes = bytes
                )
            } finally {
                connection.disconnect()
            }
        }

        resolvedPayload ?: throw IllegalStateException("无法读取链接内容。")
    }

    internal fun resolveDownloadLinkFromHtml(
        currentUrl: String,
        contentType: String,
        bytes: ByteArray
    ): String? {
        if (!contentType.contains("html", ignoreCase = true)) return null
        val text = ScheduleTextDecoder.decode(bytes, contentType).trim()
        if (text.isBlank()) return null
        if (looksLikeScheduleHtml(text) || looksLikeStructuredScheduleText(text)) {
            return null
        }

        val candidates = buildList {
            Regex("(?is)<meta\\s+http-equiv=['\"]refresh['\"][^>]*content=['\"][^>]*url=([^'\";>]+)")
                .findAll(text)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .forEach(::add)

            Regex("(?is)<a\\b[^>]*href=['\"]([^'\"]+)['\"]")
                .findAll(text)
                .map { it.groupValues[1].trim() }
                .filter { it.isNotBlank() }
                .forEach(::add)
        }

        return candidates
            .mapNotNull { candidate -> runCatching { URL(URL(currentUrl), candidate).toString() }.getOrNull() }
            .firstOrNull(::looksLikeDownloadTarget)
    }

    internal fun buildEffectiveContentType(contentType: String, contentDisposition: String?): String {
        val fileName = extractFileNameFromContentDisposition(contentDisposition)
        if (fileName.isBlank()) return contentType
        return listOf(contentType.trim(), "filename=$fileName")
            .filter { it.isNotBlank() }
            .joinToString("; ")
    }

    internal fun extractFileNameFromContentDisposition(headerValue: String?): String {
        val value = headerValue?.trim().orEmpty()
        if (value.isBlank()) return ""

        Regex("filename\\*=UTF-8''([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.let { encoded ->
                return runCatching { java.net.URLDecoder.decode(encoded, Charsets.UTF_8.name()) }.getOrDefault(encoded)
                    .trim('"', '\'')
            }

        return Regex("filename=([^;]+)", RegexOption.IGNORE_CASE)
            .find(value)
            ?.groupValues
            ?.getOrNull(1)
            ?.trim()
            ?.trim('"', '\'')
            .orEmpty()
    }

    private fun looksLikeStructuredScheduleText(text: String): Boolean {
        val normalized = text.trim()
        return Regex("(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天])").containsMatchIn(normalized) ||
            Regex("\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\s*节").containsMatchIn(normalized) ||
            Regex("\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}").containsMatchIn(normalized)
    }

    private fun looksLikeScheduleHtml(text: String): Boolean {
        val normalized = text.trim()
        return Regex("(?is)<table\\b").containsMatchIn(normalized) ||
            Regex("(?is)<tr\\b.*?<t[dh]\\b").containsMatchIn(normalized) ||
            Regex("(?is)class=['\"][^'\"]*course-content[^'\"]*['\"]").containsMatchIn(normalized) ||
            Regex("(?is)class=['\"][^'\"]*course-item-list[^'\"]*['\"]").containsMatchIn(normalized)
    }

    internal fun normalizeUrl(rawUrl: String): String {
        val trimmed = rawUrl.trim()
        require(trimmed.isNotBlank()) { "请输入可用的课表链接。" }
        return when {
            trimmed.startsWith("webcal://", ignoreCase = true) -> "https://" + trimmed.removePrefix(trimmed.substring(0, 9))
            trimmed.startsWith("webcals://", ignoreCase = true) -> "https://" + trimmed.removePrefix(trimmed.substring(0, 10))
            trimmed.startsWith("http://", ignoreCase = true) || trimmed.startsWith("https://", ignoreCase = true) -> trimmed
            else -> "https://$trimmed"
        }
    }

    private fun readWithLimit(input: java.io.InputStream): ByteArray {
        val output = ByteArrayOutputStream()
        val buffer = ByteArray(DEFAULT_BUFFER_SIZE)
        var totalBytes = 0
        while (true) {
            val read = input.read(buffer)
            if (read < 0) break
            totalBytes += read
            require(totalBytes <= MAX_DOWNLOAD_BYTES) { "链接内容过大，暂不支持导入超过 6 MB 的课表。" }
            output.write(buffer, 0, read)
        }
        return output.toByteArray()
    }

    private fun looksLikeDownloadTarget(url: String): Boolean {
        val normalized = url.lowercase()
        val parsedUrl = runCatching { URL(url) }.getOrNull()
        val path = parsedUrl?.path?.lowercase().orEmpty()
        val queryAndFragment = listOfNotNull(parsedUrl?.query, parsedUrl?.ref)
            .joinToString("&")
            .lowercase()
        fun hasKnownExtension(value: String): Boolean {
            return value.endsWith(".ics") ||
                value.endsWith(".xlsx") ||
                value.endsWith(".xls") ||
                value.endsWith(".csv") ||
                value.endsWith(".tsv") ||
                value.endsWith(".json") ||
                value.endsWith(".txt") ||
                value.endsWith(".html")
        }

        return normalized.startsWith("webcal://") ||
            normalized.startsWith("webcals://") ||
            hasKnownExtension(normalized) ||
            hasKnownExtension(path) ||
            Regex("(?:^|[=&])[^=&]*\\.(?:ics|xlsx|xls|csv|tsv|json|txt|html)(?:$|[&#])").containsMatchIn(queryAndFragment) ||
            normalized.contains("download") ||
            normalized.contains("export")
    }

    private companion object {
        const val USER_AGENT = "ScheduleAndroid/1.0"
        const val CONNECT_TIMEOUT_MS = 15_000
        const val READ_TIMEOUT_MS = 20_000
        const val MAX_REDIRECTS = 5
        const val MAX_DOWNLOAD_BYTES = 6 * 1024 * 1024
    }
}
