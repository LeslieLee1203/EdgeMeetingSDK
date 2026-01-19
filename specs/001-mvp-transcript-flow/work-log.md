# 工作紀錄：Phase 0 MVP 字幕流

**日期**：2026-01-16  
**範圍**：`specs/001-mvp-transcript-flow/`  
**目的**：提供接手工程師快速理解目前實作狀態與後續開發入口。

## 目前狀態總結
- **US1（字幕流）**：已完成，可在 Demo App 中看到每秒新增字幕。
- **US2（錯誤狀態）**：已完成，未準備即啟動與準備失敗會回報錯誤狀態。
- **US3（長時間穩定）**：已完成，測試驗證 10 分鐘仍能持續輸出。
- **文件**：`quickstart.md`、`tasks.md` 已更新並全部完成。

## 已完成事項（依 tasks）
- Phase 1：T001–T002
- Phase 2：T003–T005
- Phase 3：T006–T016
- Phase 4：T017–T022
- Phase 5：T023–T025
- Phase 6：T026–T027

## 核心設計與關鍵檔案
- **字幕輸出主體**：`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`
  - 可注入 `TimeSource`、`TranscriptGenerator`、`CoroutineDispatcher`（測試虛擬時間）
  - `transcriptFlow` 使用 `MutableSharedFlow` 緩衝
  - `start()` 會啟動 loop；`stop()`/`release()` 統一用 `stopTranscriptLoop()` 清理
- **測試**
  - `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`
  - `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStateTest.kt`
  - `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStabilityTest.kt`
- **Demo App 顯示**
  - `app/src/main/java/com/edgemeeting/sdk/MainActivity.kt`
  - `MeetingScreen` 會收集 `transcriptFlow` 並顯示最近 5 段

## 行為驗收點（與 quickstart 對齊）
- 啟動後 15 秒內至少 10 段字幕
- 停止後 2 秒內不再新增字幕
- 未準備即啟動回報錯誤狀態
- 準備失敗回報錯誤狀態

## 測試與執行
- **單元測試**
  - 需求：JDK 17+
  - 指令：`./gradlew :meeting-engine:test`
- **Demo 驗證**
  - App 點 `Prepare` → `Start`，畫面會顯示字幕流

## 已知限制 / 注意事項
- `prepare()` 目前同步呼叫 JNI，尚未移到背景執行緒
- Demo App 只顯示最近 5 段字幕（避免畫面擠滿）

## 後續開發建議
- 將 `prepare()` 改為非同步，避免 UI 卡住
- 引入真實 ASR/翻譯流程以取代 `TranscriptGenerator` 的假資料
- 長時間測試可改為自動化的 instrumented test 或壓測腳本
