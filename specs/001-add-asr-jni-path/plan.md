# Implementation Plan: Phase 1 真實字幕流（ASR）

**Branch**: `001-add-asr-jni-path` | **Date**: 2026-01-19 | **Spec**: `specs/001-add-asr-jni-path/spec.md`
**Input**: Feature specification from `specs/001-add-asr-jni-path/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

以裝置端 ASR 打通「音訊 → JNI → Kotlin → 字幕流」的完整路徑：準備階段驗證資源、開始後每秒產生字幕片段、靜音不輸出、單次失敗略過且不中斷，並維持既有狀態機與錯誤回報規則。

## Technical Context

<!--
  ACTION REQUIRED: Replace the content in this section with the technical details
  for the project. The structure here is presented in advisory capacity to guide
  the iteration process.
-->

**Language/Version**: Kotlin 2.3.0、C++17  
**Primary Dependencies**: Kotlin Coroutines/Flow、Oboe 1.10.0、JNI  
**Storage**: N/A（Phase 1 不持久化字幕）  
**Testing**: JUnit、kotlinx-coroutines-test  
**Target Platform**: Android API 33–36  
**Project Type**: Mobile SDK（Android）  
**Performance Goals**: Start 後 2 秒內出首段；約每秒 1 段字幕；5 分鐘穩定不中斷  
**Constraints**: 離線、單一通用錯誤碼、靜音不輸出、單次失敗略過不斷流  
**Scale/Scope**: 單會議 session、單裝置即時語音輸入

## Constitution Check

*GATE: Must pass before Phase 0 research. Re-check after Phase 1 design.*

- 測試先行 (TDD)：先寫測試且確認失敗，再進行最小實作
- 先註解說明「為什麼要這樣做」，再寫程式碼
- 每次輸出一個 function 或一段程式碼，需經驗證才能前進
- UI/UX 一致性：使用既有元件與樣式，避免未定義行為
- 效能預算：關鍵路徑有量測與目標，避免無理由開銷
- 程式碼品質：KISS/YAGNI/DRY/SOLID + 錯誤處理完整

## Project Structure

### Documentation (this feature)

```text
specs/001-add-asr-jni-path/
├── plan.md              # This file (/speckit.plan command output)
├── research.md          # Phase 0 output (/speckit.plan command)
├── data-model.md        # Phase 1 output (/speckit.plan command)
├── quickstart.md        # Phase 1 output (/speckit.plan command)
├── contracts/           # Phase 1 output (/speckit.plan command)
└── tasks.md             # Phase 2 output (/speckit.tasks command - NOT created by /speckit.plan)
```

### Source Code (repository root)
<!--
  ACTION REQUIRED: Replace the placeholder tree below with the concrete layout
  for this feature. Delete unused options and expand the chosen structure with
  real paths (e.g., apps/admin, packages/something). The delivered plan must
  not include Option labels.
-->

```text
app/
└── src/main/java/com/edgemeeting/app/...

meeting-core/
└── src/main/java/com/edgemeeting/core/...

meeting-engine/
├── src/main/java/com/edgemeeting/engine/...
└── src/test/java/com/edgemeeting/engine/...
```

**Structure Decision**: Mobile SDK 多模組結構；本階段主要落在 `meeting-engine` 與既有測試目錄。

## Constitution Check (Post-Design)

結論：通過，無額外違反事項。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

無憲章違反。
