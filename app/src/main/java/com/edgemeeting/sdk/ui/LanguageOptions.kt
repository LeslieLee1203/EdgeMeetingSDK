package com.edgemeeting.sdk.ui

import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.sdk.R

fun supportedLanguageOptions(): List<LanguageSetting> {
    return listOf(
        LanguageSetting.Fixed("en"),
        LanguageSetting.Fixed("zh"),
        LanguageSetting.Fixed("ja"),
        LanguageSetting.Fixed("ko")
    )
}

fun languageLabelResId(setting: LanguageSetting): Int {
    val code = (setting as? LanguageSetting.Fixed)?.languageCode ?: "en"
    return when (code) {
        "zh" -> R.string.language_chinese
        "ja" -> R.string.language_japanese
        "ko" -> R.string.language_korean
        else -> R.string.language_english
    }
}
