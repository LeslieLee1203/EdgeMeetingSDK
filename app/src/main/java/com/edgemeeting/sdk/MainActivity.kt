package com.edgemeeting.sdk

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.edgemeeting.core.MeetingSession
import com.edgemeeting.core.model.LanguageSetting
import com.edgemeeting.engine.ModelAssetManager
import com.edgemeeting.engine.RkMeetingSession
import com.edgemeeting.engine.bridge.JniEngineBridge
import com.edgemeeting.sdk.ui.MeetingScreenContent
import com.edgemeeting.sdk.ui.rememberMeetingScreenController
import com.edgemeeting.sdk.ui.theme.EdgeMeetingSDKTheme

class MainActivity : ComponentActivity() {

    // 1. 在這裡組裝依賴 (Dependency Injection Root)
    // 在真實 App 中這通常由 Hilt/Koin 負責，但 Walking Skeleton 階段直接 new 最快
    // Phase 4: 語言設定由 UI 控制，透過 createSession 建立
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
    val controller = rememberMeetingScreenController(createSession)
    MeetingScreenContent(
        state = controller.state,
        actions = controller.actions,
        modifier = modifier
    )
}
