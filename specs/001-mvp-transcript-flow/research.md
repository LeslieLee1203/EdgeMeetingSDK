# Research Notes: Phase 0 MVP 字幕流

## Decision 1: 字幕來源使用模擬資料
**Decision**: MVP 階段使用模擬字幕資料，不依賴真實 ASR 輸出。  
**Rationale**: 以最小成本達成可展示字幕流，降低依賴與風險。  
**Alternatives considered**: 直接接真實 ASR（風險高、整合成本大、影響 demo 進度）。  

## Decision 2: 更新節奏固定為 1 秒
**Decision**: 每 1 秒輸出一段字幕。  
**Rationale**: 與需求一致，方便驗證節奏穩定性與 UI demo。  
**Alternatives considered**: 200-500ms（更即時但容易造成 UI/Flow 壓力）。  

## Decision 3: Speaker placeholder 預設 Unknown
**Decision**: MVP 階段所有字幕段落使用 "Unknown" 作為說話人佔位。  
**Rationale**: 不做 diarization 仍可保持資料結構完整，便於後續升級。  
**Alternatives considered**: 隱藏 speaker 欄位（會破壞後續一致性與資料契約）。  

## Decision 4: 保留 start/end 時間欄位
**Decision**: 字幕段落保留起訖時間欄位並維持遞增。  
**Rationale**: 為後續 time alignment 與 diarization 接軌做準備。  
**Alternatives considered**: 不輸出時間欄位（會增加未來遷移成本）。  
