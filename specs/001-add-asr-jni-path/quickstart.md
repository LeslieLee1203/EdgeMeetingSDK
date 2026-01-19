# Phase 1 Quickstart

## 目標
在既有 Demo App 中，以真實語音輸入產生字幕流（靜音不輸出）。

## 基本流程
1. 準備資源（驗證可讀）
2. 啟動錄音與字幕流
3. 停止並釋放資源

## 範例（Kotlin）
```kotlin
val session = RkMeetingSession(
    engineBridge = jniEngineBridge,
    transcriptGenerator = transcriptGenerator,
    timeSource = timeSource
)

try {
    session.prepare()
    session.start()
} catch (e: Exception) {
    // 錯誤處理：顯示可理解訊息並允許重試
    showError(e.message ?: "準備失敗")
}

// 收集字幕流（靜音不輸出）
launch {
    session.transcriptFlow.collect { segments ->
        if (segments.isNotEmpty()) {
            render(segments)
        }
    }
}

// 停止
try {
    session.stop()
} catch (e: Exception) {
    showError(e.message ?: "停止失敗")
}
```
