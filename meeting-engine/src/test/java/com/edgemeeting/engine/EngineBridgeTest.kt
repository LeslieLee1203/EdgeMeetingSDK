package com.edgemeeting.engine

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.TranscriptSegment
import com.edgemeeting.engine.bridge.BridgeResult
import com.edgemeeting.engine.bridge.EngineBridge
import com.edgemeeting.engine.bridge.EngineCallback
import com.edgemeeting.engine.bridge.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * EngineBridge 單元測試
 *
 * 測試目標：
 * 1. init(EngineConfig) 新簽章
 * 2. 純錄音模式（asrConfig = null）
 * 3. 錄音 + ASR 模式
 */
class EngineBridgeTest {

    @Test
    fun `EngineBridge should accept EngineConfig with null asrConfig`() {
        // 建立純錄音配置
        val config = EngineConfig(asrConfig = null)

        // 建立 Fake Bridge
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult {
                return BridgeResult.Success
            }

            override fun setCallback(callback: EngineCallback) {}
            override fun startRecording() {}
            override fun stopRecording() {}
            override fun release() {}
        }

        // 呼叫 init
        val result = bridge.init(config)

        // 驗證：初始化成功
        assert(result is BridgeResult.Success)
    }

    @Test
    fun `EngineBridge should accept EngineConfig with Whisper asrConfig`() {
        // 建立 Whisper ASR 配置
        val asrConfig = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Auto
        )
        val config = EngineConfig(asrConfig = asrConfig)

        // 記錄傳入的 config
        var capturedConfig: EngineConfig? = null

        // 建立 Fake Bridge
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult {
                capturedConfig = config
                return BridgeResult.Success
            }

            override fun setCallback(callback: EngineCallback) {}
            override fun startRecording() {}
            override fun stopRecording() {}
            override fun release() {}
        }

        // 呼叫 init
        val result = bridge.init(config)

        // 驗證：配置正確傳遞
        assert(result is BridgeResult.Success)
        assertNotNull(capturedConfig)
        assertEquals(asrConfig, capturedConfig?.asrConfig)
    }

    @Test
    fun `EngineBridge init should return Failure on error`() {
        // 建立配置
        val config = EngineConfig(asrConfig = null)

        // 建立會失敗的 Fake Bridge
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult {
                return BridgeResult.Failure(1001, "Model not found")
            }

            override fun setCallback(callback: EngineCallback) {}
            override fun startRecording() {}
            override fun stopRecording() {}
            override fun release() {}
        }

        // 呼叫 init
        val result = bridge.init(config)

        // 驗證：回傳錯誤
        assert(result is BridgeResult.Failure)
        val failure = result as BridgeResult.Failure
        assertEquals(1001, failure.code)
        assertEquals("Model not found", failure.message)
    }

    @Test
    fun `EngineBridge should support different AsrConfig types`() {
        // 建立多種配置
        val configAudioOnly = EngineConfig(asrConfig = null)
        val configWhisperAuto = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Auto
            )
        )
        val configWhisperFixed = EngineConfig(
            asrConfig = AsrConfig.Whisper(
                modelsPath = "/data/models",
                language = LanguageSetting.Fixed("zh")
            )
        )

        // 建立 Fake Bridge
        val bridge = object : EngineBridge {
            override fun init(config: EngineConfig): BridgeResult {
                return BridgeResult.Success
            }

            override fun setCallback(callback: EngineCallback) {}
            override fun startRecording() {}
            override fun stopRecording() {}
            override fun release() {}
        }

        // 驗證：所有配置都能成功初始化
        assert(bridge.init(configAudioOnly) is BridgeResult.Success)
        assert(bridge.init(configWhisperAuto) is BridgeResult.Success)
        assert(bridge.init(configWhisperFixed) is BridgeResult.Success)
    }
}
