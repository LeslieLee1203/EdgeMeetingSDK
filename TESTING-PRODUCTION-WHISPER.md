# Production 版 Whisper 測試指南

## 🚀 快速驗證步驟

### 第一步：編譯與部署

```bash
# 1. 編譯 C++ 原生代碼
./gradlew :meeting-engine:build

# 2. 編譯 APK
./gradlew :app:assembleDebug

# 3. 安裝到設備 (RK3588)
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

### 第二步：實時日誌監控

打開終端，運行以下命令監控關鍵日誌：

```bash
# 監控 Whisper 引擎運行狀態
adb logcat | grep -E "WhisperAsrEngine|Inference triggered|LocalAgreement|Hybrid VAD"
```

**期望看到的日誌**:
```
WhisperAsrEngine: pushAudio: Buffer=128000 samples (8.0s), VAD=2, SilenceDur=0ms
WhisperAsrEngine: Inference triggered: Reached window size (8s)
WhisperAsrEngine: removePrefixOverlap: Found overlap of 15 chars
WhisperAsrEngine: LocalAgreement: Stable prefix extended to 42 chars: '今天天氣很好'
WhisperAsrEngine: LocalAgreement: Committing 12 new chars: '，我們出去玩'
WhisperAsrEngine: Buffer after overlap retention: 88000 samples (5.5s)
```

---

## 📊 核心驗證點

### ✅ 驗證點 1: 延遲優化（2.5-5 秒）

**測試方法**:
1. 啟動應用，開始錄音
2. 連續說話 30 秒不停頓
3. 觀察日誌中的 `Inference triggered` 時間戳

**成功標準**:
- 首次推論在 **8 秒內**觸發 ✅
- 後續推論間隔 **2.5-3 秒** ✅
- 沒有超過 **10 秒**的推論間隔 ✅

**驗證命令**:
```bash
# 提取推論時間戳，計算間隔
adb logcat | grep "Inference triggered" | awk '{print $1, $2}'
```

---

### ✅ 驗證點 2: 去重機制

**測試方法**:
1. 說一句完整的話："今天天氣很好，我們出去玩吧"
2. 檢查應用 UI 顯示的轉錄文字
3. 查看日誌中的 `removePrefixOverlap` 輸出

**成功標準**:
- UI 上不應出現重複的詞句 ✅
- 日誌中應看到 overlap 檢測訊息 ✅

**錯誤示例（優化前）**:
```
今天天氣很好
天氣很好，我們出去玩  ❌ 重複了 "天氣很好"
```

**正確示例（Production 版）**:
```
今天天氣很好
，我們出去玩吧  ✅ 去除重疊，只顯示新內容
```

**驗證命令**:
```bash
adb logcat | grep "removePrefixOverlap"
```

---

### ✅ 驗證點 3: Local Agreement 穩定性

**測試方法**:
1. 重複播放同一段測試音訊 3 次
2. 記錄每次的轉錄結果
3. 對比結果的一致性

**成功標準**:
- 最終文字完全一致或高度相似（>95%）✅
- 中間過程不應出現頻繁的修改與回退 ✅

**驗證命令**:
```bash
# 觀察穩定前綴的增長
adb logcat | grep "LocalAgreement: Stable prefix extended"
```

---

### ✅ 驗證點 4: Hybrid VAD（三級狀態）

**測試場景**:

#### 場景 A: 完全靜音（背景噪音）
- 保持安靜 5 秒
- **期望**: VAD=0 (SILENCE)，不觸發推論

#### 場景 B: 說話 + 思考停頓
- 說 "今天..." (停頓 1 秒) "天氣很好"
- **期望**: VAD=1 (PAUSE)，在 300-500ms 停頓後可能觸發

#### 場景 C: 活躍語音
- 連續說話不停頓
- **期望**: VAD=2 (SPEECH)，按 8 秒窗口觸發

**驗證命令**:
```bash
# 觀察 VAD 狀態變化
adb logcat | grep "Hybrid VAD: RMS"

# 期望輸出示例:
# Hybrid VAD: RMS=450.23, ZCR=0.025, State=SILENCE
# Hybrid VAD: RMS=1200.45, ZCR=0.055, State=PAUSE
# Hybrid VAD: RMS=2500.67, ZCR=0.120, State=SPEECH
```

---

### ✅ 驗證點 5: Buffer 管理（Overlap 保留）

**測試方法**:
1. 連續說話 20 秒
2. 觀察日誌中的 `Buffer after overlap retention` 訊息

**成功標準**:
- 每次推論後，buffer 應保留 **5.5 秒** (88000 samples) ✅
- Buffer 大小應在 **5.5-8 秒** (88000-128000 samples) 範圍內波動 ✅

**驗證命令**:
```bash
adb logcat | grep "Buffer after overlap retention"

