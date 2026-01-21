package com.edgemeeting.engine.bridge

import com.edgemeeting.core.model.TranscriptSegment

class JniEngineBridge: EngineBridge {

    // 1. 在類別載入時，自動載入 C++ library
    init {
        System.loadLibrary("meeting-engine")
    }

    // 持有 callback 引用，避免被 GC 回收 (雖然 JNI 會有 GlobalRef，但這邊持有比較安全)
    private var callback: EngineCallback? = null

    override fun init(config: EngineConfig): BridgeResult {
        // 呼叫 native 方法，取得簡單的 Int 結果
        // TODO: Phase 3 將實作完整的 EngineConfig 傳遞（包含 AsrConfig）
        // 目前先傳空字串，Phase 3 時會傳遞實際的模型路徑與 ASR 配置
        val resultCode = nativeInit("")

        // 在 Kotlin 這邊做「轉譯」，保持 C++ 簡單
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native init failed with code $resultCode")
        }
    }

    override fun setCallback(callback: EngineCallback) {
        this.callback = callback
        nativeSetCallback(callback)
    }

    override fun startRecording() {
        nativeStart()
    }

    override fun stopRecording() {
        nativeStop()
    }

    override fun release() {
        nativeRelease()
    }

    // ========== JNI 定義區 (External Functions) ==========
    // 命名慣例：加上 native 前綴，區分介面與實作
    private external fun nativeInit(modelPath: String): Int
    private external fun nativeSetCallback(callback: EngineCallback)
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeRelease()

    // --- 供 C++ 呼叫的方法 (Called by JNI) ---
    // C++ 無法直接呼叫 Interface，通常會呼叫這個 JniEngineBridge 的方法，再轉傳給 callback
    // 記得要加 @Keep 防止被 ProGuard 混淆 (如果沒有 @Keep，至少要確保規則有設定)

    /**
     * 音訊資料回調（由 C++ 呼叫）
     */
    fun onNativeAudioData(data: FloatArray) {
        callback?.onAudioData(data)
    }

    /**
     * 轉錄結果回調（由 C++ 呼叫）
     *
     * @param id 段落識別碼
     * @param text 轉錄文字
     * @param speakerId 說話者識別
     * @param isFinal 是否為最終結果
     * @param startTimeMs 開始時間（毫秒）
     * @param endTimeMs 結束時間（毫秒）
     * @param languageCode 語言代碼（可選）
     */
    fun onNativeTranscript(
        id: String,
        text: String,
        speakerId: String,
        isFinal: Boolean,
        startTimeMs: Long,
        endTimeMs: Long,
        languageCode: String?
    ) {
        val segment = TranscriptSegment(
            id = id,
            text = text,
            speakerId = speakerId,
            isFinal = isFinal,
            startTimeMs = startTimeMs,
            endTimeMs = endTimeMs,
            languageCode = languageCode
        )
        callback?.onTranscript(segment)
    }

    /**
     * 錯誤回調（由 C++ 呼叫）
     *
     * @param code 錯誤碼
     * @param message 錯誤訊息
     */
    fun onNativeError(code: Int, message: String) {
        callback?.onError(code, message)
    }
}