# SDK Contract

## 初始化

- **initWhisper(modelsPath, languageSetting)** → `BridgeResult`
  - modelsPath: `.rknn` 模型檔所在資料夾
  - languageSetting: `auto` 或指定語言代碼

## 音訊輸入

- **pushAudio(pcm16Mono, sampleRateHz)** → `BridgeResult`
  - pcm16Mono: 16-bit 單聲道 PCM
  - sampleRateHz: 預期 16000

## 逐段文字輸出

- **pollTranscript()** → `TranscriptSegment?`
  - 沒有新段落時回傳 `null`

## 停止與釋放

- **stopWhisper()** → `BridgeResult`
- **releaseWhisper()** → `BridgeResult`

## 回傳型別

### BridgeResult
- **code**: Int（0 為成功）
- **message**: String（可讀訊息）

### TranscriptSegment
- **text**: String
- **startMs**: Long
- **endMs**: Long
- **languageCode**: String
