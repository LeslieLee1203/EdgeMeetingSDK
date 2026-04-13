package com.edgemeeting.sdk.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.sdk.R

@Composable
fun MeetingScreenContent(
    state: MeetingScreenState,
    actions: MeetingScreenActions,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        MeetingHeader()
        Spacer(modifier = Modifier.height(32.dp))

        LanguageSelector(
            selectedLanguage = state.selectedLanguage,
            onLanguageChanged = actions.onLanguageChanged,
            enabled = state.meetingState is MeetingState.Idle || state.meetingState is MeetingState.Error
        )

        Spacer(modifier = Modifier.height(24.dp))
        StateIndicator(state = state.meetingState)
        TranslationStatus(
            isDownloading = state.isDownloading,
            modelReady = state.modelReady,
            errorResId = state.translateErrorResId
        )

        Spacer(modifier = Modifier.height(32.dp))
        TranscriptSection(items = state.transcriptItems)

        ControlButtons(
            meetingState = state.meetingState,
            onPrepare = actions.onPrepare,
            onStart = actions.onStart,
            onStop = actions.onStop,
            onRelease = actions.onRelease
        )

        Spacer(modifier = Modifier.height(8.dp))
        VadDebugToggle(
            isExpanded = state.isVadPanelExpanded,
            onToggle = actions.onToggleVadPanel
        )
        AnimatedVisibility(visible = state.isVadPanelExpanded) {
            VadConfigPanel(
                config = state.vadConfig,
                onConfigChanged = actions.onVadConfigChanged
            )
        }
    }
}

@Composable
private fun MeetingHeader() {
    Text(
        text = stringResource(id = R.string.meeting_screen_title),
        style = MaterialTheme.typography.headlineMedium,
        textAlign = TextAlign.Center
    )
}

@Composable
private fun TranslationStatus(
    isDownloading: Boolean,
    modelReady: Boolean,
    errorResId: Int?
) {
    if (isDownloading) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.translation_model_downloading),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    } else if (!modelReady) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = R.string.translation_model_pending),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Gray
        )
    }
    if (errorResId != null) {
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = stringResource(id = errorResId),
            style = MaterialTheme.typography.bodySmall,
            color = Color.Red
        )
    }
}

@Composable
private fun TranscriptSection(items: List<com.edgemeeting.sdk.translation.TranscriptWithTranslation>) {
    val recentItems = items.takeLast(4)
    if (recentItems.isEmpty()) {
        return
    }

    Text(
        text = stringResource(id = R.string.transcripts_title),
        style = MaterialTheme.typography.titleMedium
    )
    Spacer(modifier = Modifier.height(8.dp))
    recentItems.forEach { item ->
        TranscriptItemView(item = item)
    }
    Spacer(modifier = Modifier.height(24.dp))
}

@Composable
private fun ControlButtons(
    meetingState: MeetingState,
    onPrepare: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(0.95f),
        horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {
        Button(
            onClick = onPrepare,
            enabled = meetingState is MeetingState.Idle || meetingState is MeetingState.Error,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(id = R.string.button_prepare))
        }

        Button(
            onClick = onStart,
            enabled = meetingState is MeetingState.Ready,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(id = R.string.button_start))
        }

        Button(
            onClick = onStop,
            enabled = meetingState is MeetingState.Listening,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(id = R.string.button_stop))
        }

        Button(
            onClick = onRelease,
            enabled = meetingState is MeetingState.Ready || meetingState is MeetingState.Error,
            modifier = Modifier.weight(1f)
        ) {
            Text(stringResource(id = R.string.button_release))
        }
    }
}
