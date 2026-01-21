package com.edgemeeting.engine

import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.EngineCallback
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * EngineCallback 單元測試
 *
 * 測試目標：
 * 1. onAudioData 回調（既有功能）
 * 2. onTranscript 回調（新增功能）
 * 3. onError 回調（新增功能）
 * 4. 向後相容性驗證
 */
class EngineCallbackTest {

    @Test
    fun `EngineCallback should support onAudioData callback`() {
        // 準備測試資料
        val capturedAudioData = mutableListOf<FloatArray>()

        // 建立測試用 callback
        val callback = object : EngineCallback {
            override fun onAudioData(data: FloatArray) {
                capturedAudioData.add(data)
            }

            override fun onTranscript(segment: TranscriptSegment) {
                // 不實作
            }

            override fun onError(code: Int, message: String) {
                // 不實作
            }
        }

        // 模擬 C++ 回調音訊資料
        val testData = floatArrayOf(0.1f, 0.2f, 0.3f)
        callback.onAudioData(testData)

        // 驗證：回調被正確觸發
        assertEquals(1, capturedAudioData.size)
        assertEquals(3, capturedAudioData[0].size)
        assertEquals(0.1f, capturedAudioData[0][0], 0.001f)
    }

    @Test
    fun `EngineCallback should support onTranscript callback`() {
        // 準備測試資料
        val capturedSegments = mutableListOf<TranscriptSegment>()

        // 建立測試用 callback
        val callback = object : EngineCallback {
            override fun onAudioData(data: FloatArray) {
                // 不實作
            }

            override fun onTranscript(segment: TranscriptSegment) {
                capturedSegments.add(segment)
            }

            override fun onError(code: Int, message: String) {
                // 不實作
            }
        }

        // 模擬 C++ 回調轉錄結果
        val testSegment = TranscriptSegment(
            id = "seg-001",
            text = "測試文字",
            speakerId = "speaker-1",
            isFinal = true,
            startTimeMs = 1000L,
            endTimeMs = 2000L,
            languageCode = "zh"
        )
        callback.onTranscript(testSegment)

        // 驗證：回調被正確觸發
        assertEquals(1, capturedSegments.size)
        assertEquals("測試文字", capturedSegments[0].text)
        assertEquals("zh", capturedSegments[0].languageCode)
    }

    @Test
    fun `EngineCallback should support onError callback`() {
        // 準備測試資料
        val capturedErrors = mutableListOf<Pair<Int, String>>()

        // 建立測試用 callback
        val callback = object : EngineCallback {
            override fun onAudioData(data: FloatArray) {
                // 不實作
            }

            override fun onTranscript(segment: TranscriptSegment) {
                // 不實作
            }

            override fun onError(code: Int, message: String) {
                capturedErrors.add(Pair(code, message))
            }
        }

        // 模擬 C++ 回調錯誤
        callback.onError(1001, "Model not found")

        // 驗證：錯誤回調被正確觸發
        assertEquals(1, capturedErrors.size)
        assertEquals(1001, capturedErrors[0].first)
        assertEquals("Model not found", capturedErrors[0].second)
    }

    @Test
    fun `EngineCallback should handle multiple callbacks in sequence`() {
        // 準備測試資料
        val callbackSequence = mutableListOf<String>()

        // 建立測試用 callback
        val callback = object : EngineCallback {
            override fun onAudioData(data: FloatArray) {
                callbackSequence.add("audio")
            }

            override fun onTranscript(segment: TranscriptSegment) {
                callbackSequence.add("transcript")
            }

            override fun onError(code: Int, message: String) {
                callbackSequence.add("error")
            }
        }

        // 模擬連續回調
        callback.onAudioData(floatArrayOf(0.1f))
        callback.onTranscript(
            TranscriptSegment(
                id = "seg-001",
                text = "test",
                speakerId = "speaker-1",
                isFinal = true,
                startTimeMs = 0L,
                endTimeMs = 1000L
            )
        )
        callback.onError(1001, "test error")

        // 驗證：回調順序正確
        assertEquals(listOf("audio", "transcript", "error"), callbackSequence)
    }
}
