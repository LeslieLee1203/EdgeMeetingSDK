# 資料模型

## TranscriptSegment

- **text**: 逐段文字內容（必填）
- **startMs**: 段落開始時間（毫秒，必填）
- **endMs**: 段落結束時間（毫秒，必填）
- **languageCode**: 語言代碼（必填）

### 驗證規則
- `startMs >= 0`
- `endMs >= startMs`
- `text` 不得為空白

## LanguageSetting

- **mode**: `auto` 或 `fixed`（必填）
- **value**: 指定語言代碼（`mode=fixed` 時必填）

## TranscriptionSession

- **state**: `idle` | `preparing` | `ready` | `listening` | `error`
- **startTimeMs**: 會議開始時間（毫秒）
- **endTimeMs**: 會議結束時間（毫秒）
- **lastErrorCode**: 錯誤碼（可選）
- **lastErrorMessage**: 可讀錯誤訊息（可選）
