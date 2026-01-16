package com.edgemeeting.engine

import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RkMeetingSessionTranscriptTest {

    @Test
    fun `start should emit transcript segments roughly every second`() = runTest {
        // 為了驗證字幕輸出節奏，我們需要統計 15 秒內產生的段落數量。
        val bridge = object : EngineBridge {
            override fun init(modelPath: String): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: AudioCallback) = Unit
            override fun startRecording() = Unit
            override fun stopRecording() = Unit
            override fun release() = Unit
        }

        val session = RkMeetingSession(bridge)
        session.prepare()
        session.start()

        val emittedIds = mutableSetOf<String>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { segments ->
                segments.forEach { emittedIds.add(it.id) }
            }
        }

        try {
            advanceTimeBy(15_000)
        } finally {
            job.cancel()
        }

        assertTrue(
            "啟動後 15 秒內應至少產生 10 段字幕，實際為 ${emittedIds.size}",
            emittedIds.size >= 10
        )
    }
}

