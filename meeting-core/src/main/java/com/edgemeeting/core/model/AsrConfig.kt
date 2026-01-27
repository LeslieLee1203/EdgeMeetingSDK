package com.edgemeeting.core.model

/**
 * ASR 引擎配置
 *
 * 為什麼使用 sealed class（策略模式）：
 * 1. 統一介面：所有 ASR 引擎（Whisper、Zipformer 等）使用相同的配置方式
 * 2. 型別安全：編譯期確保配置正確，避免執行期錯誤
 * 3. 可擴充：新增 ASR 引擎只需新增子類，無需修改既有程式碼（開放封閉原則）
 * 4. when 表達式完整性：編譯器會檢查所有分支是否處理
 *
 * 設計考量：
 * - 每個 ASR 引擎有各自的參數（Whisper 有 language，Zipformer 有 beamSize）
 * - 模型路徑由 SDK 統一管理（透過 ModelAssetManager）
 * - 使用者只需選擇 ASR 類型與語言設定，無需關注底層實作
 *
 * 使用範例：
 * ```kotlin
 * // Whisper 指定中文
 * val whisperZh = AsrConfig.Whisper(
 *     modelsPath = "/data/data/com.edgemeeting/files/models",
 *     language = LanguageSetting.Fixed("zh")
 * )
 * ```
 */
sealed class AsrConfig {
    /**
     * Whisper ASR 配置
     *
     * 使用 RKNN 加速的 Whisper 模型進行語音辨識。
 * 支援指定語言模式。
     *
     * @property modelsPath 模型檔案資料夾的絕對路徑（必須包含 encoder 與 decoder RKNN 模型）
 * @property language 語言設定（Fixed）
     */
    data class Whisper(
        val modelsPath: String,
        val language: LanguageSetting
    ) : AsrConfig()

    // 未來擴充：Zipformer ASR
    // data class Zipformer(
    //     val modelsPath: String,
    //     val beamSize: Int = 4
    // ) : AsrConfig()
}
