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

// 定義 LOG TAG
#define LOG_TAG "EdgeMeetingJNI"
#define LOGD(...) __android_log_print(ANDROID_LOG_DEBUG, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

// 1. 對應 nativeInit
JNIEXPORT jint JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeInit(
        JNIEnv* env,
        jobject /* this */,
        jstring modelPath) {

    // 把 jstring 轉成 C++ string
    const char* pathChars = env->GetStringUTFChars(modelPath, nullptr);

    LOGD("JNI: nativeInit called with path: %s", pathChars);

    // 用完記得釋放，不然會 Memory Leak
    env->ReleaseStringUTFChars(modelPath, pathChars);

    // 回傳 0 代表成功 (模擬)
    return 0;
}

// 2. 對應 nativeStart
JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStart(JNIEnv* env, jobject) {
    LOGD("JNI: nativeStart called - Recording started (Mock)");
}

// 3. 對應 nativeStop
JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeStop(JNIEnv* env, jobject) {
    LOGD("JNI: nativeStop called - Recording stopped (Mock)");
}

// 4. 對應 nativeRelease
JNIEXPORT void JNICALL
Java_com_edgemeeting_engine_bridge_JniEngineBridge_nativeRelease(JNIEnv* env, jobject) {
    LOGD("JNI: nativeRelease called - Resources released (Mock)");
}

} // extern "C"