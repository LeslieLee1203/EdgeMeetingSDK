// meeting-engine/src/main/cpp/asr/WhisperAsrEngine.h
// Whisper ASR 引擎實作（使用 RKNN Runtime on RK3588）

#ifndef MEETING_ENGINE_WHISPER_ASR_ENGINE_H
#define MEETING_ENGINE_WHISPER_ASR_ENGINE_H

#include "AsrEngine.h"
#include <string>
#include <vector>
#include <cstdint>
#include <functional>
#include <memory> // for unique_ptr

// Transcript callback 型別定義
using TranscriptCallback = std::function<void(
    const std::string& segmentId,
    const std::string& text,
    const std::string& speakerId,
    bool isFinal,
    long startMs,
    long endMs,
    const std::string& languageCode  // Phase 4: 新增語言代碼參數
)>;

class WhisperAsrEngine : public AsrEngine {
public:
    WhisperAsrEngine();
    ~WhisperAsrEngine() override;

    // 禁止拷貝與賦值
    WhisperAsrEngine(const WhisperAsrEngine&) = delete;
    WhisperAsrEngine& operator=(const WhisperAsrEngine&) = delete;

    void setTranscriptCallback(TranscriptCallback callback);

    bool init(const std::string& modelsPath, const std::string& language) override;
    void start() override;
    void pushAudio(const int16_t* pcm, size_t samples) override;
    void stop() override;
    void release() override;

private:
    // PImpl idiom
    struct Impl;
    Impl* impl_;

    // 聲明推論線程函數為 friend，允許訪問 private 成員
    friend void inferenceThreadFunc(Impl* impl, WhisperAsrEngine* engine);

    // Private helpers for internal logic
    std::string runInference(const int16_t* pcmData, size_t samples);
    bool detectSilence(const int16_t* pcm, size_t samples);
    bool shouldSkipInference(const int16_t* pcm, size_t samples);
    void emitTranscript(const std::string& text, long startMs, long endMs, bool isFinal = true);  // 方案 B: 添加 isFinal 參數

    // VAD 狀態檢測（簡化版）
    enum class VadState {
        SILENCE,    // 完全靜音（背景噪音）
        PAUSE,      // 句內停頓（輕微能量降低）
        SPEECH      // 活躍語音
    };
    VadState detectVadState(const int16_t* pcm, size_t samples);
    
    // Load/Validate helpers
    bool loadRknnModel(const std::string& modelPath, void* context); // changed to void* to avoid rknn_context
    bool validateModelFiles(const std::string& modelsPath);
    
    // Config state
    std::string modelsPath_;
    std::string language_;
    bool initialized_;
};

#endif // MEETING_ENGINE_WHISPER_ASR_ENGINE_H