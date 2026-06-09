package com.miaom.schedule.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekRuleSupportTest {
    @Test
    fun `matches explicit week numbers before parity`() {
        assertTrue(matchesWeekRule(WeekParity.Every, listOf(2, 4, 6), 4))
        assertFalse(matchesWeekRule(WeekParity.Odd, listOf(2, 4, 6), 5))
    }

    @Test
    fun `display label shows explicit ranges`() {
        assertEquals("1-4,6周", weekRuleDisplayLabel(WeekParity.Every, listOf(1, 2, 3, 4, 6)))
    }

    @Test
    fun `short label collapses larger explicit rules`() {
        assertEquals("指定周", weekRuleShortLabel(WeekParity.Every, listOf(1, 3, 5, 7)))
    }
}
