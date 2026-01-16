# Implementation Plan: Phase 0 MVP 字幕流

**Branch**: `001-mvp-transcript-flow` | **Date**: 2026-01-16 | **Spec**: [spec.md](spec.md)  
**Input**: Feature specification from `/specs/001-mvp-transcript-flow/spec.md`

**Note**: This template is filled in by the `/speckit.plan` command. See `.specify/templates/commands/plan.md` for the execution workflow.

## Summary

Phase 0 以可展示的字幕流為目標，會議啟動後每 1 秒輸出一段字幕，包含 Unknown 說話人佔位與起訖時間，停止或錯誤時立即停止輸出並回報狀態。

## Technical Context

**Language/Version**: Kotlin (JVM 11), C++17 (NDK)  
**Primary Dependencies**: Kotlin Coroutines, AndroidX, Oboe  
**Storage**: N/A  
**Testing**: JUnit, kotlinx-coroutines-test  
**Target Platform**: Android 13+ (minSdk 33), RK3588 裝置  
**Project Type**: Mobile SDK (Android 多模組)  
**Performance Goals**: 字幕每 1 秒輸出，95% 更新間隔不超過 1.5 秒  
**Constraints**: MVP 不做 zero-copy 與 diarization；保持 demo 穩定  
**Scale/Scope**: 單裝置 demo 連續 10 分鐘

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
specs/001-mvp-transcript-flow/
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
meeting-core/
└── src/main/java/com/edgemeeting/core/
meeting-engine/
└── src/main/java/com/edgemeeting/engine/
```

**Structure Decision**: Android 多模組專案，核心介面在 `meeting-core`，引擎實作在 `meeting-engine`。

## Complexity Tracking

> **Fill ONLY if Constitution Check has violations that must be justified**

| Violation | Why Needed | Simpler Alternative Rejected Because |
|-----------|------------|-------------------------------------|
| [e.g., 4th project] | [current need] | [why 3 projects insufficient] |
| [e.g., Repository pattern] | [specific problem] | [why direct DB access insufficient] |
