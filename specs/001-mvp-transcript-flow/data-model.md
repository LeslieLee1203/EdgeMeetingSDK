# Data Model: Phase 0 MVP 字幕流

## Entities

### 字幕段落 (TranscriptSegment)
- **id**: 唯一識別（字串）
- **text**: 字幕內容
- **speakerId**: 說話人佔位（MVP 為 "Unknown"）
- **isFinal**: 是否已確認（MVP 為已確認狀態）
- **startTimeMs**: 起始時間（相對於會議開始）
- **endTimeMs**: 結束時間（相對於會議開始）

**Validation Rules**
- `startTimeMs` 必須大於等於 0
- `endTimeMs` 必須大於等於 `startTimeMs`

### 會議狀態 (MeetingState)
- **Idle**: 閒置
- **Preparing**: 準備中
- **Ready**: 就緒
- **Listening**: 進行中
- **Error**: 錯誤（包含錯誤代碼與訊息）

## State Transitions

- Idle → Preparing → Ready
- Ready → Listening → Ready
- 任一狀態 → Error
