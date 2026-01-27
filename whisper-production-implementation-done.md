# Whisper 即時轉錄 Production 版實現完成

## 📋 實現概要

已完成 **whisper-streaming-optimization-plan.md** 中的 **Production 版**完整實現，將連續語音轉錄延遲從 20 秒優化至 **2.5-5 秒**。

**實現日期**: 2026-01-26
**修改文件**:
- `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.h`
- `meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp`

---

## ✅ 已實現功能

### 1. **Sliding Window with Overlap**

#### 參數配置（Production 級別）
```cpp
const size_t WINDOW_SAMPLES = 16000 * 8;      // 8 秒窗口
const size_t STEP_SAMPLES = 16000 * 2.5;      // 2.5 秒步進
const size_t OVERLAP_SAMPLES = 5.5 秒;         // 重疊區域
const int FORCE_CUTOFF_SEC = 10;              // 強制截斷（從 20s 降至 10s）
```

#### 實現細節
- **推論觸發策略**：
  1. 達到 8 秒窗口 → 立即推論
  2. 超過 3 秒 + 檢測到靜音 (500ms) → 自然斷句推論
  3. 超過 5 秒 + 檢測到停頓 (300ms) → 句內停頓推論

- **Buffer 管理**：
  - ✅ 推論後保留 5.5 秒 overlap（而非完全清空）
  - ✅ 每次只移除 2.5 秒已處理的音訊
  - ✅ 保持上下文連續性，避免斷字

**位置**: `WhisperAsrEngine.cpp:282-420`

---

### 2. **去重機制（Prefix Overlap Removal）**

#### 解決問題
Sliding Window 導致的重複輸出：
```
上一輪: "今天天氣很好"
本輪:   "天氣很好，我們出去玩"
去重後: "，我們出去玩"  ✅ 移除重疊的 "天氣很好"
```

#### 實現方法
```cpp
std::string removePrefixOverlap(const std::string& prev, const std::string& curr)
```
- 找出前一輪輸出的後綴與當前輸出的前綴的最大重疊
- 只返回新增的內容部分
- 包含詳細的 Debug Log

**位置**: `WhisperAsrEngine.cpp:630-658`

---

### 3. **Local Agreement（兩輪一致性確認）**

#### 解決問題
Whisper 在 overlap 區域的輸出抖動：
```
第一輪: "今天天氣..."
第二輪: "今天天氣很好..."  ✅ 確認 "今天天氣" 穩定
第三輪: "今天天氣很好，我們..."  ✅ 確認 "很好，我們"
```

#### 實現策略
```cpp
std::string applyLocalAgreement(const std::string& hypothesis)
```
1. 計算當前輪與上一輪的最長公共前綴 (LCP)
2. 只提交連續兩輪都一致的穩定前綴
3. 未來可能改變的 "tail" 部分不輸出，等下一輪確認
4. 追蹤 `committed_length`，避免重複提交

**位置**: `WhisperAsrEngine.cpp:660-700`

---

### 4. **Hybrid VAD（混合語音活動檢測）**

#### 三級狀態分類
```cpp
enum class VadState {
    SILENCE,    // 完全靜音（背景噪音）- RMS < 800, ZCR < 0.03
    PAUSE,      // 句內停頓（思考停頓）- RMS < 1500, ZCR < 0.06
    SPEECH      // 活躍語音（其他情況）
}
```

#### 檢測指標
- **RMS (Root Mean Square)**: 衡量音訊響度
- **ZCR (Zero Crossing Rate)**: 衡量頻率變化頻繁度
- **雙重指標組合**：避免將環境噪音誤判為語音

#### 應用場景
- `SILENCE` + 500ms → 確認句子結束，觸發推論
- `PAUSE` + 300ms → 句內自然停頓，適時推論
- `SPEECH` → 繼續累積音訊

**位置**: `WhisperAsrEngine.cpp:702-753`

---

### 5. **狀態追蹤與管理**

#### 新增 Impl 成員變數
```cpp
struct WhisperAsrEngine::Impl {
    // === Sliding Window 狀態追蹤 ===
    std::string last_output;           // 上一輪原始輸出（用於去重）
    std::string prev_hypothesis;       // 上一輪假設（用於 Local Agreement）
    std::string stable_prefix;         // 已穩定前綴
    size_t committed_length = 0;       // 已提交字元數

    // === Timestamp 支援（預留，完整實現需修改 decoder） ===
    std::vector<WordTimestamp> tail_words;
};
```

