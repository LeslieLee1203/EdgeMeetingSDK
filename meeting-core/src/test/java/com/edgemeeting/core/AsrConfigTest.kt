package com.edgemeeting.core

import com.edgemeeting.core.model.AsrConfig
import com.edgemeeting.core.model.LanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * AsrConfig 單元測試
 *
 * 測試目標：
 * 1. Whisper 配置建立與參數驗證
 * 2. sealed class 型別安全
 * 3. 未來擴充性（預留 Zipformer 等其他 ASR）
 */
class AsrConfigTest {

    fun `Whisper config should create with Fixed language`() {
        // 建立 Whisper 配置，指定語言
        val config = AsrConfig.Whisper(
            modelsPath = "/data/data/com.edgemeeting/files/models",
            language = LanguageSetting.Fixed("zh")
        )

        // 驗證參數
        assertEquals("/data/data/com.edgemeeting/files/models", config.modelsPath)
        assert(config.language is LanguageSetting.Fixed)
        assertEquals("zh", (config.language as LanguageSetting.Fixed).languageCode)
    }

    @Test
    fun `Whisper config should accept different models path`() {
        // 測試不同的模型路徑
        val config1 = AsrConfig.Whisper(
            modelsPath = "/sdcard/models",
            language = LanguageSetting.Fixed("en")
        )
        val config2 = AsrConfig.Whisper(
            modelsPath = "/storage/emulated/0/models",
            language = LanguageSetting.Fixed("en")
        )

        // 驗證路徑不同
        assertEquals("/sdcard/models", config1.modelsPath)
        assertEquals("/storage/emulated/0/models", config2.modelsPath)
    }

    @Test
    fun `Whisper config should be sealed class member`() {
        // 建立配置
        val config: AsrConfig = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("en")
        )

        // 驗證型別安全（可用 when 表達式處理）
        assertNotNull(config)
        when (config) {
            is AsrConfig.Whisper -> {
                // 型別安全：可存取 Whisper 特定屬性
                assertNotNull(config.modelsPath)
                assertNotNull(config.language)
            }
        }
    }

    @Test
    fun `Whisper config should support all language settings`() {
        // 測試所有語言設定模式
        val configZh = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("zh")
        )
        val configEn = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("en")
        )

        // 驗證所有配置都有效
        assertNotNull(configZh)
        assertNotNull(configEn)
    }

    @Test
    fun `Whisper config should support Japanese language`() {
        // 建立日文配置
        val config = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("ja")
        )

        // 驗證語言代碼
        val lang = (config.language as LanguageSetting.Fixed).languageCode
        assertEquals("ja", lang)
    }

    @Test
    fun `Whisper config should support Korean language`() {
        // 建立韓文配置
        val config = AsrConfig.Whisper(
            modelsPath = "/data/models",
            language = LanguageSetting.Fixed("ko")
        )

        // 驗證語言代碼
        val lang = (config.language as LanguageSetting.Fixed).languageCode
        assertEquals("ko", lang)
    }
}
