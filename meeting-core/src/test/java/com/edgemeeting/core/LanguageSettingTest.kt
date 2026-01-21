package com.edgemeeting.core

import com.edgemeeting.core.model.LanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test

/**
 * LanguageSetting 單元測試
 *
 * 測試目標：
 * 1. Auto 模式建立
 * 2. Fixed 模式建立與語言代碼驗證
 * 3. sealed class 型別安全
 */
class LanguageSettingTest {

    @Test
    fun `Auto mode should create successfully`() {
        // 建立 Auto 模式
        val setting = LanguageSetting.Auto

        // 驗證型別
        assertNotNull(setting)
        assert(setting is LanguageSetting.Auto)
    }

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
    fun `Auto and Fixed should be different types`() {
        // 建立兩種模式
        val auto = LanguageSetting.Auto
        val fixed = LanguageSetting.Fixed("zh")

        // 驗證型別不同
        assert(auto is LanguageSetting.Auto)
        assert(fixed is LanguageSetting.Fixed)
        assert(auto !is LanguageSetting.Fixed)
        assert(fixed !is LanguageSetting.Auto)
    }
}
