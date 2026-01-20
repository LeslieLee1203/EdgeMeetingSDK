# Tasks: RK3588 Whisper 即時語音轉錄整合

**Input**: Design documents from `/specs/002-whisper-asr/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 測試為必要（TDD：RED → GREEN → REFACTOR）

**Organization**: 依 User Story 分組，確保每個故事可獨立驗證

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立基本路徑與模型檔佈署規則

- [ ] T001 更新模型檔放置說明到 `specs/002-whisper-asr/quickstart.md`
- [ ] T002 確認 `.rknn` 模型與 `librknnrt.so` 佈署規則文件化於 `specs/002-whisper-asr/quickstart.md`
- [ ] T003 定義模型路徑慣例：assets 打包路徑 `assets/models/`，runtime 路徑 `filesDir/models/`
- [ ] T004 建立 `app/src/main/assets/models/.gitkeep` 佔位檔

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 先打通 JNI/Native 骨架與錯誤回傳型別

**⚠️ CRITICAL**: 未完成不可進入任何 User Story

### 2.1 純骨架（無邏輯，免測試）

- [ ] T005 建立 `meeting-engine/src/main/cpp/whisper/` 目錄與空殼檔案 `RknnWhisperEngine.{h,cpp}`
- [ ] T006 建立 JNI 介面骨架於 `meeting-engine/src/main/cpp/native-lib.cpp`（僅宣告、不實作）
- [ ] T007 建立 Kotlin 端介面骨架於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt`
- [ ] T008 建立 JNI 端實作骨架於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/JniEngineBridge.kt`

### 2.2 BridgeResult（TDD：RED → GREEN → REFACTOR）

- [ ] T009 🔴 RED: 建立 `BridgeResultTest` 測試於 `meeting-engine/src/test/java/com/edgemeeting/engine/BridgeResultTest.kt`（測試先寫，預期失敗）
- [ ] T010 🟢 GREEN: 建立 `BridgeResult` 最小實作於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/BridgeResult.kt`（通過測試）
- [ ] T011 🔵 REFACTOR: 檢視 `BridgeResult` 命名與結構，必要時重構

### 2.3 ModelAssetManager（TDD：RED → GREEN → REFACTOR）

- [ ] T012 🔴 RED: 建立 `ModelAssetManagerTest` 測試於 `meeting-engine/src/test/java/com/edgemeeting/engine/ModelAssetManagerTest.kt`（測試先寫，預期失敗）
- [ ] T013 🟢 GREEN: 建立 `ModelAssetManager` 最小實作於 `meeting-engine/src/main/java/com/edgemeeting/engine/ModelAssetManager.kt`（通過測試）
- [ ] T014 🔵 REFACTOR: 檢視 `ModelAssetManager` 錯誤處理與邊界情況，必要時重構

**Checkpoint**: JNI/Bridge/Native skeleton 完成，可開始 User Story

---

## Phase 3: User Story 1 - 即時語音轉錄（逐段文字） (Priority: P1) 🎯 MVP

**Goal**: 即時輸入音訊後輸出逐段文字

**Independent Test**: 以假 PCM 輸入後能產出至少一段文字並符合分段規則

### 3.1 TranscriptSegment 資料模型（TDD）

- [ ] T015 🔴 RED: 測試 `TranscriptSegment` 欄位（text, startMs, endMs, languageCode）於 `meeting-core/src/test/java/com/edgemeeting/core/TranscriptSegmentTest.kt`
- [ ] T016 🟢 GREEN: 更新 `TranscriptSegment` 欄位於 `meeting-core/src/main/java/com/edgemeeting/core/model/TranscriptSegment.kt`
- [ ] T017 🔵 REFACTOR: 檢視欄位命名與驗證邏輯

### 3.2 模型載入流程（TDD）

- [ ] T018 🔴 RED: 測試 `prepare()` 呼叫 `ensureModels` 並傳遞路徑於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T019 🟢 GREEN: 實作 `ModelAssetManager.ensureModels(context)` 呼叫於 `RkMeetingSession.kt`
- [ ] T020 🟢 GREEN: 實作 `filesDir/models` 路徑傳遞給 native 於 `RkMeetingSession.kt`
- [ ] T021 🔵 REFACTOR: 檢視模型載入錯誤處理

