// meeting-engine/src/main/cpp/asr/WhisperAsrEngine.cpp
// Whisper ASR 引擎實作

#include "WhisperAsrEngine.h"
#include <android/log.h>
#include <cmath>
#include <fstream>

// RKNN headers（條件式 include，避免編譯時找不到）
// 注意：實際部署到 RK3588 時需要 include 真實 RKNN SDK headers
// #ifdef __aarch64__
// #include "rknn_api.h"
// #endif

#define LOG_TAG "WhisperAsrEngine"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)

// RKNN API 簡化版本（用於編譯通過，實際使用時需要真實 RKNN SDK）
// 為什麼使用簡化版本：允許在所有環境編譯與開發
// 注意：實際部署到 RK3588 時需要連結真實 RKNN SDK
typedef void* rknn_context;
#define RKNN_SUCC 0

// 條件式定義 RKNN API（僅在未定義時）
#ifndef RKNN_API_DEFINED
inline int rknn_init(rknn_context* ctx, void* model, uint32_t size, uint32_t flag) {
    (void)ctx; (void)model; (void)size; (void)flag;
    return -1;  // 簡化版本：總是回傳失敗
}
inline int rknn_destroy(rknn_context ctx) {
    (void)ctx;
    return 0;  // 簡化版本：總是回傳成功
}
#define RKNN_API_DEFINED
#endif

WhisperAsrEngine::WhisperAsrEngine()
    : encoderContext_(nullptr)
    , decoderContext_(nullptr)
    , initialized_(false)
    , silenceDurationMs_(0)
    , segmentCounter_(0)
{
    // 為什麼在建構子初始化：確保成員變數有初始值
    LOGD("WhisperAsrEngine created");
}

WhisperAsrEngine::~WhisperAsrEngine() {
    // 為什麼在解構子呼叫 release：RAII 原則，確保資源自動清理
    release();
    LOGD("WhisperAsrEngine destroyed");
}

bool WhisperAsrEngine::init(const std::string& modelsPath, const std::string& language) {
    // 為什麼先檢查參數：防止空路徑導致後續錯誤
    if (modelsPath.empty()) {
        LOGE("init failed: modelsPath is empty");
        return false;
    }

    if (language.empty()) {
        LOGE("init failed: language is empty");
        return false;
    }

    // 檢查模型檔案是否存在
    if (!validateModelFiles(modelsPath)) {
        LOGE("init failed: model files not found in %s", modelsPath.c_str());
        return false;
    }

    modelsPath_ = modelsPath;
    language_ = language;

    // 載入 encoder 模型
    std::string encoderPath = modelsPath + "/whisper_encoder_base_20s.rknn";
    if (!loadRknnModel(encoderPath, encoderContext_)) {
        LOGE("init failed: cannot load encoder model from %s", encoderPath.c_str());
        return false;
    }

    // 載入 decoder 模型
    std::string decoderPath = modelsPath + "/whisper_decoder_base_20s.rknn";
    if (!loadRknnModel(decoderPath, decoderContext_)) {
        LOGE("init failed: cannot load decoder model from %s", decoderPath.c_str());
        // 清理已載入的 encoder
        if (encoderContext_ != nullptr) {
            rknn_destroy(encoderContext_);
            encoderContext_ = nullptr;
        }
        return false;
    }

    initialized_ = true;
    LOGI("WhisperAsrEngine initialized successfully (language=%s)", language.c_str());
    return true;
}

void WhisperAsrEngine::start() {
    // 為什麼檢查 initialized：防止未初始化就使用
    if (!initialized_) {
        LOGE("start failed: engine not initialized");
        return;
    }

    // 重置內部狀態
    audioBuffer_.clear();
    silenceDurationMs_ = 0;
    segmentCounter_ = 0;

    LOGD("WhisperAsrEngine started");
}

void WhisperAsrEngine::pushAudio(const int16_t* pcm, size_t samples) {
    // 為什麼檢查參數：防止空指標導致 crash
    if (pcm == nullptr || samples == 0) {
        LOGE("pushAudio failed: invalid parameters");
        return;
    }

    if (!initialized_) {
        LOGE("pushAudio failed: engine not initialized");
        return;
    }

    // 累積音訊至 buffer
    // 為什麼累積：Whisper 需要至少 3 秒音訊進行推論
    audioBuffer_.insert(audioBuffer_.end(), pcm, pcm + samples);

    // 偵測靜音
    bool isSilence = detectSilence(pcm, samples);

    if (isSilence) {
        // 累積靜音時間（假設 samples = 160 對應 10ms @ 16kHz）
        silenceDurationMs_ += (samples * 1000) / 16000;

        // 超過門檻時切段
        if (silenceDurationMs_ >= SILENCE_THRESHOLD_MS && !audioBuffer_.empty()) {
            LOGD("Silence detected (%d ms), triggering inference", silenceDurationMs_);

            // 執行推論
            std::string text = runInference(audioBuffer_.data(), audioBuffer_.size());

            if (!text.empty()) {
                // 輸出 TranscriptSegment
                // 注意：startMs 與 endMs 需要實際時間戳，這裡簡化為 0
                emitTranscript(text, 0, 0);
            }

            // 清空 buffer，準備下一段
            audioBuffer_.clear();
            silenceDurationMs_ = 0;
        }
    } else {
        // 重置靜音計時器
        silenceDurationMs_ = 0;
    }

    // 防止 buffer 無限增長（如 10 分鐘連續語音）
    // 為什麼限制：避免 OOM
    const size_t MAX_BUFFER_SAMPLES = 16000 * 600;  // 10 分鐘 @ 16kHz
    if (audioBuffer_.size() > MAX_BUFFER_SAMPLES) {
        LOGD("Buffer too large (%zu samples), forcing inference", audioBuffer_.size());

        std::string text = runInference(audioBuffer_.data(), audioBuffer_.size());
        if (!text.empty()) {
            emitTranscript(text, 0, 0);
        }

        audioBuffer_.clear();
    }
}

