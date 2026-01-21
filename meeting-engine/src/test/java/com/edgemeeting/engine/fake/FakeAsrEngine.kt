package com.edgemeeting.engine.fake

import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.EngineBridge

/**
 * FakeAsrEngine - Kotlin 層整合測試用的 Fake 實作
 *
 * 為什麼需要 FakeAsrEngine：
 * 1. 提供無需 RKNN 硬體的測試環境（CI 友善）
 * 2. 可注入預設 TranscriptSegment 序列，驗證回調行為
 * 3. 可模擬錯誤情境（模型不存在、載入失敗等）
 * 4. 支援驗證生命週期呼叫順序（init → start → stop → release）
 *
 * 設計原則：
 * - 實作 EngineBridge 介面，與真實 JniEngineBridge 保持一致
 * - 使用 Kotlin 內部狀態追蹤生命週期（initialized, recording）
 * - 支援錯誤注入與轉錄結果注入
 * - 簡化音訊處理邏輯（直接輸出注入的 TranscriptSegment）
 *
 * 使用範例：
 * ```kotlin
 * val fake = FakeAsrEngine()
 * fake.injectTranscriptSegments(listOf(expectedSegment))
 * fake.setCallback(callback)
 * fake.init(config)
 * fake.startRecording()
 * // pushAudio 時會觸發 onTranscript callback
 * ```
 */
class FakeAsrEngine : EngineBridge {

    // 內部狀態
    private var initialized = false
    private var recording = false
    private var callback: EngineCallback? = null

    // 注入的錯誤碼與訊息
    private var initErrorCode: Int? = null
    private var initErrorMessage: String? = null

    // 注入的 TranscriptSegment 序列
    private val injectedSegments = mutableListOf<TranscriptSegment>()
    private var currentSegmentIndex = 0

    // 配置（用於驗證）
    private var currentConfig: EngineConfig? = null

    /**
     * 初始化引擎
     *
     * @param config 引擎配置
     * @return 若有注入錯誤則回傳 Failure，否則回傳 Success
     */
    override fun init(config: EngineConfig): BridgeResult {
        // 為什麼檢查注入錯誤：模擬模型載入失敗、檔案不存在等情境
        if (initErrorCode != null) {
            return BridgeResult.Failure(
                code = initErrorCode!!,
                message = initErrorMessage ?: "Unknown error"
            )
        }

        currentConfig = config
        initialized = true
        return BridgeResult.Success
    }

    /**
     * 註冊回調
     *
     * @param callback 引擎回調介面
     */
    override fun setCallback(callback: EngineCallback) {
        this.callback = callback
    }

    /**
     * 開始錄音
     *
     * 為什麼需要 check initialized：防止跳過初始化導致 crash
     */
    override fun startRecording() {
        check(initialized) { "Must call init() before startRecording()" }
        recording = true
        currentSegmentIndex = 0  // 重置段落索引
    }

    /**
     * 停止錄音
     */
    override fun stopRecording() {
        recording = false
    }

    /**
     * 釋放資源
     *
     * 重置所有內部狀態
     */
    override fun release() {
        initialized = false
        recording = false
        callback = null
        currentConfig = null
        currentSegmentIndex = 0
    }

    // === 測試輔助方法 ===

    /**
     * 注入初始化錯誤
     *
     * @param code 錯誤碼
     * @param message 錯誤訊息
     *
     * 用於測試錯誤情境（如模型不存在、載入失敗）
     */
    fun injectInitError(code: Int, message: String) {
        initErrorCode = code
        initErrorMessage = message
    }

    /**
     * 注入 TranscriptSegment 序列
     *
     * @param segments 要注入的轉錄段落列表
     *
     * 為什麼需要注入：模擬 ASR 引擎輸出，無需真實推論
     * 使用時機：測試 onTranscript callback 行為、驗證 transcriptFlow 輸出
     */
    fun injectTranscriptSegments(segments: List<TranscriptSegment>) {
        injectedSegments.clear()
        injectedSegments.addAll(segments)
        currentSegmentIndex = 0
    }

    /**
     * 模擬推送音訊並觸發轉錄回調
     *
     * 為什麼需要此方法：FakeAsrEngine 不進行真實音訊處理，
     * 但需要提供方式讓測試觸發 onTranscript callback。
     *
     * 使用範例：
     * ```kotlin
     * fake.injectTranscriptSegments(listOf(segment1, segment2))
     * fake.triggerNextTranscript()  // 觸發 segment1
     * fake.triggerNextTranscript()  // 觸發 segment2
     * ```
     */
    fun triggerNextTranscript() {
        check(recording) { "Must call startRecording() before triggering transcript" }
        check(currentSegmentIndex < injectedSegments.size) {
            "No more injected segments (index=$currentSegmentIndex, total=${injectedSegments.size})"
        }

        val segment = injectedSegments[currentSegmentIndex]
        currentSegmentIndex++

        // 觸發 callback
        callback?.onTranscript(segment)
    }

    /**
     * 模擬錯誤回調
     *
     * @param code 錯誤碼
     * @param message 錯誤訊息
     *
     * 用於測試 onError callback 行為
     */
    fun triggerError(code: Int, message: String) {
        callback?.onError(code, message)
    }

    /**
     * 模擬音訊資料回調
     *
     * @param data PCM 音訊資料
     *
     * 用於測試 onAudioData callback 行為
     */
    fun triggerAudioData(data: FloatArray) {
        check(recording) { "Must call startRecording() before triggering audio data" }
        callback?.onAudioData(data)
    }

    // === 狀態查詢方法（用於測試驗證）===

    /**
     * 查詢是否已初始化
     *
     * @return true 若已呼叫 init() 且成功
     */
    fun isInitialized(): Boolean = initialized

    /**
     * 查詢是否正在錄音
     *
     * @return true 若已呼叫 startRecording()
     */
    fun isRecording(): Boolean = recording

    /**
     * 取得當前配置
     *
     * @return 當前的 EngineConfig（用於測試驗證）
     */
    fun getCurrentConfig(): EngineConfig? = currentConfig

    /**
     * 取得剩餘未觸發的 TranscriptSegment 數量
     *
     * @return 剩餘段落數量
     */
    fun getRemainingSegmentCount(): Int {
        return injectedSegments.size - currentSegmentIndex
    }
}