### 3.3 Native Engine 初始化（TDD）

- [ ] T022 🔴 RED: 測試 `initWhisper`/`releaseWhisper` JNI 呼叫於 `meeting-engine/src/test/java/com/edgemeeting/engine/JniEngineBridgeTest.kt`
- [ ] T023 🟢 GREEN: 實作 native engine 載入與釋放於 `meeting-engine/src/main/cpp/whisper/RknnWhisperEngine.cpp`
- [ ] T024 🟢 GREEN: 實作 `initWhisper`/`releaseWhisper` JNI 串接於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T025 🟢 GREEN: 實作 `JniEngineBridge.initWhisper`/`releaseWhisper` 於 `JniEngineBridge.kt`
- [ ] T026 🔵 REFACTOR: 檢視資源釋放與生命週期管理

### 3.4 音訊輸入與轉錄輸出（TDD）

- [ ] T027 🔴 RED: 測試逐段分段規則（靜音 >= 700ms）於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTranscriptTest.kt`
- [ ] T028 🟢 GREEN: 實作 `pushAudio` JNI 串接於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T029 🟢 GREEN: 實作 `pollTranscript` JNI 串接於 `meeting-engine/src/main/cpp/native-lib.cpp`
- [ ] T030 🟢 GREEN: 實作 `JniEngineBridge.pushAudio`/`pollTranscript` 於 `JniEngineBridge.kt`
- [ ] T031 🔵 REFACTOR: 檢視音訊緩衝與記憶體使用

### 3.5 啟停轉錄流程（TDD）

- [ ] T032 🔴 RED: 測試 `start()`/`stop()` 啟停轉錄於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T033 🟢 GREEN: 實作 `start()`/`stop()` 流程於 `RkMeetingSession.kt`
- [ ] T034 🟢 GREEN: 將逐段結果接入 `transcriptFlow` 於 `RkMeetingSession.kt`
- [ ] T035 🔵 REFACTOR: 檢視狀態轉換與執行緒安全

### 3.6 效能與穩定性驗證（TDD）

- [ ] T036 🔴 RED: 測試 2 秒內產出段落於 `RkMeetingSessionTranscriptTest.kt`
- [ ] T037 🟢 GREEN: 調整實作以符合 2 秒產出要求
- [ ] T038 🔵 REFACTOR: 效能瓶頸分析與優化

- [ ] T039 🔴 RED: 測試 20 秒音訊 20 秒內完成於 `RkMeetingSessionTranscriptTest.kt`
- [ ] T040 🟢 GREEN: 調整實作以符合即時處理要求
- [ ] T041 🔵 REFACTOR: 處理延遲分析

- [ ] T042 🔴 RED: 測試 30 分鐘穩定性於 `RkMeetingSessionStabilityTest.kt`
- [ ] T043 🟢 GREEN: 調整實作以符合長時間穩定要求
- [ ] T044 🔵 REFACTOR: 記憶體洩漏檢查

- [ ] T045 🔴 RED: 測試離線狀態仍可轉錄於 `RkMeetingSessionTest.kt`
- [ ] T046 🟢 GREEN: 確認離線運作無網路依賴
- [ ] T047 🔵 REFACTOR: 離線模式驗證完整性

**Checkpoint**: US1 可獨立運作並通過所有測試

---

## Phase 4: User Story 2 - 語言自動/指定 (Priority: P2)

**Goal**: 支援 auto 與指定 Whisper 全語言

**Independent Test**: 在不同語言設定下可得到對應語言輸出

### 4.1 LanguageSetting 資料模型（TDD）

- [ ] T048 🔴 RED: 測試 `LanguageSetting`（auto/fixed mode）於 `meeting-core/src/test/java/com/edgemeeting/core/LanguageSettingTest.kt`
- [ ] T049 🟢 GREEN: 新增 `LanguageSetting` 型別於 `meeting-core/src/main/java/com/edgemeeting/core/model/LanguageSetting.kt`
- [ ] T050 🔵 REFACTOR: 檢視語言代碼驗證邏輯

### 4.2 語言設定傳遞（Kotlin → JNI）（TDD）

- [ ] T051 🔴 RED: 測試語言設定傳遞於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T052 🟢 GREEN: 調整 `EngineBridge` 介面新增語言參數於 `EngineBridge.kt`
- [ ] T053 🟢 GREEN: 調整 `JniEngineBridge` 傳遞語言設定於 `JniEngineBridge.kt`
- [ ] T054 🔵 REFACTOR: 檢視參數傳遞一致性

### 4.3 語言設定處理（JNI → Native）（TDD）

- [ ] T055 🔴 RED: 測試語言代碼輸出於 `TranscriptSegmentTest.kt`
- [ ] T056 🟢 GREEN: 調整 JNI 端語言設定處理於 `native-lib.cpp`
- [ ] T057 🟢 GREEN: 調整 native engine 語言設定處理於 `RknnWhisperEngine.cpp`
- [ ] T058 🔵 REFACTOR: 檢視語言設定傳遞路徑完整性

**Checkpoint**: US2 可獨立運作並通過所有測試

---

## Phase 5: User Story 3 - 錯誤與降級回饋 (Priority: P3)

**Goal**: 模型/資源不可用時回傳錯誤碼與訊息

**Independent Test**: 模型缺失時回傳可讀錯誤訊息與錯誤碼

### 5.1 模型缺失錯誤（TDD）

- [ ] T059 🔴 RED: 測試模型缺失時回傳錯誤於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionStateTest.kt`
- [ ] T060 🟢 GREEN: 實作 native 模型缺失錯誤回傳於 `RknnWhisperEngine.cpp`
- [ ] T061 🟢 GREEN: 實作 JNI 錯誤轉譯於 `native-lib.cpp`
- [ ] T062 🔵 REFACTOR: 檢視錯誤訊息可讀性

