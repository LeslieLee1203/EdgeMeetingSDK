// meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp
// Whisper ASR 引擎實作 - 整合 RKNN Runtime

#include "WhisperAsrEngine.h"
#include "WhisperUtils.h"
#include "rknn_api.h"
#include <cmath>
#include <fstream>
#include <vector>
#include <string>
#include <mutex>
#include <thread>
#include <queue>
#include <condition_variable>
#include <atomic>
#include <chrono>

#define LOG_TAG "WhisperAsrEngine"
#include "../Log.h"

// 前向聲明：推論線程函數
void inferenceThreadFunc(WhisperAsrEngine::Impl* impl, WhisperAsrEngine* engine);

// 輔助結構：管理 RKNN context 與 memory
struct RknnModelContext {
    rknn_context ctx = 0;
    rknn_input_output_num io_num;
    rknn_tensor_attr *input_attrs = nullptr;
    rknn_tensor_attr *output_attrs = nullptr;
};

static int release_rknn_model(RknnModelContext *app_ctx);

// 輔助函式：載入與釋放
static int init_rknn_model(const char *model_path, RknnModelContext *app_ctx) {
    int ret;
    if (!app_ctx || !model_path) return -1;

    // 確保初始狀態乾淨，避免失敗時釋放未初始化指標
    app_ctx->ctx = 0;
    app_ctx->input_attrs = nullptr;
    app_ctx->output_attrs = nullptr;

    // Load RKNN Model
    ret = rknn_init(&app_ctx->ctx, (void *)model_path, 0, 0, NULL);
    if (ret < 0) {
        LOGE("rknn_init fail! ret=%d, path=%s", ret, model_path);
        return -1;
    }

    // Get IO Num
    ret = rknn_query(app_ctx->ctx, RKNN_QUERY_IN_OUT_NUM, &app_ctx->io_num, sizeof(app_ctx->io_num));
    if (ret != RKNN_SUCC) {
        LOGE("rknn_query IO_NUM fail! ret=%d", ret);
        release_rknn_model(app_ctx);
        return -1;
    }

    // Allocate attrs
    app_ctx->input_attrs = (rknn_tensor_attr *)malloc(app_ctx->io_num.n_input * sizeof(rknn_tensor_attr));
    app_ctx->output_attrs = (rknn_tensor_attr *)malloc(app_ctx->io_num.n_output * sizeof(rknn_tensor_attr));

    // Get Input Attrs
    for (int i = 0; i < app_ctx->io_num.n_input; i++) {
        app_ctx->input_attrs[i].index = i;
        ret = rknn_query(app_ctx->ctx, RKNN_QUERY_INPUT_ATTR, &(app_ctx->input_attrs[i]), sizeof(rknn_tensor_attr));
        if (ret != RKNN_SUCC) {
            LOGE("rknn_query INPUT_ATTR %d fail! ret=%d", i, ret);
            release_rknn_model(app_ctx);
            return -1;
        }
    }

    // Get Output Attrs
    for (int i = 0; i < app_ctx->io_num.n_output; i++) {
        app_ctx->output_attrs[i].index = i;
        ret = rknn_query(app_ctx->ctx, RKNN_QUERY_OUTPUT_ATTR, &(app_ctx->output_attrs[i]), sizeof(rknn_tensor_attr));
        if (ret != RKNN_SUCC) {
            LOGE("rknn_query OUTPUT_ATTR %d fail! ret=%d", i, ret);
            release_rknn_model(app_ctx);
            return -1;
        }
    }

    return 0;
}

static int release_rknn_model(RknnModelContext *app_ctx) {
    if (app_ctx->input_attrs != nullptr) {
        free(app_ctx->input_attrs);
        app_ctx->input_attrs = nullptr;
    }
    if (app_ctx->output_attrs != nullptr) {
        free(app_ctx->output_attrs);
        app_ctx->output_attrs = nullptr;
    }
    if (app_ctx->ctx != 0) {
        rknn_destroy(app_ctx->ctx);
        app_ctx->ctx = 0;
    }
    return 0;
}

// 供測試使用：模擬失敗清理，避免依賴實際 RKNN runtime
std::vector<int> cleanupRknnOnFailureTestResult(bool setInputAttrs, bool setOutputAttrs) {
    RknnModelContext ctx;
    memset(&ctx, 0, sizeof(ctx));

    if (setInputAttrs) {
        ctx.input_attrs = (rknn_tensor_attr *)malloc(sizeof(rknn_tensor_attr));
    }
    if (setOutputAttrs) {
        ctx.output_attrs = (rknn_tensor_attr *)malloc(sizeof(rknn_tensor_attr));
    }

    release_rknn_model(&ctx);

    const int inputCleared = (ctx.input_attrs == nullptr) ? 1 : 0;
    const int outputCleared = (ctx.output_attrs == nullptr) ? 1 : 0;
    return { inputCleared, outputCleared };
}

// PImpl idiom to hide implementation details from header
struct WhisperAsrEngine::Impl {
    RknnModelContext encoder;
    RknnModelContext decoder;

    // Resources
    float* mel_filters = nullptr;
    VocabEntry* vocab = nullptr;

    // Config
    int task_code = 50259; // 50259=en, 50260=zh

    // Runtime State (重構：將 mutex 重命名為 audioMutex，語義更清晰)
    std::mutex audioMutex;  // 保護 audioBuffer（快速操作）
    std::vector<int16_t> audioBuffer;
    int silenceDurationMs = 0;
    int speechDurationMs = 0;  // 新增：追蹤語音時長，用於過濾無效推論
    int segmentCounter = 0;
    long totalAudioMs = 0;  // 累積的音訊總時間（毫秒）
    TranscriptCallback transcriptCallback;

    // === VAD 狀態穩定性機制（方案 A 任務 3）===
    VadState currentVadState = VadState::SILENCE;  // 當前穩定的 VAD 狀態
    int vadStateHoldMs = 0;                        // 當前狀態持續時間（毫秒）
    int minStateHoldMs = 300;                      // VAD 去抖動時間（固定，非熱更新參數）

