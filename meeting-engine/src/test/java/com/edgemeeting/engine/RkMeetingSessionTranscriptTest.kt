package com.edgemeeting.engine

import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import com.edgemeeting.engine.fake.FakeAsrEngine
import junit.framework.TestCase.assertEquals
import junit.framework.TestCase.assertTrue
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.launch
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runTest
import kotlinx.coroutines.test.StandardTestDispatcher
import kotlinx.coroutines.withTimeoutOrNull
import org.junit.Test

class RkMeetingSessionTranscriptTest {

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `start should emit transcript segments roughly every second`() = runTest {
        // 為了驗證字幕輸出節奏，我們需要統計 15 秒內產生的段落數量。
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: EngineCallback) = Unit
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
            override fun init(config: EngineConfig): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: EngineCallback) = Unit
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
            override fun init(config: EngineConfig): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: EngineCallback) = Unit
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
            override fun init(config: EngineConfig): BridgeResult = BridgeResult.Success
            override fun setCallback(callback: EngineCallback) = Unit
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

    // ===== T038: onTranscript callback 整合測試 =====

    /**
     * 產生可識別的假字幕（id 以 "generator-" 開頭）。
     * 用於測試時區分來自 transcriptGenerator 的 segment 和來自 onTranscript callback 的 segment。
     */
    private class MarkedTranscriptGenerator : TranscriptGenerator {
        override fun doGenerate(startTimeMs: Long, endTimeMs: Long): TranscriptSegment {
            return TranscriptSegment(
                id = "generator-${startTimeMs}-${endTimeMs}",
                text = "Generated",
                speakerId = "Generator",
                isFinal = true,
                startTimeMs = startTimeMs,
                endTimeMs = endTimeMs
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `onTranscript callback should emit to transcriptFlow`() = runTest {
        // Given: 使用 FakeAsrEngine 並注入一個 segment
        val fakeEngine = FakeAsrEngine()
        val expectedSegment = TranscriptSegment(
            id = "test-001",
            text = "Hello World",
            speakerId = "Speaker1",
            isFinal = true,
            startTimeMs = 0,
            endTimeMs = 1000
        )
        fakeEngine.injectTranscriptSegments(listOf(expectedSegment))

        val session = RkMeetingSession(
            bridge = fakeEngine,
            timeSource = object : TimeSource {
                override fun nowMs(): Long = testScheduler.currentTime
            },
            // 使用可識別的產生器，測試時可區分來源
            transcriptGenerator = MarkedTranscriptGenerator(),
            transcriptDispatcher = StandardTestDispatcher(testScheduler)
        )

        session.prepare()
        session.start()

        // When: 觸發 onTranscript callback
        val received = mutableListOf<TranscriptSegment>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { segments ->
                received.addAll(segments)
            }
        }

        // 先推進時間讓 collect 協程開始執行
        advanceTimeBy(1)
        fakeEngine.triggerNextTranscript()
        // 再推進時間讓 emit 的值被收集
        advanceTimeBy(100)

        // 先停止再檢查，避免 while 迴圈持續跑
        session.stop()
        session.release()
        job.cancel()

        // Then: transcriptFlow 應該收到來自 onTranscript 的 segment（id 不以 "generator-" 開頭）
        val fromCallback = received.filter { !it.id.startsWith("generator-") }
        assertTrue(
            "transcriptFlow 應收到 onTranscript 觸發的 segment，實際收到 ${fromCallback.size} 個（總共 ${received.size}）",
            fromCallback.any { it.id == expectedSegment.id }
        )
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `multiple onTranscript callbacks should emit in order`() = runTest {
        // Given: 注入多個 segments
        val fakeEngine = FakeAsrEngine()
        val segments = listOf(
            TranscriptSegment("seg-1", "First", "S1", true, 0, 1000),
            TranscriptSegment("seg-2", "Second", "S1", true, 1000, 2000),
            TranscriptSegment("seg-3", "Third", "S1", true, 2000, 3000)
        )
        fakeEngine.injectTranscriptSegments(segments)

        val session = RkMeetingSession(
            bridge = fakeEngine,
            timeSource = object : TimeSource {
                override fun nowMs(): Long = testScheduler.currentTime
            },
            transcriptGenerator = MarkedTranscriptGenerator(),
            transcriptDispatcher = StandardTestDispatcher(testScheduler)
        )

        session.prepare()
        session.start()

        // When: 依序觸發所有 segments
        val received = mutableListOf<TranscriptSegment>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { emitted ->
                received.addAll(emitted)
            }
        }

        // 先推進時間讓 collect 協程開始執行
        advanceTimeBy(1)
        segments.forEach { _ ->
            fakeEngine.triggerNextTranscript()
            advanceTimeBy(50)  // 推進時間讓 emit 被收集
        }

        // 先停止再檢查
        session.stop()
        session.release()
        job.cancel()

        // Then: 過濾出來自 onTranscript callback 的 segments（非 generator- 開頭）
        val fromCallback = received.filter { !it.id.startsWith("generator-") }
        assertEquals(
            "應收到 ${segments.size} 個來自 callback 的 segments，實際 ${fromCallback.size}",
            segments.size,
            fromCallback.size
        )

        segments.forEachIndexed { index, expected ->
            assertEquals(
                "第 $index 個 segment id 應為 ${expected.id}",
                expected.id,
                fromCallback[index].id
            )
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    @Test
    fun `high frequency onTranscript should not cause memory issues`() = runTest {
        // Given: 準備大量 segments 模擬高頻觸發（簡化為 100 個，足以驗證記憶體處理）
        val fakeEngine = FakeAsrEngine()
        val segmentCount = 100
        val segments = (0 until segmentCount).map { i ->
            TranscriptSegment(
                id = "seg-$i",
                text = "Segment $i content",
                speakerId = "Speaker",
                isFinal = true,
                startTimeMs = (i * 500).toLong(),
                endTimeMs = ((i + 1) * 500).toLong()
            )
        }
        fakeEngine.injectTranscriptSegments(segments)

        val session = RkMeetingSession(
            bridge = fakeEngine,
            timeSource = object : TimeSource {
                override fun nowMs(): Long = testScheduler.currentTime
            },
            transcriptGenerator = MarkedTranscriptGenerator(),
            transcriptDispatcher = StandardTestDispatcher(testScheduler)
        )

        session.prepare()
        session.start()

        // When: 高頻觸發所有 segments
        val received = mutableListOf<TranscriptSegment>()
        val job = backgroundScope.launch {
            session.transcriptFlow.collect { emitted ->
                received.addAll(emitted)
            }
        }

        // 先推進時間讓 collect 協程開始執行
        advanceTimeBy(1)
        // 每次觸發後推進時間，讓 collector 消費（避免 buffer overflow）
        repeat(segmentCount) {
            fakeEngine.triggerNextTranscript()
            advanceTimeBy(1)  // 給 collector 時間消費
        }
        advanceTimeBy(10)

        // 先停止
        session.stop()
        session.release()
        job.cancel()

        // Then: 過濾出來自 callback 的 segments，應收到大部分
        val fromCallback = received.filter { !it.id.startsWith("generator-") }
        assertTrue(
            "應收到至少 90% 的 callback segments，實際收到 ${fromCallback.size}/$segmentCount",
            fromCallback.size >= (segmentCount * 0.9).toInt()
        )
    }
}

