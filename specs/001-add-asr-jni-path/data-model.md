# Data Model

## TranscriptSegment
- **Fields**:
  - `id`: 唯一識別（建議：時間戳 + 遞增序號）
  - `text`: 字幕文字
  - `speakerId`: 固定 `Unknown`
  - `startTimeMs`: 片段起始時間（毫秒）
  - `endTimeMs`: 片段結束時間（毫秒）
  - `isFinal`: 固定 `true`
- **Validation rules**:
  - `startTimeMs`、`endTimeMs` 單調遞增
  - `endTimeMs >= startTimeMs`
  - 靜音期間不得產生段落
  - 片段輸出節奏約每秒一段

## AsrResource
- **Fields**:
  - `encoderPath`: 模型檔路徑
  - `decoderPath`: 模型檔路徑
  - `status`: `Unavailable` | `Ready` | `Error`
  - `errorMessage`: 錯誤文字（可空）
- **Validation rules**:
  - 準備階段需驗證路徑可讀
  - 失敗停留在 Error，直到重新準備成功

## AudioInput
- **Fields**:
  - `sampleRateHz`: 固定 16kHz
  - `frames`: PCM 連續樣本
- **Validation rules**:
  - 以 1 秒累積為一段輸入單位
  - 單次辨識失敗僅略過該段

## State Transitions
- `Idle` → `Preparing` → `Ready` → `Listening` → `Idle`
- 準備失敗：`Preparing` → `Error`
- Error 後復原：`Error` → `Preparing` → `Ready`
