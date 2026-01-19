# Feature Specification: Phase 1 真實字幕流（ASR）

**Feature Branch**: `001-add-asr-jni-path`  
**Created**: 2026-01-19  
**Status**: Draft  
**Input**: User description: "目前這個專案已經完成開發藍圖 Phase 0 從 UI 到 data flow 的流程設計，點擊 'start' 後已可從 meeting-engine 取得假字串 。"

## Clarifications

### Session 2026-01-19

- Q: 靜音期間字幕要不要輸出？ → A: 靜音期間不輸出任何字幕段落
- Q: prepare 失敗後的狀態停留策略？ → A: 停留在 Error，需再次呼叫 prepare 才能回到 Ready
- Q: 錯誤碼細分程度？ → A: 全部回傳同一個通用錯誤碼（訊息可不同）
- Q: 單次辨識失敗的處理策略？ → A: 只略過當下片段，後續持續輸出
- Q: 字幕是否需要持久化？ → A: 字幕只在記憶體與 UI 流內存在，不持久化

## User Scenarios & Testing *(mandatory)*

<!--
  IMPORTANT: User stories should be PRIORITIZED as user journeys ordered by importance.
  Each user story/journey must be INDEPENDENTLY TESTABLE - meaning if you implement just ONE of them,
  you should still have a viable MVP (Minimum Viable Product) that delivers value.
  
  Assign priorities (P1, P2, P3, etc.) to each story, where P1 is the most critical.
  Think of each story as a standalone slice of functionality that can be:
  - Developed independently
  - Tested independently
  - Deployed independently
  - Demonstrated to users independently
-->

### User Story 1 - 按下 Start 取得真實字幕 (Priority: P1)

作為產品驗證者，我希望按下 Start 後能持續看到真實語音轉字幕的輸出，讓 demo 可以證明音訊到字幕的完整路徑已打通。

**Why this priority**: 這是 Phase 1 的核心價值，沒有這段路徑就無法驗證接真 ASR。

**Independent Test**: 透過裝置麥克風輸入語音，觀察畫面是否每秒產出一段字幕並持續更新。

**Acceptance Scenarios**:

1. **Given** 系統已完成準備且可開始，**When** 使用者按下 Start 並說話，**Then** 畫面每秒顯示一段新字幕
2. **Given** 持續錄音中，**When** 使用者保持安靜，**Then** 系統仍穩定運作且不輸出字幕段落

---

### User Story 2 - 準備階段可辨識錯誤並提示 (Priority: P2)

作為測試者，我希望在準備階段就能知道語音辨識資源是否就緒，避免 Start 後才出錯。

**Why this priority**: 減少無效測試，讓問題在最早期被偵測。

**Independent Test**: 透過移除或放錯模型資源，驗證系統能回報錯誤並允許重新準備。

**Acceptance Scenarios**:

1. **Given** 必要資源不存在或無法讀取，**When** 進行準備流程，**Then** 進入錯誤狀態並提供可理解的錯誤訊息

---

### User Story 3 - Stop 後立即停止輸出 (Priority: P3)

作為使用者，我希望按下 Stop 後字幕立刻停止更新，確保控制行為一致。

**Why this priority**: 控制行為正確是 demo 的基本要求，但重要性低於完成路徑。

**Independent Test**: Start 後觀察輸出，Stop 後確認不再產生新字幕。

**Acceptance Scenarios**:

1. **Given** 系統正在輸出字幕，**When** 使用者按下 Stop，**Then** 之後不再產生新字幕

---

[Add more user stories as needed, each with an assigned priority]

### Edge Cases

- Start 前未完成準備
- Start 重入或重複呼叫
- Stop 在未 Start 時呼叫
- 音訊輸入短暫中斷或為靜音
- 單次辨識失敗或回傳空字串

## Requirements *(mandatory)*

<!--
  ACTION REQUIRED: The content in this section represents placeholders.
  Fill them out with the right functional requirements.
-->

### Functional Requirements

- **FR-001**: 系統必須在準備階段驗證語音辨識資源是否可用，失敗時回報錯誤並可重試
- **FR-001a**: 準備失敗後必須停留在錯誤狀態，直到再次準備成功才可進入可開始狀態
- **FR-001b**: 錯誤回報需使用單一通用錯誤碼，但可用錯誤訊息區分原因
- **FR-002**: 系統必須在開始錄音後接收即時音訊並送入語音辨識流程
- **FR-003**: 系統必須以固定節奏產生字幕（預設每 1 秒一段）
- **FR-004**: 系統必須產生含 `speakerId`、`startTimeMs`、`endTimeMs`、`isFinal`、`id` 的字幕段落
- **FR-005**: `speakerId` 必須為 `Unknown`，`isFinal` 必須為 `true`（Phase 1 固定值）
- **FR-006**: `startTimeMs` 與 `endTimeMs` 必須單調遞增，且依音訊累積時間推進
- **FR-007**: 單次辨識失敗時不得中斷會議流程，略過當下片段並持續後續輸出
- **FR-008**: 停止錄音後必須立即停止字幕輸出
- **FR-009**: 在非可開始狀態觸發開始時必須回報錯誤且不改變既有狀態
- **FR-010**: 靜音期間不得輸出字幕段落

### 非功能需求

- **NFR-001**: 字幕輸出延遲需可量測且穩定，避免明顯抖動或卡頓
- **NFR-002**: 連續錄音期間不應造成應用崩潰或無回應
- **NFR-003**: 任何錯誤必須可被 UI 感知並能回到可重試狀態
- **NFR-004**: Phase 1 不需持久化字幕資料

### Key Entities *(include if feature involves data)*

- **TranscriptSegment**: 一段字幕內容，含 `text`、`speakerId`、`startTimeMs`、`endTimeMs`、`isFinal`、`id`
- **AsrResource**: 語音辨識資源集合（模型檔或必要資源），具可用性與可讀性狀態
- **AudioInput**: 即時音訊輸入來源，提供連續的時間序列資料

## Success Criteria *(mandatory)*

<!--
  ACTION REQUIRED: Define measurable success criteria.
  These must be technology-agnostic and measurable.
-->

### Measurable Outcomes

- **SC-001**: 使用者在 Start 後 2 秒內看到第一段字幕
- **SC-002**: 連續 5 分鐘錄音期間，每分鐘至少產生 50 段字幕（約每秒一段）
- **SC-003**: 在 10 次 Start/Stop 循環中，無崩潰且狀態轉換正確率 100%
- **SC-004**: 準備失敗時 100% 會顯示可理解的錯誤訊息並可重試

## Assumptions

- Phase 1 以裝置端語音辨識為主，不依賴網路服務
- 字幕內容可接受粗略品質，主要驗證端到端路徑
