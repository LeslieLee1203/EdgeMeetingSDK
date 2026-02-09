package com.edgemeeting.sdk.translation

import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.common.model.DownloadConditions
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.Translator
import com.google.mlkit.nl.translate.TranslatorOptions
import com.google.mlkit.nl.translate.TranslateLanguage
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import java.io.Closeable
import kotlin.coroutines.resume

/**
 * ML Kit 翻譯服務封裝
 *
 * 功能：
 * - 將英文文字翻譯成中文
 * - 管理翻譯模型的下載狀態
 * - 提供非同步翻譯 API
 *
 * 使用方式：
 * 1. 建立實例
 * 2. 呼叫 downloadModelIfNeeded() 確保模型已下載
 * 3. 使用 translate() 進行翻譯
 * 4. 完成後呼叫 close() 釋放資源
 *
 * 注意：首次使用需要網路連線下載模型（約 30MB）
 */
class MlKitTranslator : Closeable {

    companion object {
        private const val TAG = "MlKitTranslator"
    }

    private val translator: Translator

    private val _modelReady = MutableStateFlow(false)
    val modelReady: StateFlow<Boolean> = _modelReady.asStateFlow()

    private val _isDownloading = MutableStateFlow(false)
    val isDownloading: StateFlow<Boolean> = _isDownloading.asStateFlow()

    init {
        val options = TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.CHINESE)
            .build()
        translator = Translation.getClient(options)
        Log.d(TAG, "MlKitTranslator initialized (English → Chinese)")
    }

    /**
     * 下載翻譯模型（如果尚未下載）
     *
     * @param requireWifi 是否要求在 Wi-Fi 環境下載（預設 true）
     * @return true 表示模型已就緒，false 表示下載失敗
     */
    suspend fun downloadModelIfNeeded(requireWifi: Boolean = true): Boolean {
        if (_modelReady.value) {
            Log.d(TAG, "Model already downloaded")
            return true
        }

        _isDownloading.value = true
        Log.d(TAG, "Starting model download (requireWifi=$requireWifi)")

        return suspendCancellableCoroutine { continuation ->
            val conditions = DownloadConditions.Builder()
                .apply { if (requireWifi) requireWifi() }
                .build()

            translator.downloadModelIfNeeded(conditions)
                .addOnSuccessListener {
                    Log.d(TAG, "Model download successful")
                    _modelReady.value = true
                    _isDownloading.value = false
                    continuation.resume(true)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Model download failed", exception)
                    _isDownloading.value = false
                    continuation.resume(false)
                }
        }
    }

    /**
     * 翻譯文字
     *
     * @param text 要翻譯的英文文字
     * @return 翻譯後的中文文字，失敗時回傳 null
     */
    suspend fun translate(text: String): String? {
        if (text.isBlank()) {
            return ""
        }

        // 如果模型尚未就緒，嘗試下載
        if (!_modelReady.value) {
            val downloaded = downloadModelIfNeeded(requireWifi = false)
            if (!downloaded) {
                Log.w(TAG, "Cannot translate: model not available")
                return null
            }
        }

        return suspendCancellableCoroutine { continuation ->
            translator.translate(text)
                .addOnSuccessListener { translatedText ->
                    Log.d(TAG, "Translation: \"${text.take(30)}...\" → \"${translatedText.take(30)}...\"")
                    continuation.resume(translatedText)
                }
                .addOnFailureListener { exception ->
                    Log.e(TAG, "Translation failed for: \"${text.take(50)}\"", exception)
                    continuation.resume(null)
                }
        }
    }

    /**
     * 同步檢查模型是否已下載（不會觸發下載）
     */
    fun isModelReady(): Boolean = _modelReady.value

    /**
     * 釋放翻譯器資源
     */
    override fun close() {
        Log.d(TAG, "Closing MlKitTranslator")
        translator.close()
    }
}
