// meeting-engine/src/main/cpp/native-lib.cpp

#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "AudioRecorder.h"
#include "AudioProcessor.h"
#include "asr/AsrEngine.h"
#include "asr/WhisperAsrEngine.h"

// 全域指標 (MVP 階段暫時做法)
static std::unique_ptr<AudioRecorder> gRecorder = nullptr;
static std::unique_ptr<AudioProcessor> gProcessor = nullptr;
static std::unique_ptr<AsrEngine> gAsrEngine = nullptr;  // Phase 3: ASR 引擎
static JavaVM* gJavaVM = nullptr; // 儲存 JVM 指標
static jobject gCallbackObj = nullptr;  // T041: 全域 callback 物件（用於 ASR 回調）

#define LOG_TAG "native-lib"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

// 釋放順序（測試可驗證）
std::vector<std::string> buildReleaseOrderLabels(
    bool hasProcessor,
    bool hasRecorder,
    bool hasAsr,
    bool hasCallback
) {
    std::vector<std::string> order;
    if (hasProcessor) order.emplace_back("stop_processor");
    if (hasRecorder) order.emplace_back("stop_recorder");
    if (hasAsr) order.emplace_back("release_asr");
    if (hasCallback) order.emplace_back("delete_callback");
    return order;
}

// Helper function to setup ASR callbacks
// This logic is needed in both nativeInit (if callback exists) and nativeSetCallback (if engine exists)
void setupAsrCallbacks() {
    if (!gAsrEngine || !gCallbackObj || !gJavaVM) return;

    auto* whisperEngine = dynamic_cast<WhisperAsrEngine*>(gAsrEngine.get());
    if (whisperEngine) {
        whisperEngine->setTranscriptCallback([](
            const std::string& segmentId,
            const std::string& text,
            const std::string& speakerId,
            bool isFinal,
            long startMs,
            long endMs,
            const std::string& languageCode  // Phase 4: 新增語言代碼參數
        ) {
            // 在背景執行緒呼叫 JNI
            if (!gJavaVM || !gCallbackObj) {
                LOGE("JNI callback: VM or callback object is null");
                return;
            }

            JNIEnv* env;
            bool needDetach = false;

            // 嘗試取得當前執行緒的 JNIEnv
            int getEnvResult = gJavaVM->GetEnv((void**)&env, JNI_VERSION_1_6);
            if (getEnvResult == JNI_EDETACHED) {
                // 執行緒未附加，需要 attach
                if (gJavaVM->AttachCurrentThread(&env, nullptr) != JNI_OK) {
                    LOGE("JNI callback: Failed to attach thread");
                    return;
                }
                needDetach = true;
            } else if (getEnvResult != JNI_OK) {
                LOGE("JNI callback: Failed to get JNIEnv");
                return;
            }

            // 找到 onNativeTranscript 方法
            jclass bridgeClass = env->GetObjectClass(gCallbackObj);
            jmethodID methodId = env->GetMethodID(
                bridgeClass,
                "onNativeTranscript",
                "(Ljava/lang/String;Ljava/lang/String;Ljava/lang/String;ZJJLjava/lang/String;)V"
            );

            if (methodId) {
                jstring jSegmentId = env->NewStringUTF(segmentId.c_str());
                jstring jText = env->NewStringUTF(text.c_str());
                jstring jSpeakerId = env->NewStringUTF(speakerId.c_str());
                // Phase 4: 傳遞語言代碼（若為空則傳 null）
                jstring jLanguageCode = languageCode.empty() ? nullptr : env->NewStringUTF(languageCode.c_str());

                env->CallVoidMethod(
                    gCallbackObj,
                    methodId,
                    jSegmentId,
                    jText,
                    jSpeakerId,
                    static_cast<jboolean>(isFinal),
                    static_cast<jlong>(startMs),
                    static_cast<jlong>(endMs),
                    jLanguageCode
                );

                env->DeleteLocalRef(jSegmentId);
                env->DeleteLocalRef(jText);
                env->DeleteLocalRef(jSpeakerId);
                if (jLanguageCode) env->DeleteLocalRef(jLanguageCode);
            } else {
                LOGE("JNI callback: Method onNativeTranscript not found");
            }

            env->DeleteLocalRef(bridgeClass);

            if (needDetach) {
                gJavaVM->DetachCurrentThread();
            }
        });
        LOGI("ASR transcript callback set");
    }
}

