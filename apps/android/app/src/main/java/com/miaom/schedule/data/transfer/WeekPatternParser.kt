package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.WeekParity
import com.miaom.schedule.domain.model.resolveWeekParity

data class ParsedWeekPattern(
    val weekParity: WeekParity,
    val weekNumbers: List<Int>,
    val warnings: List<String> = emptyList()
)

object WeekPatternParser {
    fun parse(rawValue: String): ParsedWeekPattern {
        return parse(rawValue, forceWeekNumbers = false)
    }

    fun parse(rawValue: String, forceWeekNumbers: Boolean): ParsedWeekPattern {
        val value = normalize(rawValue)
        if (value.isBlank()) {
            return ParsedWeekPattern(weekParity = WeekParity.Every, weekNumbers = emptyList())
        }

        val explicitWeeks = extractWeekNumbers(value, forceWeekNumbers)
        if (explicitWeeks.isNotEmpty()) {
            return ParsedWeekPattern(
                weekParity = resolveWeekParity(explicitWeeks),
                weekNumbers = explicitWeeks
            )
        }

        val parity = when {
            value.contains("单") || value.contains("odd", ignoreCase = true) -> WeekParity.Odd
            value.contains("双") || value.contains("even", ignoreCase = true) -> WeekParity.Even
            else -> WeekParity.Every
        }
        return ParsedWeekPattern(weekParity = parity, weekNumbers = emptyList())
    }

    fun looksLikeWeekPattern(rawValue: String): Boolean {
        val value = normalize(rawValue)
        if (value.isBlank()) return false
        val normalized = value.lowercase()
        return normalized.contains("周") ||
            normalized.contains("week") ||
            normalized.contains("单") ||
            normalized.contains("双") ||
            normalized.contains("odd") ||
            normalized.contains("even") ||
            normalized.contains("每") ||
            looksLikeBareWeekToken(normalized)
    }

    fun parseExplicitParity(rawValue: String): WeekParity? {
        val value = normalize(rawValue)
        return when {
            value.isBlank() -> null
            value.contains("单") || value.contains("odd", ignoreCase = true) -> WeekParity.Odd
            value.contains("双") || value.contains("even", ignoreCase = true) -> WeekParity.Even
            value.contains("每") || value.contains("all") || value.contains("every", ignoreCase = true) -> WeekParity.Every
            else -> null
        }
    }

    private fun extractWeekNumbers(value: String, forceWeekNumbers: Boolean): List<Int> {
        if (!forceWeekNumbers && !looksLikeWeekPattern(value)) return emptyList()

        val normalized = value
            .replace("第", "")
            .replace("周", "")
            .replace("星期", "")
            .replace('（', '(')
            .replace('）', ')')
            .replace('【', '[')
            .replace('】', ']')
            .replace('［', '[')
            .replace('］', ']')
            .replace('，', ',')
            .replace('、', ',')
            .replace(';', ',')
            .replace('；', ',')
            .replace('至', '-')
            .replace('~', '-')
            .replace('～', '-')
            .replace('－', '-')
            .replace('—', '-')
            .replace('–', '-')
            .lowercase()

        val normalizedForLists = Regex("(?<=\\d)\\s+(?=\\d)")
            .replace(normalized) { match ->
                val afterIndex = match.range.last + 1
                val remaining = normalized.substring(afterIndex)
                if (remaining.startsWith("-")) {
                    " "
                } else {
                    ","
                }
            }

        val parityFilter = when {
            normalized.contains("单") || normalized.contains("odd") -> WeekParity.Odd
            normalized.contains("双") || normalized.contains("even") -> WeekParity.Even
            else -> null
        }

        val tokens = Regex("\\d+\\s*-\\s*\\d+|\\d+").findAll(normalizedForLists)
            .map { it.value.replace(" ", "") }
            .toList()
        if (tokens.isEmpty()) return emptyList()

        val weeks = mutableListOf<Int>()
        tokens.forEach { token ->
            if ('-' in token) {
                val parts = token.split('-', limit = 2)
                val start = parts.getOrNull(0)?.toIntOrNull() ?: return@forEach
                val end = parts.getOrNull(1)?.toIntOrNull() ?: return@forEach
                val range = if (start <= end) start..end else end..start
                weeks += range
            } else {
                token.toIntOrNull()?.let(weeks::add)
            }
        }

        val normalizedWeeks = weeks
            .map { it.coerceAtLeast(1) }
            .distinct()
            .sorted()
        return if (parityFilter == null) {
            normalizedWeeks
        } else {
            normalizedWeeks.filter { week ->
                when (parityFilter) {
                    WeekParity.Odd -> week % 2 == 1
                    WeekParity.Even -> week % 2 == 0
                    WeekParity.Every -> true
                }
            }
        }
    }

    private fun looksLikeBareWeekToken(value: String): Boolean {
        val compact = value.replace(Regex("\\s+"), "")
        if (compact.isBlank()) return false
        return Regex("^\\[?\\d{1,2}(?:[-,，、]\\d{1,2})*(?:\\])?(?:单|双|odd|even|all|every)?$").matches(compact) ||
            Regex("^\\[?\\d{1,2}(?:\\s+\\d{1,2})+(?:\\])?(?:单|双|odd|even|all|every)?$").matches(value.trim()) ||
            Regex("^\\[?\\d{1,2}[-~至]\\d{1,2}(?:[,，、]\\d{1,2}(?:[-~至]\\d{1,2})?)*\\]?(?:单|双|odd|even|all|every)?$").matches(compact)
    }

    private fun normalize(rawValue: String): String = rawValue.trim()
}
