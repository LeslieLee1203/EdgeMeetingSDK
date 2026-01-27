# Whisper 即時轉錄低延遲優化方案

## 📋 當前問題分析

### 現狀
- **問題**：連續語音不間斷時，需等待 20 秒才觸發推論
- **體驗**：用戶感知延遲過高（最長 20s），不符合即時轉錄需求
- **目標**：將最長推論間隔縮短至 **3 秒以內**

### 現有實現（WhisperAsrEngine.cpp:244-317）
```cpp
// 目前的觸發邏輯
if (impl_->audioBuffer.size() > 48000 && impl_->silenceDurationMs >= SILENCE_THRESHOLD_MS) {
    // 3秒以上 + 靜音≥700ms 才推論
}

if (impl_->audioBuffer.size() > 16000 * 20) {
    // 強制截斷：20秒
}
```

**問題點**：
1. ❌ 沒有 Sliding Window Overlap（完全清空 buffer）
2. ❌ 沒有 Timestamp Commit（無法穩定提交確認字詞）
3. ❌ 沒有 Local Agreement（輸出會抖動）
4. ❌ 連續語音場景下只能靠 20s 強制截斷

---

## 🎯 推薦策略：Hybrid VAD + Timestamp Commit + Local Agreement

基於 reference_01.md 第 174-180 行的 Production 級別方案。

### 核心理念

```
┌─────────────────────────────────────────────────────────────┐
│  VAD 控制           Sliding Window        穩定提交策略      │
│  ↓                  ↓                      ↓                 │
│  決定何時開始/停止  → 保持連續推論上下文 → 只輸出穩定內容   │
└─────────────────────────────────────────────────────────────┘
```

### 三大支柱

#### 1. **Hybrid VAD（混合語音活動檢測）**
- **作用**：控制「何時開始」和「何時結束」一句話
- **實現**：
  - ✅ 已有基礎：`detectSilence()` (RMS + ZCR)
  - 🔧 需優化：區分「句內停頓」vs「句子結束」

#### 2. **Sliding Window with Overlap（滑動窗口 + 重疊）**
- **作用**：保持上下文連續性，避免斷字
- **參數**（參考 reference_01.md:26-29）：
  ```
  window = 8 秒      # 每次推論處理的音訊長度
  step = 3 秒        # 每次推進的時間（達到 3s 延遲目標）
  overlap = 5 秒     # window - step = 重疊區域
  ```

#### 3. **Timestamp Commit（時間戳穩定提交）**
- **作用**：只輸出「已穩定」的詞，未來部分保留在 tail 待修正
- **機制**（參考 reference_01.md:124-139）：
  ```
  commit_time = now - overlap_margin (例如 2秒)
  確認所有 end_time < commit_time 的詞彙
  ```

#### 4. **Local Agreement（多輪一致性確認）**
- **作用**：避免字幕抖動（前面的字被改來改去）
- **策略**（參考 reference_01.md:111-120）：
  - 兩輪 Longest Common Prefix
  - 只提交連續兩輪都一致的前綴

---

## 🔧 具體實現步驟

### Phase 1: Sliding Window 基礎架構

#### 1.1 修改 Buffer 管理策略

**當前**：
```cpp
// 推論後完全清空
impl_->audioBuffer.clear();
```

**改為**：
```cpp
// 保留 overlap 部分
const size_t OVERLAP_SAMPLES = 16000 * 5; // 5秒 overlap
if (impl_->audioBuffer.size() > OVERLAP_SAMPLES) {
    impl_->audioBuffer.erase(
        impl_->audioBuffer.begin(),
        impl_->audioBuffer.begin() + (impl_->audioBuffer.size() - OVERLAP_SAMPLES)
    );
} else {
    impl_->audioBuffer.clear();
}
```

#### 1.2 調整推論觸發邏輯

**修改位置**：`WhisperAsrEngine.cpp:244` `pushAudio()` 方法

