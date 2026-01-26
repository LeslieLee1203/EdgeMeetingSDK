# Whisper 幻覺(Hallucination)問題解決方案

## 問題描述

### 現象
在靜音或環境雜音狀態下，Whisper ASR 會產生不存在的文字，例如：
- `"static"` (靜電)
- `"Bell"` (鈴聲)
- `"bells chiming"` (鐘聲響起)
- `"thank you"` (感謝)
- `"thanks for watching"` (感謝觀看)

### 根本原因

#### 1. Whisper 模型訓練資料偏差
- 訓練資料主要來自 YouTube 影片字幕
- 包含大量 `[Bell ringing]`, `[static noise]` 等音效標註
- 影片結尾常有 "thank you", "thanks for watching"
- 模型在非語音音訊時會「猜測」訓練資料中的常見模式

#### 2. 靜音檢測不夠嚴格
目前實作 (`WhisperAsrEngine.cpp:464-471`)：
```cpp
bool WhisperAsrEngine::detectSilence(const int16_t* pcm, size_t samples) {
    double rms = std::sqrt(sum / samples);
    return rms < 500.0; // 閾值過低
}
```

**問題**：
- RMS < 500 視為靜音，但：
  - 環境白噪音 RMS ≈ 300-800
  - 空調噪音 RMS ≈ 400-1200
  - 麥克風底噪 RMS ≈ 200-600
- 這些「偽靜音」仍會觸發推論

#### 3. 強制推論機制
```cpp
// 累積 3 秒以上 + 靜音 700ms → 觸發推論
if (audioBuffer.size() > 48000 && silenceDurationMs >= 700) {
    runInference(...);
}
```
即使檢測到「靜音」，若累積超過 3 秒仍會推論，導致對雜音產生幻覺。

---

## 研究數據參考

