package com.edgemeeting.sdk

import com.edgemeeting.core.model.LanguageSetting
import org.junit.Assert.assertEquals
import org.junit.Test

class LanguageOptionsTest {

    @Test
    fun `supported language options should match native support`() {
        val options = supportedLanguageOptions()
        val settings = options.map { it.first }

        val expected = listOf(
            LanguageSetting.Fixed("en"),
            LanguageSetting.Fixed("zh")
        )

        assertEquals(expected, settings)
    }
}
