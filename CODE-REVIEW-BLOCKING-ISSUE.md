# Code Review：推論阻塞音頻累積問題

## 🚨 嚴重問題：Mutex 阻塞導致音頻延遲

### 問題位置

`meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp:246-326`

### 問題描述

在 `pushAudio()` 方法中，整個方法被 `std::lock_guard` 保護，包括耗時的 `runInference()` 調用：

```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    if (!initialized_ || samples == 0) return;

    std::lock_guard<std::mutex> lock(impl_->mutex);  // 🔒 鎖住整個方法

    // 1. 累積音頻（快速，< 1ms）
    impl_->audioBuffer.insert(impl_->audioBuffer.end(), pcm, pcm + samples);
    impl_->totalAudioMs += (samples * 1000) / 16000;

    // 2. VAD 檢測（快速，< 1ms）
    VadState vadState = detectVadState(pcm, samples);

    // 3. 判斷是否推論
    if (shouldInfer) {
        // 4. 能量檢查（快速，< 10ms）
        if (shouldSkipInference(...)) { ... }

        // 5. 執行推論（🐌 慢速，350-1400ms）
        std::string text = runInference(impl_->audioBuffer.data(), impl_->audioBuffer.size());

        // 6. 輸出結果（快速，< 1ms）
        if (!text.empty()) {
            emitTranscript(text, startMs, endMs);
        }

        impl_->audioBuffer.clear();
        impl_->silenceDurationMs = 0;
    }
}  // 🔓 鎖在這裡才釋放
```

### 推論耗時分析

| 階段 | 操作 | 耗時 | 位置 |
|------|------|------|------|
| 1 | PCM → Float 轉換 | ~5ms | runInference():458 |
| 2 | FFT + Mel Spectrogram | 50-100ms | audio_preprocess() |
| 3 | RKNN Encoder Inference | 100-300ms | inference_encoder() |
| 4 | RKNN Decoder (100 iterations) | 200-1000ms | inference_decoder() |
| **總計** | - | **350-1400ms** | - |

### 時序示意圖

```
音頻線程 (Oboe AudioCallback, 每 10ms 回調一次):
────────────────────────────────────────────────────────────>

T=0ms:    [pushAudio #1] 獲取鎖 → 累積音頻 → 觸發推論 → runInference() 開始
           |
           |---- 鎖被持有 --------------------(持續 500ms)----------------------|
           |                                                                    |
T=10ms:   [pushAudio #2] 嘗試獲取鎖 → ❌ 阻塞等待                               |
T=20ms:   [pushAudio #3] 嘗試獲取鎖 → ❌ 阻塞等待                               |
T=30ms:   [pushAudio #4] 嘗試獲取鎖 → ❌ 阻塞等待                               |
T=40ms:   [pushAudio #5] 嘗試獲取鎖 → ❌ 阻塞等待                               |
...       (40+ 次 pushAudio 調用全部阻塞)                                       |
           |                                                                    |
T=500ms:  [pushAudio #1] runInference() 完成 → 釋放鎖 -------------------------+
           |
T=501ms:  [pushAudio #2] 獲取鎖 → 處理 10ms 音頻 → 釋放鎖
T=502ms:  [pushAudio #3] 獲取鎖 → 處理 10ms 音頻 → 釋放鎖
...

結果：500ms 期間累積的音頻延遲處理，實時性嚴重下降
```

### 實際影響

1. **音頻延遲累積**
   - Oboe 每 10ms 調用一次 `onAudioReady()`
   - AudioProcessor 調用 `pushAudio()` 但被 mutex 阻塞
   - 500ms 推論期間，約 50 次 `pushAudio()` 調用被串行化
   - 實際延遲可能達到 1-2 秒

2. **Oboe Buffer 風險**
   - Oboe 的內部 buffer 有限（通常 2-4 個 buffer）
   - 如果 `onAudioReady()` 阻塞時間過長，新音頻無處寫入
   - 可能導致 `AAUDIO_ERROR_TIMEOUT` 或音頻丟失

3. **實時性下降**
   - 目標延遲：< 1 秒（用戶可接受）
   - 當前延遲：2-3 秒（包含推論阻塞 + 靜音等待）
   - 用戶體驗：明顯的「卡頓感」

4. **CPU 不必要的忙等待**
   - 50 次阻塞的 `pushAudio()` 調用佔用 CPU 時間
   - AudioCallback 線程優先級較高，阻塞可能影響其他音頻處理

### 驗證方法

在 `pushAudio()` 和 `runInference()` 中添加時間戳 log：

