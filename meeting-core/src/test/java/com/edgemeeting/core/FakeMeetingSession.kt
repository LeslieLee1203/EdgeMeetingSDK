package com.edgemeeting.core

import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.TranscriptSegment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.launch

// 這是一個「乖巧」的 Session 實作，專門用來欺騙 UI 和測試案例
class FakeMeetingSession(
    private val scope: CoroutineScope // 注入 Scope 讓我們可以控制時間
) : MeetingSession {

    private val _state = MutableStateFlow<MeetingState>(MeetingState.Idle)
    override val state: StateFlow<MeetingState> = _state.asStateFlow()

    override val transcriptFlow: Flow<List<TranscriptSegment>> = flow {
        // 這裡可以之後模擬吐出假字幕，目前先留空
    }

    override fun prepare() {
        // 模擬真實行為：先切換到 Preparing
        _state.value = MeetingState.Preparing

        scope.launch {
            // 模擬 NPU 載入耗時 100ms
            delay(100)
            // 載入完成，變更為 Ready
            _state.value = MeetingState.Ready
        }
    }

    override fun start() {
        // 只有 Ready 才能 Start (模擬狀態機保護)
        if (_state.value == MeetingState.Ready) {
            // 切換到 Listening
            _state.value = MeetingState.Listening
        }
    }

    override fun stop() {
        if (_state.value == MeetingState.Listening) {
            // 切換回 Ready
            _state.value = MeetingState.Ready
        }
    }

    override fun release() {
        _state.value = MeetingState.Idle
    }

}