package com.edgemeeting.sdk.util

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * TDD Test for TimeFormatter
 * 將毫秒時間戳格式化為 HH:MM:SS 格式
 */
class TimeFormatterTest {

    @Test
    fun `should format 0 milliseconds as 00_00_00`() {
        val result = TimeFormatter.formatMillisToTime(0L)
        assertEquals("00:00:00", result)
    }

    @Test
    fun `should format 1000 milliseconds as 00_00_01`() {
        val result = TimeFormatter.formatMillisToTime(1000L)
        assertEquals("00:00:01", result)
    }

    @Test
    fun `should format 61000 milliseconds as 00_01_01`() {
        val result = TimeFormatter.formatMillisToTime(61000L)
        assertEquals("00:01:01", result)
    }

    @Test
    fun `should format 3661000 milliseconds as 01_01_01`() {
        val result = TimeFormatter.formatMillisToTime(3661000L)
        assertEquals("01:01:01", result)
    }

    @Test
    fun `should format 6120 milliseconds as 00_00_06`() {
        // 實際案例：從截圖中的 [6120-9180]
        val result = TimeFormatter.formatMillisToTime(6120L)
        assertEquals("00:00:06", result)
    }

    @Test
    fun `should format 9180 milliseconds as 00_00_09`() {
        // 實際案例：從截圖中的 [6120-9180]
        val result = TimeFormatter.formatMillisToTime(9180L)
        assertEquals("00:00:09", result)
    }

    @Test
    fun `should format 15300 milliseconds as 00_00_15`() {
        // 實際案例：從截圖中的 [15300-18360]
        val result = TimeFormatter.formatMillisToTime(15300L)
        assertEquals("00:00:15", result)
    }

    @Test
    fun `should format time range correctly`() {
        val start = TimeFormatter.formatMillisToTime(6120L)
        val end = TimeFormatter.formatMillisToTime(9180L)
        val range = "[$start-$end]"
        assertEquals("[00:00:06-00:00:09]", range)
    }

    @Test
    fun `should handle large time values correctly`() {
        // 測試 1 小時 23 分 45 秒 = (1*3600 + 23*60 + 45) * 1000 = 5025000 ms
        val result = TimeFormatter.formatMillisToTime(5025000L)
        assertEquals("01:23:45", result)
    }

    @Test
    fun `should handle edge case of 59 minutes 59 seconds`() {
        // 59:59 = (59*60 + 59) * 1000 = 3599000 ms
        val result = TimeFormatter.formatMillisToTime(3599000L)
        assertEquals("00:59:59", result)
    }
}
