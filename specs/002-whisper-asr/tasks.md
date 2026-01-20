# Tasks: RK3588 Whisper 即時語音轉錄整合

**Input**: Design documents from `/specs/002-whisper-asr/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 測試為必要（TDD：RED → GREEN → REFACTOR）

**Organization**: 依 User Story 分組，確保每個故事可獨立驗證

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立基本路徑與模型檔佈署規則

- [ ] T001 更新模型檔放置說明到 `specs/002-whisper-asr/quickstart.md`
- [ ] T002 確認 `.rknn` 模型與 `librknnrt.so` 佈署規則文件化於 `specs/002-whisper-asr/quickstart.md`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 先打通 JNI/Native 骨架與錯誤回傳型別

**⚠️ CRITICAL**: 未完成不可進入任何 User Story

- [ ] T003 建立 `meeting-engine/src/main/cpp/whisper/` 目錄與空殼檔案 `RknnWhisperEngine.{h,cpp}`
- [ ] T004 建立 JNI 介面骨架於 `meeting-engine/src/main/cpp/native-lib.cpp`（僅宣告、不實作）
- [ ] T005 建立 Kotlin 端介面骨架於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt`
- [ ] T006 建立 JNI 端實作骨架於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt`
- [ ] T007 建立錯誤碼與訊息規格對照表於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/BridgeResult.kt`
- [ ] T008 [P] 建立 `BridgeResult` 最小單元測試於 `meeting-engine/src/test/java/com/edgemeeting/engine/BridgeResultTest.kt`

**Checkpoint**: JNI/Bridge/Native skeleton 完成，可開始 User Story

---

## Phase 3: User Story 1 - 即時語音轉錄（逐段文字） (Priority: P1) 🎯 MVP

**Goal**: 即時輸入音訊後輸出逐段文字

**Independent Test**: 以假 PCM 輸入後能產出至少一段文字並符合分段規則

### Tests for User Story 1 (REQUIRED) ⚠️

- [ ] T009 [P] [US1] 測試逐段分段規則於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`
- [ ] T010 [P] [US1] 測試 2 秒內產出段落於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`
- [ ] T011 [P] [US1] 測試輸出段落時間與語言欄位於 `meeting-core/src/test/java/com/edgemeeting/core/TranscriptSegmentTest.kt`
- [ ] T012 [P] [US1] 測試離線狀態仍可轉錄於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T013 [P] [US1] 測試 20 秒音訊 20 秒內完成於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`
- [ ] T014 [P] [US1] 測試 30 分鐘穩定性於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStabilityTest.kt`
- [ ] T015 [P] [US1] 測試 start/stop 啟停轉錄於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`

### Implementation for User Story 1

- [ ] T016 [US1] 實作會議前載入模型流程於 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`
- [ ] T017 [US1] 實作 native engine 載入與釋放骨架於 `meeting-engine/src/main/cpp/whisper/RknnWhisperEngine.cpp`
- [ ] T018 [US1] 實作 `initWhisper`/`releaseWhisper` JNI 串接於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T019 [US1] 實作 `pushAudio` 與 `pollTranscript` JNI 串接於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T020 [US1] 實作 `JniEngineBridge` 對應方法於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt`
- [ ] T021 [US1] 實作 start/stop 啟停轉錄流程於 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`
- [ ] T022 [US1] 將逐段結果接入 `RkMeetingSession` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`
- [ ] T023 [US1] 更新 `TranscriptSegment` 欄位（時間/語言）於 `meeting-core/src/main/java/com/edgemeeting/core/model/TranscriptSegment.kt`

**Checkpoint**: US1 可獨立運作並通過測試

---

## Phase 4: User Story 2 - 語言自動/指定 (Priority: P2)

**Goal**: 支援 auto 與指定 Whisper 全語言

**Independent Test**: 在不同語言設定下可得到對應語言輸出

### Tests for User Story 2 (REQUIRED) ⚠️

- [ ] T024 [P] [US2] 測試語言設定傳遞於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T025 [P] [US2] 測試語言代碼輸出於 `meeting-core/src/test/java/com/edgemeeting/core/TranscriptSegmentTest.kt`

### Implementation for User Story 2

- [ ] T026 [US2] 新增語言設定型別於 `meeting-core/src/main/java/com/edgemeeting/core/model/LanguageSetting.kt`
- [ ] T027 [US2] 調整 `EngineBridge` 介面新增語言參數於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt`
- [ ] T028 [US2] 調整 `JniEngineBridge` 傳遞語言設定於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt`
- [ ] T029 [US2] 調整 JNI 端語言設定處理於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T030 [US2] 調整 native engine 語言設定處理於 `meeting-engine/src/main/cpp/whisper/RknnWhisperEngine.cpp`

**Checkpoint**: US2 可獨立運作並通過測試

---

## Phase 5: User Story 3 - 錯誤與降級回饋 (Priority: P3)

**Goal**: 模型/資源不可用時回傳錯誤碼與訊息

**Independent Test**: 模型缺失時回傳可讀錯誤訊息與錯誤碼

### Tests for User Story 3 (REQUIRED) ⚠️

- [ ] T031 [P] [US3] 測試模型缺失錯誤回傳於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStateTest.kt`
- [ ] T032 [P] [US3] 測試錯誤碼與訊息欄位於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`

### Implementation for User Story 3

- [ ] T033 [US3] 實作 native 錯誤碼與訊息回傳於 `meeting-engine/src/main/cpp/whisper/RknnWhisperEngine.cpp`
- [ ] T034 [US3] JNI 錯誤轉譯與回傳於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T035 [US3] `BridgeResult` 錯誤碼表與訊息整理於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/BridgeResult.kt`
- [ ] T036 [US3] `RkMeetingSession` 錯誤狀態回傳於 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt`

**Checkpoint**: US3 可獨立運作並通過測試

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 打包、效能與回歸驗證

- [ ] T037 [P] 更新 CMake 連結 `librknnrt.so` 於 `meeting-engine/src/main/cpp/CMakeLists.txt`
- [ ] T038 [P] 更新 Gradle ABI/打包規則於 `meeting-engine/build.gradle.kts`
- [ ] T039 [P] 新增 `jniLibs/arm64-v8a` 放置說明於 `specs/002-whisper-asr/quickstart.md`
- [ ] T040 [P] 補上效能驗證說明於 `specs/002-whisper-asr/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 無依賴
- **Foundational (Phase 2)**: 依賴 Phase 1 完成，阻擋全部故事
- **User Stories (Phase 3-5)**: 依賴 Phase 2 完成，可依優先序進行
- **Polish (Phase 6)**: 依賴完成任一或全部故事

### User Story Dependencies

- **US1 (P1)**: 依賴 Foundational 完成
- **US2 (P2)**: 可接續 US1，或與 US3 並行（需共用 Bridge 介面）
- **US3 (P3)**: 可接續 US1，或與 US2 並行（需共用 Bridge 介面）

### Parallel Opportunities

- 同一階段的 [P] 任務可並行
- US2/US3 若不互相影響可由不同人並行

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. Phase 1 → Phase 2 完成
2. 完成 US1 測試 → 最小實作 → 重構
3. **停止並驗證**：逐段輸出與 2 秒內產出

### Incremental Delivery

1. US1 完成並驗證
2. US2 完成並驗證
3. US3 完成並驗證