    // === 熱更新 VAD 參數 ===
    WhisperAsrEngine::VadParams vadParams;         // 可調整的 VAD 參數（預設值與 C++ 常數一致）
    std::mutex vadMutex;                           // 保護 vadParams 讀寫（獨立於 audioMutex）

    // === 新增：推論線程相關 ===
    struct InferenceRequest {
        std::vector<int16_t> audioData;  // 深拷貝的音頻數據
        long startMs;
        long endMs;
        bool isFinal;  // 方案 B：標記是否為最終推論（true=最終, false=中間）
    };

    std::queue<InferenceRequest> inferenceQueue;
    std::mutex queueMutex;               // 保護 inferenceQueue
    std::condition_variable queueCV;     // 通知推論線程
    std::thread inferenceThread;
    std::atomic<bool> shouldStopThread{false};
    std::atomic<bool> isInferring{false}; // 防止重複推論

    // === 方案 B：漸進式推論相關變數 ===
    long lastIntermediateInferenceMs = 0;       // 上次中間推論的時間點
    // INTERMEDIATE_INTERVAL_MS 已移至 vadParams.intermediateIntervalMs（支援熱更新）

    // === 品質感知：追蹤最佳 INTERMEDIATE 結果 ===
    std::string lastBestIntermediateText;      // 最佳 INTERMEDIATE 文字
    size_t lastBestIntermediateTextLen = 0;    // 最佳 INTERMEDIATE 文字長度（bytes）

    ~Impl() {
        if (mel_filters) free(mel_filters);
        if (vocab) {
            for(int i=0; i<VOCAB_NUM; i++) {
                if(vocab[i].token) free(vocab[i].token);
            }
            delete[] vocab;
        }
    }
};

WhisperAsrEngine::WhisperAsrEngine()
    : initialized_(false)
    , impl_(new Impl())
{
    LOGD("WhisperAsrEngine created");
}

WhisperAsrEngine::~WhisperAsrEngine() {
    release();
    delete impl_;
    LOGD("WhisperAsrEngine destroyed");
}

void WhisperAsrEngine::setTranscriptCallback(TranscriptCallback callback) {
    impl_->transcriptCallback = std::move(callback);
}

bool WhisperAsrEngine::init(const std::string& modelsPath, const std::string& language) {
    if (modelsPath.empty()) return false;

    // 1. 設定模型路徑
    std::string encoderPath = modelsPath + "/whisper_encoder_base_20s.rknn";
    std::string decoderPath = modelsPath + "/whisper_decoder_base_20s.rknn";
    std::string filtersPath = modelsPath + "/mel_80_filters.txt";

    // 2. 設定語言與 task_code
    // vocab_en.txt 是 Whisper 的統一多語言 BPE 詞彙表，所有語言共享
    std::string vocabPath = modelsPath + "/vocab_en.txt";

    if (language == "zh") {
        impl_->task_code = 50260;  // 中文
    } else if (language == "ja") {
        impl_->task_code = 50266;  // 日文
    } else if (language == "ko") {
        impl_->task_code = 50264;  // 韓文
    } else {
        impl_->task_code = 50259;  // 英文（預設）
    }

    // 3. 讀取資源
    // Mel Filters
    impl_->mel_filters = (float*)malloc(N_MELS * MELS_FILTERS_SIZE * sizeof(float));
    if (read_mel_filters(filtersPath.c_str(), impl_->mel_filters, N_MELS * MELS_FILTERS_SIZE) != 0) {
        LOGE("Failed to read mel filters from %s", filtersPath.c_str());
        return false;
    }

    // Vocab
    impl_->vocab = new VocabEntry[VOCAB_NUM];
    memset(impl_->vocab, 0, sizeof(VocabEntry) * VOCAB_NUM);
    if (read_vocab(vocabPath.c_str(), impl_->vocab) != 0) {
        LOGE("Failed to read vocab from %s", vocabPath.c_str());
        return false;
    }

    // 4. 初始化 RKNN 模型
    if (init_rknn_model(encoderPath.c_str(), &impl_->encoder) != 0) {
        LOGE("Failed to init encoder");
        return false;
    }

    if (init_rknn_model(decoderPath.c_str(), &impl_->decoder) != 0) {
        LOGE("Failed to init decoder");
        release_rknn_model(&impl_->encoder);
        return false;
    }

    // 啟動推論線程
    impl_->shouldStopThread.store(false, std::memory_order_release);
    impl_->inferenceThread = std::thread(inferenceThreadFunc, impl_, this);

    modelsPath_ = modelsPath;
    language_ = language;
    initialized_ = true;
    LOGI("WhisperAsrEngine initialized successfully (inference thread started)");
    return true;
}

void WhisperAsrEngine::start() {
    if (!initialized_) return;
    std::lock_guard<std::mutex> lock(impl_->audioMutex);

    // 簡化版：只重置必要狀態
    impl_->audioBuffer.clear();
    impl_->silenceDurationMs = 0;
    impl_->speechDurationMs = 0;
    impl_->segmentCounter = 0;
    impl_->totalAudioMs = 0;

    // 方案 A 任務 3：重置 VAD 狀態穩定性機制
    impl_->currentVadState = VadState::SILENCE;
    impl_->vadStateHoldMs = 0;

    // 方案 B：重置漸進式推論計時器
    impl_->lastIntermediateInferenceMs = 0;

    LOGD("WhisperAsrEngine started - Buffers cleared");
}

void WhisperAsrEngine::stop() {
    if (!initialized_) return;

    std::lock_guard<std::mutex> lock(impl_->audioMutex);

    // 處理剩餘音頻（如果有）
    if (!impl_->audioBuffer.empty()) {
        const size_t MIN_SAMPLES = 16000 * 1;  // stop() 時降低閾值至 1 秒

        if (impl_->audioBuffer.size() >= MIN_SAMPLES &&
            !impl_->isInferring.load(std::memory_order_acquire)) {

            if (!shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
                Impl::InferenceRequest req;
                req.audioData = impl_->audioBuffer;
                long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
                req.startMs = impl_->totalAudioMs - bufferDurationMs;
                req.endMs = impl_->totalAudioMs;

                {
                    std::lock_guard<std::mutex> qlock(impl_->queueMutex);
                    impl_->inferenceQueue.push(std::move(req));
                }
                impl_->queueCV.notify_one();

                LOGD("stop(): Queued remaining %.1fs audio for inference",
                     impl_->audioBuffer.size() / 16000.0);
            }
        }

        impl_->audioBuffer.clear();
    }

    impl_->silenceDurationMs = 0;
    impl_->speechDurationMs = 0;
    LOGD("WhisperAsrEngine stopped");
}

