# Tasks: RK3588 Whisper 即時語音轉錄整合

**Input**: Design documents from `/specs/002-whisper-asr/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 測試為必要（TDD：RED → GREEN → REFACTOR）

**Organization**: 依功能切片分組，每個切片獨立完成 TDD 循環

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立基本路徑與模型檔佈署規則

- [ ] T001 更新模型檔放置說明到 `specs/002-whisper-asr/quickstart.md`
- [ ] T002 確認 `.rknn` 模型與 `librknnrt.so` 佈署規則文件化於 `specs/002-whisper-asr/quickstart.md`
- [ ] T003 定義模型路徑慣例：assets 打包路徑 `assets/models/`，runtime 路徑 `filesDir/models/`
- [ ] T004 建立 `app/src/main/assets/models/.gitkeep` 佔位檔

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立統一介面基礎設施

**⚠️ CRITICAL**: 未完成不可進入任何 User Story

### 2.1 AsrConfig 資料模型（TDD）

- [ ] T005 🔴 RED: 測試 `AsrConfig.Whisper` 建構與驗證於 `meeting-core/src/test/java/com/edgemeeting/core/AsrConfigTest.kt`
- [ ] T006 🟢 GREEN: 建立 `AsrConfig` sealed class 於 `meeting-core/src/main/java/com/edgemeeting/core/model/AsrConfig.kt`
- [ ] T007 🔵 REFACTOR: 檢視 sealed class 結構與命名

### 2.2 EngineConfig 資料模型（TDD）

- [ ] T008 🔴 RED: 測試 `EngineConfig` 建構於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineConfigTest.kt`
- [ ] T009 🟢 GREEN: 建立 `EngineConfig` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineConfig.kt`
- [ ] T010 🔵 REFACTOR: 檢視預設值與 null 處理

### 2.3 EngineCallback 擴充（TDD）

- [ ] T011 🔴 RED: 測試 `EngineCallback.onTranscript` 回調於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineCallbackTest.kt`
- [ ] T012 🟢 GREEN: 擴充 `EngineCallback` 介面（從 `AudioCallback` 重構）於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineCallback.kt`
- [ ] T013 🔵 REFACTOR: 確認向後相容性

### 2.4 EngineBridge 介面更新（TDD）

- [ ] T014 🔴 RED: 測試 `EngineBridge.init(EngineConfig)` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineBridgeTest.kt`
- [ ] T015 🟢 GREEN: 更新 `EngineBridge.init` 簽章於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt`
- [ ] T016 🔵 REFACTOR: 檢視介面一致性

### 2.5 ModelAssetManager（TDD）

- [ ] T017 🔴 RED: 測試 `ModelAssetManager.ensureModels` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/ModelAssetManagerTest.kt`
- [ ] T018 🟢 GREEN: 建立 `ModelAssetManager` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/ModelAssetManager.kt`
- [ ] T019 🔵 REFACTOR: 檢視錯誤處理與邊界情況

### 2.6 BridgeResult 錯誤碼（TDD）

- [ ] T020 🔴 RED: 測試 `BridgeResult` 錯誤碼常數於 `meeting-engine/src/test/java/com/edgemeeting/engine/BridgeResultTest.kt`
- [ ] T021 🟢 GREEN: 擴充 `BridgeResult` 錯誤碼於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/BridgeResult.kt`
- [ ] T022 🔵 REFACTOR: 檢視錯誤碼命名與範圍

**Checkpoint**: 統一介面基礎設施完成，可開始 User Story

---

## Phase 3: User Story 1 - 即時語音轉錄（逐段文字） (Priority: P1) 🎯 MVP

**Goal**: 即時輸入音訊後輸出逐段文字

**Independent Test**: 以假 PCM 輸入後能產出至少一段文字並符合分段規則

### 3.1 TranscriptSegment 資料模型（TDD）

- [ ] T023 🔴 RED: 測試 `TranscriptSegment` 新欄位（startMs, endMs, languageCode）於 `meeting-core/src/test/java/com/edgemeeting/core/TranscriptSegmentTest.kt`
- [ ] T024 🟢 GREEN: 更新 `TranscriptSegment` 欄位於 `meeting-core/src/main/java/com/edgemeeting/core/model/TranscriptSegment.kt`
- [ ] T025 🔵 REFACTOR: 檢視欄位命名與驗證邏輯

### 3.2 Native AsrEngine 抽象層（TDD）

- [ ] T026 🔴 RED: 建立 `AsrEngine` 介面測試骨架（C++ mock）
- [ ] T027 🟢 GREEN: 建立 `AsrEngine.h` 抽象類於 `meeting-engine/src/main/cpp/asr/AsrEngine.h`（含 init/start/pushAudio/stop/release）
- [ ] T028 🔵 REFACTOR: 檢視介面最小化

