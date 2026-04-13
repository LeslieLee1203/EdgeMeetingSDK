package com.edgemeeting.core.model

/**
 * VAD（Voice Activity Detection）可調整參數
 *
 * 用途：在開發/測試階段透過 UI 動態調整 VAD 行為，
 * 找出最佳參數組合後再寫回 C++ 預設值。
 *
 * 參數分為五組：
 * 1. 快速靜音偵測（detectSilence）—— chunk 級別即時判斷
 * 2. 精確 VAD 分類（detectVadState）—— SILENCE / PAUSE / SPEECH 邊界
 * 3. 推論觸發時間閾值（pushAudio）—— 控制何時送 Whisper 推論
 * 4. 樣本大小限制（pushAudio）—— 控制最短/最長緩衝
 * 5. 能量預檢（shouldSkipInference）—— 推論前過濾幻覺
 *
 * 熱更新：透過 MeetingSession.updateVadConfig() 傳遞，
 * C++ 層下一個 pushAudio() 呼叫即生效（延遲 ≤10ms）。
 */
data class VadConfig(
    // === 快速靜音偵測（detectSilence）===
    /** RMS 能量閾值：低於此值且 ZCR 低則判為靜音。預設 1200（≈ -45dBFS）。*/
    val fastSilenceRmsThreshold: Float = 1200.0f,
    /** 零交叉率閾值：低於此值表示訊號單調（無語音頻率變化）。預設 0.05。*/
    val fastSilenceZcrThreshold: Float = 0.05f,

    // === 精確 VAD 分類（detectVadState）===
    /** 絕對靜音 RMS 下界：低於此值直接判為 SILENCE。預設 40.0。*/
    val silenceRmsThreshold: Float = 40.0f,
    /** PAUSE 邊界 RMS：低於此值且 ZCR 低判為 PAUSE，高於此值判為 SPEECH。預設 150.0。*/
    val pauseRmsThreshold: Float = 150.0f,
    /** SILENCE 邊界 ZCR（精確層，未使用於主要判斷路徑）。預設 0.03。*/
    val silenceZcrThreshold: Float = 0.03f,
    /** PAUSE/SPEECH 邊界 ZCR：高於此值表示有語音頻率變化。預設 0.20。*/
    val pauseZcrThreshold: Float = 0.20f,

    // === 推論觸發時間閾值（pushAudio）===
    /** 靜音持續此毫秒數後觸發推論。預設 800ms。*/
    val silenceThresholdMs: Int = 800,
    /** PAUSE 持續此毫秒數後觸發推論。預設 1500ms。*/
    val pauseThresholdMs: Int = 1500,
    /** 最低有效語音時長：低於此值不觸發推論（防止幻覺）。預設 2000ms。*/
    val minSpeechDurationMs: Int = 2000,
    /** 定時中間推論間隔：每隔此毫秒數輸出一次中間結果。預設 5000ms。*/
    val intermediateIntervalMs: Int = 5000,

    // === 樣本大小限制（pushAudio）===
    /** 觸發推論的最低音訊樣本數。預設 48000（= 3 秒 @ 16kHz）。*/
    val minSamples: Int = 48000,
    /** 緩衝最大樣本數，超過則強制推論。預設 256000（= 16 秒 @ 16kHz）。*/
    val maxSamples: Int = 256000,

    // === 能量預檢（shouldSkipInference）===
    /** 窗口 RMS 能量閾值：低於此值的窗口計為靜音窗口。預設 25.0。*/
    val energyThreshold: Float = 25.0f,
    /** 靜音窗口比例閾值：超過此比例則跳過推論。預設 0.90（90%）。*/
    val silenceRatioThreshold: Float = 0.90f
) {
    companion object {
        /** 與 C++ 硬編碼預設值完全相同的預設參數組。*/
        val Default = VadConfig()
    }
}
