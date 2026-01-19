package com.edgemeeting.engine.fixtures

// 提供可預期的 1 秒靜音 PCM 測試資料。
fun pcm1s16kSilence(sampleRateHz: Int = 16000, durationSec: Int = 1): FloatArray {
    require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
    require(durationSec > 0) { "durationSec must be > 0" }
    val samples = sampleRateHz * durationSec
    return FloatArray(samples)
}

// 提供可預期的 1 秒語音 PCM 測試資料（固定頻率正弦波）。
fun pcm1s16kSpeech(
    sampleRateHz: Int = 16000,
    durationSec: Int = 1,
    frequencyHz: Double = 440.0
): FloatArray {
    require(sampleRateHz > 0) { "sampleRateHz must be > 0" }
    require(durationSec > 0) { "durationSec must be > 0" }
    require(frequencyHz > 0.0) { "frequencyHz must be > 0" }
    val samples = sampleRateHz * durationSec
    val data = FloatArray(samples)
    val twoPi = 2.0 * Math.PI
    for (i in 0 until samples) {
        data[i] = kotlin.math.sin(twoPi * frequencyHz * i / sampleRateHz).toFloat()
    }
    return data
}