根據 [2025 年 arXiv 論文](https://arxiv.org/html/2501.11378v1)（301,317 個非語音檔案測試）：

| 情境 | 幻覺率 | 說明 |
|------|--------|------|
| 1 秒靜音/噪音 | 52.1% | 短時間容易產生常見詞彙 |
| 10 秒靜音/噪音 | 11.6% | 中等長度相對穩定 |
| 30 秒靜音/噪音 | **62.3%** | 超過模型處理窗口（30s）幻覺激增 |
| 靜音 + 語音混合 | 17.1% | 靜音段常出現幻覺 |

**最常見幻覺詞彙佔比**：
- Top 2 詞彙（"thank you", "thanks for watching"）佔 **35%**
- Top 10 詞彙佔 **50%**
- Top 30 詞彙佔 **67%**

---

## 解決方案規劃

### 方案優先級總覽

| 方案 | 實作難度 | 效能影響 | 預期效果 | 優先級 |
|------|---------|---------|---------|--------|
| **方案 1A**: 提高 RMS 閾值 | ⭐ 極低 | 無 | 中等（減少 30-40%） | 🔥 立即實施 |
| **方案 1B**: 多維度靜音檢測 | ⭐⭐ 低 | 微小 | 高（減少 50-60%） | 🔥 立即實施 |
| **方案 2**: 音訊能量預檢 | ⭐⭐ 低 | 微小 | 高（減少 60-70%） | ⚡ 短期實施 |
| **方案 3**: 跳過純靜音推論 | ⭐⭐ 低 | 微小（節省推論） | 極高（減少 70-80%） | ⚡ 短期實施 |
| **方案 4**: VAD 預處理（SileroVAD） | ⭐⭐⭐⭐ 高 | 中等（+50ms） | 極高（減少 90%+） | 🔄 中期考慮 |
| **方案 5**: 調整 Whisper 參數 | ⭐⭐⭐ 中 | 視參數而定 | 中等（減少 30%） | 🔄 中期實驗 |

**不採用方案**：
- ❌ **後處理移除幻覺詞彙（BoH）**：可能誤刪真實轉錄內容（如使用者真的說 "thank you"）

---

## 方案 1A：提高靜音檢測 RMS 閾值（立即實施）

### 原理
將 RMS 閾值從 500 提高到 1000-1500，過濾更多環境噪音。

### 實作

#### 步驟 1：調整閾值常數
**檔案**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

```cpp
bool WhisperAsrEngine::detectSilence(const int16_t* pcm, size_t samples) {
    long long sum = 0;
    for (size_t i = 0; i < samples; i++) {
        sum += static_cast<long long>(pcm[i]) * pcm[i];
    }
    double rms = std::sqrt(static_cast<double>(sum) / samples);

    // 從 500 提高到 1200（可根據實測調整）
    const double SILENCE_RMS_THRESHOLD = 1200.0;
    return rms < SILENCE_RMS_THRESHOLD;
}
```

#### 步驟 2：新增可配置參數（可選）
在 `AsrConfig.Whisper` 中新增可選的靜音閾值參數：

**檔案**: `meeting-core/src/main/kotlin/com/edgemeeting/core/asr/AsrConfig.kt`

```kotlin
sealed class AsrConfig {
    data class Whisper(
        val language: LanguageSetting = LanguageSetting.Auto,
        val silenceRmsThreshold: Double = 1200.0  // 新增參數
    ) : AsrConfig()
}
```

### 測試方式
1. 編譯並部署到 RK3588 裝置
2. 測試不同環境：
   - 安靜室內（預期 RMS < 300）
   - 辦公室環境（預期 RMS 500-800）
   - 噪音環境（預期 RMS > 1000）
3. 觀察 logcat 輸出的 RMS 值：
   ```bash
   adb logcat | grep "pushAudio"
   ```

### 預期效果
- ✅ 減少 30-40% 幻覺
- ✅ 無效能損耗
- ⚠️ 可能在嘈雜環境中漏掉輕聲說話

### 風險評估
- **低風險**：純參數調整，可輕鬆回退
- **可逆性**：保留原始值 500 作為最低值選項

---

## 方案 1B：多維度靜音檢測（立即實施）

### 原理
除了 RMS 能量外，結合 **零交叉率 (Zero Crossing Rate, ZCR)** 來判斷：
- 語音通常有較高 ZCR（200-3000 Hz 變化）
- 靜音/噪音 ZCR 較低（< 100 crossings/frame）

### 實作

**檔案**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

```cpp
bool WhisperAsrEngine::detectSilence(const int16_t* pcm, size_t samples) {
    // 1. RMS 能量檢測
    long long sum = 0;
    for (size_t i = 0; i < samples; i++) {
        sum += static_cast<long long>(pcm[i]) * pcm[i];
    }
    double rms = std::sqrt(static_cast<double>(sum) / samples);

    // 2. 零交叉率檢測（Zero Crossing Rate）
    int zeroCrossings = 0;
    for (size_t i = 1; i < samples; i++) {
        if ((pcm[i] >= 0 && pcm[i-1] < 0) || (pcm[i] < 0 && pcm[i-1] >= 0)) {
            zeroCrossings++;
        }
    }
    double zcr = static_cast<double>(zeroCrossings) / samples;

    // 3. 組合判斷
    const double SILENCE_RMS_THRESHOLD = 1200.0;
    const double SILENCE_ZCR_THRESHOLD = 0.05;  // 每樣本 5% 交叉率

    bool isLowEnergy = (rms < SILENCE_RMS_THRESHOLD);
    bool isLowVariation = (zcr < SILENCE_ZCR_THRESHOLD);

    // Debug log (每 5 秒輸出一次)
    if (impl_->audioBuffer.size() % (16000 * 5) < samples) {
        LOGD("Silence detection: RMS=%.2f, ZCR=%.4f, isSilence=%d",
             rms, zcr, (isLowEnergy && isLowVariation));
    }

    return isLowEnergy && isLowVariation;
}
```

### 預期效果
- ✅ 減少 50-60% 幻覺
- ✅ 更準確區分「環境噪音」vs「語音」
- ✅ 效能影響極小（< 1ms per frame）

---

## 方案 2：推論前音訊能量預檢（短期實施）

### 原理
在 `runInference()` 之前，計算整段音訊的能量分布：
- 若 90% 以上樣本 RMS < 閾值 → **跳過推論**
- 避免對長時間噪音浪費 NPU 資源

### 實作

**檔案**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

在 `pushAudio()` 中觸發推論前新增檢查：

```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    // ... 現有代碼 ...

    if (isSilence) {
        impl_->silenceDurationMs += (samples * 1000) / 16000;

        if (impl_->audioBuffer.size() > 48000 && impl_->silenceDurationMs >= 700) {
            // === 新增：推論前預檢 ===
            if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
                LOGD("Skipping inference: Audio energy too low (likely silence/noise)");
                impl_->audioBuffer.clear();
                impl_->silenceDurationMs = 0;
                return;
            }

            // 原本的推論邏輯
            std::string text = runInference(...);
            // ...
        }
    }

    // ... 現有代碼 ...
}

// 新增輔助函數
bool WhisperAsrEngine::shouldSkipInference(const int16_t* pcm, size_t samples) {
    const size_t WINDOW_SIZE = 1600;  // 100ms @ 16kHz
    const double ENERGY_THRESHOLD = 1000.0;
    const double SILENCE_RATIO_THRESHOLD = 0.9;  // 90% 以上為靜音

    size_t silentWindows = 0;
    size_t totalWindows = samples / WINDOW_SIZE;

    for (size_t i = 0; i < totalWindows; i++) {
        const int16_t* window = pcm + (i * WINDOW_SIZE);

        long long sum = 0;
        for (size_t j = 0; j < WINDOW_SIZE; j++) {
            sum += static_cast<long long>(window[j]) * window[j];
        }
        double rms = std::sqrt(static_cast<double>(sum) / WINDOW_SIZE);

        if (rms < ENERGY_THRESHOLD) {
            silentWindows++;
        }
    }

    double silenceRatio = static_cast<double>(silentWindows) / totalWindows;
    return silenceRatio >= SILENCE_RATIO_THRESHOLD;
}
```

### 預期效果
- ✅ 減少 60-70% 幻覺
- ✅ **節省 NPU 推論資源**（每次省 50-200ms）
- ✅ 降低功耗

---

## 方案 3：跳過純靜音片段推論（短期實施）

### 原理
在 `stop()` 時的 flush 邏輯中，檢查 buffer 是否為有效語音：

### 實作

**檔案**: `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

```cpp
void WhisperAsrEngine::stop() {
    if (!initialized_) return;
    std::lock_guard<std::mutex> lock(impl_->mutex);

    if (!impl_->audioBuffer.empty()) {
        LOGD("Stop: Flushing remaining audio (%zu samples)", impl_->audioBuffer.size());

        // === 新增：檢查是否應該跳過 ===
        if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
            LOGD("Stop: Skipping flush (audio energy too low)");
            impl_->audioBuffer.clear();
            return;
        }

        // 原本的推論邏輯
        long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
        long startMs = impl_->totalAudioMs - bufferDurationMs;
        long endMs = impl_->totalAudioMs;

        std::string text = runInference(impl_->audioBuffer.data(), impl_->audioBuffer.size());
        if (!text.empty()) {
            emitTranscript(text, startMs, endMs);
        }
        impl_->audioBuffer.clear();
    }
}
```

### 預期效果
- ✅ 避免結束時對剩餘雜音推論
- ✅ 減少 70-80% 的 "static", "Bell" 等幻覺

---

## 方案 4：VAD 預處理（中期考慮）

### 原理
在音訊送入 Whisper 前，使用 **Voice Activity Detection** 預先過濾：
- 只保留「有語音」的段落
- 移除靜音/噪音段

### 候選方案

#### 選項 A：SileroVAD（推薦）
- **優點**：
  - ONNX 模型，可在 RK3588 NPU 加速
  - 開源（MIT License）
  - 準確率高（根據論文可減少 90%+ 幻覺）
- **缺點**：
  - 需整合 ONNX Runtime for RKNN
  - 增加 30-50ms 延遲
  - 模型大小 ~2MB

#### 選項 B：WebRTC VAD
- **優點**：
  - 輕量級（純算法，無需模型）
  - 延遲低（< 10ms）
- **缺點**：
  - 準確率較低（論文中效果一般）

### 實作架構

```
AudioProcessor → VAD → Whisper
                  │
                  └─ 判斷：有語音 → 送入 Whisper
                          無語音 → 丟棄