### 5.2 錯誤碼與訊息傳遞（TDD）

- [ ] T063 🔴 RED: 測試錯誤碼與訊息欄位於 `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt`
- [ ] T064 🟢 GREEN: 擴充 `BridgeResult` 錯誤碼表於 `BridgeResult.kt`
- [ ] T065 🟢 GREEN: 實作 `RkMeetingSession` 錯誤狀態回傳於 `RkMeetingSession.kt`
- [ ] T066 🔵 REFACTOR: 檢視錯誤碼一致性與文件化

### 5.3 降級與恢復（TDD）

- [ ] T067 🔴 RED: 測試錯誤後可重新 `prepare()` 恢復於 `RkMeetingSessionStateTest.kt`
- [ ] T068 🟢 GREEN: 實作錯誤狀態可恢復邏輯於 `RkMeetingSession.kt`
- [ ] T069 🔵 REFACTOR: 檢視狀態機完整性

**Checkpoint**: US3 可獨立運作並通過所有測試

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 打包、效能與回歸驗證

- [ ] T070 更新 CMake 連結 `librknnrt.so` 於 `meeting-engine/src/main/cpp/CMakeLists.txt`
- [ ] T071 更新 Gradle ABI/打包規則於 `meeting-engine/build.gradle.kts`
- [ ] T072 新增 `jniLibs/arm64-v8a` 放置說明於 `specs/002-whisper-asr/quickstart.md`
- [ ] T073 補上效能驗證說明於 `specs/002-whisper-asr/quickstart.md`

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

- Phase 2.1 骨架任務可並行
- US2/US3 若不互相影響可由不同人並行

---

## Implementation Strategy

### TDD 執行原則

每個功能切片依序執行：
1. 🔴 **RED**: 寫測試，確認失敗
2. 🟢 **GREEN**: 最小實作，通過測試
3. 🔵 **REFACTOR**: 重構，確認測試仍通過

**切片完成後才進入下一個切片**

### MVP First (User Story 1 Only)

1. Phase 1 (T001-T004) → Phase 2 (T005-T014) 完成
2. 依序完成 US1 各切片 (3.1 → 3.2 → 3.3 → 3.4 → 3.5 → 3.6)
3. **停止並驗證**：逐段輸出與 2 秒內產出

### Incremental Delivery

1. US1 (Phase 3) 完成並驗證
2. US2 (Phase 4) 完成並驗證
3. US3 (Phase 5) 完成並驗證
4. Phase 6 收尾
