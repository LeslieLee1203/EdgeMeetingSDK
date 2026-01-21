package com.edgemeeting.engine

import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RkMeetingSessionStateTest {

    @Test
    fun `start without prepare should become Error`() = runTest {
        // 為了確保錯誤狀態可見，未準備就啟動應回報 Error。
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: EngineCallback) = Unit
            override fun startRecording() = Unit
            override fun stopRecording() = Unit
            override fun release() = Unit
        }

        val session = RkMeetingSession(bridge)

        session.start()

        val state = session.state.value
        assertTrue("未準備即啟動應回報錯誤", state is MeetingState.Error)
        if (state is MeetingState.Error) {
            assertTrue("錯誤訊息不可為空", state.message.isNotBlank())
        }
    }

    @Test
    fun `prepare failure should become Error`() = runTest {
        // 為了確保準備失敗可回報錯誤狀態，模擬 init 失敗。
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult =
                BridgeResult.Failure(123, "Init failed")

            override fun setCallback(callback: EngineCallback) = Unit
            override fun startRecording() = Unit
            override fun stopRecording() = Unit
            override fun release() = Unit
        }

        val session = RkMeetingSession(bridge)

        session.prepare()

        val state = session.state.value
        assertTrue("準備失敗應回報錯誤", state is MeetingState.Error)
        if (state is MeetingState.Error) {
            assertTrue("錯誤碼應透傳", state.code == 123)
            assertTrue("錯誤訊息不可為空", state.message.isNotBlank())
        }
    }
}
