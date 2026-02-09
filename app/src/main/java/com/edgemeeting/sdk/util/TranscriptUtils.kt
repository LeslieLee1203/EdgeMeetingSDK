package com.edgemeeting.sdk.util

import com.edgemeeting.core.model.TranscriptSegment

/**
 * 判斷舊段落是否應該被新段落取代（方案 B - 修正版）
 *
 * 覆蓋規則（按優先級）：
 *
 * 規則 1：FINAL 永遠不被覆蓋
 * - 如果舊段落是 FINAL，不刪除
 *
 * 規則 2：相同起點的延伸覆蓋
 * - 如果兩個段落的 startTimeMs 接近（差距 < 1000ms）
 * - 且新段落的 endTimeMs >= 舊段落的 endTimeMs
 * - 則新段落是舊段落的延伸，應該覆蓋舊段落
 * - 範例：[0s-5s] 被 [0s-10s] 覆蓋
 *
 * 規則 3：時間範圍重疊
 * - 如果新段落是 FINAL，且時間範圍與舊段落重疊
 * - 則 FINAL 覆蓋所有重疊的 INTERMEDIATE
 *
 * 實際案例：
 * - INTERMEDIATE [0s-5s]: "進來這個一面之後"
 * - INTERMEDIATE [0s-10s]: "進來這個一面之後,你會看到"  ← 應該覆蓋 [0s-5s]
 * - INTERMEDIATE [0s-15s]: "進來這個一面之後,你會看到他在啟動中"  ← 應該覆蓋 [0s-10s]
 * - FINAL       [0s-20s]: "進來這個一面之後,你會看到他在啟動中的狀態..."  ← 應該覆蓋 [0s-15s]
 *
 * @param oldSegment 已存在的段落
 * @param newSegment 新收到的段落
 * @return true 如果舊段落應該被刪除
 */
fun shouldReplaceSegment(
    oldSegment: TranscriptSegment,
    newSegment: TranscriptSegment
): Boolean {
    // 規則 1：FINAL 結果永遠不被覆蓋
    if (oldSegment.isFinal) {
        return false
    }

    // 規則 2：相同起點的延伸覆蓋（最常見情況）
    // 允許 1000ms 的誤差（因為音訊分段可能有微小偏移）
    val hasSimilarStart = kotlin.math.abs(oldSegment.startTimeMs - newSegment.startTimeMs) < 1000
    val isExtension = newSegment.endTimeMs >= oldSegment.endTimeMs

    if (hasSimilarStart && isExtension) {
        return true  // 新段落是舊段落的延伸，覆蓋它
    }

    // 規則 3：FINAL 覆蓋所有時間重疊的 INTERMEDIATE
    if (newSegment.isFinal) {
        val hasOverlap =
            oldSegment.startTimeMs < newSegment.endTimeMs &&
                newSegment.startTimeMs < oldSegment.endTimeMs

        return hasOverlap
    }

    return false
}
