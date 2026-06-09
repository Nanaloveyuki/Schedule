package com.miaom.schedule.data.transfer

enum class ScheduleTextImportKind {
    SchedulePack,
    Ics,
    JsonSchedule,
    RemoteUrl,
    HtmlSchedule,
    CommonText,
    Unsupported
}

object ScheduleImportSniffer {
    fun detectTextPayload(text: String): ScheduleTextImportKind {
        val normalized = normalized(text)
        return when {
            normalized.isBlank() -> ScheduleTextImportKind.Unsupported
            isSchedulePackClipboard(normalized) -> ScheduleTextImportKind.SchedulePack
            isIcs(normalized) -> ScheduleTextImportKind.Ics
            isLikelyJsonSchedule(normalized) -> ScheduleTextImportKind.JsonSchedule
            extractRemoteUrl(normalized) != null -> ScheduleTextImportKind.RemoteUrl
            isHtmlDocument(normalized) -> ScheduleTextImportKind.HtmlSchedule
            looksLikeCommonScheduleText(normalized) -> ScheduleTextImportKind.CommonText
            else -> ScheduleTextImportKind.Unsupported
        }
    }

    fun extractRemoteUrl(text: String): String? {
        val normalized = normalized(text)
        if (normalized.isBlank()) return null
        sanitizeRemoteUrlCandidate(normalized)
            ?.takeIf(::looksLikeRemoteUrl)
            ?.let { return it }

        normalized.lines()
            .map(String::trim)
            .filter(String::isNotBlank)
            .forEach { line ->
                sanitizeRemoteUrlCandidate(line)
                    ?.takeIf(::looksLikeRemoteUrl)
                    ?.let { return it }
            }

        Regex("(?i)(?:https?|webcals?)://\\S+|www\\.[^\\s]+")
            .findAll(normalized)
            .forEach { match ->
                sanitizeRemoteUrlCandidate(match.value)
                    ?.takeIf(::looksLikeRemoteUrl)
                    ?.let { return it }
            }
        return null
    }

    fun looksLikeRemoteUrl(text: String): Boolean {
        val normalized = normalized(text)
        if (normalized.isBlank() || normalized.contains('\n') || normalized.contains('\r')) return false
        return normalized.matches(Regex("(?i)^(https?|webcals?)://\\S+$")) ||
            normalized.matches(Regex("(?i)^www\\.[^\\s]+$")) ||
            normalized.matches(Regex("(?i)^[a-z0-9][a-z0-9.-]*\\.[a-z]{2,}(/\\S*)?$"))
    }

    fun isIcs(text: String): Boolean =
        normalized(text).contains("BEGIN:VCALENDAR")

    fun isSchedulePackClipboard(text: String): Boolean =
        normalized(text).startsWith("SCHEDULEPACK:1:")

    fun isLikelyJsonSchedule(text: String): Boolean {
        val normalized = normalized(text)
        if (!(normalized.startsWith("{") || normalized.startsWith("["))) return false
        return JsonScheduleImportParser.looksLikeSupportedPayload(normalized)
    }

    fun isHtmlDocument(text: String): Boolean {
        val normalized = normalized(text)
        return normalized.startsWith("<!doctype html", ignoreCase = true) ||
            normalized.startsWith("<html", ignoreCase = true) ||
            Regex("(?is)<table\\b").containsMatchIn(normalized) ||
            Regex("(?is)<tr\\b.*?<t[dh]\\b").containsMatchIn(normalized) ||
            Regex("(?is)class=['\"][^'\"]*course-content[^'\"]*['\"]").containsMatchIn(normalized) ||
            Regex("(?is)class=['\"][^'\"]*course-item-list[^'\"]*['\"]").containsMatchIn(normalized)
    }

    fun looksLikeCommonScheduleText(text: String): Boolean {
        val normalized = normalized(text)
        return normalized.contains("周") ||
            normalized.contains("星期") ||
            normalized.contains("礼拜") ||
            Regex("\\d{1,2}\\s*[-~至]\\s*\\d{1,2}\\s*节").containsMatchIn(normalized) ||
            Regex("\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}").containsMatchIn(normalized) ||
            normalized.contains('|') ||
            normalized.contains(',') ||
            normalized.contains("\t")
    }

    private fun sanitizeRemoteUrlCandidate(value: String): String? {
        return value.trim()
            .trimStart('(', '（', '[', '【', '<', '"', '\'')
            .trimEnd(')', '）', ']', '】', '>', '"', '\'', '。', '，', ',', '；', ';')
            .takeIf { it.isNotBlank() }
    }

    private fun normalized(text: String): String =
        text.replace("\uFEFF", "").trim()
}
