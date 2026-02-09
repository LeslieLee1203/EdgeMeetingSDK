package com.edgemeeting.engine.translation

/**
 * 翻譯模型檔案路徑與名稱常數
 *
 * 設計原則：Single Source of Truth
 * - 避免在多處重複定義路徑字串
 * - 便於未來模型升級時統一修改
 *
 * 路徑慣例：
 * - Assets 路徑：meeting-engine/src/main/assets/{ASSETS_TRANSLATION_MODEL_DIR}/
 * - Runtime 路徑：context.filesDir.absolutePath + "/{RUNTIME_TRANSLATION_MODEL_DIR}/"
 */
object TranslationConstants {

    /**
     * Assets 中的翻譯模型目錄名稱
     */
    const val ASSETS_TRANSLATION_MODEL_DIR = "translation_models"

    /**
     * Runtime 翻譯模型目錄名稱（複製目標）
     */
    const val RUNTIME_TRANSLATION_MODEL_DIR = "translation_models"

    /**
     * Gemma-3 1B 模型檔案名稱
     *
     * MediaPipe Task 格式 (.task) 是 MediaPipe LLM Inference API 所需的格式。
     * 從 Hugging Face 下載後需轉換為此格式。
     */
    const val GEMMA_3_1B_MODEL_FILE = "gemma3-1b-it-int4.task"

    /**
     * 預期的翻譯模型檔案清單
     *
     * 用於 TranslationModelManager 驗證檔案完整性。
     */
    val EXPECTED_TRANSLATION_MODEL_FILES = listOf(
        GEMMA_3_1B_MODEL_FILE
    )
}
