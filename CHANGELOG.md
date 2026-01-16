# 更新日誌 (CHANGELOG)

## [2026-01-16]

### 新增
- 加入專案根目錄 `.gitignore` 以忽略通用建置與 IDE 產物。
- 建立專案憲章，明確規範程式碼品質、TDD、UI/UX 一致性與效能要求。
- 更新 Speckit 模板以對齊憲章（計畫、規格、任務、檢核清單）。

### 調整
- 重新整理 `.gitignore`，去除重複項目並補上分類註解。
- 更新任務清單，標記 T001 完成。
- 更新任務清單，標記 T002 完成。
- 新增 `TimeSource` 介面提供可控時間來源。
- 新增 `FakeTimeSource` 供測試控制時間。
- 新增 `TranscriptGenerator` 介面供可控字幕產生。
- 新增字幕輸出節奏的單元測試。
- 修正測試用 `FakeEngineBridge` 遺漏 `setCallback` 實作。
- 修正字幕節奏測試的 `assertTrue` 參數順序。
- 新增停止後 2 秒內不再輸出字幕的單元測試。
- 新增字幕欄位完整性與時間遞增的單元測試。
- 新增更新間隔 95% 不超過 1.5 秒的單元測試。
- 在 `RkMeetingSession` 新增可注入的 `timeSource`。
- 在 `RkMeetingSession` 新增可注入的 `transcriptGenerator`。
- 在 `RkMeetingSession` 新增每秒字幕輸出 loop。
- 在 `RkMeetingSession` 實作 `transcriptFlow` 緩衝策略（SharedFlow）。
- 在 `RkMeetingSession.start()` 啟動字幕輸出。
- 在 `RkMeetingSession.stop()` 停止字幕輸出。
- 在 `RkMeetingSession.release()` 清理字幕輸出。
- 讓 `RkMeetingSession` 可注入 Dispatcher 以支援測試虛擬時間。
- 修正更新間隔測試，結束時停止並釋放 session。
- 修正字幕節奏測試，結束時停止並釋放 session。
- 修正欄位完整性測試，結束時停止並釋放 session。

## [2026-01-09]

### 新增
- 為 `meeting-core` 模組新增 Kotlin Coroutines 測試支援。
- 更新 `libs.versions.toml`，新增 `kotlinx-coroutines-test` 函式庫定義。
- 建立專案多模組結構，包含 `meeting-core` 與 `meeting-engine` 模組。
- 在 `meeting-core` 中初始化基本的 Android Library 設定。
- 在 `meeting-engine` 中初始化基本的 Android Library 設定。
- 設定 `settings.gradle.kts` 以包含新建立的模組。
- 完善 `gradle/libs.versions.toml` 版本管理設定。
- 初始化專案基本的 Gradle 建置腳本。
