#include "WhisperRunner.h"

// 提供最小可測的 init 實作，先做參數檢查。
int WhisperRunner::init(const std::string& encoderPath, const std::string& decoderPath) {
    if (encoderPath.empty() || decoderPath.empty()) {
        return 1;
    }
    return 0;
}

// 提供最小可測的 acceptPcm 實作，先做參數檢查並累積樣本數。
int WhisperRunner::acceptPcm(const float* data, int samples, int sampleRate) {
    if (data == nullptr || samples <= 0 || sampleRate <= 0) {
        return 1;
    }
    acceptedSamples_ += samples;
    return 0;
}

// 提供最小可測的 flush 實作，flush 後重設累積樣本數。
int WhisperRunner::flush() {
    acceptedSamples_ = 0;
    return 0;
}

// 提供最小可測的 stop 實作，stop 後重設累積樣本數。
int WhisperRunner::stop() {
    acceptedSamples_ = 0;
    return 0;
}