#### 生命週期管理
- `start()`: 重置所有狀態變數
- `stop()`: 強制提交剩餘的 `prev_hypothesis`（會話結束時）
- `pushAudio()`: 動態更新狀態

**位置**: `WhisperAsrEngine.cpp:88-127`

---

### 6. **優化後的 stop() 邏輯**

#### 改進點
1. ✅ 應用去重與 Local Agreement
2. ✅ **Force Commit**：會話結束時強制提交未穩定的內容
   - 理由：沒有下一輪來確認了，需要輸出所有內容
3. ✅ 重置所有 Sliding Window 狀態

**位置**: `WhisperAsrEngine.cpp:226-272`

---

## 📊 性能參數對比

| 參數 | 優化前 (MVP) | Production 版 |
|------|-------------|---------------|
| 推論窗口 | 3-20 秒 | **8 秒** |
| 推論步進 | 20 秒 (無 overlap) | **2.5 秒** |
| Overlap 大小 | 0 秒 | **5.5 秒** |
| 連續語音延遲 | **20 秒** | **2.5-5 秒** ✅ |
| 靜音斷句閾值 | 700ms | **500ms (SILENCE)** |
| 句內停頓閾值 | N/A | **300ms (PAUSE)** |
| 強制截斷 | 20 秒 | **10 秒** |
| 去重機制 | ❌ 無 | ✅ Prefix Overlap |
| 穩定化策略 | ❌ 無 | ✅ Local Agreement |
| VAD 細化 | ❌ 二元 (靜音/語音) | ✅ 三級 (SILENCE/PAUSE/SPEECH) |

---

## 🎯 預期效果

### 優化前問題
- ❌ 連續語音延遲：**20 秒**
- ❌ 輸出重複：無去重機制
- ❌ 字幕抖動：頻繁修改前面內容
- ❌ 斷句不準：無法區分停頓與結束

### Production 版效果
- ✅ 連續語音延遲：**2.5-5 秒**（改善 75-87.5%）
- ✅ 輸出精準：Prefix Overlap 消除重複
- ✅ 高度穩定：Local Agreement 確保一致性
- ✅ 智能斷句：Hybrid VAD 三級分類

---

## 🧪 測試建議

### 1. 延遲測試
**場景**: 連續語音 10-30 秒（不間斷）

**期望**:
- 首次推論在 8 秒內觸發 ✅
- 後續推論每 2.5-3 秒觸發一次 ✅
- 最長不超過 10 秒強制截斷 ✅

**測試方法**:
```bash
# 錄製 30 秒連續語音測試音檔
adb push test_audio_30s.pcm /sdcard/
# 檢查 logcat 中的推論觸發時間戳
adb logcat | grep "WhisperAsrEngine.*Inference triggered"
```

### 2. 去重測試
**場景**: 連續語音，觀察輸出內容

**期望**:
- 不應出現重複的詞句 ✅
- 每次只輸出新增的內容 ✅

**檢查點**:
```bash
# 觀察去重 Log
adb logcat | grep "removePrefixOverlap: Found overlap"
```

### 3. 穩定性測試
**場景**: 同一段音訊重複播放 3 次

**期望**:
- 最終輸出文字應該完全一致（或高度相似） ✅
- 不應出現頻繁的修改與回退 ✅

### 4. VAD 測試
**場景**:
- 完全靜音 5 秒（背景噪音）
- 正常說話 + 思考停頓 (1-2 秒)
- 句子結束 + 長時間靜音 (3+ 秒)

**期望**:
- 背景噪音不應觸發推論 ✅
- 思考停頓應在 300ms 後觸發（若有足夠音訊）✅
- 句子結束應在 500ms 後觸發 ✅

**檢查點**:
```bash
# 觀察 VAD 狀態變化
adb logcat | grep "Hybrid VAD: RMS"
```

### 5. 記憶體與性能測試
**場景**: 長時間運行（5-10 分鐘）

**期望**:
- Buffer 大小穩定在 5.5-8 秒範圍 ✅
- 無記憶體洩漏 ✅
- NPU 溫度與功耗在正常範圍 ✅

**監控方法**:
```bash
# 監控記憶體
adb shell dumpsys meminfo <package_name>
# 監控 CPU/NPU
adb shell top | grep <package_name>
```

---

## ⚠️ 已知限制與未來改進