extern "C" {

JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeInit(
        JNIEnv* env,
        jobject thiz,
        jstring modelsPath,
        jstring language) {

    const char* modelsPathChars = env->GetStringUTFChars(modelsPath, nullptr);
    const char* languageChars = env->GetStringUTFChars(language, nullptr);

    std::string modelsPathStr(modelsPathChars);
    std::string languageStr(languageChars);

    env->ReleaseStringUTFChars(modelsPath, modelsPathChars);
    env->ReleaseStringUTFChars(language, languageChars);

    if (modelsPathStr.empty()) {
        LOGI("Init: Pure audio mode (no ASR)");
        return 0;
    }

    LOGI("Init: ASR mode (modelsPath=%s, language=%s)", modelsPathStr.c_str(), languageStr.c_str());

    if (gAsrEngine) {
        LOGI("Releasing old ASR engine");
        gAsrEngine->release();
        gAsrEngine.reset();
    }

    gAsrEngine = std::make_unique<WhisperAsrEngine>();

    bool initSuccess = gAsrEngine->init(modelsPathStr, languageStr);
    if (!initSuccess) {
        LOGE("ASR engine init failed");
        gAsrEngine.reset();
        return 1002;
    }

    // Critical Fix: Update gProcessor with new engine if it exists
    if (gProcessor) {
        gProcessor->setAsrEngine(gAsrEngine.get());
        LOGI("Updated gProcessor with new ASR engine");
    }

    // Critical Fix: Re-attach callback to new engine if callback object exists
    if (gCallbackObj) {
        setupAsrCallbacks();
    }

    LOGI("ASR engine initialized successfully");
    return 0;
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeSetCallback(
        JNIEnv* env, jobject thiz, jobject callback) {

    if (gCallbackObj) {
        env->DeleteGlobalRef(gCallbackObj);
        gCallbackObj = nullptr;
    }

    if (gProcessor) gProcessor->stop();
    gProcessor.reset();
    gRecorder.reset();

    gCallbackObj = env->NewGlobalRef(thiz);

    gRecorder = std::make_unique<AudioRecorder>();
    gProcessor = std::make_unique<AudioProcessor>(gRecorder.get(), gJavaVM, thiz);

    if (gAsrEngine) {
        gProcessor->setAsrEngine(gAsrEngine.get());
        setupAsrCallbacks(); // Use helper
    }
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStart(JNIEnv* env, jobject) {
    if (gRecorder == nullptr) {
        gRecorder = std::make_unique<AudioRecorder>();
    }

    if (gRecorder) gRecorder->start(0, 0);
    if (gProcessor) gProcessor->start();

    if (gAsrEngine) {
        gAsrEngine->start();
        LOGI("ASR engine started");
    }

    LOGI("Native Start (Recorder + Processor + ASR)");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStop(JNIEnv* env, jobject) {
    if (gAsrEngine) {
        gAsrEngine->stop();
        LOGI("ASR engine stopped");
    }

    if (gProcessor) gProcessor->stop();
    if (gRecorder) gRecorder->stop();

    LOGI("Native Stop");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeRelease(JNIEnv* env, jobject) {
    const auto order = buildReleaseOrderLabels(
        gProcessor != nullptr,
        gRecorder != nullptr,
        gAsrEngine != nullptr,
        gCallbackObj != nullptr
    );

    for (const auto& step : order) {
        if (step == "stop_processor") {
            if (gProcessor) {
                gProcessor->stop();
                gProcessor->setAsrEngine(nullptr);
                gProcessor.reset();
            }
        } else if (step == "stop_recorder") {
            if (gRecorder) {
                gRecorder->stop();
                gRecorder.reset();
            }
        } else if (step == "release_asr") {
            if (gAsrEngine) {
                gAsrEngine->release();
                gAsrEngine.reset();
                LOGI("ASR engine released");
            }
        } else if (step == "delete_callback") {
            if (gCallbackObj) {
                if (env) {
                    env->DeleteGlobalRef(gCallbackObj);
                } else {
                    LOGE("Native Release: env is null, cannot delete callback");
                }
                gCallbackObj = nullptr;
            }
        }
    }

    LOGI("Native Release");
}

} // extern "C"