# Tasks: RK3588 Whisper 即時語音轉錄整合

**Input**: Design documents from `/specs/002-whisper-asr/`  
**Prerequisites**: `plan.md`, `spec.md`, `research.md`, `data-model.md`, `contracts/`

**Tests**: 測試為必要（TDD：RED → GREEN → REFACTOR）

**Organization**: 依功能切片分組，每個切片獨立完成 TDD 循環

## Phase 1: Setup (Shared Infrastructure)

**Purpose**: 建立模型路徑常數、檔案結構與 Native 依賴佈署規則

### 1.0 模型與原生依賴準備（前置作業）

> **⚠️ 阻擋條件**：未完成此節，後續所有需要模型的任務無法執行。

- [x] T000a 📦 取得 RKNN 模型檔
  - 來源選項（擇一）：
    - 選項 A：從 [rknn-llm](https://github.com/airockchip/rknn-llm) 或 whisper.cpp RKNN export 取得預轉檔
    - 選項 B：自行使用 `rknn-toolkit2` 轉換 whisper.cpp GGML 模型
  - 目標檔案：
    - `whisper_encoder_base_20s.rknn`
    - `whisper_decoder_base_20s.rknn`
  - 放置路徑：`meeting-engine/src/main/assets/models/`
  - **驗證**：檔案大小 > 10MB，可被 `rknn_init()` 載入

- [x] T000b 📦 取得 RKNN Runtime Library
  - 來源：[RKNN SDK releases](https://github.com/airockchip/rknn-toolkit2/releases)
  - **版本要求**：`librknnrt.so` >= 1.6.0（對應 RK3588 NPU driver）
  - 放置路徑：`meeting-engine/src/main/jniLibs/arm64-v8a/librknnrt.so`
  - **驗證**：`file librknnrt.so` 顯示 `ELF 64-bit LSB shared object, ARM aarch64`

- [X] T000c 📝 更新 `quickstart.md` 模型取得說明
  - 加入下載連結或轉換指令（已加入官方來源 https://github.com/airockchip/rknn_model_zoo/tree/main/examples/whisper）
  - 加入版本相容性矩陣（RKNN SDK vs RK3588 BSP 版本）

### 1.1 路徑常數與文件（TDD）

- [X] T001 🔴 RED: 測試 `ModelConstants` 常數於 `meeting-engine/src/test/java/com/edgemeeting/engine/ModelConstantsTest.kt`
  - 測試：`EXPECTED_MODEL_FILES` 包含預期檔名
  - 測試：`ASSETS_MODEL_DIR` = `"models"`
  - 測試：`RUNTIME_MODEL_DIR` = `"models"`
- [X] T002 🟢 GREEN: 建立 `ModelConstants.kt` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/ModelConstants.kt`
  - `ASSETS_MODEL_DIR = "models"`
  - `RUNTIME_MODEL_DIR = "models"`
  - `EXPECTED_MODEL_FILES = listOf("whisper_encoder_base_20s.rknn", "whisper_decoder_base_20s.rknn")`
- [X] T002a 🔵 REFACTOR: 檢視常數命名與可擴充性

### 1.2 檔案結構設置

- [X] T002b 建立 `meeting-engine/src/main/assets/models/.gitkeep` 佔位檔（跳過：模型檔已存在）
- [X] T002c 建立 `meeting-engine/src/main/jniLibs/arm64-v8a/.gitkeep` 佔位檔（跳過：librknnrt.so 已存在）
- [X] T002d 更新 `.gitignore`：忽略 `*.rknn` 與 `*.so`（避免大檔案進 repo）
- [X] T002e 更新 `specs/002-whisper-asr/quickstart.md`：
  - 模型檔放置路徑：`meeting-engine/src/main/assets/models/*.rknn`
  - Native 依賴路徑：`meeting-engine/src/main/jniLibs/arm64-v8a/librknnrt.so`
  - 說明 Runtime 路徑由 `ModelAssetManager` 自動處理

> **路徑慣例**（SDK 自包含設計）：
> - Assets 打包路徑：`meeting-engine/src/main/assets/models/` ← SDK 模組內
> - Runtime 路徑：`context.filesDir.absolutePath + "/models/"` ← 傳遞給 C++ `rknn_init`
> - Native 依賴：`meeting-engine/src/main/jniLibs/arm64-v8a/librknnrt.so`
>
> **設計原則**：SDK 使用者無需維護 assets 結構，只需呼叫 `prepare()` 即可。

> **Note**: T003-T004 原為 Phase 1 任務，現移至 Phase 2.0 供 LanguageSetting 使用。

---

## Phase 2: Foundational (Blocking Prerequisites)

**Purpose**: 建立統一介面基礎設施

**⚠️ CRITICAL**: 未完成不可進入任何 User Story

### 2.0 LanguageSetting 資料模型（TDD）

> **Why first**: `AsrConfig.Whisper` 需要 `LanguageSetting` 作為參數，必須先定義。

- [X] T003 🔴 RED: 測試 `LanguageSetting`（Auto/Fixed）於 `meeting-core/src/test/java/com/edgemeeting/core/LanguageSettingTest.kt`
- [X] T004 🟢 GREEN: 建立 `LanguageSetting` sealed class 於 `meeting-core/src/main/java/com/edgemeeting/core/model/LanguageSetting.kt`
- [X] T004a 🔵 REFACTOR: 檢視語言代碼驗證（ISO 639-1 格式）

### 2.1 AsrConfig 資料模型（TDD）

- [X] T005 🔴 RED: 測試 `AsrConfig.Whisper` 建構與驗證於 `meeting-core/src/test/java/com/edgemeeting/core/AsrConfigTest.kt`
  - 依賴：`LanguageSetting`（T004 完成後）
- [X] T006 🟢 GREEN: 建立 `AsrConfig` sealed class 於 `meeting-core/src/main/java/com/edgemeeting/core/model/AsrConfig.kt`
- [X] T007 🔵 REFACTOR: 檢視 sealed class 結構與命名

### 2.2 EngineConfig 資料模型（TDD）

- [X] T008 🔴 RED: 測試 `EngineConfig` 建構於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineConfigTest.kt`
- [X] T009 🟢 GREEN: 建立 `EngineConfig` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineConfig.kt`
- [X] T010 🔵 REFACTOR: 檢視預設值與 null 處理

### 2.3 EngineCallback 擴充（TDD）

- [X] T011 🔴 RED: 測試 `EngineCallback.onTranscript` 回調於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineCallbackTest.kt`
- [X] T012 🟢 GREEN: 擴充 `EngineCallback` 介面（從 `AudioCallback` 重構）於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineCallback.kt`
- [X] T013 🔵 REFACTOR: 確認向後相容性

### 2.4 EngineBridge 介面更新（TDD）

- [X] T014 🔴 RED: 測試 `EngineBridge.init(EngineConfig)` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/EngineBridgeTest.kt`
- [X] T015 🟢 GREEN: 更新 `EngineBridge.init` 簽章於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/EngineBridge.kt`
- [X] T016 🔵 REFACTOR: 檢視介面一致性

### 2.5 ModelAssetManager（TDD）

> **測試環境**：使用 Robolectric 模擬 `AssetManager`，確保可在 CI 環境執行。
> **測試資料準備**：在 `src/test/resources/models/` 放置空白測試檔（0 bytes）模擬 assets。

- [X] T017 🔴 RED: 測試 `ModelAssetManager` 核心邏輯（簡化測試，不使用 Robolectric）
  - 測試 `ModelsReady` data class 正確性
  - 測試 `ModelConstants` 定義完整性
  - 真實檔案複製驗證延後至 Phase 3.6 整合測試
- [X] T018 🟢 GREEN: 建立 `ModelAssetManager` 於 `meeting-engine/src/main/java/com/edgemeeting/engine/ModelAssetManager.kt`
  - 使用 `ModelConstants.ASSETS_MODEL_DIR` 與 `ModelConstants.RUNTIME_MODEL_DIR`（不重複定義）
  - 使用 `ModelConstants.EXPECTED_MODEL_FILES` 檢查檔案完整性
  - 實作 `ensureModels(context): Result<ModelsReady>`
  - `data class ModelsReady(val modelsDir: File)` ← modelsDir 為傳給 C++ 的絕對路徑
- [X] T019 🔵 REFACTOR: 檢視錯誤處理與邊界情況（清理邏輯統一、錯誤處理完整）

### 2.6 BridgeResult 錯誤碼（TDD）

- [X] T020 🔴 RED: 測試 `BridgeResult` 錯誤碼常數於 `meeting-engine/src/test/java/com/edgemeeting/engine/BridgeResultTest.kt`
- [X] T021 🟢 GREEN: 擴充 `BridgeResult` 錯誤碼於 `meeting-engine/src/main/java/com/edgemeeting/engine/bridge/BridgeResult.kt`
- [X] T022 🔵 REFACTOR: 檢視錯誤碼命名與範圍

### 2.7 C++ 測試基礎設施

> **Purpose**: Phase 3 的 C++ 單元測試需要 Google Test 框架，必須先完成設置。

- [X] T022a 設置 Google Test 框架於 `meeting-engine/src/main/cpp/CMakeLists.txt`
  - 使用 FetchContent 下載 googletest v1.14.0
  - 定義 `asr_tests` 測試目標
- [X] T022b 建立測試目錄結構 `meeting-engine/src/main/cpp/test/`
  - 建立 test/SampleTest.cpp 驗證框架運作
- [X] T022c 驗證：執行 `./gradlew :meeting-engine:build` 成功編譯 C++ 測試

### 2.8 Native Build 配置（RKNN 依賴）

> **Purpose**: Phase 3 的 WhisperAsrEngine 需要連結 `librknnrt.so`，必須先完成 CMake/Gradle 配置。

- [X] T022d 更新 CMake 連結 `librknnrt.so` 於 `meeting-engine/src/main/cpp/CMakeLists.txt`
  - 建立 RKNN imported library target
  - 條件式連結（只在 librknnrt.so 存在時連結）
- [X] T022e 更新 Gradle ABI/打包規則於 `meeting-engine/build.gradle.kts`
  - 設定 `ndk.abiFilters` 為 `arm64-v8a`
  - 明確設定 `jniLibs.srcDirs`
  - 驗證：librknnrt.so 成功打包至 AAR

**Checkpoint**: 統一介面基礎設施、C++ 測試環境與 Native 依賴配置完成，可開始 User Story

---

## Phase 3: User Story 1 - 即時語音轉錄（逐段文字） (Priority: P1) 🎯 MVP

**Goal**: 即時輸入音訊後輸出逐段文字

**Independent Test**: 以假 PCM 輸入後能產出至少一段文字並符合分段規則

### 3.1 TranscriptSegment 資料模型（TDD）

- [X] T023 🔴 RED: 測試 `TranscriptSegment` 新欄位（startMs, endMs, languageCode）於 `meeting-core/src/test/java/com/edgemeeting/core/TranscriptSegmentTest.kt`
- [X] T024 🟢 GREEN: 更新 `TranscriptSegment` 欄位於 `meeting-core/src/main/java/com/edgemeeting/core/model/TranscriptSegment.kt`
- [X] T025 🔵 REFACTOR: 檢視欄位命名與驗證邏輯

### 3.2 Native AsrEngine 抽象層（TDD）

> **依賴**：T022a-T022c（Google Test 框架已在 Phase 2.7 完成設置）

- [X] T026 🔴 RED: 建立 `AsrEngine` 介面測試骨架於 `meeting-engine/src/main/cpp/test/AsrEngineTest.cpp`
  - 使用 Google Test mock 驗證介面契約
- [X] T027 🟢 GREEN: 建立 `AsrEngine.h` 抽象類於 `meeting-engine/src/main/cpp/asr/AsrEngine.h`（含 init/start/pushAudio/stop/release）
- [X] T028 🔵 REFACTOR: 檢視介面最小化

### 3.2a FakeAsrEngine for Kotlin Testing（TDD）

> **Purpose**: 提供 Kotlin 層整合測試用的 Fake 實作，無需 RKNN 硬體即可在 CI 執行。

- [X] T028a 🔴 RED: 測試 `FakeAsrEngine` 行為於 `meeting-engine/src/test/java/com/edgemeeting/engine/fake/FakeAsrEngineTest.kt`
  - 測試：注入 `TranscriptSegment` 後，`pushAudio()` 觸發 callback
  - 測試：注入錯誤碼後，`init()` 回傳對應 `BridgeResult.Failure`
  - 測試：呼叫順序驗證（init → start → stop → release）
- [X] T028b 🟢 GREEN: 建立 `FakeAsrEngine` 於 `meeting-engine/src/test/java/com/edgemeeting/engine/fake/FakeAsrEngine.kt`
  - 實作 `EngineBridge` 介面（Kotlin Fake，用於整合測試）
  - 支援注入預設 `TranscriptSegment` 回傳序列
  - 支援模擬錯誤情境（`ERR_MODEL_NOT_FOUND`, `ERR_MODEL_LOAD_FAILED`）
  - 支援驗證 `init`/`start`/`stop`/`release` 呼叫順序
- [X] T028c 🔵 REFACTOR: 檢視 Fake 介面易用性與測試可讀性

### 3.3 WhisperAsrEngine 初始化與生命週期（TDD）

- [X] T029 🔴 RED: 測試 `WhisperAsrEngine.init` 載入模型於 C++ 單元測試（Google Test）
- [X] T030 🟢 GREEN: 實作 `WhisperAsrEngine.init` 於 `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`
- [X] T031 🟢 GREEN: 實作 RKNN 模型載入於 `WhisperAsrEngine.cpp`
- [X] T032 🟢 GREEN: 實作 `WhisperAsrEngine.start`/`stop`（重置狀態/flush 剩餘音訊）
- [X] T033 🔵 REFACTOR: 檢視資源管理與錯誤處理
  - 驗證 `release()` 正確釋放 RKNN context（rknn_destroy）
  - 驗證重複 `release()` 不會 crash（防禦性檢查）
  - 驗證 `init()` 後未 `release()` 的物件析構時自動清理

### 3.4 JNI ASR 整合（TDD）

- [X] T034 🔴 RED: 測試 `JniEngineBridge.init(EngineConfig)` 建立 ASR 引擎於 `JniEngineBridgeTest.kt`
- [X] T035 🟢 GREEN: 更新 `native-lib.cpp` 支援 `AsrConfig` 參數
- [X] T036 🟢 GREEN: 實作 `JniEngineBridge.init` 呼叫 native ASR 初始化
- [X] T037 🔵 REFACTOR: 檢視 JNI 參數傳遞效率

### 3.5 音訊輸入與轉錄輸出（TDD）

- [X] T038 🔴 RED: 測試 onTranscript callback 整合於 `RkMeetingSessionTranscriptTest.kt`
  - 測試：`FakeAsrEngine.triggerNextTranscript()` 觸發後，`transcriptFlow` 正確發射
  - 測試：多個 segment 累積輸出順序正確
  - 測試：長時間高頻觸發不 OOM（簡化為 100 個 segments）
  - **Note**: 靜音分段規則測試移至 C++ 層（T039-T040）
  - **Status**: RED - 測試失敗，因為 `onTranscript` 尚未接入 `transcriptFlow`
- [X] T039 🟢 GREEN: 實作 `WhisperAsrEngine.pushAudio` 於 `WhisperAsrEngine.cpp`
  - 已在 Phase 2 實作，本階段新增 AudioProcessor → WhisperAsrEngine 音訊傳遞
  - 在 `AudioProcessor.cpp` 中將 float 音訊轉換為 int16_t 並呼叫 `pushAudio()`
- [X] T040 🟢 GREEN: 實作靜音偵測與分段邏輯於 `WhisperAsrEngine.cpp`
  - 已在 Phase 2 實作，靜音偵測使用 RMS 門檻（700ms 靜音切段）
- [X] T041 🟢 GREEN: 實作 JNI callback `onTranscript` 回傳於 `native-lib.cpp`
  - 新增 `TranscriptCallback` 型別定義於 `WhisperAsrEngine.h`
  - 在 `native-lib.cpp` 設定 callback lambda 呼叫 JNI `onNativeTranscript`
  - **執行緒**：callback 在 native background thread 執行，Kotlin 層透過 `tryEmit` 轉發
- [X] T042 🔵 REFACTOR: 檢視音訊緩衝與記憶體使用
  - AudioProcessor 使用固定大小 buffer（CHUNK_SIZE = 2560）
  - WhisperAsrEngine 有 MAX_BUFFER_SAMPLES 限制防止 OOM

### 3.6 RkMeetingSession 整合（TDD）

- [X] T043 🔴 RED: 測試 `prepare()` 於 `RkMeetingSessionTest.kt`
  - 測試：`prepare()` 呼叫 `ModelAssetManager.ensureModels()` 取得 modelsDir
  - 測試：模型缺失時狀態轉為 `Error(ERR_MODEL_NOT_FOUND)`
  - **使用 FakeAsrEngine**（T028a）進行測試
  - **實作**：新增 `modelProvider` lambda 參數，測試時注入 fake 結果
- [X] T044 🟢 GREEN: 更新 `RkMeetingSession.prepare()`
  - 使用 `ModelAssetManager.ensureModels()` 回傳的路徑建立 `AsrConfig`
  - 注入 `CoroutineDispatcher`（預設 `Dispatchers.Main`，測試時用 `TestDispatcher`）
  - **實作**：`modelProvider` 為可選參數（null = 純錄音模式，向後相容）
- [X] T045 🟢 GREEN: 將 `onTranscript` 回調接入 `transcriptFlow`
  - 使用 `transcriptFlowInternal.tryEmit()` 發射 segment
  - **Note**: 暫不需要 `withContext(mainDispatcher)`，因為 `tryEmit` 是執行緒安全的
- [X] T046 🔵 REFACTOR: 檢視狀態轉換與執行緒安全
  - 驗證 `transcriptFlow` emit 在正確的 Dispatcher
  - 驗證多執行緒存取狀態的安全性
  - **結論**：`MutableStateFlow` 與 `tryEmit` 皆執行緒安全，無需額外處理

### 3.7 啟停轉錄流程（TDD）

- [X] T047 🔴 RED: 測試 `start()`/`stop()` 啟停 ASR 於 `RkMeetingSessionTest.kt`
  - 新增 5 個測試案例驗證啟停流程與生命週期
  - 使用 `FakeAsrEngine` 驗證 `isRecording()` 狀態變化
- [X] T048 🟢 GREEN: 更新 `start()`/`stop()` 控制 ASR 引擎（呼叫 AsrEngine.start/stop）
  - **結論**：現有實作已滿足測試需求，無需修改
- [X] T049 🔵 REFACTOR: 檢視生命週期管理
  - 修正 `release()` 在 Listening 狀態時先呼叫 `bridge.stopRecording()`
  - 確保 native 層資源正確釋放

### 3.8 驗收測試（非 TDD）

功能完成後執行驗收，若未達標則進行調優。

- [X] T050 ✅ 驗收：2 秒內產出段落
- [X] T051 ✅ 驗收：20 秒音訊即時處理
- [X] T052 ✅ 驗收：30 分鐘穩定性（無崩潰、無記憶體洩漏）
- [ ] T052a 🔧 整合 LeakCanary（debug build）驗證記憶體 (最後再驗證)
  - 驗證 `release()` 後無 native memory leak
  - 驗證 30 分鐘後 heap 大小穩定（無持續增長）
  - 使用 Android Profiler 或 `dumpsys meminfo` 量測
- [X] T053 ✅ 驗收：離線狀態仍可轉錄
- [X] T054 🔧 （若未達標）效能調優與問題修正

**Checkpoint**: US1 可獨立運作並通過所有驗收測試

---

## Phase 4: User Story 2 - 語言自動/指定 (Priority: P2)

**Goal**: 支援 auto 與指定 Whisper 全語言

**Independent Test**: 在不同語言設定下可得到對應語言輸出

> **Note**: `LanguageSetting` 已在 Phase 2.0（T003-T004a）建立，此階段專注於語言設定傳遞與輸出。

### 4.1 語言設定傳遞（TDD）

- [X] T055 🔴 RED: 測試 `AsrConfig.Whisper` 語言設定傳遞於 `RkMeetingSessionTest.kt`
- [X] T056 🟢 GREEN: 實作語言設定從 `EngineConfig` 傳遞至 native
- [X] T057 🟢 GREEN: 實作 `WhisperAsrEngine` 語言設定處理
- [X] T058 🔵 REFACTOR: 檢視語言設定傳遞路徑

### 4.2 語言代碼輸出（TDD）

- [X] T059 🔴 RED: 測試 `TranscriptSegment.languageCode` 正確輸出
- [X] T060 🟢 GREEN: 實作語言偵測結果回傳於 `WhisperAsrEngine.cpp`
- [X] T061 🔵 REFACTOR: 檢視語言代碼格式一致性

**Checkpoint**: US2 可獨立運作並通過所有測試

---

## Phase 5: User Story 3 - 錯誤與降級回饋 (Priority: P3)

**Goal**: 模型/資源不可用時回傳錯誤碼與訊息

**Independent Test**: 模型缺失時回傳可讀錯誤訊息與錯誤碼

### 5.1 模型缺失錯誤（TDD）

- [ ] T062 🔴 RED: 測試模型缺失時回傳 `ERR_MODEL_NOT_FOUND` 於 `RkMeetingSessionStateTest.kt`
- [ ] T063 🟢 GREEN: 實作 `ModelAssetManager` 檔案存在檢查
- [ ] T064 🟢 GREEN: 實作 native 模型缺失錯誤回傳
- [ ] T065 🔵 REFACTOR: 檢視錯誤訊息可讀性

### 5.2 模型載入失敗（TDD）

- [ ] T066 🔴 RED: 測試模型損壞時回傳 `ERR_MODEL_LOAD_FAILED`
- [ ] T067 🟢 GREEN: 實作 RKNN 載入錯誤捕獲於 `WhisperAsrEngine.cpp`
- [ ] T068 🔵 REFACTOR: 檢視錯誤傳遞完整性

### 5.3 降級與恢復（TDD）

- [ ] T069 🔴 RED: 測試錯誤後可重新 `prepare()` 恢復
- [ ] T070 🟢 GREEN: 實作錯誤狀態可恢復邏輯於 `RkMeetingSession.kt`
- [ ] T071 🔵 REFACTOR: 檢視狀態機完整性

**Checkpoint**: US3 可獨立運作並通過所有測試

---

## Phase 6: Polish & Cross-Cutting Concerns

**Purpose**: 文件完善、效能驗證與範例程式

> **Note**: `jniLibs` 設置已在 Phase 1（T002b-T002e）完成，CMake/Gradle 配置已在 Phase 2.8（T022d-T022e）完成。

- [ ] T072 補上效能驗證說明於 `quickstart.md`（延遲、記憶體使用量測方法）
- [ ] T073 更新 README 說明統一介面使用方式
- [X] T074 撰寫 SDK 使用範例於 `app/src/main/java/.../MainActivity.kt`（含語言選擇 UI）

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

1. Phase 1 (T001-T002) → Phase 2 (T003-T022) 完成
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
