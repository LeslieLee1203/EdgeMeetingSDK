package com.edgemeeting.engine.fake

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * FakeAsrEngine 測試
 *
 * 為什麼需要 FakeAsrEngine：
 * 1. 提供 Kotlin 層整合測試用的 Fake 實作
 * 2. 無需 RKNN 硬體即可在 CI 環境執行測試
 * 3. 可注入預設 TranscriptSegment 序列，驗證回調行為
 * 4. 可模擬錯誤情境（模型不存在、載入失敗等）
 *
 * 測試策略：
 * - 測試注入 TranscriptSegment 後，pushAudio() 觸發 callback
 * - 測試注入錯誤碼後，init() 回傳對應 Failure
 * - 測試呼叫順序驗證（init → start → stop → release）
 */
class FakeAsrEngineTest {

    // 測試 1：成功初始化並返回 Success
    @Test
    fun `init with valid config should return Success`() {
        // 為什麼測試：驗證 FakeAsrEngine 可以成功初始化
        val fake = FakeAsrEngine()

        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("en")
            )
        )

        val result = fake.init(config)

        assertTrue(result is BridgeResult.Success)
    }

    // 測試 2：init 失敗時返回 Failure（注入錯誤碼）
    @Test
    fun `init with injected error code should return Failure`() {
        // 為什麼測試：驗證 FakeAsrEngine 可以模擬初始化失敗情境
        val fake = FakeAsrEngine()

        // 注入錯誤碼：模型檔案不存在
        fake.injectInitError(1001, "Model file not found")

        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/invalid/path",
                language = LanguageSetting.Fixed("zh")
            )
        )

        val result = fake.init(config)

        assertTrue(result is BridgeResult.Failure)
        assertEquals(1001, (result as BridgeResult.Failure).code)
        assertEquals("Model file not found", result.message)
    }

    // 測試 3：pushAudio 觸發 onTranscript callback（注入 TranscriptSegment）
    @Test
    fun `pushAudio should trigger onTranscript callback with injected segment`() {
        // 為什麼測試：驗證 FakeAsrEngine 可以模擬 ASR 輸出
        val fake = FakeAsrEngine()

        // 注入預設轉錄結果
        val expectedSegment = TranscriptSegment(
            id = "test-1",
            text = "Hello world",
            speakerId = "Speaker_A",
            isFinal = true,
            startTimeMs = 0,
            endTimeMs = 1000,
            languageCode = "en"
        )
        fake.injectTranscriptSegments(listOf(expectedSegment))

        // 設置 callback
        var receivedSegment: TranscriptSegment? = null
        fake.setCallback(object : EngineCallback {
            override fun onAudioData(data: FloatArray) {}
            override fun onTranscript(segment: TranscriptSegment) {
                receivedSegment = segment
            }
            override fun onError(code: Int, message: String) {}
        })

        // 初始化並開始
        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("en")
            )
        )
        fake.init(config)
        fake.startRecording()

        // 觸發轉錄回調（模擬音訊處理完成）
        fake.triggerNextTranscript()

        // 驗證：receivedSegment 應為注入的 segment
        assertEquals(expectedSegment, receivedSegment)
    }

    // 測試 4：驗證呼叫順序（init → start → stop → release）
    @Test
    fun `lifecycle methods should be called in correct order`() {
        // 為什麼測試：驗證 FakeAsrEngine 正確追蹤生命週期
        val fake = FakeAsrEngine()

        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("en")
            )
        )

        fake.init(config)
        assertTrue(fake.isInitialized())

        fake.startRecording()
        assertTrue(fake.isRecording())

        fake.stopRecording()
        assertTrue(fake.isRecording().not())

        fake.release()
        assertTrue(fake.isInitialized().not())
    }

    // 測試 5：未 init 就 start 應拋出異常
    @Test(expected = IllegalStateException::class)
    fun `startRecording without init should throw IllegalStateException`() {
        // 為什麼測試：防止跳過初始化導致 crash
        val fake = FakeAsrEngine()
        fake.startRecording()
    }

    // 測試 6：注入多個 TranscriptSegment 後，按順序輸出
    @Test
    fun `multiple injected segments should be output in sequence`() {
        // 為什麼測試：驗證 FakeAsrEngine 可以模擬多段語音轉錄
        val fake = FakeAsrEngine()

        val segments = listOf(
            TranscriptSegment(
                id = "seg-1",
                text = "First segment",
                speakerId = "Speaker_A",
                isFinal = true,
                startTimeMs = 0,
                endTimeMs = 1000
            ),
            TranscriptSegment(
                id = "seg-2",
                text = "Second segment",
                speakerId = "Speaker_A",
                isFinal = true,
                startTimeMs = 1000,
                endTimeMs = 2000
            )
        )
        fake.injectTranscriptSegments(segments)

        val receivedSegments = mutableListOf<TranscriptSegment>()
        fake.setCallback(object : EngineCallback {
            override fun onAudioData(data: FloatArray) {}
            override fun onTranscript(segment: TranscriptSegment) {
                receivedSegments.add(segment)
            }
            override fun onError(code: Int, message: String) {}
        })

        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("en")
            )
        )
        fake.init(config)
        fake.startRecording()

        // 觸發多次轉錄回調
        fake.triggerNextTranscript()
        fake.triggerNextTranscript()

        // 驗證：receivedSegments 應包含所有注入的 segments
        assertEquals(2, receivedSegments.size)
        assertEquals("First segment", receivedSegments[0].text)
        assertEquals("Second segment", receivedSegments[1].text)
    }
}
