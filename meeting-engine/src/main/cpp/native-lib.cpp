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
#include "AudioRecorder.h" // 引入新標頭檔

// 全域指標 (MVP 階段暫時做法)
static std::unique_ptr<AudioRecorder> gRecorder = nullptr;

extern "C" {

JNIEXPORT jint JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeInit(
        JNIEnv* env,
        jobject,
        jstring modelPath) {

    // MVP: 這裡還不載入模型，只確認路徑
    // 這裡也不需要初始化 Recorder，因為 Oboe 建議在 Start 時再 open stream
    return 0; // Success
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStart(JNIEnv* env, jobject) {
    if (gRecorder == nullptr) {
        gRecorder = std::make_unique<AudioRecorder>();
    }

    // MVP: 這裡傳入 0, 0 使用預設麥克風
    // 未來：可以透過 JNI 參數傳入 deviceId
    bool success = gRecorder->start(0, 0);

    if (success) {
        __android_log_print(ANDROID_LOG_INFO, "JNI", "Native Start Success");
    } else {
        __android_log_print(ANDROID_LOG_ERROR, "JNI", "Native Start Failed");
    }
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStop(JNIEnv* env, jobject) {
    if (gRecorder) {
        gRecorder->stop();
        // 這裡可以選擇 reset 也可以保留物件，視需求而定
        // gRecorder.reset();
    }
    __android_log_print(ANDROID_LOG_INFO, "JNI", "Native Stop Success");
}

JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeRelease(JNIEnv* env, jobject) {
    if (gRecorder) {
        gRecorder->stop();
        gRecorder.reset();
    }
}

} // extern "C"