package com.edgemeeting.core

import com.edgemeeting.core.model.TranscriptSegment
import org.junit.Assert.assertThrows
import org.junit.Test

class TranscriptSegmentTest {

    @Test
    fun should_throw_exception_when_endTime_is_less_than_startTime() {

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
}