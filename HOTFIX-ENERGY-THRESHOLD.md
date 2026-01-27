# Hotfix: 能量檢測閾值修正

## 🐛 問題描述

**症狀**: Production 版實現後，所有推論都被跳過，完全沒有文字輸出。

**日誌證據** (`log_001.txt`):
```
Line 93: Inference triggered: Reached window size (8s)
Line 94: Skipping inference: Audio energy too low  ❌
Line 96: Skipping inference: Audio energy too low  ❌
... (所有推論都被跳過)
```

但同時 VAD 檢測到語音活動：
```
Line 90: Hybrid VAD: RMS=160.59, State=SPEECH  ✅
Line 97: Hybrid VAD: RMS=19.77, State=SPEECH   ✅
Line 103: Hybrid VAD: RMS=69.42, State=PAUSE   ✅
```

## 🔍 根本原因

### 閾值不一致

| 函數 | 閾值設定 | 用途 |
|------|---------|------|
| `detectVadState()` | RMS < 800 → SILENCE | VAD 狀態判定 |
| `shouldSkipInference()` | RMS < 1000 → 跳過 | 推論前能量檢查 |

**矛盾點**：
- VAD 判定 RMS=160.59 為 **SPEECH** (> 800) ✅
- 能量檢查判定 RMS=160.59 為 **太低** (< 1000) ❌
- 結果：有語音但不推論 → **完全沒有輸出**

### 實際錄音音量

從日誌分析：
- 真實語音 RMS 範圍：**20-200**
- 麥克風 RMS (歸一化): **0.01-0.02**

原始閾值 1000.0 是針對「理想錄音條件」設定的，但實際環境：
- 辦公室/家庭環境較安靜
- 麥克風增益設定較低
- 說話音量中等

導致真實語音被誤判為噪音。

## ✅ 修正方案

### 修正內容

```cpp
// 修正前 (WhisperAsrEngine.cpp:559)
const double ENERGY_THRESHOLD = 1000.0;  // ❌ 太高
const double SILENCE_RATIO_THRESHOLD = 0.9;

// 修正後
const double ENERGY_THRESHOLD = 30.0;    // ✅ 降低至 30.0
const double SILENCE_RATIO_THRESHOLD = 0.95; // ✅ 提高至 95%
```

### 修正邏輯

1. **降低 ENERGY_THRESHOLD**:
   - 從 1000.0 降至 **30.0**
   - 理由：根據日誌，真實語音 RMS 最低為 19.77，設定 30.0 可覆蓋此範圍
   - 保險：仍比純噪音（< 10）高 3 倍

2. **提高 SILENCE_RATIO_THRESHOLD**:
   - 從 90% 提高至 **95%**
   - 理由：更嚴格的靜音判定，需要 95% 以上的時間都低於 30.0 才跳過
   - 避免：誤殺包含短暫語音的 buffer

3. **新增 Debug Log**:
   - 追蹤 `maxRms` 和 `silenceRatio`
   - 每 10 秒輸出一次，方便調優

### 預期效果

**修正前**:
```
VAD: RMS=160.59, State=SPEECH
→ shouldSkipInference: 160 < 1000 → Skip ❌
→ 結果: 沒有輸出
```

**修正後**:
```
VAD: RMS=160.59, State=SPEECH
→ shouldSkipInference: 160 > 30 → Process ✅
→ 結果: 正常推論並輸出文字
```

## 🧪 重新測試步驟

### 1. 重新編譯

```bash
# 清理並重新編譯
./gradlew clean
./gradlew :meeting-engine:build
./gradlew :app:assembleDebug

# 安裝到設備
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 2. 監控關鍵日誌

```bash
# 啟動日誌監控
adb logcat -c
adb logcat | grep -E "WhisperAsrEngine|shouldSkipInference|runInference|LocalAgreement"
```

### 3. 預期看到的日誌

**修正前 (問題狀態)**:
```
WhisperAsrEngine: Inference triggered: Reached window size (8s)
WhisperAsrEngine: Skipping inference: Audio energy too low  ❌
```

**修正後 (正常狀態)**:
```
WhisperAsrEngine: Inference triggered: Reached window size (8s)
WhisperAsrEngine: shouldSkipInference: maxRMS=160.59, silenceRatio=0.12, shouldSkip=0
WhisperAsrEngine: runInference: Processing 128000 samples  ✅
WhisperAsrEngine: runInference: Preprocessing done. Mel size: 160000
WhisperAsrEngine: runInference: Encoder done
WhisperAsrEngine: Decoder Result: '今天天氣很好'  ✅
WhisperAsrEngine: removePrefixOverlap: Found overlap of 0 chars
WhisperAsrEngine: LocalAgreement: Stable prefix extended to 15 chars  ✅
```

### 4. 驗證要點

| 驗證項 | 檢查方法 | 預期結果 |
|--------|---------|---------|
| 推論不再被跳過 | 檢查 "Skipping inference" 頻率 | 應大幅減少（僅真正靜音時出現） |
| 有轉錄輸出 | 查看 "Decoder Result" | 應出現中文/英文文字 |
| Local Agreement 運作 | 查看 "LocalAgreement" | 應顯示穩定前綴增長 |
| Buffer 管理正常 | 查看 "Buffer after overlap" | 應保持 88000 samples (5.5s) |

## 📊 閾值調優指南

如果修正後仍有問題，可根據實際情況調整：

### 情況 A: 仍然跳過太多推論

**症狀**: 有語音但還是被跳過

**調整**:
```cpp
const double ENERGY_THRESHOLD = 20.0;  // 進一步降低至 20.0
```

**驗證**:
```bash
adb logcat | grep "shouldSkipInference: maxRMS"
# 檢查實際的 maxRMS 值，應該都高於新閾值
```

### 情況 B: 噪音產生幻覺字幕

**症狀**: 完全靜音時出現 "Thank you" 等幻覺

**調整**:
```cpp
const double ENERGY_THRESHOLD = 40.0;  // 提高至 40.0
const double SILENCE_RATIO_THRESHOLD = 0.98;  // 提高至 98%
```

### 情況 C: 輕聲說話被漏掉

**症狀**: 大聲說話有輸出，輕聲沒有

**調整**:
```cpp
const double ENERGY_THRESHOLD = 15.0;  // 降至 15.0（非常低）
```

**代價**: 可能增加噪音幻覺

## 🔧 進階：自適應閾值（未來改進）

當前為固定閾值，未來可改為自適應：

```cpp
// 動態調整閾值（偽代碼）
double adaptiveThreshold = calculate_background_noise_level() * 3.0;
if (rms < adaptiveThreshold) {
    silentWindows++;
}
```

**好處**:
- 自動適應不同錄音環境
- 無需手動調優
- 更強的泛化能力

## 📝 總結

**問題**: 能量檢測閾值過高 (1000.0) → 真實語音被誤判 → 無輸出

**修正**: 降低至 30.0，提高靜音比例至 95%

**驗證**: 重新編譯 → 測試 → 檢查日誌中有 "Decoder Result"

**下一步**: 如有問題，根據實際 RMS 值微調閾值

---

**修正日期**: 2026-01-26
**修正文件**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp:557-602`