```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    auto t0 = std::chrono::steady_clock::now();

    std::lock_guard<std::mutex> lock(impl_->mutex);

    auto t1 = std::chrono::steady_clock::now();
    auto lockWaitMs = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();

    if (lockWaitMs > 10) {
        LOGW("pushAudio: Waited %lld ms to acquire lock (BLOCKING DETECTED)", lockWaitMs);
    }

    // ... 原邏輯 ...
}

std::string WhisperAsrEngine::runInference(...) {
    auto t0 = std::chrono::steady_clock::now();

    // ... 推論邏輯 ...

    auto t1 = std::chrono::steady_clock::now();
    auto inferenceMs = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();
    LOGD("runInference: Took %lld ms (HOLDING LOCK)", inferenceMs);

    return result_text;
}
```

預期 log 輸出：
```
D/WhisperAsrEngine: pushAudio #1: Waited 0 ms to acquire lock
D/WhisperAsrEngine: runInference: Took 487 ms (HOLDING LOCK)
W/WhisperAsrEngine: pushAudio #2: Waited 487 ms to acquire lock (BLOCKING DETECTED)
W/WhisperAsrEngine: pushAudio #3: Waited 475 ms to acquire lock (BLOCKING DETECTED)
...
```

---

## 🔧 修復方案

### 方案 A：後台推論線程（推薦）✅

**核心思想**：將推論工作移到專用的後台線程，`pushAudio()` 只負責快速累積音頻和觸發推論請求。

#### 設計架構

```
[Audio Thread (Oboe)]                [Inference Thread]
        |                                    |
        v                                    |
  pushAudio(pcm)                             |
        |                                    |
    [快速操作]                                |
    - 累積到 buffer                           |
    - VAD 檢測                                |
    - 檢查是否觸發推論                         |
        |                                    |
        | 觸發推論                             |
        |                                    v
    複製 buffer  ---------------------->  [推論隊列]
        |                                    |
    釋放鎖 <-- 立即返回                        |
        |                                    v
        |                              取出任務
        |                                    |
        |                              runInference()
        |                              (350-1400ms)
        |                                    |
        |                                    v
        |                              emitTranscript()
        |                                    |
        v                                    v
  持續累積新音頻...                    等待下一個任務...
```

#### 實作摘要

需要修改的文件：
1. `WhisperAsrEngine.h` - 添加線程和隊列成員
2. `WhisperAsrEngine.cpp` - 實現生產者-消費者模式

核心變更：

**1. Impl 結構體添加線程相關成員**

```cpp
struct WhisperAsrEngine::Impl {
    // ... 現有成員 ...

    // 線程安全的推論請求隊列
    struct InferenceRequest {
        std::vector<int16_t> audioData;  // 複製的音頻數據
        long startMs;
        long endMs;
    };

    std::queue<InferenceRequest> inferenceQueue;
    std::mutex queueMutex;               // 保護 queue（不是 audioBuffer）
    std::condition_variable queueCv;     // 通知推論線程

    std::thread inferenceThread;
    std::atomic<bool> shouldStopThread{false};
    std::atomic<bool> isInferring{false}; // 防止重複推論

    // 原 mutex 只保護 audioBuffer（快速操作）
    std::mutex audioMutex;               // 重命名，語義更清晰
    std::vector<int16_t> audioBuffer;
    // ...
};
```

**2. pushAudio() 改為非阻塞**

```cpp
void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    if (!initialized_ || samples == 0) return;

    {
        std::lock_guard<std::mutex> lock(impl_->audioMutex);  // 短暫鎖定

        impl_->audioBuffer.insert(impl_->audioBuffer.end(), pcm, pcm + samples);
        impl_->totalAudioMs += (samples * 1000) / 16000;

        VadState vadState = detectVadState(pcm, samples);

        if (vadState == VadState::SILENCE) {
            impl_->silenceDurationMs += (samples * 1000) / 16000;
        } else {
            impl_->silenceDurationMs = 0;
        }

        bool shouldInfer = false;

        if (impl_->audioBuffer.size() >= MIN_SAMPLES &&
            vadState == VadState::SILENCE &&
            impl_->silenceDurationMs >= SILENCE_THRESHOLD_MS) {
            shouldInfer = true;
        }

        if (!shouldInfer && impl_->audioBuffer.size() >= MAX_SAMPLES) {
            shouldInfer = true;
        }

        if (shouldInfer && !impl_->isInferring.load()) {
            // 防止重複推論：如果後台線程還在處理，跳過本次
            if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
                impl_->audioBuffer.clear();
                impl_->silenceDurationMs = 0;
                return;  // 鎖在這裡釋放
            }

            // 複製音頻數據到推論請求
            InferenceRequest req;
            req.audioData = impl_->audioBuffer;  // 複製（std::vector 深拷貝）
            long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
            req.startMs = impl_->totalAudioMs - bufferDurationMs;
            req.endMs = impl_->totalAudioMs;

            // 清空當前 buffer（立即釋放記憶體）
            impl_->audioBuffer.clear();
            impl_->silenceDurationMs = 0;

            // 推送到推論隊列（快速操作）
            {
                std::lock_guard<std::mutex> qlock(impl_->queueMutex);
                impl_->inferenceQueue.push(std::move(req));  // move 避免再次複製
            }
            impl_->queueCv.notify_one();  // 通知推論線程
        }
    }  // audioMutex 在這裡釋放，總耗時 < 5ms
}
```

