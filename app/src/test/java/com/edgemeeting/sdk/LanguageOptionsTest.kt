package com.edgemeeting.sdk

import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.sdk.ui.supportedLanguageOptions
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOptionsTest {

    @Test
    fun `supported language options should match native support`() {
        val settings = supportedLanguageOptions()

        val expected = listOf(
            LanguageSetting.Fixed("en"),
            LanguageSetting.Fixed("zh"),
            LanguageSetting.Fixed("ja"),
            LanguageSetting.Fixed("ko")
        )

        assertEquals(expected, settings)
    }
}
