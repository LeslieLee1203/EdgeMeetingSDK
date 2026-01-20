# Feature Specification: RK3588 Whisper 即時語音轉錄整合

**Feature Branch**: `002-whisper-asr`  
**Created**: 2026-01-20  
**Status**: Draft  
**Input**: User description: "Integrate RK3588 Whisper ASR into Android meeting-engine with segment text output and auto language"

## Clarifications

### Session 2026-01-20

- Q: 逐段分段規則要用哪種？ → A: 靜音 >= 700ms 即切段
- Q: 指定語言支援範圍？ → A: 支援 Auto + Whisper 所有可用語言
- Q: 轉錄結果的語言標記？ → A: 每段回傳語言代碼
- Q: 轉錄段落輸出的時間資訊？ → A: 回傳起訖時間（毫秒）
- Q: 輸出錯誤的回傳方式？ → A: 錯誤碼 + 可讀訊息

## User Scenarios & Testing *(mandatory)*

### User Story 1 - 即時語音轉錄（逐段文字） (Priority: P1)

在裝置上開始會議後，使用者可以看到語音被即時轉成逐段文字。

**Why this priority**: 這是核心價值，沒有即時轉錄就沒有功能價值。

**Independent Test**: 在裝置上輸入一段語音後，應能在會議進行中產生逐段轉錄文字。

**Acceptance Scenarios**:

1. **Given** 會議已開始，**When** 使用者說話一段時間後停頓，**Then** 系統輸出一段完整文字段落
2. **Given** 會議進行中，**When** 持續說話 30 秒，**Then** 系統分段輸出多段文字

---

### User Story 2 - 語言自動/指定 (Priority: P2)

使用者可用自動偵測語言，或指定 Whisper 所有可用語言以提升準確度。

**Why this priority**: 語言設定會直接影響轉錄品質，是次核心功能。

**Independent Test**: 以相同語音，在自動模式與指定語言模式下比較輸出是否合理。

**Acceptance Scenarios**:

1. **Given** 語言模式為自動，**When** 輸入中文語音，**Then** 產生中文逐段轉錄
2. **Given** 指定語言為英文，**When** 輸入英文語音，**Then** 產生英文逐段轉錄

---

### User Story 3 - 錯誤與降級回饋 (Priority: P3)

當模型或資源不可用時，使用者會收到清楚的錯誤回饋，而不是卡住。

**Why this priority**: 裝置端資源異常不可避免，需要可理解的失敗模式。

**Independent Test**: 移除模型檔後啟動轉錄，應得到明確錯誤訊息。

**Acceptance Scenarios**:

1. **Given** 模型檔缺失，**When** 啟動會議轉錄，**Then** 系統回傳錯誤並提示模型不可用

---

### Edge Cases

- 長時間靜音時不應輸出重複或空白段落
- 靜音門檻不足以形成分段時，維持同一段落累積輸出
- 模型檔損壞或版本不相容時要有明確錯誤
- 連續會議 30 分鐘不中斷時仍可穩定輸出

## Requirements *(mandatory)*

### Functional Requirements

- **FR-001**: 系統必須能在會議開始前載入裝置端語音轉錄模型
- **FR-002**: 系統必須能接收即時音訊並產生逐段文字輸出
- **FR-003**: 系統必須支援語言自動偵測與指定 Whisper 所有可用語言
- **FR-004**: 系統必須在模型或資源不可用時回傳可理解的錯誤訊息
- **FR-005**: 使用者必須能在會議開始與結束時啟用或停止轉錄
- **FR-006**: 轉錄流程必須在裝置離線狀態下可運作
- **FR-007**: 逐段文字分段規則必須以靜音 >= 700ms 為切段條件
- **FR-008**: 錯誤回傳必須包含錯誤碼與可讀訊息

### 非功能需求

- **NFR-001**: 逐段文字輸出需在語音段落結束後 2 秒內產生
- **NFR-002**: 20 秒語音輸入需在 20 秒內完成轉錄產出
- **NFR-003**: 連續 30 分鐘轉錄過程不得出現崩潰或卡死

### Key Entities *(include if feature involves data)*

- **TranscriptSegment**: 文字段落、起訖時間（毫秒）、語言代碼
- **LanguageSetting**: 自動或指定語言的設定值
- **TranscriptionSession**: 會議轉錄的狀態與時間範圍

### Assumptions & Dependencies

- 模型檔 (`*.rknn`) 打包於 APK `assets/models/` 目錄，首次啟動時複製至 app-specific storage
- `librknnrt.so` 放置於 `jniLibs/arm64-v8a/`
- 轉錄功能僅在支援的裝置上啟用
- 使用者已授權麥克風使用權限

## Success Criteria *(mandatory)*

### Measurable Outcomes

- **SC-001**: 95% 的語音段落在結束後 2 秒內產生逐段文字
- **SC-002**: 20 秒語音輸入在 20 秒內完成轉錄輸出
- **SC-003**: 模型缺失時，1 秒內回傳可理解的錯誤訊息
- **SC-004**: 30 分鐘連續轉錄中，無崩潰且無明顯輸出中斷
