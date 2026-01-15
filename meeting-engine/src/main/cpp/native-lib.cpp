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

// 全域指標 (MVP 階段暫時做法)
static std::unique_ptr<AudioRecorder> gRecorder = nullptr;
static std::unique_ptr<AudioProcessor> gProcessor = nullptr;
static JavaVM* gJavaVM = nullptr; // 儲存 JVM 指標

extern "C" {

// 1. 自動呼叫：當 Library 載入時，保存 JVM 指標
JNIEXPORT jint JNICALL JNI_OnLoad(JavaVM* vm, void* reserved) {
    gJavaVM = vm;
    return JNI_VERSION_1_6;
}

JNIEXPORT jint JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeInit(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    // MVP: 這裡還不載入模型，只確認路徑
    // 這裡也不需要初始化 Recorder，因為 Oboe 建議在 Start 時再 open stream
    return 0; // Success
}

// 2. 新增：設定 Callback
JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeSetCallback(
        JNIEnv* env, jobject thiz, jobject callback) {

    // 如果之前有 Recorder/Processor，先清掉
    if (gProcessor) gProcessor->stop();
    gProcessor.reset();
    gRecorder.reset(); // Recorder 也重建比較保險

    // 建立新物件
    gRecorder = std::make_unique<AudioRecorder>();

    // 這裡傳入的是 `thiz` (JniEngineBridge 實例)，因為我們要呼叫它的 onNativeAudioData
    gProcessor = std::make_unique<AudioProcessor>(gRecorder.get(), gJavaVM, thiz);
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

    __android_log_print(ANDROID_LOG_INFO, "JNI", "Native Start (Recorder + Processor)");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStop(JNIEnv* env, jobject) {
    if (gProcessor) gProcessor->stop();
    if (gRecorder) gRecorder->stop();
    __android_log_print(ANDROID_LOG_INFO, "JNI", "Native Stop");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeRelease(JNIEnv* env, jobject) {
    if (gProcessor) { gProcessor->stop(); gProcessor.reset(); }
    if (gRecorder) { gRecorder->stop(); gRecorder.reset(); }
}

} // extern "C"