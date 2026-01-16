package com.edgemeeting.engine

import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.bridge.AudioCallback
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
            override fun init(modelPath: String): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: AudioCallback) = Unit
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
}
