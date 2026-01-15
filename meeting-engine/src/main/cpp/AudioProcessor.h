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

class AudioProcessor {
public:
    // 建構子需要 AudioRecorder (資料來源) 和 JavaVM (JNI 溝通工具)
    AudioProcessor(AudioRecorder* recorder, JavaVM* jvm, jobject callbackObj);
    ~AudioProcessor();

    void start();
    void stop();

private:
    void workerLoop(); // 這是 Worker Thread 的主迴圈

    AudioRecorder* recorder;
    JavaVM* javaVM;
    jobject globalCallbackObj; // 必須將 Kotlin 物件升級為 GlobalRef

    std::atomic<bool> isRunning { false };
    std::thread workerThread;
};

#endif //EDGEMEETINGSDK_AUDIOPROCESSOR_H