**3. 後台推論線程**

```cpp
void WhisperAsrEngine::Impl::inferenceThreadFunc() {
    LOGD("Inference thread started");

    while (!shouldStopThread.load()) {
        InferenceRequest req;

        // 等待推論請求
        {
            std::unique_lock<std::mutex> lock(queueMutex);
            queueCv.wait(lock, [this] {
                return !inferenceQueue.empty() || shouldStopThread.load();
            });

            if (shouldStopThread.load()) break;

            if (!inferenceQueue.empty()) {
                req = std::move(inferenceQueue.front());
                inferenceQueue.pop();
            } else {
                continue;
            }
        }

        // 執行推論（不持有任何鎖）
        isInferring.store(true);

        LOGD("Inference thread: Processing %zu samples", req.audioData.size());
        std::string text = runInference(req.audioData.data(), req.audioData.size());

        if (!text.empty()) {
            emitTranscript(text, req.startMs, req.endMs);
            LOGD("Transcript emitted: '%s' [%ld-%ld]", text.c_str(), req.startMs, req.endMs);
        }

        isInferring.store(false);
    }

    LOGD("Inference thread stopped");
}
```

**4. init() 中啟動線程**

```cpp
bool WhisperAsrEngine::init(const std::string& modelsPath, const std::string& language) {
    // ... 現有初始化邏輯 ...

    // 啟動推論線程
    impl_->shouldStopThread.store(false);
    impl_->inferenceThread = std::thread([this]() {
        impl_->inferenceThreadFunc();
    });

    initialized_ = true;
    return true;
}
```

**5. release() 中停止線程**

```cpp
void WhisperAsrEngine::release() {
    if (!initialized_) return;

    // 停止推論線程
    impl_->shouldStopThread.store(true);
    impl_->queueCv.notify_all();

    if (impl_->inferenceThread.joinable()) {
        impl_->inferenceThread.join();
    }

    // ... 現有清理邏輯 ...

    initialized_ = false;
}
```

#### 優點

✅ **完全消除阻塞**：`pushAudio()` 耗時 < 5ms，不影響音頻累積
✅ **充分利用多核**：推論在專用線程，不佔用音頻線程 CPU
✅ **記憶體可控**：透過 `isInferring` flag 防止推論隊列無限增長
✅ **簡單清晰**：生產者-消費者模式，易於理解和調試

#### 缺點

⚠️ **記憶體複製開銷**：每次推論需要複製 32-640KB 音頻數據（2-20 秒 @ 16kHz）
   - 2 秒音頻：32,000 樣本 × 2 bytes = 64 KB
   - 20 秒音頻：320,000 樣本 × 2 bytes = 640 KB
   - 但相比推論時間（500ms），複製耗時（< 1ms）可忽略

⚠️ **線程管理複雜度**：需要正確處理線程生命週期和同步

---

### 方案 B：雙 Mutex 設計（備選）

**核心思想**：使用兩個 mutex，分離快速操作和慢速操作。

```cpp
struct WhisperAsrEngine::Impl {
    std::mutex audioMutex;      // 保護 audioBuffer（快速操作）
    std::mutex inferenceMutex;  // 保護推論狀態（慢速操作）
    std::atomic<bool> isInferring{false};
    // ...
};

void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    {
        std::lock_guard<std::mutex> lock(impl_->audioMutex);
        impl_->audioBuffer.insert(...);
        // VAD 檢測、觸發判斷...
    }  // audioMutex 釋放

    if (shouldInfer && !impl_->isInferring.load()) {
        std::lock_guard<std::mutex> lock(impl_->inferenceMutex);
        impl_->isInferring.store(true);

        // 複製 buffer（需要再次鎖 audioMutex）
        std::vector<int16_t> bufferCopy;
        {
            std::lock_guard<std::mutex> lock(impl_->audioMutex);
            bufferCopy = impl_->audioBuffer;
            impl_->audioBuffer.clear();
        }

        // 推論（不持有 audioMutex）
        std::string text = runInference(bufferCopy.data(), bufferCopy.size());
        // ...

        impl_->isInferring.store(false);
    }
}
```

