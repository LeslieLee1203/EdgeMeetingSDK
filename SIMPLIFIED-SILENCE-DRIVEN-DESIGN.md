# Whisper ASR 簡化設計：純靜音驅動推論

## 設計理念

根據用戶反饋「已經有轉錄文字，但我覺得效果不好。請先移除去重設計，完全以靜音作為推論分割基準」，本次簡化移除了複雜的 Sliding Window、Prefix Overlap Deduplication 和 Local Agreement 機制，改為**純靜音驅動的推論策略**。

## 核心變更

### 1. 移除的複雜機制

#### Sliding Window 重疊保留
```cpp
// 移除前：保留 5.5 秒重疊音頻
const size_t WINDOW_SIZE = 16000 * 8;     // 8s
const size_t STEP_SIZE = 16000 * 2.5;     // 2.5s 步進
const size_t OVERLAP_SIZE = WINDOW_SIZE - STEP_SIZE; // 5.5s 重疊

// 推論後保留重疊部分
std::vector<int16_t> nextBuffer;
if (audioBuffer.size() > OVERLAP_SIZE) {
    nextBuffer.assign(
        audioBuffer.end() - OVERLAP_SIZE,
        audioBuffer.end()
    );
}
audioBuffer = std::move(nextBuffer);

// 移除後：完全清空 buffer
audioBuffer.clear();
```

#### Prefix Overlap Deduplication
```cpp
// 移除的函數：removePrefixOverlap()
// 功能：比對前後兩次推論結果，移除重複的前綴
// 例如：
//   上一輪: "今天天氣很好"
//   本輪:   "天氣很好，我們出去玩"
//   去重後: "，我們出去玩"
```

#### Local Agreement
```cpp
// 移除的函數：applyLocalAgreement()
// 功能：使用最長公共前綴 (LCP) 確保輸出穩定性
// 問題：過於複雜，committed_length 追蹤邏輯容易出錯
```

### 2. 簡化後的 pushAudio() 邏輯

```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    // 配置參數
    const size_t MIN_SAMPLES = 16000 * 2;    // 最小 2 秒
    const size_t MAX_SAMPLES = 16000 * 20;   // 最大 20 秒（強制切割）
    const int SILENCE_THRESHOLD_MS = 500;    // 500ms 靜音觸發推論

    // 1. 累積音頻
    impl_->audioBuffer.insert(impl_->audioBuffer.end(), pcm, pcm + samples);
    impl_->totalAudioMs += (samples * 1000) / 16000;

    // 2. 檢測 VAD 狀態
    VadState vadState = detectVadState(pcm, samples);

    // 3. 更新靜音持續時間
    if (vadState == VadState::SILENCE) {
        impl_->silenceDurationMs += (samples * 1000) / 16000;
    } else {
        impl_->silenceDurationMs = 0;  // 重置
    }

    // 4. 判斷是否需要推論
    bool shouldInfer = false;

    // 策略 1：最小時長 + 檢測到靜音
    if (impl_->audioBuffer.size() >= MIN_SAMPLES &&
        vadState == VadState::SILENCE &&
        impl_->silenceDurationMs >= SILENCE_THRESHOLD_MS) {
        shouldInfer = true;
    }

    // 策略 2：達到最大時長，強制切割
    if (!shouldInfer && impl_->audioBuffer.size() >= MAX_SAMPLES) {
        shouldInfer = true;
    }

    // 5. 執行推論並完全清空 buffer
    if (shouldInfer) {
        if (!shouldSkipInference(...)) {
            std::string text = runInference(...);
            if (!text.empty()) {
                emitTranscript(text, startMs, endMs);
            }
        }

        // 關鍵：完全清空 buffer，不保留任何重疊
        impl_->audioBuffer.clear();
        impl_->silenceDurationMs = 0;
    }
}
```

### 3. 簡化的 Impl 結構體

```cpp
struct WhisperAsrEngine::Impl {
    // RKNN 模型上下文
    RknnModelContext encoder;
    RknnModelContext decoder;

    // Mel 濾波器和詞彙表
    float* mel_filters = nullptr;
    VocabEntry* vocab = nullptr;
    int vocab_size = 0;
    int task_code = 50259;  // <|transcribe|> token

    // 運行時狀態（簡化）
    std::mutex mutex;
    std::vector<int16_t> audioBuffer;          // 累積的音頻
    int silenceDurationMs = 0;                 // 當前靜音持續時間
    int segmentCounter = 0;                    // 片段計數器
    long totalAudioMs = 0;                     // 總音頻時長
    TranscriptCallback transcriptCallback;     // 轉錄回調

    // 移除的狀態變數：
    // - prev_hypothesis（用於 Local Agreement）
    // - stable_prefix（用於 Local Agreement）
    // - committed_length（用於 Local Agreement）
    // - lastEmittedText（用於 deduplication）
};
```