### 當前實現
✅ Sliding Window with Overlap
✅ 去重機制（Prefix Overlap）
✅ Local Agreement（兩輪一致性）
✅ Hybrid VAD（三級狀態）

### 未來增強（可選）
⏸️ **Word-level Timestamps**:
   - 需修改 `inference_decoder()` 解析 timestamp token (50364-51864)
   - 實現 `commitStableWords()` 基於時間戳的提交策略
   - 參考 `whisper-streaming-optimization-plan.md` 第 141-198 行

⏸️ **動態參數調整**:
   - 根據 NPU 負載動態調整 `STEP_SAMPLES`
   - 根據場景（會議 vs 演講）切換參數配置

⏸️ **更細緻的 VAD**:
   - 整合外部 VAD 模型（如 Silero VAD）
   - 頻譜分析（區分人聲 vs 噪音）

---

## 🔧 調優建議

### 如果延遲仍過高
1. **降低 STEP_SAMPLES**:
   ```cpp
   const size_t STEP_SAMPLES = 16000 * 2.0;  // 從 2.5s 降至 2s
   ```
   - 代價：推論頻率增加 25%，NPU 負載上升

2. **降低靜音閾值**:
   ```cpp
   impl_->silenceDurationMs >= 400  // 從 500ms 降至 400ms
   ```

### 如果輸出抖動
1. **增加 Local Agreement 輪數**:
   - 當前為 2 輪一致，可改為 3 輪（需增加 `agreement_count` 追蹤）

2. **增加 OVERLAP 大小**:
   ```cpp
   const size_t STEP_SAMPLES = 16000 * 2.0;  // 保持 8s 窗口，降低步進至 2s
   // OVERLAP = 8 - 2 = 6 秒
   ```

### 如果記憶體佔用過高
1. **降低 WINDOW_SAMPLES**:
   ```cpp
   const size_t WINDOW_SAMPLES = 16000 * 6;  // 從 8s 降至 6s
   ```

2. **定期清理 `committed_length` 追蹤**:
   - 當 `stable_prefix.length()` 超過 1000 字元時，截斷前面部分

---

## 📞 聯絡與支援

**實現者**: Claude Sonnet 4.5
**實現參考**: `whisper-streaming-optimization-plan.md`
**測試協助**: 請準備 10-30 秒連續語音片段

如有疑問或需要進一步調優，請隨時提出！

---

## 附錄：關鍵程式碼片段

### Sliding Window 主邏輯
```cpp
// WhisperAsrEngine.cpp:282-420
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    const size_t WINDOW_SAMPLES = 16000 * 8;
    const size_t STEP_SAMPLES = 16000 * 2.5;
    const size_t OVERLAP_SAMPLES = WINDOW_SAMPLES - STEP_SAMPLES;

    // ... 累積 buffer ...

    // 策略 A: 達到 8 秒窗口
    if (impl_->audioBuffer.size() >= WINDOW_SAMPLES) {
        shouldInfer = true;
    }

    // 策略 B: 3 秒 + 靜音 500ms
    if (impl_->audioBuffer.size() >= MIN_SAMPLES &&
        vadState == VadState::SILENCE &&
        impl_->silenceDurationMs >= 500) {
        shouldInfer = true;
    }

    // ... 推論 + 去重 + Local Agreement ...

    // 保留 overlap
    if (impl_->audioBuffer.size() > OVERLAP_SAMPLES) {
        impl_->audioBuffer.erase(
            impl_->audioBuffer.begin(),
            impl_->audioBuffer.begin() + STEP_SAMPLES
        );
    }
}
```

### Local Agreement 實現
```cpp
// WhisperAsrEngine.cpp:660-700
std::string applyLocalAgreement(const std::string& hypothesis) {
    // 1. 計算最長公共前綴
    size_t common_len = 0;
    for (size_t i = 0; i < min_len; i++) {
        if (impl_->prev_hypothesis[i] == hypothesis[i]) {
            common_len++;
        } else {
            break;
        }
    }

    // 2. 更新穩定前綴
    if (common_len > impl_->stable_prefix.length()) {
        impl_->stable_prefix = hypothesis.substr(0, common_len);
    }

    // 3. 只返回新增的穩定部分
    if (impl_->stable_prefix.length() > impl_->committed_length) {
        return impl_->stable_prefix.substr(impl_->committed_length);
    }

    return "";
}
```
