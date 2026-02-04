package com.edgemeeting.sdk.translation

import android.content.Context
import android.util.Log
import com.google.mediapipe.tasks.genai.llminference.LlmInference
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.util.concurrent.TimeoutException

/**
 * 翻譯服務：使用 MediaPipe LLM (Gemma-3 1B) 進行英翻中
 *
 * 設計重點：
 * - 使用 Mutex 確保一次只處理一個翻譯（避免並發衝突）
 * - 使用 generateResponse() 同步 API（穩定性優先）
 * - 簡化 Prompt 避免 few-shot 被照抄
 * - 強化後處理：偵測重複、簡轉繁、移除多餘內容
 * - 10 秒超時保護
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

        // 翻譯超時時間（毫秒）
        private const val TRANSLATION_TIMEOUT_MS = 10_000L

        // 最大 token 數
        private const val MAX_TOKENS = 256

        // 簡化 Prompt（不使用 few-shot 避免被照抄）
        // 設計重點：
        // 1. 極簡指示
        // 2. 強調 Traditional Chinese (Taiwan)
        // 3. 明確禁止額外內容
        private const val TRANSLATION_PROMPT_TEMPLATE = """You are a translate machine. Translate the following English text to Traditional Chinese (Taiwan zh_TW).
        Output ONLY the Chinese translation.
        No pinyin. No explanations. No questions.
        -----
        %s"""

        // 常見簡體轉繁體字元映射表
        // 涵蓋翻譯中最常見的簡繁差異字
        private val SIMPLIFIED_TO_TRADITIONAL = mapOf(
            // 常用字
            '儿' to '兒', '从' to '從', '将' to '將', '国' to '國',
            '岁' to '歲', '亿' to '億', '为' to '為', '这' to '這',
            '个' to '個', '们' to '們', '来' to '來', '时' to '時',
            '会' to '會', '对' to '對', '发' to '發', '动' to '動',
            '机' to '機', '没' to '沒', '说' to '說', '过' to '過',
            '还' to '還', '进' to '進', '种' to '種', '面' to '麵',
            '里' to '裡', '后' to '後', '头' to '頭', '点' to '點',
            '问' to '問', '间' to '間', '开' to '開', '关' to '關',
            '电' to '電', '话' to '話', '东' to '東', '西' to '西',
            '见' to '見', '现' to '現', '实' to '實', '学' to '學',
            '经' to '經', '济' to '濟', '业' to '業', '数' to '數',
            '万' to '萬', '与' to '與', '并' to '並', '车' to '車',
            '长' to '長', '张' to '張', '当' to '當', '着' to '著',
            '场' to '場', '体' to '體', '系' to '係', '变' to '變',
            '华' to '華', '产' to '產', '无' to '無', '网' to '網',
            '络' to '絡', '转' to '轉', '运' to '運', '传' to '傳',
            '统' to '統', '认' to '認', '识' to '識', '设' to '設',
            '计' to '計', '术' to '術', '节' to '節', '报' to '報',
            '达' to '達', '历' to '歷', '史' to '史', '农' to '農',
            '乡' to '鄉', '县' to '縣', '城' to '城', '区' to '區',
            '级' to '級', '部' to '部', '门' to '門', '员' to '員',
            '军' to '軍', '战' to '戰', '斗' to '鬥', '队' to '隊',
            '组' to '組', '织' to '織', '领' to '領', '导' to '導',
            '党' to '黨', '政' to '政', '府' to '府', '权' to '權',
            '法' to '法', '规' to '規', '则' to '則', '条' to '條',
            '款' to '款', '项' to '項', '标' to '標', '准' to '準',
            '质' to '質', '量' to '量', '价' to '價', '值' to '值',
            '费' to '費', '税' to '稅', '银' to '銀', '钱' to '錢',
            '币' to '幣', '贸' to '貿', '易' to '易', '购' to '購',
            '买' to '買', '卖' to '賣', '货' to '貨', '物' to '物'
        )

        // 模型常添加的多餘尾綴
        private val EXTRA_SUFFIXES = listOf(
            "好嗎？", "好吗？", "是嗎？", "是吗？",
            "對嗎？", "对吗？", "呢？", "啊？", "嗎？", "吗？",
            "好嗎", "好吗", "是嗎", "是吗", "對嗎", "对吗"
        )

        // 拼音匹配正則表達式
        private val PINYIN_PATTERN = Regex("""\s*[\(（][A-Za-zāáǎàēéěèīíǐìōóǒòūúǔùǖǘǚǜÜü\s\-\',\.]+[\)）]""")

        // 重複模式匹配（如 "你，你，你，..."）
        private val REPETITION_PATTERN = Regex("""(.{1,10}?)\1{3,}""")
    }

    private var llmInference: LlmInference? = null
    private var isInitialized = false
    private var initError: String? = null

    // Mutex 確保一次只處理一個翻譯（避免並發衝突）
    private val mutex = Mutex()

    init {
        try {
            Log.d(TAG, "Initializing LlmInference with model: $DEFAULT_MODEL_PATH")

            val options = LlmInference.LlmInferenceOptions.builder()
                .setModelPath(DEFAULT_MODEL_PATH)
                .setMaxTokens(MAX_TOKENS)
                .build()

            llmInference = LlmInference.createFromOptions(context, options)
            isInitialized = true
            Log.d(TAG, "TranslationService initialized successfully")
        } catch (e: Exception) {
            Log.e(TAG, "Failed to initialize TranslationService", e)
            initError = e.message ?: "Unknown initialization error"
            isInitialized = false
        }
    }

    /**
     * 翻譯英文文字為繁體中文
     *
     * 特性：
     * - 使用 Mutex 確保一次只處理一個翻譯
     * - 10 秒超時保護
     * - 強化後處理確保輸出品質
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

        // 輸入預處理：移除前後空白
        val cleanedInput = text.trim()
        if (cleanedInput.isBlank()) {
            return@withContext Result.failure(
                IllegalArgumentException("Text to translate cannot be blank")
            )
        }

        // 使用 Mutex 確保一次只處理一個翻譯
        mutex.withLock {
            // 超時保護
            val result = withTimeoutOrNull(TRANSLATION_TIMEOUT_MS) {
                translateInternal(cleanedInput)
            }

            result ?: Result.failure(
                TimeoutException("Translation timeout after ${TRANSLATION_TIMEOUT_MS}ms")
            )
        }
    }

    /**
     * 內部翻譯實作
     */
    private fun translateInternal(text: String): Result<String> {
        return try {
            val prompt = TRANSLATION_PROMPT_TEMPLATE.format(text)
            Log.d(TAG, "Translating: ${text.take(50)}...")

            val startTime = System.currentTimeMillis()

            // 使用同步 API（穩定性優先）
            val response = llmInference!!.generateResponse(prompt)

            val elapsed = System.currentTimeMillis() - startTime
            Log.d(TAG, "Translation completed in ${elapsed}ms")
            Log.d(TAG, "Raw response: ${response.take(100)}...")

            // 強化後處理
            val cleanedResponse = postProcessTranslation(response)

            if (cleanedResponse.isBlank()) {
                return Result.failure(
                    RuntimeException("Empty translation response after post-processing")
                )
            }

            Log.d(TAG, "Cleaned result: ${cleanedResponse.take(50)}...")
            Result.success(cleanedResponse)
        } catch (e: Exception) {
            Log.e(TAG, "Translation failed", e)
            Result.failure(e)
        }
    }

    /**
     * 強化後處理：清理翻譯結果
     *
     * 處理步驟：
     * 1. 偵測並截斷重複輸出
     * 2. 簡體轉繁體
     * 3. 移除多餘尾綴
     * 4. 移除拼音和引號
     * 5. 清理標點符號
     */
    private fun postProcessTranslation(raw: String): String {
        var result = raw.trim()

        // Step 1: 偵測並截斷重複輸出（如 "你，你，你，..."）
        result = truncateRepetition(result)

        // Step 2: 簡體轉繁體
//        result = convertToTraditional(result)

        // Step 3: 移除多餘尾綴（如 "好嗎？"）
        result = removeExtraSuffix(result)

        // Step 4: 移除拼音
        result = result.replace(PINYIN_PATTERN, "")

        // Step 5: 移除引號
        result = result.removeSurrounding("\"")
        result = result.removeSurrounding("「", "」")
        result = result.removeSurrounding("『", "』")

        // Step 6: 清理多餘標點符號
        result = result.replace(Regex("""[、。！？，]{2,}""")) { it.value.first().toString() }

        // 最終清理
        result = result.trim()

        return result
    }

    /**
     * 偵測並截斷重複輸出
     *
     * 例如："你，你，你，你，你，..." -> "你"
     */
    private fun truncateRepetition(text: String): String {
        return text.replace(REPETITION_PATTERN) { match ->
            match.groupValues[1]  // 只保留一次重複的單元
        }
    }

    /**
     * 簡體轉繁體
     *
     * 使用字元映射表轉換常見的簡繁差異字
     */
    private fun convertToTraditional(text: String): String {
        return text.map { char ->
            SIMPLIFIED_TO_TRADITIONAL[char] ?: char
        }.joinToString("")
    }

    /**
     * 移除多餘尾綴
     *
     * 模型有時會在翻譯後加上 "好嗎？" 等問句
     * 只有在翻譯長度足夠時才移除（避免誤刪短翻譯）
     */
    private fun removeExtraSuffix(text: String): String {
        var result = text
        for (suffix in EXTRA_SUFFIXES) {
            if (result.endsWith(suffix) && result.length > suffix.length + 3) {
                result = result.removeSuffix(suffix).trim()
                break  // 只移除一個尾綴
            }
        }
        return result
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
        Log.d(TAG, "Releasing TranslationService resources")
        try {
            llmInference?.close()
            llmInference = null
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing resources", e)
        } finally {
            isInitialized = false
        }
        Log.d(TAG, "TranslationService released")
    }
}
