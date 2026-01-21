package com.edgemeeting.engine.bridge

import com.edgemeeting.core.model.TranscriptSegment

/**
 * 引擎回調介面（統一音訊與 ASR 回調）
 *
 * 為什麼設計為 EngineCallback：
 * 1. 統一介面：將音訊回調與轉錄回調整合在同一介面
 * 2. 關注點分離：onAudioData 處理音訊流、onTranscript 處理 ASR 結果、onError 處理錯誤
 * 3. 擴充性：未來若需新增其他回調（如 VAD、說話者辨識），可在此擴充
 *
 * 設計考量：
 * - onAudioData：音訊回調，用於音訊資料流處理（波形顯示、音量監控）
 * - onTranscript：轉錄回調，用於接收 ASR 結果
 * - onError：錯誤回調，用於處理 Native 層錯誤
 *
 * 執行緒安全：
 * - C++ 層回調發生在 native background thread
 * - 實作者需自行處理執行緒切換（如使用 withContext(Dispatchers.Main)）
 */
interface EngineCallback {
    /**
     * 音訊資料回調
     *
     * C++ 會呼叫這個方法，把 PCM float array 傳回來。
     * 用於音訊資料流處理（如波形顯示、音量監控）。
     *
     * @param data PCM 音訊資料（float 陣列，範圍 -1.0 到 1.0）
     */
    fun onAudioData(data: FloatArray)

    /**
     * 轉錄結果回調
     *
     * C++ ASR 引擎會呼叫這個方法，回傳轉錄結果。
     * 每個語音段落結束後會觸發一次回調。
     *
     * @param segment 轉錄段落（包含文字、時間、語言代碼等資訊）
     */
    fun onTranscript(segment: TranscriptSegment)

    /**
     * 錯誤回調
     *
     * C++ 層發生錯誤時會呼叫這個方法。
     * 錯誤碼定義於 BridgeResult 中。
     *
     * @param code 錯誤碼（如 1001=模型檔案不存在）
     * @param message 可讀錯誤訊息
     */
    fun onError(code: Int, message: String)
}

/**
 * 這是 Kotlin 與 C++ 之間的安全氣囊（Bridge Pattern）
 *
 * 為什麼使用 Bridge Pattern：
 * 1. 測試性：在單元測試中使用 Fake 實作，無需真實 JNI
 * 2. 解耦：將 Kotlin 層與 C++ 層分離，降低耦合度
 * 3. 彈性：可輕鬆切換實作（Fake、JNI、Mock）
 *
 * 設計考量：
 * - init(EngineConfig)：統一配置介面，支援純錄音或錄音+ASR 模式
 * - setCallback：註冊回調，接收音訊資料、轉錄結果、錯誤訊息
 * - startRecording/stopRecording：控制錄音生命週期
 * - release：釋放 C++ 資源（RAII 原則）
 */
interface EngineBridge {
    /**
     * 初始化引擎
     *
     * @param config 引擎配置（包含 ASR 配置或 null）
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun init(config: EngineConfig): BridgeResult

    /**
     * 註冊回調
     *
     * @param callback 引擎回調介面（音訊、轉錄、錯誤）
     */
    fun setCallback(callback: EngineCallback)

    /**
     * 開始錄音並進行推論
     */
    fun startRecording()

    /**
     * 停止錄音
     */
    fun stopRecording()

    /**
     * 釋放 C++ 記憶體資源
     */
    fun release()
}