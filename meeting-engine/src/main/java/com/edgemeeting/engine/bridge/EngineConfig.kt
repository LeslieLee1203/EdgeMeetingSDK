package com.edgemeeting.engine.bridge

import com.edgemeeting.core.model.AsrConfig

/**
 * 引擎統一配置
 *
 * 為什麼需要 EngineConfig：
 * 1. 統一介面：將音訊錄製與 ASR 配置整合在單一配置物件
 * 2. 彈性模式：支援純錄音（asrConfig = null）或錄音+ASR 兩種模式
 * 3. 擴充性：未來若需新增音訊處理參數（如 sampleRate, channels），可在此擴充
 *
 * 設計考量：
 * - asrConfig 為 nullable：null 表示純錄音模式，不啟動 ASR 引擎
 * - 音訊錄製（Oboe）不需要模型檔案，ASR 模型路徑已在 AsrConfig 中定義
 * - 使用 data class 提供自動的 copy、equals、hashCode 等功能
 *
 * 使用範例：
 * ```kotlin
 * // 純錄音模式
 * val audioOnlyConfig = EngineConfig(asrConfig = null)
 *
 * // 錄音 + Whisper ASR
 * val asrConfig = AsrConfig.Whisper(
 *     modelsPath = "/data/data/com.edgemeeting/files/models",
 *     language = LanguageSetting.Auto
 * )
 * val fullConfig = EngineConfig(asrConfig = asrConfig)
 * ```
 */
data class EngineConfig(
    /**
     * ASR 配置
     *
     * - null：純錄音模式，不啟動 ASR 引擎
     * - AsrConfig.Whisper：啟動 Whisper ASR
     * - 未來可擴充其他 ASR 類型（如 AsrConfig.Zipformer）
     */
    val asrConfig: AsrConfig? = null
)
