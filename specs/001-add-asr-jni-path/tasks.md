# Tasks: Phase 1 真實字幕流（ASR）

**Input**: Design documents from `specs/001-add-asr-jni-path/`  
**Prerequisites**: plan.md, spec.md, research.md, data-model.md, contracts/  
**Tests**: Required（TDD：RED → GREEN → REFACTOR）  
**Organization**: 依 User Story 分階段，確保可獨立驗證

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立測試輔助，讓後續 TDD 快速落地

- [x] T001 建立 `pcm1s16kSilence()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/fixtures/PcmFixtures.kt`
- [x] T002 建立 `pcm1s16kSpeech()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/fixtures/PcmFixtures.kt`
- [x] T003 建立 `assertNoSegments()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/fixtures/TranscriptAssertions.kt`
- [x] T004 建立 `assertSegmentFields()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/fixtures/TranscriptAssertions.kt`

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 所有 user story 共用的核心基礎

- [ ] T005 [P] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt` 新增 `initAsr()` 宣告
- [ ] T006 [P] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt` 新增 `pushPcm()` 宣告
- [ ] T007 [P] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt` 新增 `flushAsr()` 宣告
- [ ] T008 [P] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt` 新增 `stopAsr()` 宣告
- [ ] T009 [P] 在 `meeting-engine/src/test/java/com/edgemeeting/engine/fakes/FakeEngineBridge.kt` 實作 `initAsr()` stub
- [ ] T010 [P] 在 `meeting-engine/src/test/java/com/edgemeeting/engine/fakes/FakeEngineBridge.kt` 實作 `pushPcm()` stub
- [ ] T011 [P] 在 `meeting-engine/src/test/java/com/edgemeeting/engine/fakes/FakeEngineBridge.kt` 實作 `flushAsr()` stub
- [ ] T012 [P] 在 `meeting-engine/src/test/java/com/edgemeeting/engine/fakes/FakeEngineBridge.kt` 實作 `stopAsr()` stub
- [ ] T013 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt` 新增 `initAsr()` wrapper
- [ ] T014 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt` 新增 `pushPcm()` wrapper
- [ ] T015 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt` 新增 `flushAsr()` wrapper
- [ ] T016 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt` 新增 `stopAsr()` wrapper
- [ ] T017 在 `meeting-engine/src/main/cpp/WhisperRunner.h` 新增 `WhisperRunner` 類別宣告
- [ ] T018 在 `meeting-engine/src/main/cpp/WhisperRunner.cpp` 實作 `WhisperRunner::init`
- [ ] T019 在 `meeting-engine/src/main/cpp/WhisperRunner.cpp` 實作 `WhisperRunner::acceptPcm`
- [ ] T020 在 `meeting-engine/src/main/cpp/WhisperRunner.cpp` 實作 `WhisperRunner::flush`
- [ ] T021 在 `meeting-engine/src/main/cpp/WhisperRunner.cpp` 實作 `WhisperRunner::stop`
- [ ] T022 在 `meeting-engine/src/main/cpp/native-lib.cpp` 新增 `nativeInitAsr` JNI 綁定
- [ ] T023 在 `meeting-engine/src/main/cpp/native-lib.cpp` 新增 `nativePushPcm` JNI 綁定
- [ ] T024 在 `meeting-engine/src/main/cpp/native-lib.cpp` 新增 `nativeFlushAsr` JNI 綁定
- [ ] T025 在 `meeting-engine/src/main/cpp/native-lib.cpp` 新增 `nativeStopAsr` JNI 綁定
- [ ] T026 在 `meeting-engine/src/main/cpp/CMakeLists.txt` 加入 `WhisperRunner.cpp`

**Checkpoint**: 基礎完成後才能進入各 User Story

---

## Phase 3: User Story 1 - 按下 Start 取得真實字幕 (Priority: P1) 🎯 MVP

**Goal**: Start 後以 1 秒 PCM 節奏輸出字幕，靜音不輸出，單次失敗略過不斷流  
**Independent Test**: 連續說話 5 秒可看到約 5 段字幕，靜音不輸出

### Tests for User Story 1 (REQUIRED) ⚠️

> **NOTE**: RED → GREEN → REFACTOR（先寫測試失敗 → 最小實作通過 → 重構）

- [ ] T027 [P] [US1] 新增 `startProducesSegmentEachSecond()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionAsrFlowTest.kt`
- [ ] T028 [P] [US1] 新增 `silenceProducesNoSegments()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionAsrFlowTest.kt`
- [ ] T029 [P] [US1] 新增 `singleAsrFailureIsSkipped()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionAsrFlowTest.kt`
- [ ] T030 [P] [US1] 新增 `firstSegmentWithin2s()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionAsrFlowTest.kt`
- [ ] T031 [P] [US1] 新增 `startRejectedWhenNotReady()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStartGuardTest.kt`

