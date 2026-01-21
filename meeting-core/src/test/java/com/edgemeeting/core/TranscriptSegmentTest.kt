package com.edgemeeting.core

import com.edgemeeting.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptSegmentTest {

    // 驗證 startTimeMs 與 endTimeMs 的基本行為
    @Test
    fun should_create_segment_with_valid_time_range() {
        // 測試：正常的時間區間
        val segment = TranscriptSegment(
            id = "test-1",
            text = "Hello world",
            speakerId = "Speaker_A",
            isFinal = true,
            startTimeMs = 1000,
            endTimeMs = 2500
        )

        assertEquals(1000, segment.startTimeMs)
        assertEquals(2500, segment.endTimeMs)
        assertEquals(1500, segment.durationMs)
    }

    @Test
    fun should_throw_exception_when_endTime_is_less_than_startTime() {
        // 測試：結束時間早於開始時間應拋出例外
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptSegment(
                id = "test-1",
                text = "這是錯誤的資料",
                speakerId = "unknown",
                isFinal = false,
                startTimeMs = 1000,
                endTimeMs = 500
            )
        }
    }

    @Test
    fun should_throw_exception_when_startTime_is_negative() {
        // 測試：開始時間為負數應拋出例外
        assertThrows(IllegalArgumentException::class.java) {
            TranscriptSegment(
                id = "test-2",
                text = "負時間測試",
                speakerId = "Speaker_B",
                isFinal = false,
                startTimeMs = -100,
                endTimeMs = 1000
            )
        }
    }

    @Test
    fun should_allow_equal_start_and_end_time() {
        // 測試：開始時間與結束時間相等（0 duration）應為合法
        val segment = TranscriptSegment(
            id = "test-3",
            text = "瞬間語音",
            speakerId = "Speaker_A",
            isFinal = true,
            startTimeMs = 1000,
            endTimeMs = 1000
        )

        assertEquals(0, segment.durationMs)
    }

    // 驗證 languageCode 的行為
    @Test
    fun should_create_segment_with_language_code() {
        // 測試：指定語言代碼
        val segment = TranscriptSegment(
            id = "test-4",
            text = "Hello world",
            speakerId = "Speaker_A",
            isFinal = true,
            startTimeMs = 0,
            endTimeMs = 1000,
            languageCode = "en"
        )

        assertEquals("en", segment.languageCode)
    }

    @Test
    fun should_create_segment_without_language_code() {
        // 測試：未指定語言代碼（預設 null）
        val segment = TranscriptSegment(
            id = "test-5",
            text = "自動偵測",
            speakerId = "Speaker_B",
            isFinal = false,
            startTimeMs = 500,
            endTimeMs = 1500
        )

        assertNull(segment.languageCode)
    }

    @Test
    fun should_support_multiple_language_codes() {
        // 測試：支援多種語言代碼（zh, en, ja 等）
        val languages = listOf("zh", "en", "ja", "ko", "fr", "de")

        languages.forEach { lang ->
            val segment = TranscriptSegment(
                id = "test-$lang",
                text = "Test for $lang",
                speakerId = "Speaker_A",
                isFinal = true,
                startTimeMs = 0,
                endTimeMs = 1000,
                languageCode = lang
            )

            assertEquals(lang, segment.languageCode)
        }
    }
}