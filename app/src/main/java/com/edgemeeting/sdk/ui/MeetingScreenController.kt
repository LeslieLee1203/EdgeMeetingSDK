package com.edgemeeting.sdk.ui

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.sdk.R
import com.edgemeeting.sdk.translation.MlKitTranslator
import com.edgemeeting.sdk.translation.TranscriptWithTranslation
import com.edgemeeting.sdk.util.shouldReplaceSegment
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.sync.withPermit

private const val TRANSLATION_PARALLELISM = 1

data class MeetingScreenController(
    val state: MeetingScreenState,
    val actions: MeetingScreenActions
)

@Composable
fun rememberMeetingScreenController(
    createSession: (LanguageSetting) -> MeetingSession
): MeetingScreenController {
    val scope = rememberCoroutineScope()
    var selectedLanguage by remember { mutableStateOf<LanguageSetting>(LanguageSetting.Fixed("en")) }
    val session = remember(selectedLanguage) { createSession(selectedLanguage) }
    val meetingState by session.state.collectAsState()

    val translationState = rememberTranslationState()
    val transcriptState = rememberTranscriptState()

    RequestAudioPermission(rememberPermissionLauncher())
    AutoDownloadTranslationModel(translationState.translator, translationState.setError)
    CollectTranscripts(
        session = session,
        translator = translationState.translator,
        transcriptState = transcriptState,
        onTranslationError = { translationState.setError(R.string.translation_failed) },
        onTranslationRecovered = translationState.clearError
    )

    val actions = rememberActions(
        translator = translationState.translator,
        scope = scope,
        onPrepareBlocked = { translationState.setError(it) },
        onPrepareAllowed = { session.prepare() },
        onStart = { session.start() },
        onStop = { session.stop() },
        onRelease = {
            session.release()
            transcriptState.items.clear()
        },
        onLanguageChanged = { newLanguage ->
            selectedLanguage = newLanguage
            transcriptState.items.clear()
        }
    )

    return MeetingScreenController(
        state = MeetingScreenState(
            selectedLanguage = selectedLanguage,
            meetingState = meetingState,
            transcriptItems = transcriptState.items,
            modelReady = translationState.modelReady,
            isDownloading = translationState.isDownloading,
            translateErrorResId = translationState.translateErrorResId
        ),
        actions = actions
    )
}

private data class TranslationState(
    val translator: MlKitTranslator,
    val modelReady: Boolean,
    val isDownloading: Boolean,
    val translateErrorResId: Int?,
    val setError: (Int?) -> Unit,
    val clearError: () -> Unit
)

@Composable
private fun rememberTranslationState(): TranslationState {
    val translator = remember { MlKitTranslator() }
    val modelReady by translator.modelReady.collectAsState()
    val isDownloading by translator.isDownloading.collectAsState()
    var translateErrorResId by remember { mutableStateOf<Int?>(null) }

    DisposableEffect(Unit) {
        onDispose { translator.close() }
    }

    return TranslationState(
        translator = translator,
        modelReady = modelReady,
        isDownloading = isDownloading,
        translateErrorResId = translateErrorResId,
        setError = { translateErrorResId = it },
        clearError = { translateErrorResId = null }
    )
}

private data class TranscriptState(
    val items: androidx.compose.runtime.snapshots.SnapshotStateList<TranscriptWithTranslation>,
    val mutex: Mutex,
    val translationSemaphore: Semaphore
)

@Composable
private fun rememberTranscriptState(): TranscriptState {
    return TranscriptState(
        items = remember { mutableStateListOf() },
        mutex = remember { Mutex() },
        translationSemaphore = remember { Semaphore(TRANSLATION_PARALLELISM) }
    )
}

@Composable
private fun rememberPermissionLauncher() =
    rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }

@Composable
private fun RequestAudioPermission(permissionLauncher: androidx.activity.result.ActivityResultLauncher<String>) {
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }
}

@Composable
private fun AutoDownloadTranslationModel(
    translator: MlKitTranslator,
    onError: (Int) -> Unit
) {
    LaunchedEffect(Unit) {
        val ready = translator.downloadModelIfNeeded(requireWifi = false)
        if (!ready) {
            onError(R.string.translation_model_download_failed)
        }
    }
}

@Composable
private fun CollectTranscripts(
    session: MeetingSession,
    translator: MlKitTranslator,
    transcriptState: TranscriptState,
    onTranslationError: (Int) -> Unit,
    onTranslationRecovered: () -> Unit
) {
    LaunchedEffect(session) {
        session.transcriptFlow.collect { segments ->
            segments.forEach { newSegment ->
                transcriptState.mutex.withLock {
                    transcriptState.items.removeAll { existingItem ->
                        shouldReplaceSegment(existingItem.original, newSegment)
                    }
                }

                val newItem = TranscriptWithTranslation(
                    original = newSegment,
                    translatedText = null,
                    isTranslating = true
                )
                transcriptState.mutex.withLock { transcriptState.items.add(newItem) }

                launch {
                    val translated = transcriptState.translationSemaphore.withPermit {
                        translator.translate(newSegment.text)
                    }
                    if (translated == null) {
                        onTranslationError(R.string.translation_failed)
                    } else {
                        onTranslationRecovered()
                    }
                    transcriptState.mutex.withLock {
                        val index = transcriptState.items.indexOfFirst { it.original.id == newSegment.id }
                        if (index >= 0) {
                            transcriptState.items[index] = transcriptState.items[index].copy(
                                translatedText = translated,
                                isTranslating = false
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun rememberActions(
    translator: MlKitTranslator,
    scope: kotlinx.coroutines.CoroutineScope,
    onPrepareBlocked: (Int) -> Unit,
    onPrepareAllowed: () -> Unit,
    onStart: () -> Unit,
    onStop: () -> Unit,
    onRelease: () -> Unit,
    onLanguageChanged: (LanguageSetting) -> Unit
): MeetingScreenActions {
    return MeetingScreenActions(
        onLanguageChanged = onLanguageChanged,
        onPrepare = {
            scope.launch {
                val ready = if (translator.isModelReady()) {
                    true
                } else {
                    translator.downloadModelIfNeeded(requireWifi = false)
                }
                if (ready) {
                    onPrepareAllowed()
                } else {
                    onPrepareBlocked(R.string.translation_model_download_failed)
                }
            }
        },
        onStart = onStart,
        onStop = onStop,
        onRelease = onRelease
    )
}
