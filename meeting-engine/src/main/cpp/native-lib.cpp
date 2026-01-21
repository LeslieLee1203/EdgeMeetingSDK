// Write C++ code here.
//
// Do not forget to dynamically load the C++ library into your application.
//
// For instance,
//
// In MainActivity.java:
//    static {
//       System.loadLibrary("main");
//    }
//
// Or, in MainActivity.kt:
//    companion object {
//      init {
//         System.loadLibrary("main")
//      }
//    }


#include <jni.h>
#include <string>
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

extern "C" {

// 1. 自動呼叫：當 Library 載入時，保存 JVM 指標
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

/**
 * 初始化引擎（支援 ASR 配置）
 *
 * @param env JNI 環境
 * @param thiz JniEngineBridge 實例
 * @param modelsPath 模型路徑（可為空字串表示純錄音模式）
 * @param language 語言代碼（如 "auto", "zh", "en"）
 * @return 0 成功，非 0 錯誤碼
 *
 * 為什麼需要 modelsPath 與 language：
 * - modelsPath: Whisper RKNN 模型檔案夾路徑
 * - language: 語言設定（auto 自動偵測，或指定語言提升準確度）
 *
 * 錯誤碼：
 * - 0: 成功
 * - 1001: 模型檔案不存在
 * - 1002: 模型載入失敗
 */
JNIEXPORT jint JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeInit(
        JNIEnv* env,
        jobject thiz,
        jstring modelsPath,
        jstring language) {

    // 轉換 jstring 至 C++ string
    const char* modelsPathChars = env->GetStringUTFChars(modelsPath, nullptr);
    const char* languageChars = env->GetStringUTFChars(language, nullptr);

    std::string modelsPathStr(modelsPathChars);
    std::string languageStr(languageChars);

    env->ReleaseStringUTFChars(modelsPath, modelsPathChars);
    env->ReleaseStringUTFChars(language, languageChars);

    // 檢查是否為純錄音模式（modelsPath 為空）
    if (modelsPathStr.empty()) {
        LOGI("Init: Pure audio mode (no ASR)");
        // 純錄音模式：不建立 ASR 引擎
        return 0;  // Success
    }

    // ASR 模式：建立 WhisperAsrEngine
    LOGI("Init: ASR mode (modelsPath=%s, language=%s)", modelsPathStr.c_str(), languageStr.c_str());

    // 清理舊的 ASR 引擎（如果存在）
    if (gAsrEngine) {
        LOGI("Releasing old ASR engine");
        gAsrEngine->release();
        gAsrEngine.reset();
    }

    // 建立 WhisperAsrEngine
    gAsrEngine = std::make_unique<WhisperAsrEngine>();

    // 初始化 ASR 引擎
    bool initSuccess = gAsrEngine->init(modelsPathStr, languageStr);
    if (!initSuccess) {
        LOGE("ASR engine init failed");
        gAsrEngine.reset();

        // 返回錯誤碼（假設是模型載入失敗）
        // 實際錯誤碼應由 WhisperAsrEngine 提供，這裡簡化為 1002
        return 1002;  // ERR_MODEL_LOAD_FAILED
    }

    LOGI("ASR engine initialized successfully");
    return 0;  // Success
}

// 2. 新增：設定 Callback
JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeSetCallback(
        JNIEnv* env, jobject thiz, jobject callback) {

    // T041: 清理舊的全域 callback
    if (gCallbackObj) {
        env->DeleteGlobalRef(gCallbackObj);
        gCallbackObj = nullptr;
    }

    // 如果之前有 Recorder/Processor，先清掉
    if (gProcessor) gProcessor->stop();
    gProcessor.reset();
    gRecorder.reset(); // Recorder 也重建比較保險

    // T041: 儲存 callback 物件為全域引用
    gCallbackObj = env->NewGlobalRef(thiz);

    // 建立新物件
    gRecorder = std::make_unique<AudioRecorder>();

    // 這裡傳入的是 `thiz` (JniEngineBridge 實例)，因為我們要呼叫它的 onNativeAudioData
    gProcessor = std::make_unique<AudioProcessor>(gRecorder.get(), gJavaVM, thiz);

    // T039: 設定 ASR 引擎給 AudioProcessor
    if (gAsrEngine) {
        gProcessor->setAsrEngine(gAsrEngine.get());

        // T041: 設定 ASR 引擎的 transcript callback
        auto* whisperEngine = dynamic_cast<WhisperAsrEngine*>(gAsrEngine.get());
        if (whisperEngine) {
            whisperEngine->setTranscriptCallback([](
                const std::string& segmentId,
                const std::string& text,
                const std::string& speakerId,
                bool isFinal,
                long startMs,
                long endMs
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

                // 找到 onNativeTranscript 方法（7 個參數：id, text, speakerId, isFinal, startMs, endMs, languageCode）
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
                    // languageCode 暫時傳 null（Phase 4 實作語言偵測）
                    jstring jLanguageCode = nullptr;

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
                    // jLanguageCode 是 null，不需要 DeleteLocalRef
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
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStart(JNIEnv* env, jobject) {
    if (gRecorder == nullptr) {
        gRecorder = std::make_unique<AudioRecorder>();
    }

    // MVP: 這裡傳入 0, 0 使用預設麥克風
    // 未來：可以透過 JNI 參數傳入 deviceId
    if (gRecorder) gRecorder->start(0, 0); // 先開始錄音
    if (gProcessor) gProcessor->start();   // 再開始處理 (撈資料 -> 丟回 Kotlin)

    // Phase 3: 啟動 ASR 引擎（如果已初始化）
    if (gAsrEngine) {
        gAsrEngine->start();
        LOGI("ASR engine started");
    }

    LOGI("Native Start (Recorder + Processor + ASR)");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStop(JNIEnv* env, jobject) {
    // Phase 3: 停止 ASR 引擎（flush 剩餘音訊）
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
    // Phase 3: 釋放 ASR 引擎
    if (gAsrEngine) {
        gAsrEngine->release();
        gAsrEngine.reset();
        LOGI("ASR engine released");
    }

    if (gProcessor) { gProcessor->stop(); gProcessor.reset(); }
    if (gRecorder) { gRecorder->stop(); gRecorder.reset(); }

    LOGI("Native Release");
}

} // extern "C"