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
#include <oboe/Oboe.h> // 確認能不能抓到 Oboe 標頭檔

extern "C" JNIEXPORT jstring JNICALL
Java_com_edgemeeting_engine_NativeBridge_getOboeVersion(
        JNIEnv* env,
        jobject /* this */) {

    // 簡單回傳 Oboe 版本，證明連結成功
    std::string version = "Oboe v";
    version += oboe::getVersionText();
    return env->NewStringUTF(version.c_str());
}