void WhisperAsrEngine::release() {
    if (!initialized_) return;

    // 停止推論線程
    LOGD("Stopping inference thread...");
    impl_->shouldStopThread.store(true, std::memory_order_release);
    impl_->queueCV.notify_all();  // 喚醒可能等待的線程

    if (impl_->inferenceThread.joinable()) {
        impl_->inferenceThread.join();
        LOGD("Inference thread joined");
    }

    // 清理剩餘的推論隊列（防止內存洩漏）
    {
        std::lock_guard<std::mutex> lock(impl_->queueMutex);
        while (!impl_->inferenceQueue.empty()) {
            impl_->inferenceQueue.pop();
        }
        LOGD("Cleared pending inference requests");
    }

    // 釋放 RKNN 模型
    release_rknn_model(&impl_->encoder);
    release_rknn_model(&impl_->decoder);

    initialized_ = false;
    LOGD("WhisperAsrEngine released");
}

// 推論線程工作函數
void inferenceThreadFunc(WhisperAsrEngine::Impl* impl, WhisperAsrEngine* engine) {
    LOGD("Inference thread started (tid=%ld)", pthread_self());

    while (!impl->shouldStopThread.load(std::memory_order_acquire)) {
        WhisperAsrEngine::Impl::InferenceRequest req;

        // 等待推論任務
        {
            std::unique_lock<std::mutex> lock(impl->queueMutex);
            impl->queueCV.wait(lock, [impl] {
                return !impl->inferenceQueue.empty() || impl->shouldStopThread.load();
            });

            if (impl->shouldStopThread.load()) {
                LOGD("Inference thread received stop signal");
                break;
            }

            if (!impl->inferenceQueue.empty()) {
                req = std::move(impl->inferenceQueue.front());
                impl->inferenceQueue.pop();
            } else {
                continue;
            }
        }

        // 執行推論（不持有任何鎖）
        impl->isInferring.store(true, std::memory_order_release);

        auto t0 = std::chrono::steady_clock::now();
        const char* typeStr = req.isFinal ? "FINAL" : "INTERMEDIATE";

        // 方案 B-Fixed：推論前最終保護（防止隊列中的舊請求使用過長音訊）
        // 問題：Buffer overflow 修剪發生在 pushAudio，但推論請求已在隊列中
        // 解決：推論前檢查，如果音訊 > 16s，只處理最後 16s
        const size_t MAX_INFERENCE_SAMPLES = 16000 * 16;  // 16 秒上限
        const int16_t* audioPtr = req.audioData.data();
        size_t audioSamples = req.audioData.size();

        if (audioSamples > MAX_INFERENCE_SAMPLES) {
            // 只取最後 16 秒（避免 Whisper 幻覺）
            audioPtr = req.audioData.data() + (audioSamples - MAX_INFERENCE_SAMPLES);
            audioSamples = MAX_INFERENCE_SAMPLES;
            LOGW("Inference request too long (%.1fs), trimmed to last 16s",
                 req.audioData.size() / 16000.0);
        }

        LOGD("Inference thread: Processing %zu samples [%ld-%ld ms] (%s)",
             audioSamples, req.startMs, req.endMs, typeStr);

        std::string text = engine->runInference(audioPtr, audioSamples);

        auto t1 = std::chrono::steady_clock::now();
        auto inferenceMs = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();

        if (!text.empty()) {
            if (!req.isFinal) {
                // INTERMEDIATE：記錄最佳結果（只保留最長的）
                if (text.length() > impl->lastBestIntermediateTextLen) {
                    impl->lastBestIntermediateText = text;
                    impl->lastBestIntermediateTextLen = text.length();
                    LOGD("Updated best INTERMEDIATE text (%zu bytes)", text.length());
                }
            } else {
                // FINAL：品質檢查
                if (impl->lastBestIntermediateTextLen > 0 &&
                    text.length() < impl->lastBestIntermediateTextLen * 7 / 10) {
                    // Decoder 截斷！使用最佳 INTERMEDIATE 替代
                    LOGW("⚠️  FINAL decoder truncated! FINAL=%zu bytes vs best INTER=%zu bytes. "
                         "Using INTERMEDIATE text as FINAL.",
                         text.length(), impl->lastBestIntermediateTextLen);
                    text = impl->lastBestIntermediateText;
                }
                // FINAL 後重置追蹤
                impl->lastBestIntermediateText.clear();
                impl->lastBestIntermediateTextLen = 0;
            }

            engine->emitTranscript(text, req.startMs, req.endMs, req.isFinal);
            LOGI("Transcript emitted [%s] (%lld ms): '%s'", typeStr, inferenceMs, text.c_str());
        } else {
            LOGD("Inference returned empty text [%s] (%lld ms)", typeStr, inferenceMs);
        }

        impl->isInferring.store(false, std::memory_order_release);
    }

    LOGD("Inference thread stopped");
}

