package com.miaom.schedule.platform.ocr

object OcrScheduleTextNormalizer {
    fun normalize(rawText: String): String {
        val lines = rawText
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .lines()
            .map(::normalizeLine)
            .filter { it.isNotBlank() }
        if (lines.isEmpty()) return ""

        val mergedLines = mutableListOf<String>()
        val buffer = mutableListOf<String>()
        val pendingPrefix = mutableListOf<String>()

        fun flushBuffer() {
            if (buffer.isEmpty()) return
            mergedLines += buffer.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
            buffer.clear()
        }

        fun flushPendingPrefix() {
            if (pendingPrefix.isEmpty()) return
            mergedLines += pendingPrefix.joinToString(" ")
                .replace(Regex("\\s+"), " ")
                .trim()
            pendingPrefix.clear()
        }

        lines.forEach { line ->
            if (looksLikeRowStart(line)) {
                flushBuffer()
                val prefix = pendingPrefix.joinToString(" ").trim()
                pendingPrefix.clear()
                buffer += if (prefix.isBlank()) line else "$prefix $line".trim()
            } else if (buffer.isEmpty() && looksLikeLabeledFieldLine(line)) {
                pendingPrefix += line
            } else if (buffer.isEmpty() && looksLikeLeadingPrefix(line)) {
                pendingPrefix += line
            } else if (buffer.isNotEmpty()) {
                buffer += line
            } else {
                flushPendingPrefix()
                mergedLines += line
            }
        }
        flushPendingPrefix()
        flushBuffer()

        return mergedLines.joinToString("\n")
    }

    private fun normalizeLine(rawLine: String): String {
        val converted = buildString(rawLine.length) {
            rawLine.forEach { char -> append(convertChar(char)) }
        }
        return normalizeSplitWeekTokens(splitLeadingLabeledValueLine(stripLeadingRowOrdinalBeforeWeekday(converted)))
            .replace(Regex("(\\d{1,2})\\s+(\\d{2})\\s*[-~至]\\s*(\\d{1,2})\\s+(\\d{2})"), "$1:$2-$3:$4")
            .replace(Regex("(\\d{1,2}[:.]\\d{2})\\s*[-~至]\\s*(\\d{1,2})\\s+(\\d{2})"), "$1-$2:$3")
            .replace(Regex("(\\d{1,2})\\s+(\\d{2})\\s*[-~至]\\s*(\\d{1,2}[:.]\\d{2})"), "$1:$2-$3")
            .replace(Regex("(\\d{1,2})\\s*[:.]\\s*(\\d{2})"), "$1:$2")
            .replace(Regex("第\\s*(\\d{1,2})\\s+(\\d{1,2})\\s*节"), "$1-$2节")
            .replace(Regex("第\\s*(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*节"), "$1-$2节")
            .replace(Regex("(\\d{1,2})\\s+(\\d{1,2})\\s*节"), "$1-$2节")
            .replace(Regex("(\\d{1,2})\\s*[-~至]\\s*(\\d{1,2})\\s*节"), "$1-$2节")
            .replace(Regex("(周(?:[一二三四五六日天1-7])|星期(?:[一二三四五六日天1-7])|礼拜(?:[一二三四五六日天1-7]))\\s*[:：]?"), "$1 ")
            .replace(Regex("\\s+"), " ")
            .trim()
    }

    private fun normalizeSplitWeekTokens(line: String): String {
        val trimmed = line.trim()
        if (trimmed.isBlank() || trimmed.contains(':')) return trimmed

        val suffixMatch = Regex("(周(?:单周|双周)?|单周|双周|每周)$").find(trimmed) ?: return trimmed
        val suffix = suffixMatch.value
        val prefixRaw = trimmed.removeSuffix(suffix).trim()
        val hasOrdinal = prefixRaw.startsWith("第")
        val body = prefixRaw.removePrefix("第").trim()
        if (body.isBlank() || body.contains('-') || body.contains(',') || body.contains('，') || body.contains('、')) {
            return trimmed
        }

        val parts = body.split(Regex("\\s+")).filter { it.isNotBlank() }
        if (parts.size < 2) return trimmed

        val numbers = parts.mapNotNull { token -> token.trim('[', ']').toIntOrNull() }
        if (numbers.size != parts.size) return trimmed

        val prefix = if (hasOrdinal) "第" else ""
        return if (numbers.size == 2) {
            "$prefix${numbers[0]}-${numbers[1]}$suffix"
        } else {
            "$prefix${numbers.joinToString(",")}$suffix"
        }
    }

