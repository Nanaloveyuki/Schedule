package com.miaom.schedule.domain.model

fun normalizeWeekNumbers(weekNumbers: List<Int>): List<Int> = weekNumbers
    .map { it.coerceAtLeast(1) }
    .distinct()
    .sorted()

fun resolveWeekParity(weekNumbers: List<Int>, fallback: WeekParity = WeekParity.Every): WeekParity {
    val normalized = normalizeWeekNumbers(weekNumbers)
    if (normalized.isEmpty()) return fallback
    val allOdd = normalized.all { it % 2 == 1 }
    val allEven = normalized.all { it % 2 == 0 }
    return when {
        allOdd -> WeekParity.Odd
        allEven -> WeekParity.Even
        else -> WeekParity.Every
    }
}

fun matchesWeekRule(weekParity: WeekParity, weekNumbers: List<Int>, weekIndex: Int): Boolean {
    val normalized = normalizeWeekNumbers(weekNumbers)
    if (normalized.isNotEmpty()) {
        return weekIndex in normalized
    }
    return when (weekParity) {
        WeekParity.Every -> true
        WeekParity.Odd -> weekIndex % 2 == 1
        WeekParity.Even -> weekIndex % 2 == 0
    }
}

fun weekRuleDisplayLabel(weekParity: WeekParity, weekNumbers: List<Int>): String {
    val normalized = normalizeWeekNumbers(weekNumbers)
    if (normalized.isEmpty()) return weekParity.displayLabel()
    return buildWeekRangeLabel(normalized)
}

fun weekRuleShortLabel(weekParity: WeekParity, weekNumbers: List<Int>): String {
    val normalized = normalizeWeekNumbers(weekNumbers)
    if (normalized.isEmpty()) return weekParity.shortLabel()
    return when {
        normalized.size == 1 -> "${normalized.first()}周"
        isSingleContinuousRange(normalized) -> "${normalized.first()}-${normalized.last()}周"
        normalized.size <= 3 -> normalized.joinToString("/") + "周"
        else -> "指定周"
    }
}

private fun buildWeekRangeLabel(weekNumbers: List<Int>): String {
    val segments = mutableListOf<String>()
    var start = weekNumbers.first()
    var previous = start

    weekNumbers.drop(1).forEach { current ->
        if (current == previous + 1) {
            previous = current
        } else {
            segments += formatRange(start, previous)
            start = current
            previous = current
        }
    }
    segments += formatRange(start, previous)
    return segments.joinToString(",") + "周"
}

private fun formatRange(start: Int, end: Int): String = if (start == end) "$start" else "$start-$end"

private fun isSingleContinuousRange(weekNumbers: List<Int>): Boolean =
    weekNumbers.zipWithNext().all { (left, right) -> right == left + 1 }