#### 優點

✅ 不需要額外線程
✅ 部分解決阻塞問題（audioMutex 快速釋放）

#### 缺點

❌ **仍然阻塞**：推論期間 `pushAudio()` 仍在音頻線程執行（佔用 500ms）
❌ **競爭條件**：`isInferring` flag 可能導致推論跳過（當音頻累積速度 > 推論速度）
❌ **不推薦**：沒有根本解決問題，只是將阻塞從 mutex 移到 atomic flag

---

### 方案 C：Lock-Free Ring Buffer（過度工程）

使用無鎖的環形 buffer，適合極高頻的音頻處理場景。

#### 評估

❌ **複雜度過高**：實現和調試難度大
❌ **收益不明顯**：當前場景 mutex 開銷可接受（< 1ms）
❌ **不推薦**：除非性能分析證明 mutex 是瓶頸

---

## 📊 方案對比

| 方案 | 阻塞消除 | 實現複雜度 | 記憶體開銷 | CPU 利用 | 推薦度 |
|------|----------|-----------|-----------|----------|--------|
| 方案 A（後台線程） | ✅ 完全 | 中 | 中（複製 64-640KB） | ✅ 多核 | ⭐⭐⭐⭐⭐ |
| 方案 B（雙 Mutex） | ⚠️ 部分 | 低 | 中（複製 64-640KB） | ❌ 單核 | ⭐⭐ |
| 方案 C（Lock-Free） | ✅ 完全 | 高 | 低 | ✅ 多核 | ⭐⭐ |
| 當前實現 | ❌ 無 | 低 | 低 | ❌ 阻塞 | ⭐ |

---

## 🎯 建議行動

1. **立即採用方案 A**（後台推論線程）
2. **添加性能監控 log**（鎖等待時間、推論耗時）
3. **測試驗證**：
   - 快速連續說話（< 500ms 間隔）
   - 長時間連續錄音（> 1 分鐘）
   - 檢查 Oboe 是否有 underrun/timeout 錯誤

---

## 📝 額外優化建議

### 1. 推論隊列大小限制

防止推論速度 < 音頻累積速度時，隊列無限增長：

```cpp
const size_t MAX_QUEUE_SIZE = 3;  // 最多累積 3 個待推論任務

{
    std::lock_guard<std::mutex> qlock(impl_->queueMutex);
    if (impl_->inferenceQueue.size() < MAX_QUEUE_SIZE) {
        impl_->inferenceQueue.push(std::move(req));
        impl_->queueCv.notify_one();
    } else {
        LOGW("Inference queue full, dropping oldest request");
        impl_->inferenceQueue.pop();  // 丟棄最舊的
        impl_->inferenceQueue.push(std::move(req));
    }
}
```

### 2. 使用 shared_ptr 避免大量複製

如果記憶體複製成為瓶頸，可以使用 `std::shared_ptr`：

```cpp
struct InferenceRequest {
    std::shared_ptr<std::vector<int16_t>> audioData;  // 共享指針
    long startMs;
    long endMs;
};

// pushAudio() 中
req.audioData = std::make_shared<std::vector<int16_t>>(impl_->audioBuffer);
```

但需注意生命週期管理和線程安全。

### 3. 推論線程優先級

降低推論線程優先級，確保音頻線程優先執行：

```cpp
#include <pthread.h>

void WhisperAsrEngine::Impl::inferenceThreadFunc() {
    // 設置較低優先級
    pthread_t this_thread = pthread_self();
    struct sched_param params;
    params.sched_priority = sched_get_priority_min(SCHED_OTHER);
    pthread_setschedparam(this_thread, SCHED_OTHER, &params);

    // ... 推論邏輯 ...
}
```

---

## 結論

當前實現存在**嚴重的線程阻塞問題**，推論期間（350-1400ms）會阻塞所有新的音頻累積，導致延遲累積和潛在的音頻丟失。

**強烈建議立即實施方案 A（後台推論線程）**，將推論工作移到專用線程，確保 `pushAudio()` 快速返回（< 5ms），從根本上解決阻塞問題。
