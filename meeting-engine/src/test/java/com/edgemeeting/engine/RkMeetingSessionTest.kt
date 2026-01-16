package com.edgemeeting.engine

import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import junit.framework.TestCase.assertEquals
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RkMeetingSessionTest {

    // 1. 定義一個受我們控制的假 Bridge (Mock Object)
    // 它的任務是記錄「有沒有被呼叫」以及「模擬回傳結果」
    class FakeEngineBridge : EngineBridge {
        var nextInitResult: BridgeResult = BridgeResult.Success // 設定預設行為
        var isInitCalled = false

        override fun init(modelPath: String): BridgeResult {
            isInitCalled = true
            return nextInitResult
        }

        // 其他方法暫時不重要，留空即可
        override fun setCallback(callback: AudioCallback) = Unit
        override fun startRecording() {}
        override fun stopRecording() {}
        override fun release() {}
    }

    @Test
    fun `prepare should call init on bridge and_become Ready on success`() = runTest {
        // Arrange (準備)
        val fakeBridge = FakeEngineBridge()
        fakeBridge.nextInitResult = BridgeResult.Success // 我們期望底層回傳成功

        val session = RkMeetingSession(fakeBridge)

        // Act (執行)
        session.prepare()

        // Assert (驗證)
        // 驗證點 1: 確保有去呼叫底層
        assertEquals("應該要呼叫底層 C++ init", true, fakeBridge.isInitCalled)

        // 驗證點 2: 確保狀態機有正確更新
        assertEquals("成功時狀態應變為 Ready", MeetingState.Ready, session.state.value)
    }

    @Test
    fun `prepare should become Error on failure`() = runTest {
        // Arrange (準備失敗的情境)
        val fakeBridge = FakeEngineBridge()
        fakeBridge.nextInitResult = BridgeResult.Failure(101, "Model Not Found")

        val session = RkMeetingSession(fakeBridge)

        // Act
        session.prepare()

        // Assert
        val currentState = session.state.value
        assert(currentState is MeetingState.Error)
        assertEquals("錯誤碼應透傳", 101, (currentState as MeetingState.Error).code)
    }
}