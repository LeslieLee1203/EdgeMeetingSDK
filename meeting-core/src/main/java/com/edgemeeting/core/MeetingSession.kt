package com.edgemeeting.core

import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.TranscriptSegment
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow

interface MeetingSession {

    // 狀態流：UI 觀察這個來決定要不要顯示 Loading 或錯誤訊息
    val state: StateFlow<MeetingState>

    // 資料流：源源不絕的字幕事件
    // App 訂閱這個 Flow 來更新 UI
    val transcriptFlow: Flow<List<TranscriptSegment>>

    // 生命週期
    // 預先載入資源 (Idle -> Preparing -> Ready)
    fun prepare()

    // 從 Ready 進入 Listening
    fun start()

    // 暫停 (回到 Ready 狀態，不釋放模型)
    fun stop()

    // 釋放資源 (C++ 記憶體)
    fun release()
}