package com.edgemeeting.sdk.ui

import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.sdk.translation.TranscriptWithTranslation

data class MeetingScreenState(
    val selectedLanguage: LanguageSetting,
    val meetingState: MeetingState,
    val transcriptItems: List<TranscriptWithTranslation>,
    val modelReady: Boolean,
    val isDownloading: Boolean,
    val translateErrorResId: Int?
)

data class MeetingScreenActions(
    val onLanguageChanged: (LanguageSetting) -> Unit,
    val onPrepare: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRelease: () -> Unit
)
