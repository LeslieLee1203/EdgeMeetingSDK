package com.edgemeeting.sdk.translation

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 翻譯服務：使用 MediaPipe LLM (Gemma-3 1B) 進行英翻中
 *
 * 設計重點：
 * - 封裝 MediaPipe LlmInference 初始化與生命週期
 * - 使用 suspend 函數配合 Coroutines 非同步執行
 * - 必須在 Activity onDestroy 時呼叫 release() 釋放資源
 *
 * 使用方式：
 * ```kotlin
 * val service = TranslationService(context)
 * val result = service.translate("Hello, world!")
 * result.onSuccess { println(it) }  // 你好，世界！
 * service.release()
 * ```
 */
class TranslationService(context: Context) {

    companion object {
        private const val TAG = "TranslationService"

        // Gemma-3 1B 模型預設路徑（需先用 adb push 到裝置）
        private const val DEFAULT_MODEL_PATH = "/data/local/tmp/llm/gemma3-1b-it-int4.task"

        // 翻譯 Prompt 模板
        private const val TRANSLATION_PROMPT_TEMPLATE = """Translate the following English text to Traditional Chinese (Taiwan).
Only output the translation, nothing else.

Text: "%s""""
    }

    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var initError: String? = null

    init {
        try {
            Log.d(TAG, "Initializing LlmInference with model: $DEFAULT_MODEL_PATH")

            // LlmInferenceOptions 只支援 setModelPath() 和 setMaxTokens()
            // temperature/topK/topP 需要透過 LlmInferenceSession 設定（進階用法）
            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(DEFAULT_MODEL_PATH)
                .setMaxTokens(512)  // 翻譯輸出的 token 上限
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "LlmInference initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize LlmInference", e)
            initError = e.message ?: "Unknown initialization error"
            isInitialized = false
        }
    }

    /**
     * 翻譯英文文字為繁體中文
     *
     * @param text 要翻譯的英文文字
     * @return Result<String> 成功時包含翻譯結果，失敗時包含錯誤
     */
    suspend fun translate(text: String): Result<String> = withContext(Dispatchers.IO) {
        if (!isInitialized || llmInference == null) {
            return@withContext Result.failure(
                IllegalStateException("TranslationService not initialized: $initError")
            )
        }

        if (text.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Text to translate cannot be blank")
            )
        }

        try {
            val prompt = TRANSLATION_PROMPT_TEMPLATE.format(text)
            Log.d(TAG, "Translating: ${text.take(50)}...")

            val startTime = System.currentTimeMillis()
            val response = llmInference!!.generateResponse(prompt)
            val elapsed = System.currentTimeMillis() - startTime

            Log.d(TAG, "Translation completed in ${elapsed}ms")

            // 清理回應（移除可能的前後空白和引號）
            val cleanedResponse = response
                .trim()
                .removeSurrounding("\"")
                .trim()

            if (cleanedResponse.isBlank()) {
                return@withContext Result.failure(
                    RuntimeException("Empty translation response")
                )
            }

            Log.d(TAG, "Translated result: ${cleanedResponse.take(50)}...")
            Result.success(cleanedResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    /**
     * 檢查服務是否已初始化且可用
     */
    fun isAvailable(): Boolean = isInitialized && llmInference != null

    /**
     * 取得初始化錯誤訊息（如果有）
     */
    fun getInitError(): String? = initError

    /**
     * 釋放 LLM 資源
     *
     * 必須在 Activity onDestroy 或不再需要翻譯時呼叫
     */
    fun release() {
        Log.d(TAG, "Releasing LlmInference resources")
        try {
            llmInference?.close()
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing LlmInference", e)
        } finally {
            llmInference = null
            isInitialized = false
        }
    }
}
