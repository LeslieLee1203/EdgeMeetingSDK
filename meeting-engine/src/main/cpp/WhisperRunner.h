#pragma once

#include <string>

// 提供最小可測的 ASR 封裝介面。
class WhisperRunner {
public:
    int init(const std::string& encoderPath, const std::string& decoderPath);
    // 累積已接收的樣本數，提供節奏控制。
    int acceptedSamples() const { return acceptedSamples_; }
    // 重設累積樣本數。
    void resetAcceptedSamples() { acceptedSamples_ = 0; }
    int acceptPcm(const float* data, int samples, int sampleRate);
    int flush();
    int stop();

private:
    int acceptedSamples_ = 0;
};
