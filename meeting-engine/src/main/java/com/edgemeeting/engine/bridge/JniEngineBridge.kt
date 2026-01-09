package com.edgemeeting.engine.bridge

class JniEngineBridge: EngineBridge {

    // 1. 在類別載入時，自動載入 C++ library
    init {
        System.loadLibrary("meeting-engine")
    }

    override fun init(modelPath: String): BridgeResult {
        // 呼叫 native 方法，取得簡單的 Int 結果
        val resultCode = nativeInit(modelPath)

        // 在 Kotlin 這邊做「轉譯」，保持 C++ 簡單
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native init failed with code $resultCode")
        }
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
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeRelease()
}