void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    if (!initialized_ || samples == 0) return;

    auto t0 = std::chrono::steady_clock::now();

    // === 階段 1：快速累積音頻（持有鎖）===
    {
        std::lock_guard<std::mutex> lock(impl_->audioMutex);

        impl_->audioBuffer.insert(impl_->audioBuffer.end(), pcm, pcm + samples);
        impl_->totalAudioMs += (samples * 1000) / 16000;

        // 方案 B-Fixed：Buffer 硬上限保護（防止推論速度跟不上時無限累積）
        // 問題：當 RTF ≈ 0.4-0.7 時，推論耗時接近實時，Buffer 會在推論期間持續增長
        // 解決：設置 18s 硬上限（配合 MAX_SAMPLES=16s），超過時丟棄最舊的音訊，保留最新的 15s
        const size_t HARD_LIMIT_SAMPLES = 16000 * 18;  // 18 秒硬上限（配合 MAX_SAMPLES=16s）
        const size_t KEEP_SAMPLES = 16000 * 15;        // 保留 15 秒（留 1s 緩衝）

        if (impl_->audioBuffer.size() > HARD_LIMIT_SAMPLES) {
            size_t discarded_samples = impl_->audioBuffer.size() - KEEP_SAMPLES;
            impl_->audioBuffer.erase(
                impl_->audioBuffer.begin(),
                impl_->audioBuffer.begin() + discarded_samples
            );
            LOGW("⚠️  Buffer overflow! Trimmed %.1fs → 15s (discarded oldest %.1fs)",
                 (impl_->audioBuffer.size() + discarded_samples) / 16000.0,
                 discarded_samples / 16000.0);
        }

        VadState vadState = detectVadState(pcm, samples);

        // 更新靜音累積時間（修正：PAUSE 也算非語音時長）
        int prevSilenceDurationMs = impl_->silenceDurationMs;

        if (vadState == VadState::SILENCE || vadState == VadState::PAUSE) {
            impl_->silenceDurationMs += (samples * 1000) / 16000;

            // 每累積 500ms 輸出一次日誌（方便追蹤累積過程）
            if (impl_->silenceDurationMs >= 500 &&
                prevSilenceDurationMs < 500) {
                LOGI("Non-speech accumulated: %dms (VAD=%s, Buffer=%.1fs)",
                     impl_->silenceDurationMs,
                     (vadState == VadState::SILENCE) ? "SILENCE" : "PAUSE",
                     impl_->audioBuffer.size() / 16000.0);
            }
        } else {
            // State is SPEECH
            if (prevSilenceDurationMs > 0) {
                LOGD("Non-speech duration reset: %dms -> 0 (VAD changed to SPEECH)",
                     prevSilenceDurationMs);
            }
            impl_->silenceDurationMs = 0;
            
            // 累積語音時長
            impl_->speechDurationMs += (samples * 1000) / 16000;
        }

        // Debug log（節流：每 5 秒）
        if (impl_->audioBuffer.size() % (16000 * 5) < samples) {
            LOGD("pushAudio: Buffer=%.1fs, VAD=%d, Silence=%dms, Speech=%dms",
                 impl_->audioBuffer.size() / 16000.0,
                 static_cast<int>(vadState),
                 impl_->silenceDurationMs,
                 impl_->speechDurationMs);
        }

        // === 推論觸發判斷 - 方案 B：雙軌推論機制 ===
        // 從 VadParams 讀取觸發閾值（vadMutex 保護，支援熱更新）
        // 注意：vadMutex 僅在此短暫複製區間持有，不與 audioMutex 嵌套
        WhisperAsrEngine::VadParams vp;
        {
            std::lock_guard<std::mutex> lock(impl_->vadMutex);
            vp = impl_->vadParams;
        }
        const size_t MIN_SAMPLES = vp.minSamples;
        const size_t MAX_SAMPLES = vp.maxSamples;
        const int SILENCE_THRESHOLD_MS = vp.silenceThresholdMs;
        const int PAUSE_THRESHOLD_MS = vp.pauseThresholdMs;
        const int MIN_SPEECH_DURATION_MS = vp.minSpeechDurationMs;

        // 已移除 URGENT 機制（窗口期太短，效果與正常觸發重疊）
        // const size_t URGENT_SAMPLES = 16000 * 13;
        // const int URGENT_SILENCE_THRESHOLD_MS = 400;

        bool shouldInfer = false;
        bool isFinalInference = true;  // 方案 B：默認為最終推論
        const char* triggerReason = nullptr;

        // === 方案 B 策略 0：定時中間推論（快速軌）===
        // 優先檢查，提供即時反饋（每 5 秒）
        long timeSinceLastIntermediate = impl_->totalAudioMs - impl_->lastIntermediateInferenceMs;
        if (!impl_->isInferring.load(std::memory_order_acquire) &&
            timeSinceLastIntermediate >= vp.intermediateIntervalMs &&
            impl_->audioBuffer.size() >= MIN_SAMPLES &&
            impl_->speechDurationMs >= MIN_SPEECH_DURATION_MS) {
            shouldInfer = true;
            isFinalInference = false;  // 中間推論
            triggerReason = "Intermediate (5s timer)";
            LOGI("Intermediate inference triggered (timeSince=%.1fs, Buffer=%.1fs, Speech=%dms)",
                 timeSinceLastIntermediate / 1000.0,
                 impl_->audioBuffer.size() / 16000.0,
                 impl_->speechDurationMs);
        }

        // === 方案 B 策略 1-2：最終推論（準確軌）===
        // 在自然停頓或達到限制時觸發，提供高準確度轉錄

        // 策略 1a：已移除 URGENT 機制（窗口期太短，與策略 1b/1c 重疊）
        // 理由：MAX_SAMPLES = 15s 已經足夠短，策略 1b/1c 足以捕捉自然停頓

        // 策略 1b：正常觸發（最小時長 + 充分停頓）
        if (!shouldInfer &&
            impl_->audioBuffer.size() >= MIN_SAMPLES &&
            (vadState == VadState::SILENCE || vadState == VadState::PAUSE) &&
            impl_->silenceDurationMs >= SILENCE_THRESHOLD_MS) {
            shouldInfer = true;
            isFinalInference = true;  // 最終推論
            triggerReason = (vadState == VadState::SILENCE) ? "SILENCE detected" : "PAUSE detected";
        }

        // 策略 1c：PAUSE 狀態持續觸發（捕捉思考停頓）
        if (!shouldInfer &&
            impl_->audioBuffer.size() >= MIN_SAMPLES &&
            vadState == VadState::PAUSE &&
            impl_->silenceDurationMs >= PAUSE_THRESHOLD_MS) {
            shouldInfer = true;
            isFinalInference = true;  // 最終推論
            triggerReason = "PAUSE sustained";
        }

        // 策略 2：強制截斷
        if (!shouldInfer && impl_->audioBuffer.size() >= MAX_SAMPLES) {
            shouldInfer = true;
            isFinalInference = true;  // 最終推論
            triggerReason = "Max buffer reached";
        }

        if (shouldInfer && !impl_->isInferring.load(std::memory_order_acquire)) {
            // 關鍵修正：檢查是否有足夠的語音內容 (防止幻覺)
            // 方案 A-Fixed：語音時長必須 >= 2.0s，確保 Whisper 獲得足夠上下文
            if (impl_->speechDurationMs < MIN_SPEECH_DURATION_MS) {
                 LOGW("Inference SKIPPED: Insufficient speech duration (%dms < %dms, Buffer=%.1fs). Reason: %s",
                      impl_->speechDurationMs, MIN_SPEECH_DURATION_MS,
                      impl_->audioBuffer.size() / 16000.0, triggerReason);

                 // 如果 Buffer 太大但語音太少，可能是長時間靜音，清空重置
                 if (impl_->audioBuffer.size() > 16000 * 10) {  // 超過 10 秒
                     impl_->audioBuffer.clear();
                     impl_->silenceDurationMs = 0;
                     impl_->speechDurationMs = 0;
                 }
                 return;
            }

            // 能量預檢（保留作為雙重保險）
            if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
                LOGW("Inference SKIPPED: %s, but energy too low (Buffer=%.1fs, NonSpeech=%dms)",
                     triggerReason,
                     impl_->audioBuffer.size() / 16000.0,
                     impl_->silenceDurationMs);
                impl_->audioBuffer.clear();
                impl_->silenceDurationMs = 0;
                impl_->speechDurationMs = 0;
                return;
            }

            // 觸發推論（詳細日誌）
            const char* typeStr = isFinalInference ? "FINAL" : "INTERMEDIATE";
            LOGI("Inference TRIGGERED [%s]: %s (Buffer=%.1fs, Speech=%dms, NonSpeech=%dms)",
                 typeStr, triggerReason,
                 impl_->audioBuffer.size() / 16000.0,
                 impl_->speechDurationMs,
                 impl_->silenceDurationMs);

            // 準備推論請求
            Impl::InferenceRequest req;
            req.audioData = impl_->audioBuffer;  // std::vector 深拷貝（始終使用完整 Buffer）

            // 方案 B-Fixed：依賴 Buffer 硬上限保護（20s），不再截取中間推論音訊
            // 原因：截取音訊會導致 Whisper 失去上下文，FINAL 結果不完整
            // 解決：接受中間推論可能較慢（RTF 0.4-0.5），但保證內容完整性

            long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
            req.startMs = impl_->totalAudioMs - bufferDurationMs;
            req.endMs = impl_->totalAudioMs;
            req.isFinal = isFinalInference;  // 方案 B：標記推論類型

            // 方案 B：根據推論類型決定是否清空 Buffer
            if (isFinalInference) {
                // 最終推論：清空 Buffer（自然分段）
                impl_->audioBuffer.clear();
                impl_->silenceDurationMs = 0;
                impl_->speechDurationMs = 0;
                impl_->lastIntermediateInferenceMs = impl_->totalAudioMs;  // 重置中間推論計時器
                LOGD("Buffer cleared after FINAL inference");
            } else {
                // 中間推論：保留 Buffer（持續累積）
                impl_->lastIntermediateInferenceMs = impl_->totalAudioMs;
                LOGD("Buffer retained after INTERMEDIATE inference (size=%.1fs)",
                     impl_->audioBuffer.size() / 16000.0);
            }

            // 推送到推論隊列（快速操作）
            {
                std::lock_guard<std::mutex> qlock(impl_->queueMutex);

                const size_t MAX_QUEUE_SIZE = 3;
                if (impl_->inferenceQueue.size() >= MAX_QUEUE_SIZE) {
                    LOGW("Inference queue full (%zu), dropping oldest request",
                         impl_->inferenceQueue.size());
                    impl_->inferenceQueue.pop();
                }

                impl_->inferenceQueue.push(std::move(req));
            }
            impl_->queueCV.notify_one();

            LOGD("Inference request queued (queue size: %zu)",
                 impl_->inferenceQueue.size());
        }
    }  // audioMutex 在這裡釋放

    // === 階段 2：性能監控（無鎖）===
    auto t1 = std::chrono::steady_clock::now();
    auto pushAudioMs = std::chrono::duration_cast<std::chrono::milliseconds>(t1 - t0).count();

    if (pushAudioMs > 5) {
        LOGW("pushAudio took %lld ms (should be < 5ms)", pushAudioMs);
    }
}

