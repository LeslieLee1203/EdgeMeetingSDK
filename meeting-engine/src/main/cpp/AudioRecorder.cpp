//
// Created by leslie.lee on 2026/1/9.
//
#include "AudioRecorder.h"
#include <cmath>

AudioRecorder::AudioRecorder() {
    // 初始化 Buffer，給 16000 samples (約 1秒緩衝)
    audioBuffer = std::make_unique<RingBuffer<float>>(16000);
}

AudioRecorder::~AudioRecorder() {
    stop();
}

bool AudioRecorder::start(int inputPreset, int deviceId) {
    oboe::AudioStreamBuilder builder;

    builder.setDirection(oboe::Direction::Input)
            ->setPerformanceMode(oboe::PerformanceMode::LowLatency)
            ->setSharingMode(oboe::SharingMode::Shared)
            ->setFormat(oboe::AudioFormat::Float)
            ->setChannelCount(1)
            ->setSampleRate(16000)
            ->setCallback(this);

    // ✅ 修改點：應用參數
    if (inputPreset != 0) {
        LOGD("Setting InputPreset: %d", inputPreset);
        builder.setInputPreset(static_cast<oboe::InputPreset>(inputPreset));
    }

    if (deviceId != 0) {
        LOGD("Setting DeviceId: %d", deviceId);
        builder.setDeviceId(deviceId);
    }

    oboe::Result result = builder.openStream(stream);
    if (result != oboe::Result::OK) {
        LOGE("Failed to open stream: %s", oboe::convertToText(result));
        return false;
    }

    result = stream->requestStart();
    if (result != oboe::Result::OK) {
        LOGE("Failed to start stream: %s", oboe::convertToText(result));
        stream->close();
        return false;
    }

    isRecording = true;
    LOGD("Audio stream started. SampleRate: %d", stream->getSampleRate());
    return true;
}

void AudioRecorder::stop() {
    if (stream) {
        stream->stop();
        stream->close();
        stream.reset();
        isRecording = false;
        LOGD("Audio stream stopped.");
    }
}

// 讀取實作
size_t AudioRecorder::readAudio(float* dest, size_t count) {
    if (!audioBuffer) return 0;
    return audioBuffer->read(dest, count);
}

oboe::DataCallbackResult AudioRecorder::onAudioReady(
        oboe::AudioStream *audioStream,
        void *audioData,
        int32_t numFrames) {

    // 簡單計算 RMS (音量) 來驗證麥克風有在動
    auto *floatData = static_cast<float *>(audioData);

    // 1. 寫入 Ring Buffer (這是 Phase 2.5 的核心)
    if (audioBuffer) {
        audioBuffer->write(floatData, numFrames);
    }

    // 2. 為了驗證沒有卡死，我們保留 RMS Log
    float sum = 0.0f;
    for (int i = 0; i < numFrames; ++i) {
        sum += floatData[i] * floatData[i];
    }
    float rms = (numFrames > 0) ? std::sqrt(sum / numFrames) : 0.0f;

    // 過濾極小雜訊，避免 Log 洗版
    if (rms > 0.01f) {
        // 簡單過濾極小雜訊
        LOGD("MIC RMS: %.4f", rms);
    }

    return oboe::DataCallbackResult::Continue;
}

void AudioRecorder::onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) {
    LOGE("Stream error: %s", oboe::convertToText(error));
}