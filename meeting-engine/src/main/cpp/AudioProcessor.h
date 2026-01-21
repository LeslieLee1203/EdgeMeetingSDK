//
// Created by Leslie Lee 李俊德 (奧圖碼) on 2026/1/15.
//

#ifndef EDGEMEETINGSDK_AUDIOPROCESSOR_H
#define EDGEMEETINGSDK_AUDIOPROCESSOR_H

#include <thread>
#include <atomic>
#include <vector>
#include <jni.h>
#include "AudioRecorder.h"
#include "asr/AsrEngine.h"

class AudioProcessor {
public:
    // 建構子需要 AudioRecorder (資料來源) 和 JavaVM (JNI 溝通工具)
    AudioProcessor(AudioRecorder* recorder, JavaVM* jvm, jobject callbackObj);
    ~AudioProcessor();

    void start();
    void stop();

    // T039: 設定 ASR 引擎（可選，若設定則音訊會同時傳給 ASR）
    void setAsrEngine(AsrEngine* engine) { asrEngine = engine; }

private:
    void workerLoop(); // 這是 Worker Thread 的主迴圈

    AudioRecorder* recorder;
    JavaVM* javaVM;
    jobject globalCallbackObj; // 必須將 Kotlin 物件升級為 GlobalRef

    // T039: ASR 引擎指標（nullptr 表示不啟用 ASR）
    AsrEngine* asrEngine = nullptr;

    std::atomic<bool> isRunning { false };
    std::thread workerThread;
};

#endif //EDGEMEETINGSDK_AUDIOPROCESSOR_H