// === Inference Logic (Ported from whisper.cc) ===

static int inference_encoder(RknnModelContext *ctx, const std::vector<float>& audio_data, float *mel_filters, float *encoder_output) {
    int ret;
    rknn_input inputs[1];
    rknn_output outputs[1];
    memset(inputs, 0, sizeof(inputs));
    memset(outputs, 0, sizeof(outputs));

    // Input: [1, 80, 2000] (N_MELS * ENCODER_INPUT_SIZE)
    
    inputs[0].index = 0;
    inputs[0].type = RKNN_TENSOR_FLOAT32;
    inputs[0].size = N_MELS * ENCODER_INPUT_SIZE * sizeof(float);
    inputs[0].buf = (float *)malloc(inputs[0].size);
    
    memcpy(inputs[0].buf, audio_data.data(), inputs[0].size);

    ret = rknn_inputs_set(ctx->ctx, 1, inputs);
    if(ret < 0) { free(inputs[0].buf); LOGE("rknn_inputs_set failed (encoder)"); return ret; }

    ret = rknn_run(ctx->ctx, nullptr);
    if(ret < 0) { free(inputs[0].buf); LOGE("rknn_run failed (encoder)"); return ret; }

    outputs[0].want_float = 1;
    ret = rknn_outputs_get(ctx->ctx, 1, outputs, NULL);
    if(ret < 0) { free(inputs[0].buf); LOGE("rknn_outputs_get failed (encoder)"); return ret; }

    // Copy output
    memcpy(encoder_output, (float *)outputs[0].buf, ENCODER_OUTPUT_SIZE * sizeof(float));

    rknn_outputs_release(ctx->ctx, 1, outputs);
    free(inputs[0].buf);
    return 0;
}

