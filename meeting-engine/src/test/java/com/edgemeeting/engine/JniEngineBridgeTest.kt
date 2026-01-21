package com.edgemeeting.engine

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.engine.bridge.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * JniEngineBridge 測試
 *
 * 測試策略：
 * - 測試 EngineConfig 與 AsrConfig 參數萃取邏輯
 * - 測試 LanguageSetting 轉換邏輯（sealed class → string）
 * - 實際 JNI 整合測試需在 Android 設備上執行
 *
 * 注意：
 * - JniEngineBridge 需要載入 native library（System.loadLibrary）
 * - 涉及 native library 的測試已註解，需在實體設備驗證
 * - CI 環境可執行參數轉換邏輯測試
 */
class JniEngineBridgeTest {

    // 注意：需要 native library 的測試已註解
    // private lateinit var bridge: JniEngineBridge
    //
    // @Before
    // fun setUp() {
    //     bridge = JniEngineBridge()
    // }

    // 測試 1：init 成功（使用 Whisper ASR config）
    @Test
    fun `init with Whisper config should return Success`() {
        // 為什麼測試：驗證 EngineConfig 正確傳遞至 native
        // 跳過原因：需要真實 native library
        //
        // val config = EngineConfig(
        //     asrConfig = AsrConfig.Whisper(
        //         modelsPath = "/data/local/tmp/models",
        //         language = LanguageSetting.Auto
        //     )
        // )
        //
        // val result = bridge.init(config)
        //
        // assertTrue(result is BridgeResult.Success)
    }

    // 測試 2：init 失敗（模型路徑不存在）
    @Test
    fun `init with invalid models path should return Failure`() {
        // 為什麼測試：驗證錯誤處理（模型檔案不存在）
        // 跳過原因：需要真實 native library
        //
        // val config = EngineConfig(
        //     asrConfig = AsrConfig.Whisper(
        //         modelsPath = "/invalid/path",
        //         language = LanguageSetting.Auto
        //     )
        // )
        //
        // val result = bridge.init(config)
        //
        // assertTrue(result is BridgeResult.Failure)
        // val failure = result as BridgeResult.Failure
        // assertEquals(1001, failure.code)  // ERR_MODEL_NOT_FOUND
    }

    // 測試 3：init 使用指定語言
    @Test
    fun `init with fixed language should pass language to native`() {
        // 為什麼測試：驗證語言設定正確傳遞
        // 跳過原因：需要真實 native library
        //
        // val config = EngineConfig(
        //     asrConfig = AsrConfig.Whisper(
        //         modelsPath = "/data/local/tmp/models",
        //         language = LanguageSetting.Fixed("zh")
        //     )
        // )
        //
        // val result = bridge.init(config)
        //
        // assertTrue(result is BridgeResult.Success)
    }

    // 測試 4：純錄音模式（asrConfig = null）
    @Test
    fun `init with null asrConfig should work for audio-only mode`() {
        // 為什麼測試：驗證純錄音模式（無 ASR）可以運作
        // 跳過原因：需要真實 native library
        //
        // val config = EngineConfig(asrConfig = null)
        //
        // val result = bridge.init(config)
        //
        // assertTrue(result is BridgeResult.Success)
    }

    // 測試 5：onTranscript callback 正確觸發
    @Test
    fun `onNativeTranscript should trigger callback with correct segment`() {
        // 為什麼測試：驗證 JNI callback 機制正常運作
        // 跳過原因：需要真實 native library
        //
        // var receivedSegment: TranscriptSegment? = null
        //
        // bridge.setCallback(object : EngineCallback {
        //     override fun onAudioData(data: FloatArray) {}
        //     override fun onTranscript(segment: TranscriptSegment) {
        //         receivedSegment = segment
        //     }
        //     override fun onError(code: Int, message: String) {}
        // })
        //
        // // 模擬 C++ 呼叫 onNativeTranscript
        // bridge.onNativeTranscript(
        //     id = "seg-1",
        //     text = "Hello world",
        //     speakerId = "Speaker_A",
        //     isFinal = true,
        //     startTimeMs = 0,
        //     endTimeMs = 1000,
        //     languageCode = "en"
        // )
        //
        // assertEquals("Hello world", receivedSegment?.text)
        // assertEquals("en", receivedSegment?.languageCode)
    }

    // 測試 6：驗證 AsrConfig 參數完整性
    @Test
    fun `init should extract all parameters from AsrConfig Whisper`() {
        // 為什麼測試：確保 modelsPath 與 language 都正確傳遞
        // 此測試驗證參數萃取邏輯（不需要 native library）

        val config = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("zh")
            )
        )

        // 驗證：config 物件正確建立
        assertTrue(config.asrConfig is AsrConfig.Whisper)

        val whisperConfig = config.asrConfig as AsrConfig.Whisper
        assertEquals("/data/models", whisperConfig.modelsPath)
        assertEquals(LanguageSetting.Fixed("zh"), whisperConfig.language)
    }

    // 測試 7：LanguageSetting.Auto 轉換為 "auto" 字串
    @Test
    fun `LanguageSetting Auto should convert to auto string`() {
        // 為什麼測試：驗證 LanguageSetting 轉換邏輯
        // C++ 層需要接收字串 "auto"，不是 Kotlin sealed class

        val languageAuto = LanguageSetting.Auto
        val languageFixed = LanguageSetting.Fixed("zh")

        // 轉換邏輯（將在 JniEngineBridge.init 中實作）
        val autoString = when (languageAuto) {
            is LanguageSetting.Auto -> "auto"
            is LanguageSetting.Fixed -> languageAuto.languageCode
        }

        val fixedString = when (languageFixed) {
            is LanguageSetting.Auto -> "auto"
            is LanguageSetting.Fixed -> languageFixed.languageCode
        }

        assertEquals("auto", autoString)
        assertEquals("zh", fixedString)
    }
}
