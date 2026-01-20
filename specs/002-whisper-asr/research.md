# 研究與決策紀錄

## 逐段分段規則
- Decision: 靜音 >= 700ms 即切段
- Rationale: 符合語音停頓直覺，且可量測與測試
- Alternatives considered: 固定時間切段、僅 stop 輸出

## 語言支援範圍
- Decision: Auto + Whisper 所有可用語言
- Rationale: 需求明確，避免後續擴充影響 API
- Alternatives considered: 僅中/英、僅 auto

## 語言標記回傳
- Decision: 每段回傳語言代碼
- Rationale: 最小可用資訊，UI 可直接顯示或分類
- Alternatives considered: 回傳語言代碼 + 信心度、不回傳

## 段落時間資訊
- Decision: 回傳起訖時間（毫秒）
- Rationale: 方便 UI 對齊與後續同步處理
- Alternatives considered: 只回傳序號、不回傳時間

## 錯誤回傳格式
- Decision: 錯誤碼 + 可讀訊息
- Rationale: 同時支援程式處理與使用者訊息
- Alternatives considered: 只有錯誤碼、只有訊息

## 離線與執行緒限制
- Decision: 必須離線運作，推理在背景執行緒，避免阻塞音訊 thread
- Rationale: 裝置端即時轉錄需穩定、不可干擾錄音
- Alternatives considered: 允許線上依賴、在音訊 thread 直接推理
