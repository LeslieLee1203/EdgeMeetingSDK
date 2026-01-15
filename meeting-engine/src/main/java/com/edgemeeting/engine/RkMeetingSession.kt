package com.edgemeeting.engine

import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.emptyFlow

// 建構子注入 EngineBridge，這是 TDD 可測試性的關鍵！
class RkMeetingSession(
    private val bridge: EngineBridge
) : MeetingSession {

    init {
        // 註冊 Callback
        bridge.setCallback(object : AudioCallback {
            override fun onAudioData(data: FloatArray) {
                // 這裡會非常頻繁被呼叫 (每 160ms 一次)
                // 為了驗證 Phase 3 成功，我們印出陣列長度與第一個值
                if (data.isNotEmpty()) {
                    android.util.Log.d("JNI_CALLBACK", "Received ${data.size} frames. First: ${data[0]}")
                }
            }
        })
    }

    // 狀態管理
    private val _state = MutableStateFlow<MeetingState>(MeetingState.Idle)
    override val state: StateFlow<MeetingState> = _state.asStateFlow()

    // 資料流 (暫時留空)
    override val transcriptFlow: Flow<List<TranscriptSegment>> = emptyFlow()

    override fun prepare() {
// 1. 先通知 UI 我們正在忙 (顯示轉圈圈)
        _state.value = MeetingState.Preparing

        // 2. 呼叫底層 C++ (這裡是同步呼叫，會卡住一下，之後我們再優化到背景執行緒)
        // 暫時寫死路徑，Phase 1 重點是架構跑通
        val defaultModelPath = "/data/local/tmp/models"
        val result = bridge.init(defaultModelPath)

        // 3. 根據底層回傳的結果，決定下一個狀態
        when (result) {
            is BridgeResult.Success -> {
                // 成功 -> 變成 Ready (綠燈)
                _state.value = MeetingState.Ready
            }
            is BridgeResult.Failure -> {
                // 失敗 -> 變成 Error (顯示錯誤訊息)
                _state.value = MeetingState.Error(result.code, result.message)
            }
        }
    }

    override fun start() {
        // 1. 狀態防呆：只有 Ready 才能開始
        val currentState = _state.value
        if (currentState is MeetingState.Ready) {

            // 2. 呼叫 JNI
            bridge.startRecording()

            // 3. 更新狀態
            _state.value = MeetingState.Listening
        }
    }

    override fun stop() {
        val currentState = _state.value
        if (currentState is MeetingState.Listening) {

            bridge.stopRecording()

            // 更新狀態 (回到 Ready)
            _state.value = MeetingState.Ready
        }
    }

    override fun release() {
        bridge.release()
        _state.value = MeetingState.Idle
    }
}