### 3.3 WhisperAsrEngine 初始化與生命週期（TDD）

- [ ] T029 🔴 RED: 測試 `WhisperAsrEngine.init` 載入模型於 C++ 單元測試
- [ ] T030 🟢 GREEN: 實作 `WhisperAsrEngine.init` 於 `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`
- [ ] T031 🟢 GREEN: 實作 RKNN 模型載入於 `WhisperAsrEngine.cpp`
- [ ] T032 🟢 GREEN: 實作 `WhisperAsrEngine.start`/`stop`（重置狀態/flush 剩餘音訊）
- [ ] T033 🔵 REFACTOR: 檢視資源管理與錯誤處理

### 3.4 JNI ASR 整合（TDD）

- [ ] T034 🔴 RED: 測試 `JniEngineBridge.init(EngineConfig)` 建立 ASR 引擎於 `JniEngineBridgeTest.kt`
- [ ] T035 🟢 GREEN: 更新 `native-lib.cpp` 支援 `AsrConfig` 參數
- [ ] T036 🟢 GREEN: 實作 `JniEngineBridge.init` 呼叫 native ASR 初始化
- [ ] T037 🔵 REFACTOR: 檢視 JNI 參數傳遞效率

### 3.5 音訊輸入與轉錄輸出（TDD）

- [ ] T038 🔴 RED: 測試逐段分段規則（靜音 >= 700ms）於 `RkMeetingSessionTranscriptTest.kt`
- [ ] T039 🟢 GREEN: 實作 `WhisperAsrEngine.pushAudio` 於 `WhisperAsrEngine.cpp`
- [ ] T040 🟢 GREEN: 實作靜音偵測與分段邏輯於 `WhisperAsrEngine.cpp`
- [ ] T041 🟢 GREEN: 實作 JNI callback `onTranscript` 回傳於 `native-lib.cpp`
- [ ] T042 🔵 REFACTOR: 檢視音訊緩衝與記憶體使用

### 3.6 RkMeetingSession 整合（TDD）

- [ ] T043 🔴 RED: 測試 `prepare()` 使用 `EngineConfig` 於 `RkMeetingSessionTest.kt`
- [ ] T044 🟢 GREEN: 更新 `RkMeetingSession.prepare()` 使用 `EngineConfig`
- [ ] T045 🟢 GREEN: 將 `onTranscript` 回調接入 `transcriptFlow`
- [ ] T046 🔵 REFACTOR: 檢視狀態轉換與執行緒安全

### 3.7 啟停轉錄流程（TDD）

- [ ] T047 🔴 RED: 測試 `start()`/`stop()` 啟停 ASR 於 `RkMeetingSessionTest.kt`
- [ ] T048 🟢 GREEN: 更新 `start()`/`stop()` 控制 ASR 引擎（呼叫 AsrEngine.start/stop）
- [ ] T049 🔵 REFACTOR: 檢視生命週期管理

### 3.8 效能與穩定性驗證（TDD）

- [ ] T050 🔴 RED: 測試 2 秒內產出段落於 `RkMeetingSessionTranscriptTest.kt`
- [ ] T051 🟢 GREEN: 調整實作以符合 2 秒產出要求
- [ ] T052 🔵 REFACTOR: 效能瓶頸分析

- [ ] T053 🔴 RED: 測試 20 秒音訊 20 秒內完成
- [ ] T054 🟢 GREEN: 確認即時處理能力
- [ ] T055 🔵 REFACTOR: 處理延遲優化

- [ ] T056 🔴 RED: 測試 30 分鐘穩定性於 `RkMeetingSessionStabilityTest.kt`
- [ ] T057 🟢 GREEN: 確認長時間穩定性
- [ ] T058 🔵 REFACTOR: 記憶體洩漏檢查

- [ ] T059 🔴 RED: 測試離線狀態仍可轉錄
- [ ] T060 🟢 GREEN: 確認離線運作無網路依賴
- [ ] T061 🔵 REFACTOR: 離線模式驗證

**Checkpoint**: US1 可獨立運作並通過所有測試

---

## Phase 4: User Story 2 - 語言自動/指定 (Priority: P2)

**Goal**: 支援 auto 與指定 Whisper 全語言

**Independent Test**: 在不同語言設定下可得到對應語言輸出

### 4.1 LanguageSetting 資料模型（TDD）

- [ ] T062 🔴 RED: 測試 `LanguageSetting`（auto/fixed mode）於 `LanguageSettingTest.kt`
- [ ] T063 🟢 GREEN: 建立/更新 `LanguageSetting` 於 `meeting-core/src/main/java/com/edgemeeting/core/model/LanguageSetting.kt`
- [ ] T064 🔵 REFACTOR: 檢視語言代碼驗證

### 4.2 語言設定傳遞（TDD）

