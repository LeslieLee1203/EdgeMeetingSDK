# Feature Specification: Phase 0 MVP 字幕流

**Feature Branch**: `001-mvp-transcript-flow`  
**Created**: 2026-01-16  
**Status**: Draft  
**Input**: User description: "Phase 0 MVP 字幕流（demo 用、1 秒節奏、Unknown speaker、先不做 diarization/zero-copy）"

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 可展示的字幕流 (Priority: P1)

作為展示者，我可以啟動會議並在 UI 看到每秒更新的字幕，讓 MVP 可被快速 demo。

**Why this priority**: 這是 MVP 的核心價值與可展示成果。

**Independent Test**: 只要能啟動會議並看到連續字幕，就能完成獨立驗證。

**Acceptance Scenarios**:

1. **Given** 已完成準備且可開始，**When** 使用者啟動會議，**Then** UI 每 1 秒可看到新增一段字幕
2. **Given** 正在顯示字幕流，**When** 使用者停止會議，**Then** 2 秒內不再新增字幕

---

### User Story 2 - 錯誤狀態可見 (Priority: P2)

作為展示者，我在錯誤操作時能看到清楚的錯誤狀態，避免 demo 卡住。

**Why this priority**: 錯誤可見能避免無限等待，提升 demo 穩定度。

**Independent Test**: 只要在不允許的狀態下啟動或準備失敗，即可驗證錯誤狀態。

**Acceptance Scenarios**:

1. **Given** 尚未完成準備，**When** 使用者啟動會議，**Then** 會議狀態顯示錯誤
2. **Given** 準備流程失敗，**When** 系統結束準備，**Then** 會議狀態顯示錯誤

---

### User Story 3 - 長時間展示穩定 (Priority: P3)

作為展示者，我可以連續展示 10 分鐘不中斷。

**Why this priority**: 連續展示能力可避免中途重啟或重開 demo。

**Independent Test**: 持續運行 10 分鐘並觀察是否中斷即可驗證。

**Acceptance Scenarios**:

1. **Given** 會議已啟動，**When** 連續運行 10 分鐘，**Then** 字幕流不中斷且應用不崩潰

---

### Edge Cases

- 在非可開始狀態下呼叫啟動時，系統如何回報錯誤？
- 重複呼叫啟動或停止時，是否會造成狀態錯亂？
- 準備流程失敗後，是否能再次嘗試準備？

### Assumptions & Dependencies

- MVP 階段字幕來源可為模擬資料，不依賴實際語音辨識結果
- MVP 階段不包含說話人分群與回溯修正
- 展示應用可訂閱字幕並以可讀方式呈現

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系統在會議啟動後，必須每 1 秒產生並輸出一段字幕
- **FR-002**: 所有字幕段落必須包含說話人佔位值 "Unknown"
- **FR-003**: MVP 階段所有字幕段落必須標記為已確認狀態
- **FR-004**: 每段字幕必須包含遞增的開始與結束時間（相對於會議開始）
- **FR-005**: 在停止會議或釋放資源後，系統必須停止輸出字幕
- **FR-006**: 在非可開始狀態下啟動會議，系統必須回報錯誤狀態
- **FR-007**: 準備流程失敗時，系統必須回報錯誤狀態

### 非功能需求

- **NFR-001**: 連續展示 10 分鐘期間，字幕流不得中斷
- **NFR-002**: 字幕輸出節奏需保持穩定，95% 的更新間隔不超過 1.5 秒

### Key Entities *(include if feature involves data)*

- **字幕段落**: 包含唯一識別、文字內容、說話人佔位、是否確認、起訖時間
- **會議狀態**: 代表會議的準備、啟動、停止與錯誤狀態

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 啟動會議後 15 秒內可看到至少 10 段連續字幕
- **SC-002**: 停止會議後 2 秒內不再新增字幕
- **SC-003**: 連續展示 10 分鐘不中斷且應用不崩潰
- **SC-004**: 錯誤操作（未準備即啟動）後 1 秒內可觀察到錯誤狀態
