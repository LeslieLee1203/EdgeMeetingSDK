package com.edgemeeting.core

import com.edgemeeting.core.model.LanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * LanguageSetting 單元測試
 *
 * 測試目標：
 * 1. Fixed 模式建立與語言代碼驗證
 * 2. sealed class 型別安全
 */
class LanguageSettingTest {

    @Test
    fun `Fixed mode should accept valid language code`() {
        // 建立 Fixed 模式，指定中文
        val setting = LanguageSetting.Fixed("zh")

        // 驗證語言代碼
        assertEquals("zh", setting.languageCode)
        assert(setting is LanguageSetting.Fixed)
    }

    @Test
    fun `Fixed mode should accept English language code`() {
        // 建立 Fixed 模式，指定英文
        val setting = LanguageSetting.Fixed("en")

        // 驗證語言代碼
        assertEquals("en", setting.languageCode)
    }

    @Test
    fun `Fixed mode should accept Japanese language code`() {
        // 建立 Fixed 模式，指定日文
        val setting = LanguageSetting.Fixed("ja")

        // 驗證語言代碼
        assertEquals("ja", setting.languageCode)
    }

    @Test
    fun `Fixed mode should accept Korean language code`() {
        // 建立 Fixed 模式，指定韓文
        val setting = LanguageSetting.Fixed("ko")

        // 驗證語言代碼
        assertEquals("ko", setting.languageCode)
    }

    @Test
    fun `Fixed mode should be a LanguageSetting`() {
        val fixed = LanguageSetting.Fixed("zh")
        assertNotNull(fixed)
        assert(fixed is LanguageSetting.Fixed)
    }
}