static int inference_decoder(RknnModelContext *ctx, float *encoder_output, VocabEntry *vocab, int task_code, std::string &result_text) {
    int ret;
    rknn_input inputs[2];
    rknn_output outputs[1];
    memset(inputs, 0, sizeof(inputs));
    memset(outputs, 0, sizeof(outputs));

    // Inputs allocation
    inputs[0].index = 0;
    inputs[0].type = RKNN_TENSOR_INT64;
    inputs[0].size = MAX_TOKENS * sizeof(int64_t);
    inputs[0].buf = (int64_t *)malloc(inputs[0].size); // tokens

    inputs[1].index = 1;
    inputs[1].type = RKNN_TENSOR_FLOAT32;
    inputs[1].size = DECODER_INPUT_SIZE * sizeof(float);
    inputs[1].buf = (float *)malloc(inputs[1].size); // encoder_output
    memcpy(inputs[1].buf, encoder_output, inputs[1].size);

    // Initial tokens
    int64_t tokens[MAX_TOKENS + 1] = {50258, (int64_t)task_code, 50359, 50363}; 
    int next_token = 50258;
    int end_token = 50257;
    int pop_id = MAX_TOKENS;
    int count = 0;

    // 使用 Byte-Level BPE 解碼：收集 UTF-8 字節而非字符串
    std::vector<uint8_t> utf8_bytes;
    utf8_bytes.reserve(1024);  // 預分配空間

    // Fill buffer pattern
    for (int i = 0; i < MAX_TOKENS / 4; i++) {
        memcpy(&tokens[i * 4], tokens, 4 * sizeof(int64_t));
    }

    while (next_token != end_token && count < 100) { // Limit loop
        count++;
        memcpy(inputs[0].buf, tokens, inputs[0].size);

        rknn_inputs_set(ctx->ctx, 2, inputs);
        rknn_run(ctx->ctx, nullptr);

        outputs[0].want_float = 1;
        rknn_outputs_get(ctx->ctx, 1, outputs, NULL);

        next_token = argmax((float *)outputs[0].buf);

        // Debug first few tokens
        if (count < 5) {
            LOGD("Decoder Step %d: TokenID=%d", count, next_token);
        }

        // Byte-Level BPE 解碼：將 token 轉換為 UTF-8 字節
        if (next_token < VOCAB_NUM) {
             if (vocab[next_token].token) {
                 decode_bpe_token(vocab[next_token].token, utf8_bytes);
             }
        }

        int timestamp_begin = 50364;
        if (next_token > timestamp_begin) {
            continue;
        }

        if (pop_id > 4) pop_id--;
        tokens[MAX_TOKENS] = next_token;
        for (int j = pop_id; j < MAX_TOKENS; j++) {
            tokens[j] = tokens[j + 1];
        }

        rknn_outputs_release(ctx->ctx, 1, outputs);
    }

    free(inputs[0].buf);
    free(inputs[1].buf);

    // 將 UTF-8 字節序列轉換為字符串
    std::string all_token_str(utf8_bytes.begin(), utf8_bytes.end());

    // Post process: Whisper BPE 使用 U+0120 (Ġ) 表示詞首空格
    replace_substr(all_token_str, "\u0120", " ");
    replace_substr(all_token_str, "<|endoftext|>", "");
    replace_substr(all_token_str, "\n", "");

    result_text = all_token_str;
    LOG_RESULT("Decoder Result: '%s'", result_text.c_str());
    return 0;
}

std::string WhisperAsrEngine::runInference(const int16_t* pcmData, size_t samples) {
    if (!initialized_) return "";

    auto t0 = std::chrono::steady_clock::now();
    LOGD("runInference: Processing %zu samples", samples);

    // === 階段 1：音頻預處理 ===
    auto t1_start = std::chrono::steady_clock::now();

    // 1. Convert PCM to Float & Preprocess
    std::vector<float> audio_float(samples);
    float max_val = 0.0f;
    
    // Apply digital gain to normalize audio levels (Whisper expects ~ -3dBFS, input is ~ -40dBFS)
    const float GAIN_FACTOR = 10.0f; 

    for(size_t i=0; i<samples; i++) {
        float val = (pcmData[i] / 32768.0f) * GAIN_FACTOR;
        // Hard clamp to prevent clipping artifacts
        if (val > 1.0f) val = 1.0f;
        if (val < -1.0f) val = -1.0f;
        audio_float[i] = val;
        
        if (std::abs(val) > max_val) max_val = std::abs(val);
    }
    
    LOGD("runInference: Applied gain %.1fx. Max amplitude after gain: %.4f", GAIN_FACTOR, max_val);

    std::vector<float> x_mel;

    audio_preprocess(audio_float.data(), samples, impl_->mel_filters, x_mel);

    auto t1_end = std::chrono::steady_clock::now();
    auto preprocess_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t1_end - t1_start).count();
    LOGD("runInference: Preprocessing done (%lld ms). Mel size: %zu", preprocess_ms, x_mel.size());

    // === 階段 2：RKNN Encoder ===
    auto t2_start = std::chrono::steady_clock::now();

    // 2. Encoder
    float *encoder_output = (float *)malloc(ENCODER_OUTPUT_SIZE * sizeof(float));
    if (inference_encoder(&impl_->encoder, x_mel, impl_->mel_filters, encoder_output) != 0) {
        LOGE("Encoder inference failed");
        free(encoder_output);
        return "";
    }

    auto t2_end = std::chrono::steady_clock::now();
    auto encoder_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t2_end - t2_start).count();
    LOGD("runInference: Encoder done (%lld ms)", encoder_ms);

    // === 階段 3：RKNN Decoder ===
    auto t3_start = std::chrono::steady_clock::now();

    // 3. Decoder
    std::string text;
    if (inference_decoder(&impl_->decoder, encoder_output, impl_->vocab, impl_->task_code, text) != 0) {
        LOGE("Decoder inference failed");
        free(encoder_output);
        return "";
    }

    auto t3_end = std::chrono::steady_clock::now();
    auto decoder_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t3_end - t3_start).count();

    free(encoder_output);

    // === 總結日誌 ===
    auto total_ms = std::chrono::duration_cast<std::chrono::milliseconds>(t3_end - t0).count();
    double audio_duration_s = samples / 16000.0;
    double rtf = total_ms / (audio_duration_s * 1000.0);

    LOGI("Inference Complete: Total=%lld ms (Preprocess=%lld, Encoder=%lld, Decoder=%lld), "
         "Audio=%.1fs, RTF=%.3f, Text='%s'",
         total_ms, preprocess_ms, encoder_ms, decoder_ms,
         audio_duration_s, rtf, text.c_str());

    return text;
}

