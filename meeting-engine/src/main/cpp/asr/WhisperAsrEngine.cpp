// meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp
// Whisper ASR 引擎實作 - 整合 RKNN Runtime

#include "WhisperAsrEngine.h"
#include "WhisperUtils.h"
#include "rknn_api.h"
#include <android/log.h>
#include <cmath>
#include <fstream>
#include <vector>
#include <string>
#include <mutex>

#define LOG_TAG "WhisperAsrEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// 輔助結構：管理 RKNN context 與 memory
struct RknnModelContext {
    rknn_context ctx = 0;
    rknn_input_output_num io_num;
    rknn_tensor_attr *input_attrs = nullptr;
    rknn_tensor_attr *output_attrs = nullptr;
};

// 輔助函式：載入與釋放
static int init_rknn_model(const char *model_path, RknnModelContext *app_ctx) {
    int ret;
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
            return -1;
        }
    }

    // Get Output Attrs
    for (int i = 0; i < app_ctx->io_num.n_output; i++) {
        app_ctx->output_attrs[i].index = i;
        ret = rknn_query(app_ctx->ctx, RKNN_QUERY_OUTPUT_ATTR, &(app_ctx->output_attrs[i]), sizeof(rknn_tensor_attr));
        if (ret != RKNN_SUCC) {
            LOGE("rknn_query OUTPUT_ATTR %d fail! ret=%d", i, ret);
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

// PImpl idiom to hide implementation details from header
struct WhisperAsrEngine::Impl {
    RknnModelContext encoder;
    RknnModelContext decoder;
    
    // Resources
    float* mel_filters = nullptr;
    VocabEntry* vocab = nullptr;
    
    // Config
    int task_code = 50259; // 50259=en, 50260=zh
    
    // Runtime State
    std::mutex mutex;
    std::vector<int16_t> audioBuffer;
    int silenceDurationMs = 0;
    int segmentCounter = 0;
    long totalAudioMs = 0;  // 累積的音訊總時間（毫秒）- 用於計算時間戳
    TranscriptCallback transcriptCallback;
    
    ~Impl() {
        if (mel_filters) free(mel_filters);
        // vocab memory management is tricky depending on how read_vocab allocates. 
        // For simplicity assuming leakage or separate cleanup for now if implementation is complex.
        // Actually read_vocab allocates 'token' with strdup, needs cleanup.
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
    std::string vocabPath = modelsPath + "/vocab_en.txt"; // 預設英文
    std::string filtersPath = modelsPath + "/mel_80_filters.txt";

    // 2. 設定語言
    if (language == "zh") {
        impl_->task_code = 50260;
        vocabPath = modelsPath + "/vocab_zh.txt";
    } else {
        impl_->task_code = 50259; // en
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

    modelsPath_ = modelsPath;
    language_ = language;
    initialized_ = true;
    LOGI("WhisperAsrEngine initialized successfully");
    return true;
}

void WhisperAsrEngine::start() {
    if (!initialized_) return;
    std::lock_guard<std::mutex> lock(impl_->mutex);
    impl_->audioBuffer.clear();
    impl_->silenceDurationMs = 0;
    impl_->segmentCounter = 0;
    impl_->totalAudioMs = 0;  // 重置累積時間
    LOGD("WhisperAsrEngine started - Buffer cleared");
}

void WhisperAsrEngine::stop() {
    if (!initialized_) return;
    std::lock_guard<std::mutex> lock(impl_->mutex);

    // Flush remaining
    if (!impl_->audioBuffer.empty()) {
        LOGD("Stop: Flushing remaining audio (%zu samples)", impl_->audioBuffer.size());

        // Phase 3: Check if remaining audio is just noise/silence
        // 結尾幻覺防護 (End-of-Session Hallucination Protection)
        // 當使用者按下停止時，最後一段 Buffer 往往只包含按鈕聲或環境音。
        // Whisper 極容易將這段無意義的結尾翻譯成 "Bye", "Thank you for watching" 等。
        // 強制檢查能量分佈，若無效則直接丟棄。
        if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
            LOGD("Stop: Skipping flush (audio energy too low)");
            impl_->audioBuffer.clear();
            return;
        }

        // 計算時間戳：片段開始時間 = totalAudioMs - buffer 的持續時間
        long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
        long startMs = impl_->totalAudioMs - bufferDurationMs;
        long endMs = impl_->totalAudioMs;

        std::string text = runInference(impl_->audioBuffer.data(), impl_->audioBuffer.size());
        if (!text.empty()) {
            emitTranscript(text, startMs, endMs);
        } else {
            LOGD("Stop: Flush resulted in empty transcript");
        }
        impl_->audioBuffer.clear();
    }
}

void WhisperAsrEngine::release() {
    if (!initialized_) return;
    
    release_rknn_model(&impl_->encoder);
    release_rknn_model(&impl_->decoder);
    
    initialized_ = false;
    LOGD("WhisperAsrEngine released");
}

void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    if (!initialized_ || samples == 0) return;

    std::lock_guard<std::mutex> lock(impl_->mutex);

    impl_->audioBuffer.insert(impl_->audioBuffer.end(), pcm, pcm + samples);

    // 更新累積音訊時間（16kHz 採樣率，samples * 1000 / 16000 = 毫秒）
    impl_->totalAudioMs += (samples * 1000) / 16000;

    // 簡單靜音偵測
    bool isSilence = detectSilence(pcm, samples);

    // Debug Log (sampling to avoid spamming)
    if (impl_->audioBuffer.size() % (16000 * 5) < samples) { // Log roughly every 5 seconds
        LOGD("pushAudio: Buffer size = %zu, isSilence = %d, duration = %d ms, totalAudioMs = %ld",
             impl_->audioBuffer.size(), isSilence, impl_->silenceDurationMs, impl_->totalAudioMs);
    }

    if (isSilence) {
        impl_->silenceDurationMs += (samples * 1000) / 16000;

        const int SILENCE_THRESHOLD_MS = 700;
        // 3秒以上才處理，且需要靜音斷句
        // 累積樣本數 > 3秒 (16000*3 = 48000)
        if (impl_->audioBuffer.size() > 48000 && impl_->silenceDurationMs >= SILENCE_THRESHOLD_MS) {
            // Phase 2: Energy Pre-check before inference (縱深防禦策略)
            // 即使滿足了「靜音觸發」條件，我們仍需進行二次檢查。
            // 理由：若 buffer 內累積的主要是噪音（剛好超過閾值），送入 NPU 只會得到幻覺。
            // 這裡進行一個極低成本的 CPU 檢查，若判定無效則直接丟棄，
            // 既省電又避免了 "Thank you" 類型的幻覺。
            if (shouldSkipInference(impl_->audioBuffer.data(), impl_->audioBuffer.size())) {
                LOGD("Skipping inference: Audio energy too low (likely silence/noise)");
                impl_->audioBuffer.clear();
                impl_->silenceDurationMs = 0;
                return;
            }

            LOGD("Triggering inference (Silence): Buffer=%zu, Silence=%d ms",
                 impl_->audioBuffer.size(), impl_->silenceDurationMs);

            // 計算時間戳：片段開始時間 = totalAudioMs - buffer 的持續時間
            long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
            long startMs = impl_->totalAudioMs - bufferDurationMs;
            long endMs = impl_->totalAudioMs;

            std::string text = runInference(impl_->audioBuffer.data(), impl_->audioBuffer.size());
            if (!text.empty()) {
                emitTranscript(text, startMs, endMs);
            }
            impl_->audioBuffer.clear();
            impl_->silenceDurationMs = 0;
        }
    } else {
        impl_->silenceDurationMs = 0;
    }

    // 強制截斷：如果太長 (例如 20秒) 即使沒靜音也要處理
    if (impl_->audioBuffer.size() > 16000 * 20) {
        LOGD("Triggering inference (MaxLength): Buffer=%zu", impl_->audioBuffer.size());

        // 計算時間戳：片段開始時間 = totalAudioMs - buffer 的持續時間
        long bufferDurationMs = (impl_->audioBuffer.size() * 1000) / 16000;
        long startMs = impl_->totalAudioMs - bufferDurationMs;
        long endMs = impl_->totalAudioMs;

        std::string text = runInference(impl_->audioBuffer.data(), impl_->audioBuffer.size());
        if (!text.empty()) {
            emitTranscript(text, startMs, endMs);
        }
        impl_->audioBuffer.clear();
        impl_->silenceDurationMs = 0;
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
    
    std::string all_token_str = "";

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

        if (next_token < VOCAB_NUM) {
             if (vocab[next_token].token) {
                 all_token_str += vocab[next_token].token;
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
    
    // Post process
    replace_substr(all_token_str, "\u0120", " ");
    replace_substr(all_token_str, "<|endoftext|>", "");
    replace_substr(all_token_str, "\n", "");
    
    if (task_code == 50260) {
        all_token_str = base64_decode(all_token_str);
    }
    
    result_text = all_token_str;
    LOGI("Decoder Result: '%s'", result_text.c_str());
    return 0;
}

std::string WhisperAsrEngine::runInference(const int16_t* pcmData, size_t samples) {
    if (!initialized_) return "";
    
    LOGD("runInference: Processing %zu samples", samples);
    
    // 1. Convert PCM to Float & Preprocess
    std::vector<float> audio_float(samples);
    for(size_t i=0; i<samples; i++) {
        audio_float[i] = pcmData[i] / 32768.0f;
    }

    std::vector<float> x_mel;
    
    audio_preprocess(audio_float.data(), samples, impl_->mel_filters, x_mel);
    LOGD("runInference: Preprocessing done. Mel size: %zu", x_mel.size());

    // 2. Encoder
    float *encoder_output = (float *)malloc(ENCODER_OUTPUT_SIZE * sizeof(float));
    if (inference_encoder(&impl_->encoder, x_mel, impl_->mel_filters, encoder_output) != 0) {
        LOGE("Encoder inference failed");
        free(encoder_output);
        return "";
    }
    LOGD("runInference: Encoder done");

    // 3. Decoder
    std::string text;
    if (inference_decoder(&impl_->decoder, encoder_output, impl_->vocab, impl_->task_code, text) != 0) {
        LOGE("Decoder inference failed");
        free(encoder_output);
        return "";
    }
    
    free(encoder_output);
    return text;
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

    // 3. Thresholds (Phase 1A & 1B)
    // 閾值設定理由：
    // RMS = 1200: 對應約 -45dBFS (16-bit)，能過濾大多數辦公室背景噪音。
    // ZCR = 0.05: 即每 100 個採樣點少於 5 次翻轉 (對應 < 800Hz 的單調波形)。
    const double SILENCE_RMS_THRESHOLD = 1200.0; 
    const double SILENCE_ZCR_THRESHOLD = 0.05;

    bool isLowEnergy = (rms < SILENCE_RMS_THRESHOLD);
    bool isLowVariation = (zcr < SILENCE_ZCR_THRESHOLD);
    
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
    const double ENERGY_THRESHOLD = 1000.0; // 略低於 detectSilence，更嚴格
    const double SILENCE_RATIO_THRESHOLD = 0.9;  // 必須有 90% 以上區域是安靜的才跳過

    size_t totalWindows = samples / WINDOW_SIZE;
    if (totalWindows == 0) return false; 

    size_t silentWindows = 0;
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

void WhisperAsrEngine::emitTranscript(const std::string& text, long startMs, long endMs) {
    impl_->segmentCounter++;
    std::string segmentId = "seg-" + std::to_string(impl_->segmentCounter);
    if (impl_->transcriptCallback) {
        // Phase 4: 傳遞語言代碼（從 language_ 成員變數取得）
        impl_->transcriptCallback(segmentId, text, "User", true, startMs, endMs, language_);
    }
}
