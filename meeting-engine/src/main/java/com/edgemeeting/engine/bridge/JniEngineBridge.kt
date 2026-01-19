package com.edgemeeting.engine.bridge

class JniEngineBridge: EngineBridge {

    // 1. 在類別載入時，自動載入 C++ library
    init {
        System.loadLibrary("meeting-engine")
    }

    // 持有 callback 引用，避免被 GC 回收 (雖然 JNI 會有 GlobalRef，但這邊持有比較安全)
    private var callback: AudioCallback? = null

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

    override fun setCallback(callback: AudioCallback) {
        this.callback = callback
        nativeSetCallback(callback)
    }

    override fun initAsr(encoderPath: String, decoderPath: String): BridgeResult {
        // 提前檢查輸入，避免 JNI 端拋例外或發生未定義行為。
        if (encoderPath.isBlank() || decoderPath.isBlank()) {
            return BridgeResult.Failure(1, "Encoder/decoder path is blank")
        }
        val resultCode = nativeInitAsr(encoderPath, decoderPath)
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native initAsr failed with code $resultCode")
        }
    }

    override fun startRecording() {
        nativeStart()
    }

    override fun pushPcm(data: FloatArray, sampleRate: Int): BridgeResult {
        // 提前檢查輸入，避免 JNI 端拋例外或發生未定義行為。
        if (data.isEmpty()) {
            return BridgeResult.Failure(1, "PCM data is empty")
        }
        if (sampleRate <= 0) {
            return BridgeResult.Failure(1, "Invalid sampleRate")
        }
        val resultCode = nativePushPcm(data, sampleRate)
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native pushPcm failed with code $resultCode")
        }
    }

    override fun flushAsr(): BridgeResult {
        val resultCode = nativeFlushAsr()
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native flushAsr failed with code $resultCode")
        }
    }

    override fun stopAsr(): BridgeResult {
        val resultCode = nativeStopAsr()
        return if (resultCode == 0) {
            BridgeResult.Success
        } else {
            BridgeResult.Failure(resultCode, "Native stopAsr failed with code $resultCode")
        }
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
    private external fun nativeInitAsr(encoderPath: String, decoderPath: String): Int
    private external fun nativePushPcm(data: FloatArray, sampleRate: Int): Int
    private external fun nativeFlushAsr(): Int
    private external fun nativeStopAsr(): Int
    private external fun nativeSetCallback(callback: AudioCallback)
    private external fun nativeStart()
    private external fun nativeStop()
    private external fun nativeRelease()

    // --- 供 C++ 呼叫的方法 (Called by JNI) ---
    // C++ 無法直接呼叫 Interface，通常會呼叫這個 JniEngineBridge 的方法，再轉傳給 callback
    // 記得要加 @Keep 防止被 ProGuard 混淆 (如果沒有 @Keep，至少要確保規則有設定)
    fun onNativeAudioData(data: FloatArray) {
        callback?.onAudioData(data)
    }
}