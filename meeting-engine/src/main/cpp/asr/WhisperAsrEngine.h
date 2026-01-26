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
    Impl* impl_; // Raw pointer manually managed or unique_ptr? 
                 // Used raw pointer in .cpp previously, stick to it or use unique_ptr.
                 // Header file needs to know size of unique_ptr which requires complete type in some cases,
                 // but typically unique_ptr works with incomplete type if destructor is defined in .cpp.
                 // To be safe and consistent with previous manual new/delete in .cpp:
                 
    // Auxiliary methods called by Impl or used internally, 
    // but with PImpl, logic moves to .cpp.
    // Keeping these virtual overrides.
    
    // We need these for internal use if we don't move everything to Impl.
    // But better to move everything stateful to Impl.
    
    // Private helpers for internal logic
    std::string runInference(const int16_t* pcmData, size_t samples);
    bool detectSilence(const int16_t* pcm, size_t samples);
    bool shouldSkipInference(const int16_t* pcm, size_t samples);
    void emitTranscript(const std::string& text, long startMs, long endMs);
    
    // Load/Validate helpers
    bool loadRknnModel(const std::string& modelPath, void* context); // changed to void* to avoid rknn_context
    bool validateModelFiles(const std::string& modelsPath);
    
    // Config state
    std::string modelsPath_;
    std::string language_;
    bool initialized_;
};

#endif // MEETING_ENGINE_WHISPER_ASR_ENGINE_H