    private fun splitLeadingLabeledValueLine(line: String): String {
        var normalized = line.trim()
        if (normalized.isBlank()) return normalized

        val replacements = listOf(
            Regex("^(课程名称|课程|course)(?=[^:：\\s])", RegexOption.IGNORE_CASE),
            Regex("^(星期|星期几|周几)(?=(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天]))"),
            Regex("^(节次|节|课节|上课节次)(?=(第?\\d))"),
            Regex("^(时间|时间段|起止时间|上课时间段)(?=(\\d{1,2}[:：.]?\\d{2}|第?\\d))"),
            Regex("^(教师|老师)(?=[^:：\\s])"),
            Regex("^(地点|教室|位置|上课地点|授课地点)(?=[^:：\\s])"),
            Regex("^(周次|周数|周安排)(?=(第?\\[?\\d|单周|双周|每周))")
        )

        replacements.forEach { regex ->
            normalized = regex.replace(normalized) { match -> "${match.value} " }
        }
        return normalized
    }

    private fun stripLeadingRowOrdinalBeforeWeekday(line: String): String {
        return line.trimStart().replace(
            Regex(
                "^(?:(?:no\\.?|#)\\s*)?\\d{1,3}(?:[.)、])?\\s+(?=(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天]))",
                RegexOption.IGNORE_CASE
            ),
            ""
        )
    }

    private fun looksLikeRowStart(line: String): Boolean {
        val normalized = line.trim()
        return normalized.startsWith("周一") ||
            normalized.startsWith("周1") ||
            normalized.startsWith("周二") ||
            normalized.startsWith("周2") ||
            normalized.startsWith("周三") ||
            normalized.startsWith("周3") ||
            normalized.startsWith("周四") ||
            normalized.startsWith("周4") ||
            normalized.startsWith("周五") ||
            normalized.startsWith("周5") ||
            normalized.startsWith("周六") ||
            normalized.startsWith("周6") ||
            normalized.startsWith("周天") ||
            normalized.startsWith("周日") ||
            normalized.startsWith("周7") ||
            normalized.startsWith("星期一") ||
            normalized.startsWith("星期1") ||
            normalized.startsWith("星期二") ||
            normalized.startsWith("星期2") ||
            normalized.startsWith("星期三") ||
            normalized.startsWith("星期3") ||
            normalized.startsWith("星期四") ||
            normalized.startsWith("星期4") ||
            normalized.startsWith("星期五") ||
            normalized.startsWith("星期5") ||
            normalized.startsWith("星期六") ||
            normalized.startsWith("星期6") ||
            normalized.startsWith("星期天") ||
            normalized.startsWith("星期日") ||
            normalized.startsWith("星期7") ||
            normalized.startsWith("礼拜一") ||
            normalized.startsWith("礼拜1") ||
            normalized.startsWith("礼拜二") ||
            normalized.startsWith("礼拜2") ||
            normalized.startsWith("礼拜三") ||
            normalized.startsWith("礼拜3") ||
            normalized.startsWith("礼拜四") ||
            normalized.startsWith("礼拜4") ||
            normalized.startsWith("礼拜五") ||
            normalized.startsWith("礼拜5") ||
            normalized.startsWith("礼拜六") ||
            normalized.startsWith("礼拜6") ||
            normalized.startsWith("礼拜天") ||
            normalized.startsWith("礼拜日") ||
            normalized.startsWith("礼拜7")
    }

