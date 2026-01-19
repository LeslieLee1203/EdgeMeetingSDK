package com.edgemeeting.engine.bridge


// 音訊資料回調介面
interface AudioCallback {
    // C++ 會呼叫這個方法，把 PCM float array 傳回來
    fun onAudioData(data: FloatArray)
}

/**
 * 這是 Kotlin 與 C++ 之間的安全氣囊。
 * 1. 在單元測試中，我們會用 Fake 實作它。
 * 2. 在真實 App 中，我們會用 JNI 實作它。
 */
interface EngineBridge {
    /**
     * 初始化引擎
     * @param modelPath 模型檔案的絕對路徑
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun init(modelPath: String): BridgeResult

    /**
     * 初始化 ASR 資源
     * @param encoderPath 模型檔案的絕對路徑
     * @param decoderPath 模型檔案的絕對路徑
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun initAsr(encoderPath: String, decoderPath: String): BridgeResult

    /**
     * 推送 PCM 片段到 ASR
     * @param data PCM float array
     * @param sampleRate 取樣率
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun pushPcm(data: FloatArray, sampleRate: Int): BridgeResult

    /**
     * 觸發 ASR flush
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun flushAsr(): BridgeResult

    /**
     * 停止 ASR 並釋放其資源
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun stopAsr(): BridgeResult

    // 新增：註冊回調
    fun setCallback(callback: AudioCallback)

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