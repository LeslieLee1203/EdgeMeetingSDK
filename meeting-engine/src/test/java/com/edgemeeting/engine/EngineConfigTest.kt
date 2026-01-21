package com.edgemeeting.engine

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.engine.bridge.EngineConfig
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * EngineConfig 單元測試
 *
 * 測試目標：
 * 1. 純錄音模式（asrConfig = null）
 * 2. 錄音 + Whisper ASR 模式
 * 3. 預設值處理
 */
class EngineConfigTest {

    @Test
    fun `EngineConfig with null asrConfig should create audio-only mode`() {
        // 建立純錄音配置（無 ASR）
        val config = EngineConfig(asrConfig = null)

        // 驗證：無 ASR 配置
        assertNull(config.asrConfig)
    }

    @Test
    fun `EngineConfig should accept Whisper ASR config`() {
        // 建立 Whisper ASR 配置
        val asrConfig = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Auto
        )
        val config = EngineConfig(asrConfig = asrConfig)

        // 驗證：ASR 配置正確
        assertNotNull(config.asrConfig)
        assert(config.asrConfig is AsrConfig.Whisper)
        assertEquals(asrConfig, config.asrConfig)
    }

    @Test
    fun `EngineConfig should accept Whisper with Fixed language`() {
        // 建立 Whisper 指定語言配置
        val asrConfig = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("zh")
        )
        val config = EngineConfig(asrConfig = asrConfig)

        // 驗證：語言設定正確
        assertNotNull(config.asrConfig)
        val whisperConfig = config.asrConfig as AsrConfig.Whisper
        assert(whisperConfig.language is LanguageSetting.Fixed)
        assertEquals("zh", (whisperConfig.language as LanguageSetting.Fixed).languageCode)
    }

    @Test
    fun `EngineConfig should use null as default`() {
        // 建立配置時使用預設值
        val config = EngineConfig()

        // 驗證：預設為 null（純錄音模式）
        assertNull(config.asrConfig)
    }

    @Test
    fun `EngineConfig should support copy with different asrConfig`() {
        // 建立初始配置
        val originalConfig = EngineConfig(asrConfig = null)

        // 複製並修改 ASR 配置
        val newAsrConfig = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Auto
        )
        val newConfig = originalConfig.copy(asrConfig = newAsrConfig)

        // 驗證：原配置不變，新配置正確
        assertNull(originalConfig.asrConfig)
        assertNotNull(newConfig.asrConfig)
        assertEquals(newAsrConfig, newConfig.asrConfig)
    }
}