### 4. 簡化的 start() 和 stop()

```cpp
void WhisperAsrEngine::start() {
    std::lock_guard<std::mutex> lock(impl_->mutex);
    impl_->audioBuffer.clear();
    impl_->silenceDurationMs = 0;
    impl_->segmentCounter = 0;
    impl_->totalAudioMs = 0;
    LOGD("WhisperAsrEngine: Started (Simplified Silence-Driven Mode)");
}

void WhisperAsrEngine::stop() {
    std::lock_guard<std::mutex> lock(impl_->mutex);

    // 處理剩餘音頻
    if (!impl_->audioBuffer.empty()) {
        const size_t MIN_SAMPLES = 16000 * 2;
        if (impl_->audioBuffer.size() >= MIN_SAMPLES) {
            if (!shouldSkipInference(...)) {
                std::string text = runInference(...);
                if (!text.empty()) {
                    emitTranscript(text, startMs, endMs);
                }
            }
        }
        impl_->audioBuffer.clear();
    }

    LOGD("WhisperAsrEngine: Stopped");
}
```

## 優勢

1. **邏輯清晰**：每次靜音都是一個自然的分割點，完全清空 buffer
2. **無重複問題**：沒有重疊保留，自然不會有去重困擾
3. **狀態簡單**：只需追蹤 `audioBuffer` 和 `silenceDurationMs`
4. **易於調整**：只需調整 `MIN_SAMPLES`、`MAX_SAMPLES` 和 `SILENCE_THRESHOLD_MS` 三個參數

## 可調參數

```cpp
// 在 WhisperAsrEngine.cpp 中
const size_t MIN_SAMPLES = 16000 * 2;      // 最小推論時長（目前 2 秒）
const size_t MAX_SAMPLES = 16000 * 20;     // 最大推論時長（目前 20 秒）
const int SILENCE_THRESHOLD_MS = 500;       // 靜音觸發閾值（目前 500ms）
```

### 調整建議

- **延遲敏感**：降低 `MIN_SAMPLES` 至 1.5 秒，降低 `SILENCE_THRESHOLD_MS` 至 300ms
- **長句子處理**：保持 `MAX_SAMPLES` 在 20 秒，避免句子被強制切斷
- **噪音環境**：提高 `SILENCE_THRESHOLD_MS` 至 800ms，避免誤觸發

## 保留的機制

### Hybrid VAD（三級語音活動檢測）
```cpp
enum class VadState {
    SILENCE,  // 完全靜音（背景噪音）
    PAUSE,    // 句內停頓
    SPEECH    // 活躍語音
};

VadState detectVadState(const int16_t* pcm, size_t samples);
```

### shouldSkipInference（能量過濾）
```cpp
bool shouldSkipInference(const int16_t* pcm, size_t samples) {
    // 檢查音頻能量和靜音比例
    // ENERGY_THRESHOLD = 30.0
    // SILENCE_RATIO_THRESHOLD = 0.95
}
```

## 測試建議

1. **正常對話場景**：檢查是否能在自然停頓處正確分割
2. **長句子場景**：檢查 20 秒強制切割是否影響理解
3. **快速對話場景**：檢查 500ms 靜音是否過於敏感
4. **噪音環境**：檢查是否會誤將噪音當作語音

## 與 Production 版本的對比

| 特性 | Production 版 | 簡化版 |
|------|--------------|--------|
| Sliding Window 重疊 | ✓ (5.5s) | ✗ |
| Prefix Overlap Deduplication | ✓ | ✗ |
| Local Agreement | ✓ (LCP) | ✗ |
| Hybrid VAD | ✓ | ✓ |
| 能量過濾 | ✓ | ✓ |
| 狀態變數數量 | 9 | 5 |
| 程式碼複雜度 | 高 | 低 |
| 調試難度 | 高 | 低 |

## 編譯驗證

```bash
./gradlew :meeting-engine:build
```

狀態：✅ 編譯成功（2026-01-27）

## 下一步

等待實際測試反饋，根據以下指標調整參數：
- 轉錄延遲（期望 < 3 秒）
- 分割準確性（是否在句子邊界）
- 長句子完整性（20 秒切割影響）
- 噪音魯棒性
