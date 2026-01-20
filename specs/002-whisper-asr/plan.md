# Implementation Plan: RK3588 Whisper 即時語音轉錄整合

**Branch**: `002-whisper-asr` | **Date**: 2026-01-20 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/002-whisper-asr/spec.md`

## Summary

在既有 `EngineBridge` 架構上，以**策略模式**整合 RK3588 Whisper ASR。透過統一的 `AsrConfig` 配置與 `EngineCallback` 回調，支援未來切換至其他 ASR 引擎（如 Zipformer）而不需修改上層 API。

## Architecture

### 策略模式設計

```
┌─────────────────────────────────────────────────────────┐
│                    EngineBridge                         │
│  init(config) → start() → stop() → release()           │
└─────────────────────────────────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│                   EngineConfig                          │
│  └─ asrConfig: AsrConfig?  ← null = 純錄音，無 ASR      │
└─────────────────────────────────────────────────────────┘
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
   ┌─────────────────┐        ┌─────────────────────┐
   │ AsrConfig.Whisper│       │ AsrConfig.Zipformer │
   │  - modelsPath    │       │  - modelsPath       │
   │  - language      │       │  - beamSize         │
   └─────────────────┘        └─────────────────────┘
```

### Native 端策略模式

```
┌─────────────────────────────────────────────────────────┐
│                   native-lib.cpp                        │
│  gAsrEngine = createAsrEngine(asrType)                  │
└─────────────────────────────────────────────────────────┘
                          │
            ┌─────────────┴─────────────┐
            ▼                           ▼
   ┌─────────────────┐        ┌─────────────────────┐
   │ WhisperAsrEngine│        │ ZipformerAsrEngine  │
   │  (RKNN impl)    │        │  (future)           │
   └─────────────────┘        └─────────────────────┘
```

### 資料流

```
AudioRecorder (C++)
      │ PCM samples
      ▼
AsrEngine.pushAudio()
      │ 內部累積/推論
      ▼
JNI callback: onTranscript(segment)
      │
      ▼
EngineCallback.onTranscript()
      │
      ▼
RkMeetingSession.transcriptFlow
```

## Technical Context

**Language/Version**: Kotlin 2.3.0、C++17 (NDK)  
**Primary Dependencies**: Kotlin Coroutines、AndroidX、JNI、RKNN Runtime (`librknnrt.so`)  
**Storage**: 檔案（`.rknn` 模型檔）  
**Model Deployment**:
- 模型檔路徑: `app/src/main/assets/models/whisper_encoder_base_20s.rknn`, `whisper_decoder_base_20s.rknn`
- Runtime 路徑: `context.filesDir/models/` (app-specific, 不需額外權限)
- Native 依賴: `meeting-engine/src/main/jniLibs/arm64-v8a/librknnrt.so`

**Testing**: JUnit、kotlinx-coroutines-test  
**Target Platform**: Android 13-15 (API 33-36) on RK3588, arm64-v8a  
**Project Type**: Android multi-module SDK  
**Performance Goals**: 逐段文字在語音段落結束後 2 秒內產出；20 秒語音在 20 秒內完成  
**Constraints**: 離線可用、不得阻塞音訊執行緒、記憶體/CPU 開銷可量測  
**Scale/Scope**: 單裝置會議即時轉錄、連續 30 分鐘穩定輸出

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- 測試先行 (TDD)：測試先失敗 → 最小實作 → 重構（RED → GREEN → REFACTOR），小步快跑切割最小可驗證單元
- 先用 zh_TW 註解說明「為什麼要這樣做」，再寫程式碼
- 每次輸出一個 function 或一段程式碼，需經驗證才能前進
- UI/UX 一致性：使用既有元件與樣式，避免未定義行為
- 效能預算：關鍵路徑有量測與目標，避免無理由開銷
- 程式碼品質：KISS/YAGNI/DRY/SOLID + 錯誤處理完整

## Project Structure

### Documentation (this feature)

```text
specs/002-whisper-asr/
├── plan.md
├── research.md
├── data-model.md
├── quickstart.md
├── contracts/
└── tasks.md
```

### Source Code (repository root)

```text
meeting-core/
└── src/main/java/com/edgemeeting/core/
    ├── MeetingSession.kt
    ├── model/
    │   ├── AsrConfig.kt        ← 新增：ASR 配置 sealed class
    │   ├── LanguageSetting.kt
    │   └── TranscriptSegment.kt

meeting-engine/
└── src/main/
    ├── cpp/
    │   ├── native-lib.cpp
    │   ├── asr/                 ← 新增：ASR 策略模式
    │   │   ├── AsrEngine.h
    │   │   └── WhisperAsrEngine.cpp
    │   └── whisper/             ← 新增：Whisper 專屬實作
    └── java/com/edgemeeting/engine/
        ├── bridge/
        │   ├── EngineBridge.kt  ← 修改：統一介面
        │   ├── EngineCallback.kt ← 新增：統一回調
        │   ├── EngineConfig.kt  ← 新增：統一配置
        │   └── JniEngineBridge.kt
        └── RkMeetingSession.kt
```

**Structure Decision**: 

1. **策略模式**：`AsrConfig` sealed class 確保型別安全，未來新增 ASR 只需加子類
2. **統一生命週期**：一個 `init()`/`release()` 管理音訊 + ASR
3. **回調模式**：`onTranscript` 與現有 `onAudioData` 一致，避免 polling
4. **Native 策略**：C++ `AsrEngine` 抽象類，Whisper/Zipformer 各自實作

## Complexity Tracking

無需新增複雜度例外。策略模式增加的抽象層級（AsrEngine 介面）換取未來 ASR 切換的靈活性，符合 SOLID 的開放封閉原則。
