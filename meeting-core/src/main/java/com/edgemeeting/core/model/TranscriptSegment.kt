package com.edgemeeting.core.model

data class TranscriptSegment(
    val id: String,          // 用於 RecyclerView DiffUtil
    val text: String,        // 字幕內容
    val speakerId: String,   // "Speaker_A", "Unknown"
    val isFinal: Boolean,    // true=確認(黑字), false=預測(灰字)
    val startTimeMs: Long,     // 相對於會議開始的毫秒數 (例如 1500 代表 1.5秒)
    val endTimeMs: Long        // 結束時間 (例如 2800)
) {

    init {
        // 結束時間不能早於開始時間
        require(endTimeMs >= startTimeMs) {
            "End time ($endTimeMs) cannot be less than start time ($startTimeMs)"
        }

        // 開始時間不能是負數 (防呆)
        require(startTimeMs >= 0) {
            "Start time ($startTimeMs) cannot be negative"
        }
    }

    // 方便 UI 計算顯示時長或是做波形對齊
    val durationMs: Long
        get() = endTimeMs - startTimeMs
}