# Implementation Plan: RK3588 Whisper 即時語音轉錄整合

**Branch**: `002-whisper-asr` | **Date**: 2026-01-20 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/002-whisper-asr/spec.md`

## Summary

在既有 Phase 0 假字串流程上，加入 RK3588 Whisper ASR JNI 整合，產出逐段文字，支援自動/指定語言與離線運作，錯誤回傳包含錯誤碼與訊息，並以靜音 >= 700ms 作為分段規則。

## Technical Context

**Language/Version**: Kotlin 2.3.0、C++17 (NDK)  
**Primary Dependencies**: Kotlin Coroutines、AndroidX、JNI、RKNN Runtime (`librknnrt.so`)  
**Storage**: 檔案（`.rknn` 模型檔）  
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
    ├── model/LanguageSetting.kt
    └── model/TranscriptSegment.kt

meeting-engine/
└── src/main/
    ├── cpp/
    │   ├── native-lib.cpp
    │   └── (新增) whisper/
    └── java/com/edgemeeting/engine/
        ├── bridge/
        └── RkMeetingSession.kt
```

**Structure Decision**: Android 多模組 SDK，核心介面在 `meeting-core`，JNI/推理在 `meeting-engine`。

## Complexity Tracking

無需新增複雜度例外。
