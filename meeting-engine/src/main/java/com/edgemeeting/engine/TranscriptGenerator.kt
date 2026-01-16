package com.edgemeeting.engine

import com.edgemeeting.core.model.TranscriptSegment

// 為了可替換字幕來源並在測試中可控，定義產生器介面。
interface TranscriptGenerator {
    fun generate(startTimeMs: Long, endTimeMs: Long): TranscriptSegment {
        require(startTimeMs >= 0) { "startTimeMs must be >= 0" }
        require(endTimeMs >= startTimeMs) { "endTimeMs must be >= startTimeMs" }
        return doGenerate(startTimeMs, endTimeMs)
    }

    fun doGenerate(startTimeMs: Long, endTimeMs: Long): TranscriptSegment
}
