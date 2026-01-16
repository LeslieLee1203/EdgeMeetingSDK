# Quickstart: Phase 0 MVP 字幕流

## 目標
在示範 App 中啟動會議並看到每秒新增的字幕段落。

## 步驟
1. 建立 `MeetingSession`
2. 訂閱 `state` 與 `transcriptFlow`
3. 呼叫 `prepare()`，等待 Ready
4. 呼叫 `start()` 觀察字幕流
5. 呼叫 `stop()` 結束字幕

## 驗收
- 啟動後 15 秒內看到至少 10 段字幕
- 停止後 2 秒內不再新增字幕
- 未準備即啟動會回報錯誤狀態
- 準備失敗會回報錯誤狀態