### Implementation for User Story 1

- [ ] T032 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `appendPcm()` 緩衝函式
- [ ] T033 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `shouldFlushAsr()` 節奏判斷函式
- [ ] T034 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `flushAsrOnce()` 單次推論函式（含錯誤處理）
- [ ] T035 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `onAudioPcm()` 音訊回調處理
- [ ] T036 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/transcript/TranscriptSegmentFactory.kt` 新增 `create()` 產生段落函式
- [ ] T037 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/transcript/TranscriptSegmentFactory.kt` 新增 `nextId()` 產生穩定 ID
- [ ] T038 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `emitSegments()` 封裝輸出
- [ ] T039 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增空字串過濾（靜音不輸出）
- [ ] T040 [US1] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 start guard 錯誤回報

**Checkpoint**: User Story 1 可獨立 demo 與測試

---

## Phase 4: User Story 2 - 準備階段可辨識錯誤並提示 (Priority: P2)

**Goal**: prepare 驗證資源，失敗進 Error 且可重試  
**Independent Test**: 移除模型檔 → prepare 進 Error，補回後再 prepare 可 Ready

### Tests for User Story 2 (REQUIRED) ⚠️

- [ ] T041 [P] [US2] 新增 `prepareFailsAndStaysError()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionPrepareTest.kt`
- [ ] T042 [P] [US2] 新增 `prepareRetriesToReady()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionPrepareTest.kt`
- [ ] T043 [P] [US2] 新增 `prepareUsesGenericErrorCode()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionPrepareTest.kt`

### Implementation for User Story 2

- [ ] T044 [US2] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/asr/AsrResourceValidator.kt` 新增 `validate()` 回傳錯誤訊息
- [ ] T045 [US2] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `prepareAsrResources()` 封裝驗證呼叫
- [ ] T046 [US2] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `enterErrorState()` 統一錯誤狀態
- [ ] T047 [US2] 在 `meeting-core/src/main/java/com/edgemeeting/core/MeetingState.kt` 新增通用錯誤碼常數

**Checkpoint**: prepare 失敗流程可獨立驗證

---

## Phase 5: User Story 3 - Stop 後立即停止輸出 (Priority: P3)

**Goal**: stop 後不再產生字幕，清理內部緩衝  
**Independent Test**: start 後等待輸出，再 stop 並確認不再 emit

### Tests for User Story 3 (REQUIRED) ⚠️

- [ ] T048 [P] [US3] 新增 `stopStopsEmissionImmediately()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStopTest.kt`

### Implementation for User Story 3

- [ ] T049 [US3] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `clearPcmBuffer()` 清理緩衝
- [ ] T050 [US3] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 新增 `stopAsrOnce()` 呼叫 JNI 停止
- [ ] T051 [US3] 在 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt` 新增 stop/flush 清理路徑

**Checkpoint**: User Story 3 可獨立驗證

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 穩定性與可維護性提升

- [ ] T052 [P] 新增 `monotonicTimeFields()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/TranscriptSegmentValidationTest.kt`
- [ ] T053 [P] 新增 `latencyStatsRecorded()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/TranscriptLatencyTest.kt`
- [ ] T054 [P] 新增 `fiveMinuteStability()` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStabilityTest.kt`
- [ ] T055 更新 `specs/001-add-asr-jni-path/quickstart.md` 註明「Phase 1 不持久化」與測試指令
- [ ] T056 執行單元測試 `./gradlew :meeting-engine:test` 並記錄結果於 `specs/001-add-asr-jni-path/quickstart.md`

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)** → **Foundational (Phase 2)** → **User Stories (Phase 3-5)** → **Polish (Phase 6)**
- 所有 User Story 需在 Foundational 完成後才能開始

### User Story Dependencies

- **US1 (P1)**: 無依賴
- **US2 (P2)**: 可與 US1 並行，但需共享 Foundational
- **US3 (P3)**: 可與 US1 並行，但需共享 Foundational

### Within Each User Story

- 測試必須遵守 RED → GREEN → REFACTOR
- 先寫註解說明「為什麼要這樣做」
- 每次變更只包含一個 function 或一段程式碼

---

## Parallel Example: User Story 1

```bash
Task: "T027 [US1] startProducesSegmentEachSecond"
Task: "T028 [US1] silenceProducesNoSegments"
Task: "T029 [US1] singleAsrFailureIsSkipped"
```

---

## Implementation Strategy

### MVP First (User Story 1 Only)

1. 完成 Phase 1 + Phase 2
2. 完成 US1（含測試）並驗證
3. Demo 後再擴充 US2/US3

### Incremental Delivery

1. US1 → 獨立驗證
2. US2 → 獨立驗證
3. US3 → 獨立驗證
