package com.edgemeeting.engine

import com.edgemeeting.engine.bridge.AudioCallback
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import org.junit.Test

class RkMeetingSessionTranscriptTest {

    @OptIn(ExperimentalCoroutinesApi::class)
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
            advanceTimeBy(15_000)
        } finally {
            session.stop()
            session.release()
            job.cancel()
        }

        assertTrue(
            "啟動後 15 秒內應至少產生 10 段字幕，實際為 ${emittedIds.size}",
            emittedIds.size >= 10
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `stop should stop emitting transcripts within 2 seconds`() = runTest {
        // 為了驗證停止後不再輸出，我們先確保有產生字幕，再檢查停止後計數不再增加。
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

        val emittedIds = mutableListOf<String>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { segments ->
                segments.forEach { emittedIds.add(it.id) }
            }
        }

        try {
            advanceTimeBy(3_000)
            assertTrue(
                "停止前應至少產生 2 段字幕，實際為 ${emittedIds.size}",
                emittedIds.size >= 2
            )

            session.stop()
            val countAfterStop = emittedIds.size
            advanceTimeBy(2_000)

            assertTrue(
                "停止後 2 秒內不應新增字幕，停止時 ${countAfterStop}，目前 ${emittedIds.size}",
                emittedIds.size == countAfterStop
            )
        } finally {
            job.cancel()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `transcript segments should contain required fields and increasing time`() = runTest {
        // 為了驗證資料契約，我們檢查欄位完整性與時間遞增。
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

        val segments = mutableListOf<com.edgemeeting.core.model.TranscriptSegment>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { emitted ->
                emitted.forEach { segments.add(it) }
            }
        }

        try {
            advanceTimeBy(3_000)
        } finally {
            session.stop()
            session.release()
            job.cancel()
        }

        assertTrue("至少需要 2 段字幕供欄位檢查", segments.size >= 2)

        val first = segments[0]
        val second = segments[1]

        assertTrue("id 不可為空", first.id.isNotBlank())
        assertTrue("text 不可為空", first.text.isNotBlank())
        assertTrue("speakerId 必須為 Unknown", first.speakerId == "Unknown")
        assertTrue("isFinal 必須為 true", first.isFinal)
        assertTrue("startTimeMs 必須 >= 0", first.startTimeMs >= 0)
        assertTrue("endTimeMs 必須 >= startTimeMs", first.endTimeMs >= first.startTimeMs)
        assertTrue("endTimeMs 必須遞增", second.endTimeMs > first.endTimeMs)
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `update interval should stay within 1_5 seconds for 95 percent`() = runTest {
        // 為了驗證節奏穩定性，我們收集一段時間的間隔並檢查 95% 不超過 1.5 秒。
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

        val timestampsMs = mutableListOf<Long>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { segments ->
                val now = testScheduler.currentTime
                if (segments.isNotEmpty()) {
                    timestampsMs.add(now)
                }
            }
        }

        try {
            advanceTimeBy(20_000)
        } finally {
            session.stop()
            session.release()
            job.cancel()
        }

        val intervals = timestampsMs.zipWithNext { a, b -> b - a }
        val overLimit = intervals.count { it > 1_500L }
        val maxAllowedOverLimit = (intervals.size * 0.05).toInt()

        assertTrue("需要至少 5 個間隔供計算", intervals.size >= 5)
        assertTrue(
            "超過 1.5 秒的間隔過多：${overLimit}/${intervals.size}",
            overLimit <= maxAllowedOverLimit
        )
    }
}

