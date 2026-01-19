# 更新日誌 (CHANGELOG)

## [2026-01-19]

### 新增
- 建立 Phase 1 真實字幕流（ASR）規格草案與需求檢核清單（`specs/001-add-asr-jni-path/`）。

### 調整
- 補充規格：靜音期間不輸出字幕段落。
- 補充規格：prepare 失敗後停留在 Error，需再次準備才可恢復。
- 補充規格：錯誤碼採單一通用碼，訊息可區分原因。
- 補充規格：辨識失敗略過當下片段並持續輸出。
- 補充規格：Phase 1 不持久化字幕資料。

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
- 重新調整 Phase 4+ 任務為 RED → GREEN → REFACTOR 流程。
- 新增未準備即啟動的錯誤狀態測試。
- `start()` 在非 Ready 狀態時回報錯誤。
- 重構 `start()` 流程，提早返回錯誤。
- `start()` 重構時補回狀態檢查註解。
- `start()` 補回 JNI、輸出與狀態註解。
- 新增準備失敗時回報錯誤的測試。
- `prepare()` 目前已符合準備失敗錯誤測試需求，標記為 GREEN。
- 清理 `prepare()` 註解與縮排。
- 新增 10 分鐘穩定性測試。
- `RkMeetingSession` 已符合穩定性測試需求，標記為 GREEN。
- 抽出 `stopTranscriptLoop()` 統一清理流程。
- 更新 quickstart 驗收項目，補上錯誤狀態檢查。
- Demo App 開始收集並顯示字幕流。

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
