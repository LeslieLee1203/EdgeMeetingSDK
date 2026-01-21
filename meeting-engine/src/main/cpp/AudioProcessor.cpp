//
// Created by Leslie Lee 李俊德 (奧圖碼) on 2026/1/15.
//
#include "AudioProcessor.h"
#include <android/log.h>

#define TAG "AudioProcessor"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, TAG, __VA_ARGS__)

AudioProcessor::AudioProcessor(AudioRecorder* rec, JavaVM* jvm, jobject cbObj)
        : recorder(rec), javaVM(jvm) {

    // 關鍵 JNI 操作：建立 Global Reference
    // 因為 cbObj 是 Local Reference，函式結束就會失效。必須升級才能跨執行緒使用。
    JNIEnv* env;
    if (jvm->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
        globalCallbackObj = env->NewGlobalRef(cbObj);
    } else {
        LOGE("Failed to get JNIEnv for GlobalRef");
        globalCallbackObj = nullptr;
    }
}

AudioProcessor::~AudioProcessor() {
    stop();
    // 釋放 Global Reference
    if (globalCallbackObj && javaVM) {
        JNIEnv* env;
        // 注意：Destructor 可能在任意執行緒，需小心 GetEnv
        if (javaVM->GetEnv((void**)&env, JNI_VERSION_1_6) == JNI_OK) {
            env->DeleteGlobalRef(globalCallbackObj);
        }
    }
}

void AudioProcessor::start() {
    if (isRunning) return;
    isRunning = true;
    workerThread = std::thread(&AudioProcessor::workerLoop, this);
    LOGD("Processor thread started");
}

void AudioProcessor::stop() {
    if (!isRunning) return;
    isRunning = false;
    if (workerThread.joinable()) {
        workerThread.join();
    }
    LOGD("Processor thread stopped");
}

void AudioProcessor::workerLoop() {
    // 1. Attach Current Thread to JVM
    // 這是 Worker Thread，原本不認識 Java，必須 Attach 才能呼叫 JNI
    JNIEnv* env;
    if (javaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
        LOGE("Failed to attach thread to JVM");
        return;
    }

    // 2. 找到 Kotlin 的 callback 方法 ID
    // 我們呼叫的是 JniEngineBridge.onNativeAudioData(float[])
    jclass bridgeClass = env->GetObjectClass(globalCallbackObj);
    jmethodID callbackMethodId = env->GetMethodID(bridgeClass, "onNativeAudioData", "([F)V");

    if (!callbackMethodId) {
        LOGE("Method onNativeAudioData not found!");
        javaVM->DetachCurrentThread();
        return;
    }

    // 準備一個 buffer 來接 RingBuffer 的資料 (例如一次讀 160ms = 2560 samples)
    const int CHUNK_SIZE = 2560;
    std::vector<float> readBuffer(CHUNK_SIZE);

    // T039: 準備 int16_t buffer 供 ASR 引擎使用
    std::vector<int16_t> pcmBuffer(CHUNK_SIZE);

    while (isRunning) {
        // 從 RingBuffer 讀取資料
        // 如果 AudioRecorder 還沒 start，這裡會讀到 0
        size_t readCount = recorder->readAudio(readBuffer.data(), CHUNK_SIZE);

        if (readCount > 0) {
            // 3. 呼叫 Java 方法
            // 建立 Java FloatArray
            jfloatArray javaArray = env->NewFloatArray(readCount);
            env->SetFloatArrayRegion(javaArray, 0, readCount, readBuffer.data());

            // 呼叫 Kotlin
            env->CallVoidMethod(globalCallbackObj, callbackMethodId, javaArray);

            // 釋放 LocalRef (很重要！不然迴圈跑久了會 OOM)
            env->DeleteLocalRef(javaArray);

            // T039: 將音訊傳給 ASR 引擎（如果已設定）
            if (asrEngine) {
                // 轉換 float [-1.0, 1.0] → int16_t [-32768, 32767]
                for (size_t i = 0; i < readCount; i++) {
                    float sample = readBuffer[i];
                    // Clamp to [-1.0, 1.0]
                    if (sample > 1.0f) sample = 1.0f;
                    if (sample < -1.0f) sample = -1.0f;
                    pcmBuffer[i] = static_cast<int16_t>(sample * 32767.0f);
                }
                asrEngine->pushAudio(pcmBuffer.data(), readCount);
            }
        } else {
            // Buffer 空了，稍微睡一下避免 CPU 100%
            std::this_thread::sleep_for(std::chrono::milliseconds(10));
        }
    }

    // 4. Detach Thread
    javaVM->DetachCurrentThread();
    LOGD("Processor thread detached");
}