**新邏輯**：
```cpp
const size_t WINDOW_SAMPLES = 16000 * 8;   // 8秒窗口
const size_t STEP_SAMPLES = 16000 * 3;     // 3秒步進
const size_t MIN_SAMPLES = 16000 * 3;      // 最小3秒才處理

// 條件 1：達到步進時間 (3秒) + 有足夠窗口 OR 靜音斷句
if (impl_->audioBuffer.size() >= MIN_SAMPLES) {
    bool shouldInfer = false;

    // 策略 A：達到 8 秒窗口，準備推論
    if (impl_->audioBuffer.size() >= WINDOW_SAMPLES) {
        shouldInfer = true;
    }

    // 策略 B：超過 3 秒 + 靜音斷句（自然邊界）
    if (impl_->audioBuffer.size() >= MIN_SAMPLES &&
        impl_->silenceDurationMs >= 500) { // 降低閾值至 500ms
        shouldInfer = true;
    }

    if (shouldInfer && !shouldSkipInference(...)) {
        // 執行推論 + 保留 overlap
    }
}

// 條件 2：強制截斷降至 12 秒（從 20 秒下修）
if (impl_->audioBuffer.size() > 16000 * 12) {
    // 強制推論
}
```

---

### Phase 2: Timestamp Commit 機制

#### 2.1 擴展 RKNN Decoder 輸出

**目標**：獲取每個 word 的 timestamp

**現狀檢查**：
- ✅ RKNN Decoder 已支援 timestamp token（`WhisperAsrEngine.cpp:411-414`）
- ❌ 但目前直接跳過（`continue`）

**修改方向**：
```cpp
// 需要解析 timestamp token (50364~51864)
// 每個 timestamp = (token_id - 50364) * 0.02 秒
std::vector<WordTimestamp> words;

int timestamp_begin = 50364;
if (next_token > timestamp_begin && next_token <= 51864) {
    float time_sec = (next_token - timestamp_begin) * 0.02f;
    // 記錄到 words 結構
}
```

#### 2.2 實現 Commit 邏輯

**新增結構**：
```cpp
struct WordTimestamp {
    std::string word;
    float start_sec;
    float end_sec;
};

struct TranscriptState {
    std::string committed_text;      // 已確認的文字
    std::string tail_text;           // 尾巴（尚未穩定）
    std::vector<WordTimestamp> tail_words;
    long commit_time_ms;
};
```

**Commit 策略**：
```cpp
void commitStableWords(TranscriptState& state, long current_time_ms) {
    const long OVERLAP_MARGIN_MS = 2000; // 2秒安全邊界
    long commit_time = current_time_ms - OVERLAP_MARGIN_MS;

    for (auto& word : state.tail_words) {
        if (word.end_sec * 1000 < commit_time) {
            // 穩定，提交
            state.committed_text += word.word;
        } else {
            // 保留在 tail
            break;
        }
    }
}
```

---

### Phase 3: Local Agreement 穩定化

#### 3.1 追蹤連續輸出

**新增成員變數**（`WhisperAsrEngine::Impl`）：
```cpp
struct Impl {
    // ... 現有成員 ...

    // Local Agreement 追蹤
    std::string prev_hypothesis;      // 上一輪完整輸出
    std::string stable_prefix;        // 穩定前綴
    int agreement_count;              // 連續一致次數
};
```

#### 3.2 兩輪一致性檢查

```cpp
std::string applyLocalAgreement(const std::string& new_hypothesis) {
    // 找最長公共前綴
    size_t common_len = 0;
    size_t min_len = std::min(prev_hypothesis.length(), new_hypothesis.length());

    for (size_t i = 0; i < min_len; i++) {
        if (prev_hypothesis[i] == new_hypothesis[i]) {
            common_len++;
        } else {
            break;
        }
    }

    // 如果公共前綴變長 → 提交穩定部分
    if (common_len > stable_prefix.length()) {
        stable_prefix = new_hypothesis.substr(0, common_len);
    }

    prev_hypothesis = new_hypothesis;
    return stable_prefix; // 只輸出穩定的
}
```

---

### Phase 4: Hybrid VAD 細化

#### 4.1 區分「停頓」與「結束」

**當前問題**：`SILENCE_THRESHOLD_MS = 700ms` 太長，會錯過句內自然停頓

**改進方案**：
```cpp
enum VadState {
    SILENCE,        // 完全靜音（背景噪音）
    PAUSE,          // 句內停頓（輕微能量降低）
    SPEECH          // 活躍語音
};

VadState detectVadState(const int16_t* pcm, size_t samples) {
    double rms = calculateRMS(pcm, samples);
    double zcr = calculateZCR(pcm, samples);

    if (rms < 800 && zcr < 0.03) {
        return SILENCE;  // 真正的靜音
    } else if (rms < 1500 && zcr < 0.06) {
        return PAUSE;    // 可能是停頓
    } else {
        return SPEECH;   // 活躍
    }
}
```

#### 4.2 Finalize 條件優化

**參考 reference_01.md:148-151**：

