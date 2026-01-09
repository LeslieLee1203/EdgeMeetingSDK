package com.edgemeeting.engine.bridge

sealed interface BridgeResult {
    // 成功 (什麼都不用帶)
    data object Success : BridgeResult

    // 失敗 (帶著錯誤碼與訊息，方便之後映射到 MeetingState.Error)
    data class Failure(val code: Int, val message: String) : BridgeResult
}