    private fun looksLikeCoursePrefix(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) return false
        if (looksLikeRowStart(normalized)) return false
        if (normalized.contains(":") || normalized.contains("节") || normalized.contains("周")) return false
        return normalized.length in 2..24
    }

    private fun looksLikeLeadingPrefix(line: String): Boolean {
        val normalized = line.trim()
        if (looksLikeCompleteScheduleLine(normalized)) return false
        return looksLikeCoursePrefix(normalized) ||
            looksLikeLabeledFieldLine(normalized) ||
            looksLikeWeekFragment(normalized) ||
            looksLikeTeacherFragment(normalized) ||
            looksLikeLocationFragment(normalized)
    }

    private fun looksLikeLabeledFieldLine(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) return false
        return Regex(
            "^(课程|课程名称|course|星期|星期几|周几|weekday|节次|上课节次|时间|时间段|起止时间|教师|老师|teacher|地点|教室|位置|周次|周数|周安排)(?:\\s*[:：]|\\s+)",
            RegexOption.IGNORE_CASE
        )
            .containsMatchIn(normalized)
    }

    private fun looksLikeCompleteScheduleLine(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) return false
        val hasDay = Regex("(周[一二三四五六日天]|星期[一二三四五六日天]|礼拜[一二三四五六日天])").containsMatchIn(normalized)
        if (!hasDay) return false
        val hasSlotOrTime = Regex("第?\\d{1,2}(?:\\s*[-~至]\\s*\\d{1,2})?节").containsMatchIn(normalized) ||
            Regex("\\d{1,2}[:：.]?\\d{2}\\s*[-~至]\\s*\\d{1,2}[:：.]?\\d{2}").containsMatchIn(normalized)
        return hasSlotOrTime
    }

    private fun looksLikeWeekFragment(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank() || looksLikeRowStart(normalized)) return false
        return normalized == "单周" ||
            normalized == "双周" ||
            normalized == "每周" ||
            Regex("^第?\\[?\\d+(?:\\s*[-~至]\\s*\\d+)?\\]?周(?:单周|双周)?$").matches(normalized) ||
            Regex("^\\d+(?:\\s*[-~至]\\s*\\d+)?周(?:单周|双周)?$").matches(normalized) ||
            Regex("^\\[?\\d+(?:\\s*[-~至]\\s*\\d+)?(?:\\s*[,，、]\\s*\\d+(?:\\s*[-~至]\\s*\\d+)?) *\\]?(?:单|双)?$").matches(normalized.replace(" ", ""))
    }

    private fun looksLikeTeacherFragment(line: String): Boolean {
        val normalized = line.trim()
        return normalized.endsWith("老师") ||
            normalized.endsWith("教授") ||
            normalized.endsWith("讲师") ||
            normalized.startsWith("教师")
    }

    private fun looksLikeLocationFragment(line: String): Boolean {
        val normalized = line.trim()
        if (normalized.isBlank()) return false
        return normalized.startsWith("地点") ||
            normalized.startsWith("教室") ||
            normalized.contains("楼") ||
            normalized.contains("室") ||
            normalized.contains("馆") ||
            normalized.contains("校区") ||
            normalized.contains("实验") ||
            Regex("[A-Za-z]-?\\d{2,}").containsMatchIn(normalized) ||
            Regex("\\d{3,}").containsMatchIn(normalized)
    }

    private fun convertChar(char: Char): Char = when (char) {
        '０' -> '0'
        '１' -> '1'
        '２' -> '2'
        '３' -> '3'
        '４' -> '4'
        '５' -> '5'
        '６' -> '6'
        '７' -> '7'
        '８' -> '8'
        '９' -> '9'
        '：' -> ':'
        '．' -> '.'
        '，' -> ','
        '；' -> ';'
        '（' -> '('
        '）' -> ')'
        '【', '［' -> '['
        '】', '］' -> ']'
        '－', '—', '–', '～' -> '-'
        else -> char
    }
}
