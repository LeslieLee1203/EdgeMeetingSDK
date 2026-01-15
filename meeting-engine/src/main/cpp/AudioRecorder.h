//
// Created by leslie.lee on 2026/1/9.
//

#ifndef EDGEMEETINGSDK_AUDIORECORDER_H
#define EDGEMEETINGSDK_AUDIORECORDER_H

#include <oboe/Oboe.h>
#include <memory>
#include <android/log.h>
#include "RingBuffer.h"

#define MODULE_TAG "EdgeAudio"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, MODULE_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, MODULE_TAG, __VA_ARGS__)

class AudioRecorder : public oboe::AudioStreamCallback {
public:
    AudioRecorder();
    virtual ~AudioRecorder();

    // ✅ 修改點：加入參數以支援未來的多重輸入需求
    // inputPreset: 0=Default, 1=Generic, 6=VoiceRecognition ...
    // deviceId: 0=Default (Auto), 其他 ID 用於指定特定 Loopback 裝置
    bool start(int inputPreset = 0, int deviceId = 0);
    void stop();

    // 給外部讀取音訊的介面
    size_t readAudio(float* dest, size_t count);

    // Oboe Callback
    oboe::DataCallbackResult onAudioReady(
            oboe::AudioStream *audioStream,
            void *audioData,
            int32_t numFrames) override;

    void onErrorAfterClose(oboe::AudioStream *oboeStream, oboe::Result error) override;

private:
    std::shared_ptr<oboe::AudioStream> stream;
    bool isRecording = false;

    // Buffer 指標
    std::unique_ptr<RingBuffer<float>> audioBuffer;
};

#endif //EDGEMEETINGSDK_AUDIORECORDER_H
