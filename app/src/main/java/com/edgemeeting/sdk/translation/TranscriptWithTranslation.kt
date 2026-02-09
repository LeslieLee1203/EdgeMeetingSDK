package com.edgemeeting.sdk.translation

import com.edgemeeting.core.model.TranscriptSegment

/**
 * 帶有翻譯的字幕資料
 *
 * 用於 UI 層顯示原文與翻譯結果。
 * 每一個字幕項目會以兩行顯示：英文原文 + 中文翻譯。
 *
 * @property original 原始字幕片段（ASR 輸出的英文）
 * @property translatedText 翻譯後的中文文字，null 表示尚未翻譯完成
 * @property isTranslating 是否正在翻譯中
 */
data class TranscriptWithTranslation(
    val original: TranscriptSegment,
    val translatedText: String? = null,
    val isTranslating: Boolean = false
) {
    /**
     * 是否有翻譯結果
     */
    val hasTranslation: Boolean
        get() = translatedText != null
}
