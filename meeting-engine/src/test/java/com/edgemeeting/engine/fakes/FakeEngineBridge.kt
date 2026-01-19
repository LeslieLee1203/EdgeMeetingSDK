package com.edgemeeting.engine.fakes

import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge

// 提供可控的 initAsr() 回傳結果給測試使用。
abstract class FakeEngineBridge : EngineBridge {
    var nextInitAsrResult: BridgeResult = BridgeResult.Success
    var isInitAsrCalled: Boolean = false
    var nextPushPcmResult: BridgeResult = BridgeResult.Success
    var isPushPcmCalled: Boolean = false
    var nextFlushAsrResult: BridgeResult = BridgeResult.Success
    var isFlushAsrCalled: Boolean = false
    var nextStopAsrResult: BridgeResult = BridgeResult.Success
    var isStopAsrCalled: Boolean = false

    override fun initAsr(encoderPath: String, decoderPath: String): BridgeResult {
        isInitAsrCalled = true
        return nextInitAsrResult
    }

    // 提供可控的 pushPcm() 回傳結果給測試使用。
    override fun pushPcm(data: FloatArray, sampleRate: Int): BridgeResult {
        isPushPcmCalled = true
        return nextPushPcmResult
    }

    // 提供可控的 flushAsr() 回傳結果給測試使用。
    override fun flushAsr(): BridgeResult {
        isFlushAsrCalled = true
        return nextFlushAsrResult
    }

    // 提供可控的 stopAsr() 回傳結果給測試使用。
    override fun stopAsr(): BridgeResult {
        isStopAsrCalled = true
        return nextStopAsrResult
    }
}
