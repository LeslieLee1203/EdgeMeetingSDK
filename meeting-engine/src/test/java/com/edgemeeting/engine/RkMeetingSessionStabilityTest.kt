package com.edgemeeting.engine

import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import org.junit.Test

class RkMeetingSessionStabilityTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `should keep emitting transcripts for 10 minutes`() = runTest {
        // 為了驗證長時間穩定性，模擬 10 分鐘仍可持續輸出字幕。
        val bridge = object : EngineBridge {
            override fun init(modelPath: String): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: AudioCallback) = Unit
            override fun startRecording() = Unit
            override fun stopRecording() = Unit
            override fun release() = Unit
        }

        val timeSource = object : TimeSource {
            override fun nowMs(): Long = testScheduler.currentTime
        }

        val session = RkMeetingSession(
            bridge = bridge,
            timeSource = timeSource,
            transcriptDispatcher = StandardTestDispatcher(testScheduler)
        )

        session.prepare()
        session.start()

        val emittedIds = mutableSetOf<String>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { segments ->
                segments.forEach { emittedIds.add(it.id) }
            }
        }

        try {
            advanceTimeBy(10 * 60 * 1000L)
        } finally {
            session.stop()
            session.release()
            job.cancel()
        }

        assertTrue(
            "10 分鐘內應持續產生字幕",
            emittedIds.size >= 100
        )
    }
}
