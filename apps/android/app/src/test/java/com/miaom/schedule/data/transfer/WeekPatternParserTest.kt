package com.miaom.schedule.data.transfer

import com.miaom.schedule.domain.model.WeekParity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WeekPatternParserTest {
    @Test
    fun `parse continuous week range`() {
        val result = WeekPatternParser.parse("1-8周")

        assertEquals((1..8).toList(), result.weekNumbers)
        assertEquals(WeekParity.Every, result.weekParity)
    }

    @Test
    fun `parse explicit odd weeks from range`() {
        val result = WeekPatternParser.parse("1-16周单周")

        assertEquals(listOf(1, 3, 5, 7, 9, 11, 13, 15), result.weekNumbers)
        assertEquals(WeekParity.Odd, result.weekParity)
    }

    @Test
    fun `fallback to parity without explicit week numbers`() {
        val result = WeekPatternParser.parse("双周")

        assertTrue(result.weekNumbers.isEmpty())
        assertEquals(WeekParity.Even, result.weekParity)
    }

    @Test
    fun `parse bracket week range used by community adapters`() {
        val result = WeekPatternParser.parse("第[1-8]周")

        assertEquals((1..8).toList(), result.weekNumbers)
        assertEquals(WeekParity.Every, result.weekParity)
    }

    @Test
    fun `parse bracket even week range used by html card adapters`() {
        val result = WeekPatternParser.parse("第[9-16]双周")

        assertEquals(listOf(10, 12, 14, 16), result.weekNumbers)
        assertEquals(WeekParity.Even, result.weekParity)
    }

    @Test
    fun `parse bare week range without zhou suffix`() {
        val result = WeekPatternParser.parse("1-8")

        assertEquals((1..8).toList(), result.weekNumbers)
        assertEquals(WeekParity.Every, result.weekParity)
    }

    @Test
    fun `parse bare comma separated week list without zhou suffix`() {
        val result = WeekPatternParser.parse("1,3,5,7")

        assertEquals(listOf(1, 3, 5, 7), result.weekNumbers)
        assertEquals(WeekParity.Odd, result.weekParity)
    }

    @Test
    fun `parse space separated week list with zhou suffix`() {
        val result = WeekPatternParser.parse("1 3 5 7周")

        assertEquals(listOf(1, 3, 5, 7), result.weekNumbers)
        assertEquals(WeekParity.Odd, result.weekParity)
    }

    @Test
    fun `parse ordinal space separated week list with zhou suffix`() {
        val result = WeekPatternParser.parse("第1 3 5周")

        assertEquals(listOf(1, 3, 5), result.weekNumbers)
        assertEquals(WeekParity.Odd, result.weekParity)
    }

    @Test
    fun `parse space separated even week list with parity suffix`() {
        val result = WeekPatternParser.parse("2 4 6 8双周")

        assertEquals(listOf(2, 4, 6, 8), result.weekNumbers)
        assertEquals(WeekParity.Even, result.weekParity)
    }

    @Test
    fun `parse bare even week range without zhou suffix`() {
        val result = WeekPatternParser.parse("2-16双")

        assertEquals(listOf(2, 4, 6, 8, 10, 12, 14, 16), result.weekNumbers)
        assertEquals(WeekParity.Even, result.weekParity)
    }

    @Test
    fun `parse full width bracket week range`() {
        val result = WeekPatternParser.parse("第【1-8】周")

        assertEquals((1..8).toList(), result.weekNumbers)
        assertEquals(WeekParity.Every, result.weekParity)
    }

    @Test
    fun `parse full width bracket even week range`() {
        val result = WeekPatternParser.parse("第［9-16］双周")

        assertEquals(listOf(10, 12, 14, 16), result.weekNumbers)
        assertEquals(WeekParity.Even, result.weekParity)
    }
}
