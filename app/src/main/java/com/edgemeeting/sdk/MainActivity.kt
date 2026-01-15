package com.edgemeeting.sdk

import android.Manifest
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.MeetingState
import com.edgemeeting.engine.RkMeetingSession
import com.edgemeeting.engine.bridge.JniEngineBridge
import com.edgemeeting.sdk.ui.theme.EdgeMeetingSDKTheme

class MainActivity : ComponentActivity() {

    // 1. 在這裡組裝依賴 (Dependency Injection Root)
    // 在真實 App 中這通常由 Hilt/Koin 負責，但 Walking Skeleton 階段直接 new 最快
    private val session: MeetingSession by lazy {
        val bridge = JniEngineBridge() // 載入 .so 檔
        RkMeetingSession(bridge)       // 注入到 Session
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        setContent {
            EdgeMeetingSDKTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    // 傳入 session 供 UI 觀察與操作
                    MeetingScreen(
                        session = session,
                        modifier = Modifier.padding(innerPadding)
                    )
                }
            }
        }
    }
}

@Composable
fun MeetingScreen(
    session: MeetingSession,
    modifier: Modifier = Modifier
) {
    // 2. 觀察狀態流 (StateFlow -> Compose State)
    // 當 Session 狀態改變時，這裡會自動 Recomposition
    val meetingState by session.state.collectAsState()

    // 用於發動非同步操作 (雖然 prepare 目前是同步的，但好習慣還是要有)
    val scope = rememberCoroutineScope()

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

        // 3. 狀態顯示區
        StateIndicator(state = meetingState)

        Spacer(modifier = Modifier.height(32.dp))

        // 4. 操作按鈕：Prepare
        // 點擊後會呼叫 C++ JNI，若成功，狀態應變為 Ready
        Button(
            onClick = {
                Log.d("App", "User clicked Prepare")
                // 注意：在真實專案應搬到 ViewModel，這裡為了驗證直接呼叫
                session.prepare()
            },
            enabled = meetingState is MeetingState.Idle || meetingState is MeetingState.Error
        ) {
            Text("1. Initialize Engine (Prepare)")
        }

        Spacer(modifier = Modifier.height(16.dp))

        // 5. 操作按鈕：Start (Mock)
        // 目前 C++ 只會印 Log，還沒實作真正錄音
        Button(
            onClick = {
                Log.d("App", "User clicked Start")
                session.start()
            },
            // 只有 Ready 狀態才能開始
            enabled = meetingState is MeetingState.Ready
        ) {
            Text("2. Start Recording")
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