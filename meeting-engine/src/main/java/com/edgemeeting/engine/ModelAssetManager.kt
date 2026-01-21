package com.edgemeeting.engine

import android.content.Context
import java.io.File
import java.io.FileOutputStream

/**
 * 模型資產管理器
 *
 * 為什麼需要 ModelAssetManager：
 * 1. 模型檔案打包在 APK assets 中，但 C++ rknn_init 需要檔案系統路徑
 * 2. 自動處理 assets 解壓至 app-specific storage（無需權限）
 * 3. 冪等性：重複呼叫不會重新複製，提升效能
 * 4. 錯誤處理：檔案缺失、複製失敗等情況統一處理
 *
 * 設計考量：
 * - 使用 Kotlin Result 型別，明確表達成功/失敗
 * - 從 APK assets 複製到 context.filesDir（app-private，無需 WRITE_EXTERNAL_STORAGE 權限）
 * - 使用 ModelConstants 定義預期檔案，避免硬編碼
 * - 檔案已存在時跳過複製（檢查完整性）
 *
 * 使用範例：
 * ```kotlin
 * val result = ModelAssetManager.ensureModels(context)
 * when (result) {
 *     is Result.Success -> {
 *         val modelsDir = result.value.modelsDir.absolutePath
 *         // 傳給 C++ rknn_init(modelsDir)
 *     }
 *     is Result.Failure -> {
 *         // 顯示錯誤訊息
 *     }
 * }
 * ```
 */
object ModelAssetManager {

    /**
     * 確保模型檔案已從 assets 複製到 filesDir
     *
     * 冪等性：重複呼叫安全，已存在的檔案不會重新複製。
     *
     * @param context Android Context（用於存取 assets 與 filesDir）
     * @return Result<ModelsReady> 成功時包含模型目錄路徑，失敗時包含錯誤訊息
     */
    fun ensureModels(context: Context): Result<ModelsReady> {
        return try {
            // 1. 建立目標目錄（app-specific storage，無需權限）
            val modelsDir = File(context.filesDir, ModelConstants.RUNTIME_MODEL_DIR)

            // 如果目標是檔案而非目錄，無法繼續
            if (modelsDir.exists() && !modelsDir.isDirectory) {
                return Result.failure(
                    IllegalStateException("Models path exists but is not a directory: ${modelsDir.absolutePath}")
                )
            }

            // 建立目錄（如果不存在）
            if (!modelsDir.exists()) {
                val created = modelsDir.mkdirs()
                if (!created) {
                    return Result.failure(
                        IllegalStateException("Failed to create models directory: ${modelsDir.absolutePath}")
                    )
                }
            }

            // 2. 檢查所有預期檔案是否已存在（冪等性檢查）
            val allFilesExist = ModelConstants.EXPECTED_MODEL_FILES.all { filename ->
                File(modelsDir, filename).exists()
            }

            // 如果檔案已存在，直接回傳成功（跳過複製）
            if (allFilesExist) {
                return Result.success(ModelsReady(modelsDir))
            }

            // 3. 從 assets 複製檔案到 filesDir
            val assetManager = context.assets
            val copiedFiles = mutableListOf<File>()

            try {
                ModelConstants.EXPECTED_MODEL_FILES.forEach { filename ->
                    val targetFile = File(modelsDir, filename)

                    // 如果檔案已存在，跳過
                    if (targetFile.exists()) {
                        copiedFiles.add(targetFile)
                        return@forEach
                    }

                    // 從 assets 讀取並寫入 filesDir
                    val assetPath = "${ModelConstants.ASSETS_MODEL_DIR}/$filename"
                    assetManager.open(assetPath).use { inputStream ->
                        FileOutputStream(targetFile).use { outputStream ->
                            inputStream.copyTo(outputStream)
                        }
                    }

                    copiedFiles.add(targetFile)
                }

                // 4. 驗證檔案完整性
                val allCopied = ModelConstants.EXPECTED_MODEL_FILES.all { filename ->
                    File(modelsDir, filename).exists()
                }

                if (!allCopied) {
                    throw IllegalStateException("Failed to copy all model files")
                }

                // 5. 回傳成功
                Result.success(ModelsReady(modelsDir))
            } catch (e: Exception) {
                // 複製失敗，清理已複製的檔案（避免部分複製狀態）
                copiedFiles.forEach { file ->
                    try {
                        file.delete()
                    } catch (ignored: Exception) {
                        // 忽略清理錯誤
                    }
                }
                throw e
            }
        } catch (e: Exception) {
            Result.failure(e)
        }
    }
}

/**
 * 模型準備完成結果
 *
 * @property modelsDir 模型檔案所在目錄（絕對路徑，傳給 C++ rknn_init 使用）
 */
data class ModelsReady(
    val modelsDir: File
)
