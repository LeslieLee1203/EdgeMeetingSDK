package com.edgemeeting.engine.bridge


interface EngineBridge {
    /**
     * 初始化引擎
     * @param modelPath 模型檔案的絕對路徑
     * @return BridgeResult.Success 或 BridgeResult.Failure
     */
    fun init(modelPath: String): BridgeResult  // 👈 修改這裡

    fun startRecording()

    fun stopRecording()

    fun release()
}