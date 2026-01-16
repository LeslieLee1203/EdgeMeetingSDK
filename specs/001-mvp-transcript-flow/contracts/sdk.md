# Contracts: SDK 介面（MVP）

## MeetingSession 行為

### Actions
- **prepare()**: 觸發準備流程，成功後進入 Ready
- **start()**: 進入 Listening 狀態並開始輸出字幕
- **stop()**: 結束字幕輸出並回到 Ready
- **release()**: 釋放資源並回到 Idle

### Streams
- **state**: 會議狀態流（Idle/Preparing/Ready/Listening/Error）
- **transcriptFlow**: 字幕段落流（每秒一段，Unknown speaker）
