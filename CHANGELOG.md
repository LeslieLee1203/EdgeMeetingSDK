# 更新日誌 (CHANGELOG)

## [2026-02-03] - 放寬音訊時間限制至 16 秒

### 效能調整
- 將 Whisper ASR 音訊緩衝上限從 12 秒放寬至 16 秒
- 調整相關參數：
  - `MAX_SAMPLES`: 12s → 16s（最大緩衝）
  - `MAX_INFERENCE_SAMPLES`: 12s → 16s（推論前保護上限）
  - `HARD_LIMIT_SAMPLES`: 14s → 18s（Buffer 硬上限）
  - `KEEP_SAMPLES`: 11s → 15s（溢出時保留長度）

### 變更檔案
- `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

### 說明
- 允許 Whisper 處理更長的語句，減少強制截斷的情況
- 配合模型支援的 20 秒輸入上限，16 秒提供較佳的平衡點

---

## [2026-02-03] - Release Build 移除開發用 Log

### 效能優化
- 建立統一的 `Log.h` header，集中管理所有 C++ log 定義
- Release build 時自動移除開發用 log（LOGD/LOGI/LOGW），只保留：
  - `LOGE`：錯誤訊息（永遠啟用）
  - `LOG_RESULT`：重要結果輸出，如 Decoder Result（永遠啟用）
- 透過 CMake 的 `-DNDEBUG` flag 控制 log 啟用/停用

### 變更檔案
- 新增 `meeting-engine/src/main/cpp/Log.h`
- 修改 `meeting-engine/build.gradle.kts`：新增 debug/release buildType 的 CMake 設定
- 修改 `native-lib.cpp`、`AudioRecorder.h`、`AudioProcessor.cpp`、`WhisperAsrEngine.cpp`、`WhisperUtils.cpp`：改用統一的 `Log.h`

### 說明
- Debug build (`-O0 -g`)：所有 log 正常輸出
- Release build (`-DNDEBUG -O3`)：只輸出錯誤和 Decoder Result

---

## [2026-02-03] - 修正 Release Build 簽名配置

### 修正
- `app/build.gradle.kts` 新增 `signingConfigs` 區塊，讓 release build 使用 debug keystore 簽署
- 解決 Android Studio 錯誤：「The apk for your currently selected variant cannot be signed」

### 說明
- 目前使用 debug keystore 作為開發測試用途
- 正式發布時需替換為正式的 release keystore

---

## [2026-01-30] - 新增專案架構文件

### 文件
- 新增 `docs/ARCHITECTURE-OVERVIEW.md`：專案架構概述
  - 包含完整資料流程圖（從麥克風到 UI）
  - 系統架構圖與模組結構
  - 執行緒模型與狀態機
  - 關鍵技術特點與效能指標
  - SDK 整合範例程式碼

---

## [2026-01-27] - 修正單元測試回調命名衝突

### 修正
- `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt` 避免 setCallback 名稱衝突

---

## [2026-01-27] - 移除 Auto 語言與 UI 文字英文化

### 調整
- 移除 `LanguageSetting.Auto` 與相關測試/文件描述
- `app/src/main/java/com/edgemeeting/sdk/MainActivity.kt` 顯示文字全面改為英文
- 更新規格與研究文件以反映僅支援 en/zh

---

## [2026-01-27] - RKNN Whisper 預設語言改為英文

### 調整
- `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` 預設語言改為 `LanguageSetting.Fixed("en")`
- `app/src/main/java/com/edgemeeting/sdk/MainActivity.kt` 語言選項移除 auto，預設為英文
- 更新 `CLAUDE.md`、`README.md`、`specs/002-whisper-asr/data-model.md` 說明

### 測試
- `app/src/test/java/com/edgemeeting/sdk/LanguageOptionsTest.kt` 更新期望語言清單

---

## [2026-01-27] - 修正 native error 狀態與測試一致性

### 修正
- `meeting-engine/src/main/java/com/edgemeeting/engine/RkMeetingSession.kt` native error 時停止錄音並更新 Error 狀態
- `meeting-engine/src/main/cpp/native-lib.cpp` 無 ASR 時不建立清理順序，避免測試不一致

### 測試
- `meeting-engine/src/test/java/com/edgemeeting/engine/RkMeetingSessionTest.kt` 新增 onError 狀態與 stopRecording 測試

---

## [2026-01-27] - 語言選項與 Native 支援對齊

### 修正
- `app/src/main/java/com/edgemeeting/sdk/MainActivity.kt` 語言選項僅保留 auto/zh/en，避免與 Native 支援不一致

### 測試
- `app/src/test/java/com/edgemeeting/sdk/LanguageOptionsTest.kt` 新增語言選項對齊測試

---

## [2026-01-27] - 修正純錄音模式切換的 ASR 清理

### 修正
- `meeting-engine/src/main/cpp/native-lib.cpp` 純錄音模式初始化時釋放 ASR，並清除處理器 ASR 參考

### 測試
- `meeting-engine/src/main/cpp/test/SampleTest.cpp` 新增純錄音模式 ASR 清理順序測試

---

## [2026-01-27] - 修正 C++ 測試連結錯誤

### 修正
- `meeting-engine/src/main/cpp/CMakeLists.txt` 將 `native-lib.cpp` 納入 `asr_tests`，避免測試 helper 未連結

---

## [2026-01-27] - 補充 Gradle 執行 JAVA_HOME 設定

### 文件
- `CLAUDE.md` 補充先確認 `JAVA_HOME` 再執行 Gradle 的說明與範例

---

## [2026-01-27] - Native 資源釋放順序修正

### 修正
- `meeting-engine/src/main/cpp/native-lib.cpp` 調整釋放順序，避免 ASR 釋放早於處理器停止
- `nativeRelease()` 釋放 JNI callback GlobalRef，避免記憶體洩漏
- `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp` 失敗時補上 RKNN 資源清理
- `meeting-engine/src/main/cpp/AudioProcessor.cpp` 啟動前檢查 JVM 與 callback 有效性

### 測試
- `meeting-engine/src/main/cpp/test/SampleTest.cpp` 新增釋放順序測試
- `meeting-engine/src/main/cpp/test/SampleTest.cpp` 新增 RKNN 失敗清理測試
- `meeting-engine/src/main/cpp/test/SampleTest.cpp` 新增處理器啟動條件測試

---

## [2026-01-22] - Phase 6 收尾與記憶體驗證

### 新增
- `app` 模組加入 LeakCanary（debug）以驗證釋放後記憶體穩定性

### 文件
- `specs/002-whisper-asr/quickstart.md` 補上延遲與記憶體量測方法，新增 LeakCanary 驗證步驟
- `README.md` 補充統一介面（EngineBridge/EngineConfig/AsrConfig）使用方式與錯誤處理範例

---

## [2026-01-22] - 暫緩 Phase 5 任務

### 調整
- `specs/002-whisper-asr/tasks.md` 移除 Phase 5（US3）任務清單並加註暫緩說明
- 更新 Phase 依賴與交付順序，對齊目前專案階段

---

## [2026-01-21] - Phase 3.7 啟停轉錄流程完成

### T047-T049 TDD 完成 ✓

**T047 RED/GREEN: 測試 `start()`/`stop()` 啟停 ASR**
- 新增 `RkMeetingSessionTest.kt` 五個測試案例：
  - `start should call bridge startRecording and engine should be recording`
  - `stop should call bridge stopRecording and engine should stop recording`
  - `start while already listening should not change state`
  - `should be able to restart after stop`
  - `start after release should result in error`
- 使用 `FakeAsrEngine` 驗證 `isRecording()` 狀態變化
- **結論**：現有實作已滿足測試需求

**T049 REFACTOR: 檢視生命週期管理**
- 修正 `release()` 在 `Listening` 狀態時先呼叫 `bridge.stopRecording()`
- 確保 native 層資源正確釋放，避免資源洩漏

### 狀態轉換圖

```
Idle → (prepare) → Preparing → Ready / Error
Ready → (start) → Listening
Listening → (stop) → Ready
Any → (release) → Idle
```

---

## [2026-01-21] - Phase 3.6 RkMeetingSession 整合完成

### T043-T044 TDD 完成 ✓

**T043 RED: 測試 `prepare()` 整合 `ModelAssetManager`**
- 新增 `RkMeetingSessionTest.kt` 三個測試案例：
  - `prepare should use modelProvider to build AsrConfig with modelsPath`
  - `prepare should become Error with ERR_MODEL_NOT_FOUND when modelProvider fails`
  - `prepare without modelProvider should use audio-only mode for backward compatibility`

**T044 GREEN: 更新 `RkMeetingSession.prepare()`**
- 新增 `modelProvider: (() -> Result<ModelsReady>)?` 參數（可為 null，向後相容）
- `prepare()` 邏輯：
  - 若 `modelProvider` 存在且成功：使用回傳的 `modelsDir` 建立 `AsrConfig.Whisper`
  - 若 `modelProvider` 存在但失敗：狀態轉為 `Error(ERR_MODEL_NOT_FOUND)`
  - 若 `modelProvider` 為 null：使用純錄音模式（`asrConfig = null`）

**T046 REFACTOR: 檢視狀態轉換與執行緒安全**
- 驗證 `MutableStateFlow` 與 `tryEmit` 皆執行緒安全
- 程式碼結構符合 KISS 原則，無需額外重構

### 設計決策

**為什麼使用 lambda 注入而非直接依賴 `ModelAssetManager`**：
1. 可測試性：測試時無需 Android `Context`，直接注入 fake 結果
2. 彈性：未來可支援其他模型來源（網路下載、SD 卡等）
3. 關注分離：`RkMeetingSession` 不需知道模型如何準備

---

## [2026-01-21] - Phase 3.5 T038-T042 完成

### T039-T042 C++ 層實作 ✓

**T039: AudioProcessor → WhisperAsrEngine 音訊傳遞**
- 修改 `AudioProcessor.h` 新增 `setAsrEngine()` 方法
- 修改 `AudioProcessor.cpp` 在 workerLoop 中將 float 轉換為 int16_t 並呼叫 `pushAudio()`

**T040: 靜音偵測與分段邏輯**
- 已在 Phase 2 實作於 `WhisperAsrEngine.cpp`
- 使用 RMS 門檻偵測靜音，700ms 靜音自動切段

**T041: JNI callback onTranscript**
- 新增 `TranscriptCallback` 型別定義於 `WhisperAsrEngine.h`
- 在 `native-lib.cpp` 設定 callback lambda 呼叫 JNI `onNativeTranscript`
- 處理執行緒 attach/detach 確保 JNI 呼叫安全

**T042: REFACTOR 音訊緩衝與記憶體**
- AudioProcessor 使用固定大小 buffer（CHUNK_SIZE = 2560）
- WhisperAsrEngine 有 MAX_BUFFER_SAMPLES（10 分鐘）限制防止 OOM

---

## [2026-01-21] - Phase 3.5 T038/T045 TDD 完成

### T045 GREEN 階段完成 ✓

**onTranscript callback 接入 transcriptFlow**
- 修改 `RkMeetingSession.kt` 的 `onTranscript` callback
- 使用 `transcriptFlowInternal.tryEmit(listOf(segment))` 發射 segment
- 57 個測試全部通過

### T038 RED 階段完成 ✓

**onTranscript callback 整合測試**
- 新增 `RkMeetingSessionTranscriptTest.kt` 三個測試案例：
  - `onTranscript callback should emit to transcriptFlow`
  - `multiple onTranscript callbacks should emit in order`
  - `high frequency onTranscript should not cause memory issues`
- 使用 `MarkedTranscriptGenerator` 區分來自 generator 和 callback 的 segments

### 測試環境修正

- 修正 `build.gradle.kts` 加入 `testOptions.unitTests.isReturnDefaultValues = true`
  - 解決 Android SDK 類別（如 `android.util.Log`）在 JVM 單元測試中無法使用的問題

### 任務調整

- T038 測試項目調整：
  - 移除「靜音分段規則」測試（屬於 C++ 層責任，移至 T039-T040）
  - 改為測試 `FakeAsrEngine.triggerNextTranscript()` → `transcriptFlow` 整合

---

## [2026-01-20] - 規格一致性修正

### 交叉文件一致性修正

**CRITICAL 修正**
- 修正 `quickstart.md` 模型路徑錯誤：`app/src/main/assets/models/` → `meeting-engine/src/main/assets/models/`
  - 與 `tasks.md`、`plan.md` 保持一致

**HIGH 修正**
- 調整任務順序：將 CMake/Gradle 配置從 Phase 6 移至 Phase 2.8
  - 新增 T022d：CMake 連結 `librknnrt.so`
  - 新增 T022e：Gradle ABI/打包規則
  - Phase 6 任務重新編號：T072-T074（原 T074-T076）
  - 原因：Phase 3 的 WhisperAsrEngine 需要先完成 Native 依賴配置

---

## [2026-01-20]

### 規格分析與修正（Specification Analysis）

**CRITICAL 修正**
- 新增 Phase 1.0 模型與原生依賴準備任務（T000a-T000c）
  - T000a：取得 RKNN 模型檔（含來源選項與驗證標準）
  - T000b：取得 RKNN Runtime Library（版本要求 >= 1.6.0）
  - T000c：更新 quickstart.md 模型取得說明
- 修正 T028a TDD 順序違規，補上 RED 階段測試任務
  - T028a 改為 🔴 RED：測試 FakeAsrEngine 行為
  - T028b 改為 🟢 GREEN：建立 FakeAsrEngine 實作
  - 新增 T028c 🔵 REFACTOR：檢視 Fake 介面易用性

**文件澄清**
- `data-model.md`：補充 TranscriptionSession 實作澄清（由 MeetingState 涵蓋）

### 架構優化（Specification Analysis）

**SDK 自包含設計**
- 將模型 assets 從 `app/src/main/assets/models/` 改至 `meeting-engine/src/main/assets/models/`
- 設計原則：SDK 使用者無需維護 assets 結構，降低耦合度

**Phase 1 重構**
- 新增 T001-T002a：`ModelConstants.kt` TDD 任務（定義模型檔名、路徑常數）
- 新增 T002b-T002e：檔案結構設置（`.gitkeep`、`.gitignore`、`quickstart.md` 更新）
- 明確列出模型檔名：`whisper_encoder_base_20s.rknn`, `whisper_decoder_base_20s.rknn`

**Phase 2 重構**
- T017-T019 ModelAssetManager 改為引用 `ModelConstants`，不重複定義常數
- 補充 Robolectric 測試資料準備策略（`src/test/resources/models/`）
- 新增 Phase 2.7 C++ 測試基礎設施（T022a-T022c）：Google Test 框架設置提前至 Phase 2

**Phase 3 調整**
- T026 移除重複的 Google Test 設置，改為引用 Phase 2.7 完成的基礎設施

**Phase 6 整併**
- 移除與 Phase 1 重複的 `jniLibs` 說明任務
- 重新編號 T072-T076

**文件更新**
- `plan.md`：更新 Model Deployment 路徑，標註 SDK 自包含設計
- `data-model.md`：新增 `ModelConstants` 定義，補充 `ModelsReady.modelsDir` 用途說明

### 架構優化（資深審查）
- 新增 Phase 2.0 LanguageSetting（T003-T004a），解決 AsrConfig 依賴衝突。
- 新增 T028a FakeAsrEngine，支援 Kotlin 層整合測試（無需 RKNN 硬體）。
- 新增 T052a LeakCanary 整合任務，驗證記憶體穩定性。
- 在 T017 標註 Robolectric 測試環境，確保 CI 可執行。
- 在 T026 標註 Google Test 框架設置需求。
- 在 T033 新增 RKNN context 資源釋放驗證。
- 在 T038 補充靜音邊界測試案例（連續靜音、長時間語音）。
- 在 T041 標註執行緒模型（native background thread）。
- 在 T044-T046 新增 CoroutineDispatcher 注入說明。
- 重新編號 Phase 4-6 任務（T055-T076），對齊 LanguageSetting 移動後的結構。

### 調整
- 合併 `tasks.md` Phase 1 任務 T001-T003 為 T001，簡化模型設置任務描述。
- 新增路徑慣例區塊於 `tasks.md` Phase 1，明確定義 assets/runtime/native 路徑。
- 擴充 `tasks.md` T017-T019 ModelAssetManager 任務描述，明確回傳型別 `Result<ModelsReady>`。
- 擴充 `tasks.md` T043-T044 RkMeetingSession 整合任務，明確與 ModelAssetManager 的互動。
- 新增 `data-model.md` ModelsReady 資料類別定義。

### 新增
- 新增 Whisper ASR 整合規格與需求檢核清單（`specs/002-whisper-asr`）。
- 新增模型部署策略：assets 打包 + 複製至 app-specific storage。
- 新增 Whisper ASR 實作計劃與研究、資料模型、合約、快速開始文件（`specs/002-whisper-asr`）。
- 新增 Whisper ASR 任務拆解（`specs/002-whisper-asr/tasks.md`）。

### 架構重構（策略模式）
- 採用策略模式統一 ASR 介面，支援未來切換至 Zipformer 等其他 ASR 引擎。
- 新增 `AsrConfig` sealed class（Kotlin）作為 ASR 配置抽象。
- 新增 `EngineConfig` 統一引擎配置（含可選 `asrConfig`）。
- 擴充 `EngineCallback` 介面新增 `onTranscript` 回調（取代 polling 模式）。
- 設計 Native 端 `AsrEngine` 抽象類與 `WhisperAsrEngine` 實作。
- 移除獨立的 `initWhisper`/`releaseWhisper` API，整合至現有 `EngineBridge` 生命週期。

### 調整
- 更新專案憲章，補強 TDD 流程為 RED → GREEN → REFACTOR 與小步快跑要求。
- 更新 Speckit 計畫與任務模板，對齊 TDD 小步快跑與重構步驟。
- 更新專案憲章與模板，註解必須使用台灣繁體中文（zh_TW）。
- 更新 Whisper ASR 規格分段規則與澄清紀錄（`specs/002-whisper-asr/spec.md`）。
- 更新 Whisper ASR 規格語言支援範圍（`specs/002-whisper-asr/spec.md`）。
- 更新 Whisper ASR 規格語言標記回傳方式（`specs/002-whisper-asr/spec.md`）。
- 更新 Whisper ASR 規格段落時間欄位（`specs/002-whisper-asr/spec.md`）。
- 更新 Whisper ASR 規格錯誤回傳內容（`specs/002-whisper-asr/spec.md`）。
- 更新 Cursor agent context（`.cursor/rules/specify-rules.mdc`）。
- 調整 Whisper ASR 任務拆解以補齊離線與效能驗證（`specs/002-whisper-asr/tasks.md`）。
- 更新模型路徑假設與部署策略於 `spec.md`、`plan.md`、`contracts/sdk.md`、`quickstart.md`。
- 更新 T016 任務描述以包含 `ModelAssetManager` 整合。
- 重構 Phase 2 任務順序：測試先於實作（T008 → T007, T006b → T006a），符合 TDD RED → GREEN 流程。
- 為所有 User Story 新增 🔴🟢🔵 TDD 階段標記與 REFACTOR 步驟。
- 拆分 T016 為 T016/T016a 以符合最小可驗證單元原則。
- 調整 Whisper ASR 任務以明確對齊模型載入與啟停流程（`specs/002-whisper-asr/tasks.md`）。
- 重構 Phase 3-5 任務結構：依功能切片組織，每個切片獨立完成 RED → GREEN → REFACTOR 循環。
- 重新編號任務 T001-T085，依執行順序排列。
- 全面重構 `plan.md`：新增策略模式架構圖、Native 端設計、資料流說明。
- 全面重構 `contracts/sdk.md`：定義統一介面（EngineConfig、EngineCallback、AsrEngine）。
- 全面重構 `data-model.md`：新增 AsrConfig、EngineConfig、錯誤碼定義。
- 全面重構 `tasks.md`：依策略模式架構重新組織任務，新增 Native AsrEngine 抽象層任務。

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
- 新增 Phase 0 MVP 字幕流工作紀錄。

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
