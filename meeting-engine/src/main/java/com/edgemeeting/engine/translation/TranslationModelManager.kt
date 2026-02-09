package com.edgemeeting.engine.translation

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream

/**
 * 翻譯模型資產管理器
 *
 * 為什麼需要 TranslationModelManager：
 * 1. 模型檔案打包在 APK assets 中，但 MediaPipe 需要檔案系統路徑
 * 2. 自動處理 assets 解壓至 app-specific storage（無需權限）
 * 3. 冪等性：重複呼叫不會重新複製，提升效能
 *
 * 設計參考：ModelAssetManager（ASR 模型管理器）
 *
 * 使用範例：
 * ```kotlin
 * val result = TranslationModelManager.ensureTranslationModel(context)
 * when {
 *     result.isSuccess -> {
 *         val modelPath = result.getOrThrow().modelPath
 *         translationEngine.initialize(modelPath)
 *     }
 *     else -> {
 *         // 翻譯功能不可用，降級處理
 *     }
 * }
 * ```
 */
object TranslationModelManager {

    private const val TAG = "TranslationModelManager"

    /**
     * 確保翻譯模型檔案已從 assets 複製到 filesDir
     *
     * 冪等性：重複呼叫安全，已存在的檔案不會重新複製。
     *
     * @param context Android Context
     * @return Result<TranslationModelReady> 成功時包含模型路徑，失敗時包含錯誤
     */
    fun ensureTranslationModel(context: Context): Result<TranslationModelReady> {
        return try {
            // 1. 建立目標目錄
            val modelsDir = File(context.filesDir, TranslationConstants.RUNTIME_TRANSLATION_MODEL_DIR)

            if (modelsDir.exists() && !modelsDir.isDirectory) {
                return Result.failure(
                    IllegalStateException("Translation models path exists but is not a directory: ${modelsDir.absolutePath}")
                )
            }

            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                if (!created) {
                    return Result.failure(
                        IllegalStateException("Failed to create translation models directory: ${modelsDir.absolutePath}")
                    )
                }
            }

            // 2. 檢查模型檔案是否已存在
            val modelFile = File(modelsDir, TranslationConstants.GEMMA_3_1B_MODEL_FILE)
            if (modelFile.exists()) {
                Log.d(TAG, "Translation model already exists: ${modelFile.absolutePath}")
                return Result.success(TranslationModelReady(modelFile.absolutePath))
            }

            // 3. 從 assets 複製模型檔案
            Log.d(TAG, "Copying translation model from assets...")
            val assetManager = context.assets
            val assetPath = "${TranslationConstants.ASSETS_TRANSLATION_MODEL_DIR}/${TranslationConstants.GEMMA_3_1B_MODEL_FILE}"

            try {
                assetManager.open(assetPath).use { inputStream ->
                    FileOutputStream(modelFile).use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }
            } catch (e: Exception) {
                // 複製失敗，清理部分寫入的檔案
                modelFile.delete()
                throw e
            }

            // 4. 驗證複製結果
            if (!modelFile.exists()) {
                return Result.failure(
                    IllegalStateException("Failed to copy translation model file")
                )
            }

            Log.d(TAG, "Translation model copied successfully: ${modelFile.absolutePath}")
            Result.success(TranslationModelReady(modelFile.absolutePath))
        } catch (e: Exception) {
            Log.e(TAG, "Failed to ensure translation model", e)
            Result.failure(e)
        }
    }
}

/**
 * 翻譯模型準備完成結果
 *
 * @property modelPath 模型檔案的完整路徑（傳給 MediaPipe LlmInference 使用）
 */
data class TranslationModelReady(
    val modelPath: String
)
