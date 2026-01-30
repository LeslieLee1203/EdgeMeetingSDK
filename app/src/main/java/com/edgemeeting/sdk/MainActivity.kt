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
    // Phase 4: 語言設定狀態（預設 English）
    var selectedLanguage by remember { mutableStateOf<LanguageSetting>(LanguageSetting.Fixed("en")) }
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

    // 收集字幕流，智能合併中間結果與最終結果（方案 B - 修正版）
    LaunchedEffect(session) {
        try {
            session?.transcriptFlow?.collect { segments ->
                segments.forEach { newSegment ->
                    // **關鍵修正**：無論是 FINAL 還是 INTERMEDIATE，都先刪除被覆蓋的舊結果
                    val removedCount = transcriptItems.removeAll { existingSegment ->
                        shouldReplaceSegment(existingSegment, newSegment)
                    }

                    // 然後添加新結果
                    transcriptItems.add(newSegment)

                    val typeStr = if (newSegment.isFinal) "FINAL" else "INTERMEDIATE"
                    Log.d("App", "Added $typeStr segment (removed $removedCount old): ${newSegment.text.take(30)}...")
                }
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
        // 方案 B：區分中間結果（淺色）和最終結果（正常色）
        val recentSegments = transcriptItems.takeLast(5)
        if (recentSegments.isNotEmpty()) {
            Text(
                text = "Transcripts",
                style = MaterialTheme.typography.titleMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            recentSegments.forEach { segment ->
                val startTime = TimeFormatter.formatMillisToTime(segment.startTimeMs)
                val endTime = TimeFormatter.formatMillisToTime(segment.endTimeMs)

                // 方案 B：根據 isFinal 標誌調整顯示樣式
                val textColor = if (segment.isFinal) {
                    Color.Unspecified  // 正常顏色（黑色/白色，根據主題）
                } else {
                    Color.Gray  // 淺灰色表示臨時結果
                }

                val textStyle = if (segment.isFinal) {
                    MaterialTheme.typography.bodyMedium  // 正常字體
                } else {
                    MaterialTheme.typography.bodyMedium.copy(
                        fontStyle = androidx.compose.ui.text.font.FontStyle.Italic  // 斜體表示臨時
                    )
                }

                Text(
                    text = "[$startTime-$endTime] ${segment.text}",
                    style = textStyle,
                    color = textColor
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
        is MeetingState.Idle -> Color.Gray to "Idle"
        is MeetingState.Preparing -> Color.Blue to "Preparing"
        is MeetingState.Ready -> Color(0xFF4CAF50) to "Ready" // Green
        is MeetingState.Listening -> Color.Red to "Listening"
        is MeetingState.Error -> Color.Red to "Error: ${state.message}"
    }

    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = "Current State", style = MaterialTheme.typography.labelLarge)
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
 * 為什麼需要：允許使用者選擇指定語言（如英文、中文）
 * 設計考量：
 * - 預設為 English
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

    val languageOptions = supportedLanguageOptions().ifEmpty {
        listOf(LanguageSetting.Fixed("en") to "English")
    }

    val currentLabel = languageOptions.find { it.first == selectedLanguage }?.second ?: "English"

    Column(
        modifier = modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "Language:",
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

/**
 * 目前 Native 層支援的語言選項
 *
 * 注意：RKNN Whisper 不支援 auto，支援 en/zh/ja/ko
 */
fun supportedLanguageOptions(): List<Pair<LanguageSetting, String>> {
    return listOf(
        LanguageSetting.Fixed("en") to "English",
        LanguageSetting.Fixed("zh") to "中文",
        LanguageSetting.Fixed("ja") to "日本語",
        LanguageSetting.Fixed("ko") to "한국어"
    )
}

/**
 * 判斷舊段落是否應該被新段落取代（方案 B - 修正版）
 *
 * 覆蓋規則（按優先級）：
 *
 * 規則 1：FINAL 永遠不被覆蓋
 * - 如果舊段落是 FINAL，不刪除
 *
 * 規則 2：相同起點的延伸覆蓋
 * - 如果兩個段落的 startTimeMs 接近（差距 < 1000ms）
 * - 且新段落的 endTimeMs >= 舊段落的 endTimeMs
 * - 則新段落是舊段落的延伸，應該覆蓋舊段落
 * - 範例：[0s-5s] 被 [0s-10s] 覆蓋
 *
 * 規則 3：時間範圍重疊
 * - 如果新段落是 FINAL，且時間範圍與舊段落重疊
 * - 則 FINAL 覆蓋所有重疊的 INTERMEDIATE
 *
 * 實際案例：
 * - INTERMEDIATE [0s-5s]: "進來這個一面之後"
 * - INTERMEDIATE [0s-10s]: "進來這個一面之後,你會看到"  ← 應該覆蓋 [0s-5s]
 * - INTERMEDIATE [0s-15s]: "進來這個一面之後,你會看到他在啟動中"  ← 應該覆蓋 [0s-10s]
 * - FINAL       [0s-20s]: "進來這個一面之後,你會看到他在啟動中的狀態..."  ← 應該覆蓋 [0s-15s]
 *
 * @param oldSegment 已存在的段落
 * @param newSegment 新收到的段落
 * @return true 如果舊段落應該被刪除
 */
fun shouldReplaceSegment(
    oldSegment: com.edgemeeting.core.model.TranscriptSegment,
    newSegment: com.edgemeeting.core.model.TranscriptSegment
): Boolean {
    // 規則 1：FINAL 結果永遠不被覆蓋
    if (oldSegment.isFinal) {
        return false
    }

    // 規則 2：相同起點的延伸覆蓋（最常見情況）
    // 允許 1000ms 的誤差（因為音訊分段可能有微小偏移）
    val hasSimilarStart = kotlin.math.abs(oldSegment.startTimeMs - newSegment.startTimeMs) < 1000
    val isExtension = newSegment.endTimeMs >= oldSegment.endTimeMs

    if (hasSimilarStart && isExtension) {
        return true  // 新段落是舊段落的延伸，覆蓋它
    }

    // 規則 3：FINAL 覆蓋所有時間重疊的 INTERMEDIATE
    if (newSegment.isFinal) {
        val hasOverlap =
            oldSegment.startTimeMs < newSegment.endTimeMs &&
            newSegment.startTimeMs < oldSegment.endTimeMs

        return hasOverlap
    }

    return false
}