void WhisperAsrEngine::updateVadConfig(const VadParams& params) {
    std::lock_guard<std::mutex> lock(impl_->vadMutex);
    impl_->vadParams = params;
    LOGI("VAD config updated: fastSilRms=%.1f, silRms=%.1f, pauseRms=%.1f, "
         "silMs=%d, pauseMs=%d, minSpeechMs=%d, intermediateMs=%d, "
         "minSamples=%zu, maxSamples=%zu, energyThr=%.1f, silRatio=%.2f",
         params.fastSilenceRms, params.silenceRms, params.pauseRms,
         params.silenceThresholdMs, params.pauseThresholdMs, params.minSpeechDurationMs,
         params.intermediateIntervalMs, params.minSamples, params.maxSamples,
         params.energyThreshold, params.silenceRatioThreshold);
}

bool WhisperAsrEngine::loadRknnModel(const std::string& modelPath, void* context) {
    return false; // Deprecated, using init_rknn_model locally
}

bool WhisperAsrEngine::validateModelFiles(const std::string& modelsPath) {
    return true; // Simplified
}

bool WhisperAsrEngine::detectSilence(const int16_t* pcm, size_t samples) {
    if (samples == 0) return true;

    // === 設計理念：雙重指標靜音檢測 ===
    // 
    // 1. RMS (Root Mean Square): 衡量音訊的「響度」。
    //    單純使用 RMS 容易將持續性的環境噪音（如空調、風扇）誤判為語音，
    //    或者為了過濾噪音將閾值設太高而漏掉輕聲細語。
    //
    // 2. ZCR (Zero Crossing Rate): 衡量訊號的「頻率變化頻繁度」。
    //    - 語音 (Speech): 通常具有豐富的頻譜變化，ZCR 波動較大且特定頻段集中。
    //    - 噪音 (Noise): 
    //      - 低頻噪音 (如空調嗡嗡聲): ZCR 極低。
    //      - 高頻噪音 (如電流聲): ZCR 極高且穩定。
    // 
    // 策略：只有當「能量低」且「訊號變化單調 (低 ZCR)」時，才判定為靜音。
    // 這允許我們設定較高的 RMS 閾值來過濾噪音，同時利用 ZCR 保護低音量的語音。

    // 1. RMS Energy calculation
    long long sum = 0;
    for (size_t i = 0; i < samples; i++) {
        sum += static_cast<long long>(pcm[i]) * pcm[i];
    }
    double rms = std::sqrt(static_cast<double>(sum) / samples);

    // 2. Zero Crossing Rate (ZCR) calculation
    int zeroCrossings = 0;
    for (size_t i = 1; i < samples; i++) {
        // 偵測正負號翻轉
        if ((pcm[i] >= 0 && pcm[i-1] < 0) || (pcm[i] < 0 && pcm[i-1] >= 0)) {
            zeroCrossings++;
        }
    }
    double zcr = static_cast<double>(zeroCrossings) / samples;

    // 3. 從 VadParams 讀取閾值（vadMutex 保護，支援熱更新）
    WhisperAsrEngine::VadParams vp;
    {
        std::lock_guard<std::mutex> lock(impl_->vadMutex);
        vp = impl_->vadParams;
    }

    bool isLowEnergy = (rms < vp.fastSilenceRms);
    bool isLowVariation = (zcr < vp.fastSilenceZcr);
    
    bool isSilence = isLowEnergy && isLowVariation;

    // Debug Log (Throttled: Log roughly every 5 seconds)
    if (impl_->audioBuffer.size() > 0 && impl_->audioBuffer.size() % (16000 * 5) < samples) {
        LOGD("Silence detection: RMS=%.2f, ZCR=%.4f, isSilence=%d (Energy=%d, Var=%d)", 
             rms, zcr, isSilence, isLowEnergy, isLowVariation);
    }

    return isSilence;
}

// ... (Inference Logic) ...

// === 設計理念：推論前能量分佈預檢 ===
//
// 問題：為什麼有了 detectSilence 還需要這個？
// 答：detectSilence 是針對「短片段 (chunk)」的即時判斷。但即使累積了 3 秒的資料，
// 可能整段資料都是由「稍微大聲一點的噪音」組成的（剛好超過 RMS 閾值）。
// Whisper 模型對於這種「非語音的雜訊」極易產生幻覺 (Hallucination)，
// 會強行將噪音翻譯成 "Thank you" 或 "字幕版權宣告"。
//
// 解決方案：窗口化分析 (Windowing Analysis)
// 將長緩衝區切分為 100ms 的小窗口，統計「靜音窗口」的比例。
// 如果 90% 以上的時間都是靜音/底噪，代表這段音訊缺乏連續的語音特徵，
// 應直接在 CPU 層級丟棄，避免浪費 NPU 算力並產生幻覺。
bool WhisperAsrEngine::shouldSkipInference(const int16_t* pcm, size_t samples) {
    const size_t WINDOW_SIZE = 1600;  // 100ms @ 16kHz

    // 從 VadParams 讀取閾值（vadMutex 保護，支援熱更新）
    WhisperAsrEngine::VadParams vp;
    {
        std::lock_guard<std::mutex> lock(impl_->vadMutex);
        vp = impl_->vadParams;
    }

    size_t totalWindows = samples / WINDOW_SIZE;
    if (totalWindows == 0) return false;

    size_t silentWindows = 0;
    double maxRms = 0.0;  // 追蹤最大 RMS 用於 debug

    for (size_t i = 0; i < totalWindows; i++) {
        const int16_t* window = pcm + (i * WINDOW_SIZE);

        long long sum = 0;
        for (size_t j = 0; j < WINDOW_SIZE; j++) {
            sum += static_cast<long long>(window[j]) * window[j];
        }
        double rms = std::sqrt(static_cast<double>(sum) / WINDOW_SIZE);

        if (rms > maxRms) maxRms = rms;

        if (rms < vp.energyThreshold) {
            silentWindows++;
        }
    }

    double silenceRatio = static_cast<double>(silentWindows) / totalWindows;
    bool shouldSkip = silenceRatio >= vp.silenceRatioThreshold;

    // Debug Log
    if (impl_->audioBuffer.size() % (16000 * 10) < samples) {  // Log every ~10s
        LOGD("shouldSkipInference: maxRMS=%.2f, silenceRatio=%.2f, shouldSkip=%d",
             maxRms, silenceRatio, shouldSkip);
    }

    return shouldSkip;
}