void WhisperAsrEngine::stop() {
    if (!initialized_) {
        return;
    }

    // Flush 剩餘音訊
    // 為什麼需要 flush：確保最後一段語音也被處理
    if (!audioBuffer_.empty()) {
        LOGD("Flushing remaining audio (%zu samples)", audioBuffer_.size());

        std::string text = runInference(audioBuffer_.data(), audioBuffer_.size());
        if (!text.empty()) {
            emitTranscript(text, 0, 0);
        }

        audioBuffer_.clear();
    }

    LOGD("WhisperAsrEngine stopped");
}

void WhisperAsrEngine::release() {
    // 為什麼檢查 initialized：防止重複釋放
    if (!initialized_) {
        return;
    }

    // 釋放 RKNN contexts
    if (encoderContext_ != nullptr) {
        rknn_destroy(encoderContext_);
        encoderContext_ = nullptr;
        LOGD("Encoder context released");
    }

    if (decoderContext_ != nullptr) {
        rknn_destroy(decoderContext_);
        decoderContext_ = nullptr;
        LOGD("Decoder context released");
    }

    // 清空內部狀態
    audioBuffer_.clear();
    modelsPath_.clear();
    language_.clear();
    initialized_ = false;

    LOGD("WhisperAsrEngine released");
}

// === 私有輔助方法 ===

bool WhisperAsrEngine::loadRknnModel(const std::string& modelPath, rknn_context& context) {
    // 為什麼讀取檔案：RKNN API 需要 model buffer
    std::ifstream file(modelPath, std::ios::binary | std::ios::ate);
    if (!file.is_open()) {
        LOGE("Failed to open model file: %s", modelPath.c_str());
        return false;
    }

    // 取得檔案大小
    size_t fileSize = file.tellg();
    file.seekg(0, std::ios::beg);

    // 讀取模型至 buffer
    std::vector<char> modelBuffer(fileSize);
    if (!file.read(modelBuffer.data(), fileSize)) {
        LOGE("Failed to read model file: %s", modelPath.c_str());
        return false;
    }

    LOGD("Model file loaded: %s (%zu bytes)", modelPath.c_str(), fileSize);

    // 初始化 RKNN context
    // 注意：此處使用簡化實作，實際部署時需要呼叫真實 rknn_init
    int ret = rknn_init(&context, modelBuffer.data(), fileSize, 0);
    if (ret != RKNN_SUCC) {
        // 簡化版本總是回傳失敗，這裡跳過錯誤處理
        LOGD("RKNN init returned %d (using simplified implementation)", ret);
    }

    // 為了開發環境測試，設定一個 fake context
    context = reinterpret_cast<rknn_context>(0x1);
    LOGD("RKNN context created (simplified for development)");
    return true;
}

bool WhisperAsrEngine::validateModelFiles(const std::string& modelsPath) {
    // 檢查兩個模型檔是否存在
    std::string encoderPath = modelsPath + "/whisper_encoder_base_20s.rknn";
    std::string decoderPath = modelsPath + "/whisper_decoder_base_20s.rknn";

    std::ifstream encoderFile(encoderPath);
    std::ifstream decoderFile(decoderPath);

    bool encoderExists = encoderFile.good();
    bool decoderExists = decoderFile.good();

    if (!encoderExists) {
        LOGE("Encoder model not found: %s", encoderPath.c_str());
    }

    if (!decoderExists) {
        LOGE("Decoder model not found: %s", decoderPath.c_str());
    }

    return encoderExists && decoderExists;
}

std::string WhisperAsrEngine::runInference(const int16_t* pcmData, size_t samples) {
    // 為什麼簡化實作：完整的 Whisper 推論邏輯複雜，Phase 3.3 先建立框架
    // 實際實作需要：
    // 1. PCM → mel-spectrogram (80-bin mel filterbank)
    // 2. encoder: mel → features
    // 3. decoder: features → tokens (beam search)
    // 4. tokens → text (tokenizer decode)

    LOGD("runInference called with %zu samples (simplified)", samples);

    // 簡化實作：回傳測試字串
    // Phase 3.5 將實作真實推論邏輯
    return "Test transcript";
}

bool WhisperAsrEngine::detectSilence(const int16_t* pcm, size_t samples) {
    // 計算 RMS (Root Mean Square)
    // 為什麼使用 RMS：簡單且有效的音量指標
    long long sum = 0;
    for (size_t i = 0; i < samples; i++) {
        sum += static_cast<long long>(pcm[i]) * pcm[i];
    }

    double rms = std::sqrt(static_cast<double>(sum) / samples);

    // 靜音門檻（經驗值，可調整）
    // 為什麼 500：int16_t 範圍為 -32768 ~ 32767，500 約為 1.5% 音量
    const double SILENCE_THRESHOLD = 500.0;

    return rms < SILENCE_THRESHOLD;
}

void WhisperAsrEngine::emitTranscript(const std::string& text, long startMs, long endMs) {
    // 為什麼需要此方法：將轉錄結果回傳至 Kotlin 層
    // Phase 3.4 將實作 JNI callback 整合

    segmentCounter_++;

    LOGI("Transcript #%d: %s (start=%ld, end=%ld)",
         segmentCounter_, text.c_str(), startMs, endMs);

    // TODO: Phase 3.4 實作 JNI callback
    // jniCallback->onTranscript(segment);
}
