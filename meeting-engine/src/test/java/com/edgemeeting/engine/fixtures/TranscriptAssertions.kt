package com.edgemeeting.engine.fixtures

import com.edgemeeting.core.model.TranscriptSegment
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue

// 提供字幕列表的空結果斷言。
fun assertNoSegments(segments: List<TranscriptSegment>) {
    assertTrue("Expected no transcript segments", segments.isEmpty())
}

// 斷言字幕欄位符合 Phase 1 的固定規則。
fun assertSegmentFields(segment: TranscriptSegment) {
    assertEquals("Unknown", segment.speakerId)
    assertEquals(true, segment.isFinal)
    assertTrue("Expected non-empty text", segment.text.isNotEmpty())
    assertTrue("Expected non-negative startTimeMs", segment.startTimeMs >= 0)
    assertTrue("Expected endTimeMs >= startTimeMs", segment.endTimeMs >= segment.startTimeMs)
    assertTrue("Expected non-empty id", segment.id.isNotEmpty())
}