# 期望輸出:
# Buffer after overlap retention: 88000 samples (5.5s)
# Buffer after overlap retention: 92000 samples (5.8s)
```

---

## 🧪 完整測試場景

### 測試 1: 連續語音低延遲

**音訊**: 30 秒連續演講（無停頓）

**操作**:
```bash
# 1. 準備測試音訊
adb push test_continuous_30s.pcm /sdcard/

# 2. 啟動應用並播放音訊

# 3. 監控日誌
adb logcat -c  # 清空日誌
adb logcat | grep -E "Inference triggered|Buffer after"
```

**預期結果**:
```
00:00:08 - Inference triggered: Reached window size (8s)
00:00:10 - Inference triggered: Reached window size (8s)  // 8 + 2.5 = 10.5s
00:00:13 - Inference triggered: Reached window size (8s)  // 10.5 + 2.5 = 13s
...
```

**成功標準**:
- ✅ 首次推論 ≤ 8 秒
- ✅ 推論間隔 2.5-3 秒
- ✅ 無超過 10 秒的空窗期

---

### 測試 2: 間歇對話場景

**音訊**: 一問一答，每句話後停頓 2 秒

**操作**:
```bash
# 監控 VAD 狀態與推論觸發
adb logcat | grep -E "Hybrid VAD|Inference triggered: Natural boundary"
```

**預期結果**:
```
Hybrid VAD: State=SPEECH  // 正在說話
Hybrid VAD: State=PAUSE   // 停頓開始
Inference triggered: Natural boundary (3s+ audio, 500ms silence)
```

**成功標準**:
- ✅ 每句話結束後 500ms 內觸發推論
- ✅ VAD 正確區分 SPEECH / PAUSE / SILENCE

---

### 測試 3: 噪音環境穩定性

**音訊**: 包含背景噪音（空調、鍵盤聲）的語音

**操作**:
```bash
# 監控能量檢查與推論跳過
adb logcat | grep -E "shouldSkipInference|Skipping inference"
```

**預期結果**:
```
Skipping inference: Audio energy too low  // 純噪音片段被跳過
LocalAgreement: Stable prefix extended    // 真實語音被正確提交
```

**成功標準**:
- ✅ 純噪音片段不產生幻覺字幕
- ✅ 真實語音正常轉錄

---

## 🔧 問題排查

### 問題 1: 延遲仍然很高（>5 秒）

**可能原因**:
1. NPU 負載過高
2. STEP_SAMPLES 配置過大

**排查步驟**:
```bash
# 檢查 NPU 使用率
adb shell cat /sys/kernel/debug/rknpu/load

# 檢查日誌中的推論觸發頻率
adb logcat | grep "Inference triggered" | wc -l
```

**解決方案**:
- 降低 `STEP_SAMPLES` 至 2.0 秒
- 檢查其他應用是否佔用 NPU

---

### 問題 2: 輸出重複或抖動

**可能原因**:
1. 去重未生效
2. Local Agreement 閾值過低

**排查步驟**:
```bash
# 檢查是否有 overlap 檢測
adb logcat | grep "removePrefixOverlap: Found overlap"

# 檢查穩定前綴增長
adb logcat | grep "Stable prefix extended"
```

**解決方案**:
- 確認 `last_output` 與 `prev_hypothesis` 正確更新
- 增加 OVERLAP_SAMPLES 至 6 秒

---

### 問題 3: VAD 誤判（語音被當作靜音）

**可能原因**:
1. RMS/ZCR 閾值過高
2. 錄音音量過低

**排查步驟**:
```bash
# 檢查實際的 RMS/ZCR 數值
adb logcat | grep "Hybrid VAD: RMS"
```

**解決方案**:
- 降低 `SILENCE_RMS_THRESHOLD` 至 600
- 提高錄音增益（硬體設置）

---

## 📈 性能監控

### CPU/NPU 使用率
```bash
# 實時監控
adb shell top | grep <package_name>
```

### 記憶體使用
```bash
# 詳細記憶體資訊
adb shell dumpsys meminfo <package_name>

# 關注 Native Heap (C++ buffer)
```

### 電池消耗
```bash
# 電池統計
adb shell dumpsys batterystats | grep <package_name>
```

---

## ✅ 通過標準

**Production 版實現被視為成功，當且僅當**:

1. ✅ **延遲**: 連續語音推論間隔 ≤ 5 秒
2. ✅ **準確**: 去重正常，無重複輸出
3. ✅ **穩定**: 同一音訊多次轉錄結果一致
4. ✅ **智能**: VAD 正確區分 SILENCE/PAUSE/SPEECH
5. ✅ **效能**: 無記憶體洩漏，CPU/NPU 使用合理

---

## 📞 測試協助

**需要協助？** 提供以下資訊：
1. `adb logcat` 完整日誌（問題發生時）
2. 測試音訊檔案（如可分享）
3. 預期行為 vs 實際行為描述

**快速診斷命令**:
```bash
# 捕獲 5 分鐘的關鍵日誌
adb logcat -v time WhisperAsrEngine:D *:S > whisper_test_log.txt
```

祝測試順利！🚀
