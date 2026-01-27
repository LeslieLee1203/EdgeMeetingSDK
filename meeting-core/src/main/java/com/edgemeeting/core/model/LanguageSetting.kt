package com.edgemeeting.core.model

/**
 * 語言設定模式
 *
 * 為什麼使用 sealed class：
 * 1. 型別安全：確保只有 Fixed 模式，避免無效狀態
 * 2. 可擴充：未來若需新增模式（如 MultiLanguage），可輕鬆新增
 * 3. when 表達式完整性：編譯器會檢查所有分支是否處理
 *
 * 使用範例：
 * ```kotlin
 * // 指定語言以提升準確度
 * val fixedMode = LanguageSetting.Fixed("zh")
 * ```
 */
sealed class LanguageSetting {
    /**
     * 指定語言模式
     *
     * 明確指定語言可提升辨識準確度與速度。
     * 適用場景：已知語言的單一語言會議
     *
     * @property languageCode ISO 639-1 語言代碼（如 "zh", "en", "ja"）
     */
    data class Fixed(val languageCode: String) : LanguageSetting()
}
