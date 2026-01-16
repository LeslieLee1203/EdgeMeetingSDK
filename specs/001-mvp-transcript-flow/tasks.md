# Tasks: Phase 0 MVP 字幕流

**Input**: Design documents from `/specs/001-mvp-transcript-flow/`  
**Prerequisites**: plan.md (required), spec.md, research.md, data-model.md, contracts/  
**Tests**: TDD 強制，先寫測試且確認失敗，再進行最小實作  
**Organization**: 依 User Story 分組，確保可獨立測試與展示

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 任務最小化，確認測試與規格路徑

- [x] T001 確認測試依賴可用：檢查 `meeting-engine/build.gradle.kts` 已含 `kotlinx-coroutines-test`（單一步驟）
- [x] T002 建立任務對應測試檔案：建立空測試檔 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`（單一步驟）

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 共用基礎，需先完成再進行 User Story

- [x] T003 定義測試用假時間來源介面（`meeting-engine/src/main/java/com/edgemeeting/engine/TimeSource.kt`）
- [x] T004 建立測試用假時間來源實作（`meeting-engine/src/test/java/com/edgemeeting/engine/FakeTimeSource.kt`）
- [x] T005 建立可控的假字幕產生器介面（`meeting-engine/src/main/java/com/edgemeeting/engine/TranscriptGenerator.kt`）

**Checkpoint**: Foundation ready - user story implementation can now begin

---

## Phase 3: User Story 1 - 可展示的字幕流 (Priority: P1) 🎯 MVP

**Goal**: 啟動後每秒輸出字幕段落，包含 Unknown speaker 與遞增時間

**Independent Test**: 啟動會議後 15 秒內看到 10 段字幕，停止後 2 秒內不再新增

### Tests for User Story 1 (REQUIRED) ⚠️

> **NOTE: Write these tests FIRST, ensure they FAIL before implementation**

- [x] T006 [P] [US1] 建立字幕輸出節奏測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`）
- [x] T007 [P] [US1] 建立停止後 2 秒內不再輸出測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`）
- [x] T008 [P] [US1] 建立字幕欄位完整性與時間遞增測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`）
- [x] T009 [P] [US1] 建立更新間隔 95% 不超過 1.5 秒測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`）

### Implementation for User Story 1

- [x] T010 [US1] 在 `RkMeetingSession` 新增注入欄位（timeSource）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T011 [US1] 在 `RkMeetingSession` 新增注入欄位（transcriptGenerator）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T012 [US1] 在 `RkMeetingSession` 實作每秒輸出 loop（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T013 [US1] 在 `RkMeetingSession` 實作 `transcriptFlow` 緩衝策略（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T014 [US1] 在 `RkMeetingSession.start()` 加入輸出啟動（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T015 [US1] 在 `RkMeetingSession.stop()` 加入輸出停止（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [x] T016 [US1] 在 `RkMeetingSession.release()` 加入清理（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）

**Checkpoint**: User Story 1 can be demoed independently

---

## Phase 4: User Story 2 - 錯誤狀態可見 (Priority: P2)

**Goal**: 錯誤操作或準備失敗能回報狀態

**Independent Test**: 未準備即啟動會回報錯誤；準備失敗回報錯誤

### Tests for User Story 2 (REQUIRED) ⚠️

- [ ] T017 [P] [US2] 建立未準備即啟動錯誤測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStateTest.kt`）
- [ ] T018 [P] [US2] 建立準備失敗錯誤測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStateTest.kt`）

### Implementation for User Story 2

- [ ] T019 [US2] 強化 `prepare()` 錯誤狀態回報（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）
- [ ] T020 [US2] 強化 `start()` 狀態檢查與錯誤回報（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）

**Checkpoint**: User Story 2 works independently with US1

---

## Phase 5: User Story 3 - 長時間展示穩定 (Priority: P3)

**Goal**: 連續 10 分鐘展示不中斷

**Independent Test**: 模擬長時間運行仍持續輸出字幕

### Tests for User Story 3 (REQUIRED) ⚠️

- [ ] T021 [P] [US3] 建立長時間穩定性測試（`meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStabilityTest.kt`）

### Implementation for User Story 3

- [ ] T022 [US3] 補齊長時間運行的記憶體釋放與取消處理（單一函式變更）（`meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`）

**Checkpoint**: All user stories are independently functional

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文件與驗證

- [ ] T023 [P] 更新 quickstart 驗證步驟（`specs/001-mvp-transcript-flow/quickstart.md`）
- [ ] T024 [P] 更新 CHANGELOG（`CHANGELOG.md`）

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: No dependencies
- **Foundational (Phase 2)**: Depends on Setup completion
- **User Stories (Phase 3+)**: Depends on Foundational completion
- **Polish (Phase 6)**: Depends on all desired user stories

### User Story Dependencies

- **US1 (P1)**: Depends on Foundation only
- **US2 (P2)**: Depends on Foundation only
- **US3 (P3)**: Depends on Foundation only

### Within Each User Story

- Tests MUST be written and FAIL before implementation
- 先用註解說明「為什麼要這樣做」，再寫程式碼
- 每次變更只包含一個 function 或一段程式碼
- 完成一段程式碼後必須等待使用者確認

---

## Parallel Example: User Story 1

```text
Task: T006 [US1] Subtitle cadence test in meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt
Task: T007 [US1] Stop emission test in meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt
Task: T008 [US1] Field completeness test in meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 + Phase 2
2. 完成 User Story 1 測試與最小實作
3. **停止並驗證**：確認字幕節奏與停止行為

### Incremental Delivery

1. US1 完成後可展示
2. US2 加入錯誤狀態
3. US3 補強長時間穩定性