```

**檔案結構**：
```
meeting-engine/src/main/cpp/
├── vad/
│   ├── VadEngine.h          # VAD 抽象介面
│   ├── SileroVadEngine.cpp  # Silero 實作
│   └── WebRtcVadEngine.cpp  # WebRTC 實作
└── asr/
    └── WhisperAsrEngine.cpp # 整合 VAD
```

### 實作時程評估
- **研究與選型**：1-2 天
- **整合 ONNX Runtime**：2-3 天（若選 Silero）
- **整合 VAD 邏輯**：1 天
- **測試與調優**：2-3 天
- **總計**：6-9 個工作天

### 預期效果
- ✅ 減少 90%+ 幻覺（根據論文數據）
- ⚠️ 增加 30-50ms 延遲
- ⚠️ 增加約 2-5% CPU 使用率

---

## 方案 5：調整 Whisper 內部參數（中期實驗）

### 可嘗試的參數

RK Whisper 可能支援的配置（需確認 RKNN 版本是否暴露）：

#### 1. Temperature（解碼溫度）
```cpp
// 原始 Whisper Python 參數
temperature = 0.0  // 當前可能是預設值
```
- **調低** (0.0)：減少隨機性，更保守（可能減少幻覺）
- **調高** (0.5-1.0)：增加多樣性（可能增加幻覺）

#### 2. Beam Size（束搜尋寬度）
- 當前可能使用 Greedy Decoding (beam_size=1)
- **根據論文**：beam_size=1 幻覺率最低

#### 3. No Speech Threshold
- 偵測音訊中「無語音機率」
- 若超過閾值 → 跳過該段

### 限制
- RK Whisper 為 **靜態量化模型**，許多參數在模型轉換時已固定
- 需查閱 RKNN Toolkit 文件確認可調參數

---

## 實施建議

### Phase 1：立即實施（1-2 天）
1. ✅ **方案 1A**：調整 RMS 閾值到 1200
2. ✅ **方案 1B**：加入 ZCR 檢測
3. ✅ **測試**：在實際裝置上驗證效果

**預期成果**：減少 50-60% 幻覺，無效能損失

### Phase 2：短期實施（3-5 天）
1. ✅ **方案 2**：實作推論前能量預檢
2. ✅ **方案 3**：優化 stop() 時的 flush 邏輯
3. ✅ **測試**：長時間錄音測試（30 分鐘以上）

**預期成果**：總計減少 70-80% 幻覺，節省 NPU 資源

### Phase 3：中期評估（2-3 週後）
1. 🔄 **評估當前方案效果**
2. 🔄 若仍不滿意，考慮 **方案 4（VAD）**
3. 🔄 實驗 **方案 5（Whisper 參數）**

---

## 測試與驗證

### 測試場景設計

#### 場景 1：純靜音
- **輸入**：30 秒麥克風靜音（關閉所有聲源）
- **預期**：0 個轉錄片段
- **當前問題**：可能出現 "thank you", "Bell" 等

#### 場景 2：環境白噪音
- **輸入**：30 秒空調/風扇噪音
- **預期**：0-1 個轉錄片段（可容忍）
- **當前問題**：可能出現 "static", "bells chiming"

#### 場景 3：語音 + 間歇靜音
- **輸入**：說話 3 秒 → 靜音 5 秒 → 說話 3 秒
- **預期**：2 個轉錄片段（對應兩段語音）
- **風險**：靜音段可能產生幻覺

#### 場景 4：低音量語音
- **輸入**：輕聲說話（RMS ≈ 800-1500）
- **預期**：正確轉錄
- **風險**：新閾值可能漏掉

### 驗證指標

| 指標 | 測量方式 | 目標值 |
|------|---------|--------|
| **幻覺率** | (幻覺片段數 / 總片段數) × 100% | < 5% |
| **漏檢率** | (漏掉的語音段 / 實際語音段) × 100% | < 2% |
| **平均推論時間** | NPU 推論耗時（不含音訊採集） | < 300ms |
| **CPU 使用率** | AudioProcessor + AsrEngine | < 15% |

### 日誌分析

在實施方案後，收集以下日誌：

```bash
# 1. 靜音檢測日誌
adb logcat | grep "Silence detection"

