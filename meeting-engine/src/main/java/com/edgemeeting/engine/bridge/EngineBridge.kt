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
    fun init(modelPath: String): BridgeResult  // 👈 修改這裡

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