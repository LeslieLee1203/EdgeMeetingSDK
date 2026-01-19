# Phase 0 Research

## Decision 1: 模型資源採 RKNN Whisper base 20s（encoder/decoder）
- **Decision**: 使用 RKNN 轉換後的 Whisper base 20s encoder/decoder 作為 Phase 1 的語音辨識資源。
- **Rationale**: 對應 RK3588 目標平台且有完整範例與效能基準可參考，能快速驗證端到端路徑。
- **Alternatives considered**: Wav2Vec2、Zipformer（模型路徑不同且整合成本較高）。

## Decision 2: 離線即時路徑優先
- **Decision**: Phase 1 完全離線，ASR 直接在裝置上處理，不依賴網路服務。
- **Rationale**: 滿足 demo 時即時性與穩定性需求，避免網路造成不可控延遲。
- **Alternatives considered**: 雲端 ASR（需網路與伺服器資源，非本階段目標）。

## Decision 3: 1 秒節奏產生字幕片段
- **Decision**: 以 1 秒 PCM 累積為單位輸出字幕片段。
- **Rationale**: 與既有輸出節奏一致，測試成本最低，符合「每秒一段」的成功標準。
- **Alternatives considered**: 0.5 秒或可變長度輸出（增加對齊複雜度與測試成本）。

## Decision 4: 靜音不輸出、單次失敗略過
- **Decision**: 靜音期間不輸出字幕段落；單次辨識失敗略過片段且持續後續輸出。
- **Rationale**: 避免 UI 空白垃圾資料與不必要的狀態分支，維持穩定輸出。
- **Alternatives considered**: 輸出空字串或占位符；失敗立即停止流程。
