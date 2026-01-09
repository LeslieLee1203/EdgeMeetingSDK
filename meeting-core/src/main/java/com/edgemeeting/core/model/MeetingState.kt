package com.edgemeeting.core.model

sealed interface MeetingState {
    // 閒置 (記憶體乾淨)
    data object Idle : MeetingState

    // 準備中 (正在載入 Model, Check License...)
    data object Preparing : MeetingState

    // 就緒 (Model 已載入，隨時可以開始錄音) ✅ 這就是你想要的狀態
    data object Ready : MeetingState

    // 聽寫中 (NPU 與 Mic 都在跑)
    data object Listening : MeetingState

    // 錯誤
    data class Error(val code: Int, val message: String) : MeetingState
}