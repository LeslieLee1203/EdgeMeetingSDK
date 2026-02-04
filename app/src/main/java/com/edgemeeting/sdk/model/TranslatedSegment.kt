package com.edgemeeting.sdk.model

import com.edgemeeting.core.model.TranscriptSegment

/**
 * 包含翻譯狀態的字幕段落
 *
 * 設計考量：
 * - 保留原始 TranscriptSegment 不變，避免污染核心資料模型
 * - 使用獨立的翻譯狀態欄位追蹤翻譯進度
 *
 * 狀態說明：
 * - translatedText = null, isTranslating = false → 尚未翻譯
 * - translatedText = null, isTranslating = true → 翻譯中
 * - translatedText = "...", isTranslating = false → 翻譯完成
 * - translationError != null → 翻譯失敗
 *
 * @property original 原始字幕段落
 * @property translatedText 翻譯後的文字，null 表示尚未翻譯或翻譯中
 * @property isTranslating 是否正在翻譯中
 * @property translationError 翻譯錯誤訊息，null 表示無錯誤
 */
data class TranslatedSegment(
    val original: TranscriptSegment,
    val translatedText: String? = null,
    val isTranslating: Boolean = false,
    val translationError: String? = null
) {
    /**
     * 段落 ID，來自原始 TranscriptSegment
     */
    val id: String get() = original.id

    /**
     * 是否需要翻譯（尚未翻譯且無錯誤且是最終結果）
     */
    val needsTranslation: Boolean
        get() = original.isFinal &&
                translatedText == null &&
                !isTranslating &&
                translationError == null

    /**
     * 翻譯是否完成（成功或失敗）
     */
    val translationComplete: Boolean
        get() = translatedText != null || translationError != null
}
