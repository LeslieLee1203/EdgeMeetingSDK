package com.edgemeeting.engine

/**
 * 模型檔案路徑與名稱常數
 *
 * 設計原則：Single Source of Truth
 * - 避免在多處重複定義路徑字串，降低維護成本
 * - 便於未來模型升級時統一修改（如從 base 升級至 large）
 * - 提供型別安全的常數存取
 *
 * 路徑慣例：
 * - Assets 路徑：meeting-engine/src/main/assets/{ASSETS_MODEL_DIR}/
 * - Runtime 路徑：context.filesDir.absolutePath + "/{RUNTIME_MODEL_DIR}/"
 */
object ModelConstants {

    /**
     * Assets 中的模型目錄名稱
     *
     * 為什麼是 "models"：
     * - 語義清晰，符合慣例
     * - 與其他 assets（如音效、圖片）區分
     */
    const val ASSETS_MODEL_DIR = "models"

    /**
     * Runtime 模型目錄名稱（複製目標）
     *
     * 為什麼與 ASSETS_MODEL_DIR 相同：
     * - 保持一致性，簡化路徑管理
     * - 未來若需分離，可獨立修改
     */
    const val RUNTIME_MODEL_DIR = "models"

    /**
     * 預期的模型檔案清單
     *
     * 為什麼需要這個清單：
     * - 用於 ModelAssetManager 驗證檔案完整性
     * - 避免部分複製導致執行期錯誤
     * - 便於未來新增或替換模型（如支援 Zipformer）
     *
     * Whisper RKNN 模型組成：
     * - encoder: 音訊特徵提取（Mel Spectrogram → Hidden States）
     * - decoder: 文字生成（Hidden States → Tokens → Text）
     */
    val EXPECTED_MODEL_FILES = listOf(
        "whisper_encoder_base_20s.rknn",
        "whisper_decoder_base_20s.rknn"
    )
}
