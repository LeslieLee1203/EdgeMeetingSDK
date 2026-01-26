package com.edgemeeting.sdk

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import com.edgemeeting.sdk.util.TimeFormatter
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.ModelAssetManager
import com.edgemeeting.engine.RkMeetingSession
import com.edgemeeting.engine.bridge.JniEngineBridge
import com.edgemeeting.sdk.ui.theme.EdgeMeetingSDKTheme

class MainActivity : ComponentActivity() {

    // 1. 在這裡組裝依賴 (Dependency Injection Root)
    // 在真實 App 中這通常由 Hilt/Koin 負責，但 Walking Skeleton 階段直接 new 最快
    // Phase 4: 語言設定由 UI 控制，透過 createSession 建立
    private var session: MeetingSession? = null

    private fun createSession(languageSetting: LanguageSetting): MeetingSession {
        val bridge = JniEngineBridge() // 載入 .so 檔
        return RkMeetingSession(
            bridge = bridge,
            languageSetting = languageSetting,
            modelProvider = { ModelAssetManager.ensureModels(this) }
        )       // 注入到 Session
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EdgeMeetingSDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // Phase 4: 傳入 createSession 函數供 UI 建立 session
                    MeetingScreen(
                        createSession = ::createSession,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingScreen(
    createSession: (LanguageSetting) -> MeetingSession,
    modifier: Modifier = Modifier
) {
    // Phase 4: 語言設定狀態（預設 Auto）
    var selectedLanguage by remember { mutableStateOf<LanguageSetting>(LanguageSetting.Auto) }
    var session by remember { mutableStateOf<MeetingSession?>(null) }

    // 當語言設定改變時，重新建立 session
    LaunchedEffect(selectedLanguage) {
        session = createSession(selectedLanguage)
    }

    // 2. 觀察狀態流 (StateFlow -> Compose State)
    // 當 Session 狀態改變時，這裡會自動 Recomposition
    val meetingState by (session?.state ?: return).collectAsState()

    // 用於發動非同步操作 (雖然 prepare 目前是同步的，但好習慣還是要有)
    val scope = rememberCoroutineScope()
    val transcriptItems = remember { mutableStateListOf<com.edgemeeting.core.model.TranscriptSegment>() }

    // --- 權限處理邏輯 ---
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            Log.d("App", "Permission Granted")
        } else {
            Log.e("App", "Permission Denied")
        }
    }

    // 當 UI 顯示時，自動檢查並請求權限
    LaunchedEffect(Unit) {
        permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
    }

    // 收集字幕流，將最新段落追加到列表
    LaunchedEffect(session) {
        try {
            session?.transcriptFlow?.collect { segments ->
                segments.forEach { transcriptItems.add(it) }
            }
        } catch (error: Throwable) {
            Log.e("App", "Collect transcript failed", error)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 標題
        Text(
            text = "EdgeMeeting SDK\nIntegration Test",
            style = MaterialTheme.typography.headlineMedium,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center
        )

        Spacer(modifier = Modifier.height(32.dp))

        // 3. 語言設定選擇器（Phase 4）
        LanguageSelector(
            selectedLanguage = selectedLanguage,
            onLanguageChanged = { newLanguage ->
                selectedLanguage = newLanguage
                // 清空字幕列表，因為將重新建立 session
                transcriptItems.clear()
            },
            enabled = meetingState is MeetingState.Idle || meetingState is MeetingState.Error
        )

        Spacer(modifier = Modifier.height(24.dp))

        // 4. 狀態顯示區
        StateIndicator(state = meetingState)

        Spacer(modifier = Modifier.height(32.dp))

        // 3.1 字幕顯示（取最後幾段避免擠滿畫面）
        val recentSegments = transcriptItems.takeLast(5)
        if (recentSegments.isNotEmpty()) {
            Text(
                text = "Transcripts:",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentSegments.forEach { segment ->
                val startTime = TimeFormatter.formatMillisToTime(segment.startTimeMs)
                val endTime = TimeFormatter.formatMillisToTime(segment.endTimeMs)
                Text(
                    text = "[$startTime-$endTime] ${segment.text}",
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            Spacer(modifier = Modifier.height(24.dp))
        }

        // 5. 操作按鈕區（單行橫向排列）
        Row(
            modifier = Modifier.fillMaxWidth(0.95f),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Prepare
            Button(
                onClick = {
                    Log.d("App", "User clicked Prepare")
                    session?.prepare()
                },
                enabled = meetingState is MeetingState.Idle || meetingState is MeetingState.Error,
                modifier = Modifier.weight(1f)
            ) {
                Text("Prepare")
            }

            // Start
            Button(
                onClick = {
                    Log.d("App", "User clicked Start")
                    session?.start()
                },
                enabled = meetingState is MeetingState.Ready,
                modifier = Modifier.weight(1f)
            ) {
                Text("Start")
            }

            // Stop
            Button(
                onClick = {
                    Log.d("App", "User clicked Stop")
                    session?.stop()
                },
                enabled = meetingState is MeetingState.Listening,
                modifier = Modifier.weight(1f)
            ) {
                Text("Stop")
            }

            // Release
            Button(
                onClick = {
                    Log.d("App", "User clicked Release")
                    session?.release()
                    transcriptItems.clear()
                },
                enabled = meetingState is MeetingState.Ready || meetingState is MeetingState.Error,
                modifier = Modifier.weight(1f)
            ) {
                Text("Release")
            }
        }
    }
}

@Composable
fun StateIndicator(state: MeetingState) {
    val (color, text) = when (state) {
        is MeetingState.Idle -> Color.Gray to "Idle (未初始化)"
        is MeetingState.Preparing -> Color.Blue to "Preparing (載入中...)"
        is MeetingState.Ready -> Color(0xFF4CAF50) to "Ready (就緒)" // Green
        is MeetingState.Listening -> Color.Red to "Listening (錄音中)"
        is MeetingState.Error -> Color.Red to "Error: ${state.message}"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Current State:", style = MaterialTheme.typography.labelLarge)
        Text(
            text = text,
            style = MaterialTheme.typography.titleLarge,
            color = color,
            modifier = Modifier.padding(8.dp)
        )
    }
}

/**
 * 語言選擇器 (Phase 4)
 *
 * 為什麼需要：允許使用者選擇 Auto（自動偵測）或指定語言（如中文、英文）
 * 設計考量：
 * - 預設為 Auto，符合多數使用情境
 * - 只在 Idle 或 Error 狀態可切換（避免執行中切換導致狀態不一致）
 * - 使用 DropdownMenu 提供清晰的選項
 */
@Composable
fun LanguageSelector(
    selectedLanguage: LanguageSetting,
    onLanguageChanged: (LanguageSetting) -> Unit,
    enabled: Boolean,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }

    val languageOptions = listOf(
        LanguageSetting.Auto to "Auto (自動偵測)",
        LanguageSetting.Fixed("zh") to "中文 (Chinese)",
        LanguageSetting.Fixed("en") to "English (英文)",
        LanguageSetting.Fixed("ja") to "日本語 (Japanese)",
        LanguageSetting.Fixed("ko") to "한국어 (Korean)"
    )

    val currentLabel = languageOptions.find { it.first == selectedLanguage }?.second ?: "Auto (自動偵測)"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "語言設定 (Language):",
            style = MaterialTheme.typography.labelLarge
        )
        Spacer(modifier = Modifier.height(8.dp))

        OutlinedButton(
            onClick = { if (enabled) expanded = true },
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(0.8f)
        ) {
            Text(currentLabel)
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            languageOptions.forEach { (setting, label) ->
                DropdownMenuItem(
                    text = { Text(label) },
                    onClick = {
                        onLanguageChanged(setting)
                        expanded = false
                    }
                )
            }
        }
    }
}