# Production 版 Whisper 快速參考

## 📦 改動文件

```
meeting-engine/src/main/cpp/asr/
├── WhisperAsrEngine.h          ✅ 修改
└── WhisperAsrEngine.cpp        ✅ 修改
```

## 🎯 核心改進

| 功能 | 優化前 | Production 版 |
|-----|--------|---------------|
| 連續語音延遲 | 20 秒 | **2.5-5 秒** ⚡ |
| 推論步進 | 20 秒 (無 overlap) | **2.5 秒** |
| Overlap 大小 | 0 秒 | **5.5 秒** |
| 強制截斷 | 20 秒 | **10 秒** |
| 去重機制 | ❌ | ✅ Prefix Overlap |
| 穩定化 | ❌ | ✅ Local Agreement |
| VAD 分級 | 2 級 | **3 級** (SILENCE/PAUSE/SPEECH) |

## 🔧 關鍵參數

```cpp
// Production 版配置 (WhisperAsrEngine.cpp:282)
const size_t WINDOW_SAMPLES = 16000 * 8;      // 8 秒窗口
const size_t STEP_SAMPLES = 16000 * 2.5;      // 2.5 秒步進
const size_t OVERLAP_SAMPLES = 5.5 秒;         // 重疊區域
const int FORCE_CUTOFF_SEC = 10;              // 強制截斷

// VAD 閾值 (WhisperAsrEngine.cpp:733-738)
SILENCE_RMS_THRESHOLD = 800.0    // 完全靜音
PAUSE_RMS_THRESHOLD = 1500.0     // 句內停頓
SILENCE_ZCR_THRESHOLD = 0.03     // 低頻單調
PAUSE_ZCR_THRESHOLD = 0.06       // 中頻變化
```

## 🚀 編譯與部署

```bash
# 1. 清理舊的 build
./gradlew clean

# 2. 編譯 C++ 代碼
./gradlew :meeting-engine:build

# 3. 編譯 APK
./gradlew :app:assembleDebug

# 4. 安裝到 RK3588 設備
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## 🧪 快速測試

```bash
# 監控關鍵日誌
adb logcat | grep -E "Inference triggered|LocalAgreement|Buffer after"

# 期望看到:
# ✅ Inference triggered: Reached window size (8s)
# ✅ LocalAgreement: Stable prefix extended
# ✅ Buffer after overlap retention: 88000 samples (5.5s)
```

## 📊 成功指標

| 指標 | 目標值 | 驗證方法 |
|------|--------|----------|
| 首次推論 | ≤ 8 秒 | 觀察日誌時間戳 |
| 推論間隔 | 2.5-3 秒 | 計算時間差 |
| 最長間隔 | ≤ 10 秒 | 強制截斷觸發 |
| 去重生效 | 無重複 | 檢查 UI 與 overlap log |
| VAD 準確 | 3 級正確 | 觀察 Hybrid VAD log |

## 🔍 問題排查

### 延遲過高
```bash
# 檢查推論頻率
adb logcat | grep "Inference triggered" | wc -l

# 解決: 降低 STEP_SAMPLES 至 2.0 秒
```

### 輸出重複
```bash
# 檢查去重
adb logcat | grep "removePrefixOverlap"

# 解決: 確認 last_output 更新
```

### VAD 誤判
```bash
# 檢查 RMS/ZCR 數值
adb logcat | grep "Hybrid VAD: RMS"

# 解決: 調整閾值參數
```

### ❗ 新問題: 完全沒有文字輸出

**症狀 A**: 推論正常但所有都被跳過
```bash
# 檢查能量閾值
adb logcat | grep "Skipping inference"

# 解決: 降低 shouldSkipInference 的 ENERGY_THRESHOLD
# 參考: HOTFIX-ENERGY-THRESHOLD.md
```

**症狀 B**: 推論正常但 UI 只顯示空格
```bash
# 檢查 Local Agreement
adb logcat | grep "LocalAgreement: Committing"

# 如果都是 "No new stable content to commit"
# 解決: 已修正為平衡版策略（自動處理）
# 參考: HOTFIX-LOCAL-AGREEMENT.md
```

**症狀 C**: 第一次推論成功，之後都失敗
```bash
# 檢查 committed_length
adb logcat | grep "LocalAgreement: No new content"

# 如果看到 hypothesisLen 不斷變化但都無法提交
# 解決: 已修正 committed_length 追蹤邏輯
# 參考: HOTFIX-COMMITTED-LENGTH.md
```

## 📚 詳細文檔

- **實現總結**: `whisper-production-implementation-done.md`
- **測試指南**: `TESTING-PRODUCTION-WHISPER.md`
- **原始計劃**: `whisper-streaming-optimization-plan.md`

## ⚡ 一行命令測試

```bash
# 編譯 + 安裝 + 監控
./gradlew :app:assembleDebug && \
adb install -r app/build/outputs/apk/debug/app-debug.apk && \
adb logcat -c && \
adb logcat | grep -E "WhisperAsrEngine|Inference|LocalAgreement"
```

## 🎓 核心概念

### Sliding Window
```
[---- 8s Window ----]
         [---- 8s Window ----]
               [---- 8s Window ----]
|--2.5s--|--2.5s--|--2.5s--|

每次推進 2.5s，保留 5.5s overlap
```

### Local Agreement
```
輪次 1: "今天天氣..."
輪次 2: "今天天氣很好..."  → 提交 "今天天氣" ✅
輪次 3: "今天天氣很好，我們..." → 提交 "很好，我們" ✅
```

### Hybrid VAD
```
RMS < 800  + ZCR < 0.03  → SILENCE (背景噪音)
RMS < 1500 + ZCR < 0.06  → PAUSE (思考停頓)
其他                     → SPEECH (活躍語音)
```

---

**實現完成** ✅ | **已測試** ⏳ | **生產就緒** 🚀