```cpp
bool shouldFinalizeSentence() {
    // 條件 1：連續靜音 > 500ms
    if (impl_->silenceDurationMs > 500 && vad_state == SILENCE) {
        return true;
    }

    // 條件 2：檢測到句號 + 短暫停頓 (200-400ms)
    if (endsWithPunctuation(tail_text) && impl_->silenceDurationMs > 300) {
        return true;
    }

    // 條件 3：累積超過 10 秒（避免永不結束）
    if (bufferDurationMs > 10000) {
        return true;
    }

    return false;
}
```

---

## 📊 參數配置建議

### MVP 版本（快速實現）

基於 reference_01.md:161-167

| 參數 | 數值 | 說明 |
|------|------|------|
| `WINDOW_SIZE` | 8 秒 | 每次推論的音訊長度 |
| `STEP_SIZE` | 3 秒 | 推進間隔（**達成 3s 目標**） |
| `OVERLAP_SIZE` | 5 秒 | 重疊區域（window - step） |
| `SILENCE_THRESHOLD` | 500ms | 靜音斷句閾值（從 700ms 降低） |
| `FORCE_CUTOFF` | 12 秒 | 強制截斷（從 20s 降低） |

### Production 版本（完整策略）

| 參數 | 數值 | 說明 |
|------|------|------|
| `WINDOW_SIZE` | 8 秒 | 同上 |
| `STEP_SIZE` | 2.5 秒 | 更短延遲（代價：更多推論次數） |
| `OVERLAP_MARGIN` | 2 秒 | Timestamp commit 安全邊界 |
| `LOCAL_AGREEMENT_N` | 2 輪 | 連續一致次數 |
| `SILENCE_SHORT` | 300ms | 句內停頓 |
| `SILENCE_LONG` | 800ms | 句子結束 |

---

## 🛠️ 實作路徑

### 建議分階段實施

#### **第一階段（1-2 天）**：Sliding Window 基礎
- [ ] 修改 `pushAudio()` 觸發邏輯（3秒步進）
- [ ] 實現 overlap buffer 保留
- [ ] 調整強制截斷至 12 秒
- [ ] 測試：確認延遲降至 3-12 秒範圍

#### **第二階段（2-3 天）**：去重與穩定
- [ ] 實現最長重疊 prefix 去重（參考 reference_01.md:78-95）
- [ ] 簡單版 Local Agreement（兩輪一致提交）
- [ ] 測試：確認輸出不重複

#### **第三階段（3-5 天）**：Timestamp 整合
- [ ] 解析 RKNN timestamp token
- [ ] 實現 word-level timestamps
- [ ] Timestamp-based commit
- [ ] 測試：確認輸出更穩定

#### **第四階段（2-3 天）**：VAD 細化
- [ ] 區分 PAUSE vs SILENCE
- [ ] 句子邊界優化
- [ ] 完整測試與調優

---

## 🔍 關鍵程式碼修改點

### 檔案：`WhisperAsrEngine.cpp`

#### 修改點 1：`pushAudio()` 方法（244 行）
```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    // ===== 新增：Sliding Window 邏輯 =====
    const size_t WINDOW_SAMPLES = 16000 * 8;
    const size_t STEP_SAMPLES = 16000 * 3;
    const size_t OVERLAP_SAMPLES = WINDOW_SAMPLES - STEP_SAMPLES;

    // ... 現有 buffer 累積邏輯 ...

    // ===== 修改：推論觸發條件 =====
    bool shouldInfer = false;

    // 條件 1：達到窗口大小
    if (impl_->audioBuffer.size() >= WINDOW_SAMPLES) {
        shouldInfer = true;
    }

    // 條件 2：達到最小步進 + 檢測到靜音斷句
    if (impl_->audioBuffer.size() >= STEP_SAMPLES &&
        impl_->silenceDurationMs >= 500) {
        shouldInfer = true;
    }

    if (shouldInfer && !shouldSkipInference(...)) {
        // 執行推論
        std::string text = runInference(...);

        // ===== 新增：去重處理 =====
        std::string deduped = removePrefixOverlap(impl_->last_output, text);

        // ===== 新增：Local Agreement =====
        std::string stable = applyLocalAgreement(deduped);

        emitTranscript(stable, ...);

        // ===== 修改：保留 overlap 而非清空 =====
        if (impl_->audioBuffer.size() > OVERLAP_SAMPLES) {
            impl_->audioBuffer.erase(
                impl_->audioBuffer.begin(),
                impl_->audioBuffer.begin() + STEP_SAMPLES
            );
        }
    }

    // 強制截斷降至 12 秒
    if (impl_->audioBuffer.size() > 16000 * 12) {
        // ... 同上推論邏輯 ...
    }
}
```

