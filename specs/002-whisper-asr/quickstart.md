# Quickstart

## 前置條件

### 模型檔放置

1. 將 `whisper_encoder_base_20s.rknn` 與 `whisper_decoder_base_20s.rknn` 放入 `meeting-engine/src/main/assets/models/`
2. SDK 首次 `prepare()` 時會自動複製至 `context.filesDir/models/`

### Native 依賴

1. 將 `librknnrt.so` 放入 `meeting-engine/src/main/jniLibs/arm64-v8a/`
2. 確認版本與模型相容

## 基本流程

1. 建立 `MeetingSession` 並設定語言模式（auto 或指定語言）
2. 呼叫 `prepare()` 載入模型
3. 呼叫 `start()` 開始即時轉錄
4. 透過 `transcriptFlow` 觀察逐段文字輸出
5. 呼叫 `stop()` 停止轉錄並釋放資源

## 錯誤處理

- 模型檔缺失或版本不相容時，應回傳錯誤碼 + 可讀訊息