- [ ] T065 🔴 RED: 測試 `AsrConfig.Whisper` 語言設定傳遞於 `RkMeetingSessionTest.kt`
- [ ] T066 🟢 GREEN: 實作語言設定從 `EngineConfig` 傳遞至 native
- [ ] T067 🟢 GREEN: 實作 `WhisperAsrEngine` 語言設定處理
- [ ] T068 🔵 REFACTOR: 檢視語言設定傳遞路徑

### 4.3 語言代碼輸出（TDD）

- [ ] T069 🔴 RED: 測試 `TranscriptSegment.languageCode` 正確輸出
- [ ] T070 🟢 GREEN: 實作語言偵測結果回傳於 `WhisperAsrEngine.cpp`
- [ ] T071 🔵 REFACTOR: 檢視語言代碼格式一致性

**Checkpoint**: US2 可獨立運作並通過所有測試

---

## Phase 5: User Story 3 - 錯誤與降級回饋 (Priority: P3)

**Goal**: 模型/資源不可用時回傳錯誤碼與訊息

**Independent Test**: 模型缺失時回傳可讀錯誤訊息與錯誤碼

### 5.1 模型缺失錯誤（TDD）

- [ ] T072 🔴 RED: 測試模型缺失時回傳 `ERR_MODEL_NOT_FOUND` 於 `RkMeetingSessionStateTest.kt`
- [ ] T073 🟢 GREEN: 實作 `ModelAssetManager` 檔案存在檢查
- [ ] T074 🟢 GREEN: 實作 native 模型缺失錯誤回傳
- [ ] T075 🔵 REFACTOR: 檢視錯誤訊息可讀性

### 5.2 模型載入失敗（TDD）

- [ ] T076 🔴 RED: 測試模型損壞時回傳 `ERR_MODEL_LOAD_FAILED`
- [ ] T077 🟢 GREEN: 實作 RKNN 載入錯誤捕獲於 `WhisperAsrEngine.cpp`
- [ ] T078 🔵 REFACTOR: 檢視錯誤傳遞完整性

### 5.3 降級與恢復（TDD）

- [ ] T079 🔴 RED: 測試錯誤後可重新 `prepare()` 恢復
- [ ] T080 🟢 GREEN: 實作錯誤狀態可恢復邏輯於 `RkMeetingSession.kt`
- [ ] T081 🔵 REFACTOR: 檢視狀態機完整性

**Checkpoint**: US3 可獨立運作並通過所有測試

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 打包、效能與回歸驗證

- [ ] T082 更新 CMake 連結 `librknnrt.so` 於 `meeting-engine/src/main/cpp/CMakeLists.txt`
- [ ] T083 更新 Gradle ABI/打包規則於 `meeting-engine/build.gradle.kts`
- [ ] T084 新增 `jniLibs/arm64-v8a` 放置說明於 `quickstart.md`
- [ ] T085 補上效能驗證說明於 `quickstart.md`
- [ ] T086 更新 README 說明統一介面使用方式

---

## Dependencies & Execution Order

### Phase Dependencies

- **Setup (Phase 1)**: 無依賴
- **Foundational (Phase 2)**: 依賴 Phase 1 完成，阻擋全部故事
- **User Stories (Phase 3-5)**: 依賴 Phase 2 完成，可依優先序進行
- **Polish (Phase 6)**: 依賴完成任一或全部故事

### User Story Dependencies

- **US1 (P1)**: 依賴 Foundational 完成
- **US2 (P2)**: 依賴 US1 的 AsrConfig 傳遞機制
- **US3 (P3)**: 依賴 US1 的錯誤回傳基礎

### Parallel Opportunities

- Phase 2 各切片可部分並行（無相依者）
- US2/US3 在 US1 完成後可並行

---

## Implementation Strategy

### TDD 執行原則

每個功能切片依序執行：
1. 🔴 **RED**: 寫測試，確認失敗
2. 🟢 **GREEN**: 最小實作，通過測試
3. 🔵 **REFACTOR**: 重構，確認測試仍通過

**切片完成後才進入下一個切片**

### MVP First (User Story 1 Only)

1. Phase 1 (T001-T004) → Phase 2 (T005-T022) 完成
2. 依序完成 US1 各切片 (3.1 → 3.2 → ... → 3.8)
3. **停止並驗證**：逐段輸出與 2 秒內產出

### Incremental Delivery

1. US1 (Phase 3) 完成並驗證
2. US2 (Phase 4) 完成並驗證
3. US3 (Phase 5) 完成並驗證
4. Phase 6 收尾

### 策略模式優勢

未來新增 Zipformer 或其他 ASR：
1. 新增 `AsrConfig.Zipformer` 子類（Kotlin）
2. 新增 `ZipformerAsrEngine` 實作（C++）
3. 更新 `native-lib.cpp` 的 engine factory
4. **無需修改 EngineBridge 或 RkMeetingSession**