#### 修改點 2：`Impl` 結構（87-119 行）
```cpp
struct WhisperAsrEngine::Impl {
    // ... 現有成員 ...

    // ===== 新增：Sliding Window 狀態 =====
    std::string last_output;           // 上一輪原始輸出
    std::string prev_hypothesis;       // 上一輪假設（用於 Local Agreement）
    std::string stable_prefix;         // 已穩定前綴
    size_t committed_length;           // 已提交字元數

    // ===== 新增：Timestamp 支援 =====
    struct WordTimestamp {
        std::string word;
        float start_sec;
        float end_sec;
    };
    std::vector<WordTimestamp> tail_words;
};
```

#### 修改點 3：新增輔助函式

```cpp
// 去重：找最大重疊後綴/前綴
std::string removePrefixOverlap(const std::string& prev, const std::string& curr) {
    size_t max_overlap = 0;
    size_t max_len = std::min(prev.length(), curr.length());

    for (size_t k = 1; k <= max_len; k++) {
        if (prev.substr(prev.length() - k) == curr.substr(0, k)) {
            max_overlap = k;
        }
    }

    return curr.substr(max_overlap);
}

// Local Agreement：兩輪一致提交
std::string applyLocalAgreement(Impl* impl, const std::string& hypothesis) {
    // 計算最長公共前綴
    size_t common = 0;
    size_t min_len = std::min(impl->prev_hypothesis.length(), hypothesis.length());

    for (size_t i = 0; i < min_len; i++) {
        if (impl->prev_hypothesis[i] == hypothesis[i]) {
            common++;
        } else {
            break;
        }
    }

    // 更新穩定前綴
    if (common > impl->stable_prefix.length()) {
        impl->stable_prefix = hypothesis.substr(0, common);
    }

    impl->prev_hypothesis = hypothesis;

    // 返回穩定部分（扣除已提交的）
    if (impl->stable_prefix.length() > impl->committed_length) {
        std::string new_part = impl->stable_prefix.substr(impl->committed_length);
        impl->committed_length = impl->stable_prefix.length();
        return new_part;
    }

    return "";
}
```

---

## 📚 參考資料來源

1. **reference_01.md**
   - 第 54-63 行：Hybrid VAD 策略
   - 第 161-167 行：MVP 參數配置
   - 第 174-182 行：Production 級方案

2. **whisper_streaming (GitHub)**
   - Local Agreement 實作參考
   - Prefix mechanism

3. **Medium 文章**
   - Sliding Window 實務經驗
   - `prefix` 參數使用技巧

---

## ⚠️ 注意事項

### 性能考量
- **推論頻率增加**：從 20s 一次 → 3s 一次（約 6.7 倍）
- **NPU 負載**：需監控 RK3588 溫度與功耗
- **建議**：可透過 VAD 優化，靜音時跳過推論

### 記憶體管理
- **Overlap Buffer**：額外保留 5 秒（80KB PCM）
- **Timestamp 結構**：每次推論約 50-100 個詞
- **建議**：定期清理已提交的 committed_text

### 測試場景
1. ✅ **連續語音**：長段演講、不間斷討論
2. ✅ **間歇對話**：會議發言、一問一答
3. ✅ **噪音環境**：辦公室、咖啡廳
4. ⚠️ **快速口語**：需確保 overlap 足夠

---

## 🎯 預期效果

### 優化前
- ❌ 連續語音延遲：**20 秒**
- ❌ 輸出不穩定：無去重機制
- ❌ 字幕抖動：頻繁修改前面內容

### 優化後（MVP）
- ✅ 連續語音延遲：**3-8 秒**
- ✅ 輸出去重：prefix overlap 消除
- ✅ 基本穩定：兩輪一致提交

### 優化後（Production）
- ✅ 連續語音延遲：**2.5-5 秒**
- ✅ 輸出精準：word-level timestamp commit
- ✅ 高度穩定：Local Agreement + VAD 細化

---

## 📞 下一步行動

1. **確認需求**：是否需要先實現 MVP，還是直接上 Production 版？
2. **排程規劃**：預計投入時間？優先級？
3. **測試準備**：需要準備哪些測試音檔？（建議：10-30秒連續語音片段）

如有疑問或需要更詳細的程式碼範例，請隨時提出！