void WhisperAsrEngine::emitTranscript(const std::string& text, long startMs, long endMs, bool isFinal) {
    impl_->segmentCounter++;
    std::string segmentId = "seg-" + std::to_string(impl_->segmentCounter);
    if (impl_->transcriptCallback) {
        // 方案 B: 傳遞 isFinal 標誌（true=最終推論, false=中間推論）
        impl_->transcriptCallback(segmentId, text, "User", isFinal, startMs, endMs, language_);
    }
}

// Hybrid VAD：區分 SILENCE（完全靜音）、PAUSE（句內停頓）、SPEECH（活躍語音）
// 解決問題：
//   1. 單純的二元靜音檢測無法區分「句子結束」vs「思考停頓」
//   2. 需要更細緻的能量與頻率分析
// 策略：
//   - SILENCE: RMS < 800 且 ZCR < 0.03 (背景噪音或完全安靜)
//   - PAUSE:   RMS < 1500 且 ZCR < 0.06 (輕微說話或思考停頓)
//   - SPEECH:  其他情況 (活躍的語音能量)
WhisperAsrEngine::VadState WhisperAsrEngine::detectVadState(const int16_t* pcm, size_t samples) {
    if (samples == 0) return VadState::SILENCE;

    // 1. RMS Energy calculation
    long long sum = 0;
    for (size_t i = 0; i < samples; i++) {
        sum += static_cast<long long>(pcm[i]) * pcm[i];
    }
    double rms = std::sqrt(static_cast<double>(sum) / samples);

    // 2. Zero Crossing Rate (ZCR) calculation
    int zeroCrossings = 0;
    for (size_t i = 1; i < samples; i++) {
        if ((pcm[i] >= 0 && pcm[i-1] < 0) || (pcm[i] < 0 && pcm[i-1] >= 0)) {
            zeroCrossings++;
        }
    }
    double zcr = static_cast<double>(zeroCrossings) / samples;

    // 3. 從 VadParams 讀取閾值（vadMutex 保護，支援熱更新）
    WhisperAsrEngine::VadParams vp;
    {
        std::lock_guard<std::mutex> lock(impl_->vadMutex);
        vp = impl_->vadParams;
    }

    VadState rawState;

    // === VAD 三層判定邏輯 ===
    // - RMS < silenceRms:             絕對靜音 → SILENCE
    // - silenceRms ≤ RMS < pauseRms:  中等能量，依 ZCR 判斷
    //   - ZCR < pauseZcr:             低頻停頓 → PAUSE
    //   - ZCR ≥ pauseZcr:             語音 → SPEECH
    // - RMS ≥ pauseRms:               高能量直接 → SPEECH

    if (rms < vp.silenceRms) {
        // 第 1 層：絕對靜音
        rawState = VadState::SILENCE;
    } else if (rms < vp.pauseRms) {
        // 第 2 層：中等能量區，依賴 ZCR 判斷
        if (zcr < vp.pauseZcr) {
            rawState = VadState::PAUSE;    // 低頻停頓或輕聲
        } else {
            rawState = VadState::SPEECH;   // 語音（有頻率變化）
        }
    } else {
        // 第 3 層：高能量區，直接判為 SPEECH
        rawState = VadState::SPEECH;
    }

    // === 3.5 狀態穩定性機制（方案 A 任務 3 - 狀態去抖動）===
    // 計算當前 chunk 的時長（毫秒）
    int chunkDurationMs = (samples * 1000) / 16000;

    VadState state;
    if (rawState == impl_->currentVadState) {
        // 狀態相同，累積持續時間
        impl_->vadStateHoldMs += chunkDurationMs;
        state = impl_->currentVadState;
    } else {
        // 狀態變化，檢查是否持續足夠長時間
        if (impl_->vadStateHoldMs >= impl_->minStateHoldMs) {
            // 允許切換到新狀態
            impl_->currentVadState = rawState;
            impl_->vadStateHoldMs = chunkDurationMs;
            state = rawState;
        } else {
            // 持續時間不足，保持舊狀態（忽略瞬間變化）
            impl_->vadStateHoldMs += chunkDurationMs;
            state = impl_->currentVadState;
        }
    }

    // 4. 增強日誌機制（除錯與監控）
    static VadState lastState = VadState::SILENCE;
    static long lastLogTimeMs = 0;

    // 4.1 記錄狀態轉換（每次變化都輸出）
    if (state != lastState) {
        const char* state_str = (state == VadState::SILENCE) ? "SILENCE" :
                                (state == VadState::PAUSE) ? "PAUSE" : "SPEECH";
        const char* last_state_str = (lastState == VadState::SILENCE) ? "SILENCE" :
                                     (lastState == VadState::PAUSE) ? "PAUSE" : "SPEECH";
        LOGI("VAD State Transition: %s -> %s (RMS=%.2f, ZCR=%.4f)",
             last_state_str, state_str, rms, zcr);
        lastState = state;
    }

    // 4.2 定期輸出詳細狀態（每 5 秒，包含閾值）
    long currentTimeMs = (impl_->audioBuffer.size() * 1000) / 16000;
    if (currentTimeMs - lastLogTimeMs >= 5000) {
        const char* state_str = (state == VadState::SILENCE) ? "SILENCE" :
                                (state == VadState::PAUSE) ? "PAUSE" : "SPEECH";
        LOGD("VAD Status: RMS=%.2f (S<%.0f, P<%.0f), ZCR=%.4f (S<%.2f, P<%.2f), State=%s",
             rms, vp.silenceRms, vp.pauseRms,
             zcr, vp.silenceZcr, vp.pauseZcr,
             state_str);
        lastLogTimeMs = currentTimeMs;
    }

    return state;
}
