package com.edgemeeting.sdk.ui

import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.core.model.VadConfig
import com.edgemeeting.sdk.translation.TranscriptWithTranslation

data class MeetingScreenState(
    val selectedLanguage: LanguageSetting,
    val meetingState: MeetingState,
    val transcriptItems: List<TranscriptWithTranslation>,
    val modelReady: Boolean,
    val isDownloading: Boolean,
    val translateErrorResId: Int?,
    val vadConfig: VadConfig = VadConfig.Default,
    val isVadPanelExpanded: Boolean = false
)

data class MeetingScreenActions(
    val onLanguageChanged: (LanguageSetting) -> Unit,
    val onPrepare: () -> Unit,
    val onStart: () -> Unit,
    val onStop: () -> Unit,
    val onRelease: () -> Unit,
    val onVadConfigChanged: (VadConfig) -> Unit,
    val onToggleVadPanel: () -> Unit
)