# 2. 跳過推論日誌
adb logcat | grep "Skipping inference"

# 3. 轉錄結果
adb logcat | grep "onNativeTranscript"

# 4. 效能數據
adb logcat | grep "runInference"
```

---

## 附錄：參考資料

### 學術論文
- [Investigation of Whisper ASR Hallucinations Induced by Non-Speech Audio (2025)](https://arxiv.org/html/2501.11378v1)
  - 301K 非語音檔案測試
  - BoH (Bag of Hallucinations) 方法
  - VAD 效果驗證

### 開源專案
- [OpenAI Whisper GitHub](https://github.com/openai/whisper)
- [Silero VAD](https://github.com/snakers4/silero-vad)
- [WebRTC VAD Python Binding](https://github.com/wiseman/py-webrtcvad)

### RKNN 文件
- [RKNN Toolkit 2 文檔](https://github.com/airockchip/rknn-toolkit2)
- [RK3588 NPU 使用指南](https://github.com/airockchip/rknn_model_zoo)

---

## 變更歷史

| 日期 | 版本 | 變更內容 | 作者 |
|------|------|---------|------|
| 2026-01-26 | 1.0 | 初版建立，定義問題與解決方案 | Claude |

---

## 討論與回饋

請在此記錄實施過程中的發現、調整建議或遇到的問題：

### 實測數據（待填寫）
- RMS 閾值實測最佳值：`____`
- 幻覺率改善比例：`____%`
- 是否需要啟用 VAD：`是 / 否`

### 問題紀錄
1.
2.
3.
