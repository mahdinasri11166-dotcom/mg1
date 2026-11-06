#include <jni.h>
#include <string>
#include <android/log.h>

#define LOG_TAG "GhostNative"

extern "C" {

JNIEXPORT jstring JNICALL
Java_com_ghostmago_app_crypto_NativeBridge_nativePepper(
        JNIEnv* env,
        jobject /* this */) {
    // Obfuscated-ish constant fragments (not strong against skilled reverse, but raises bar)
    std::string a = "Gh0st";
    std::string b = "Mago";
    std::string c = "NdK";
    std::string d = "2026";
    std::string pepper = a + "|" + b + "|" + c + "|" + d;
    return env->NewStringUTF(pepper.c_str());
}

JNIEXPORT jboolean JNICALL
Java_com_ghostmago_app_crypto_NativeBridge_nativeIntegrityHint(
        JNIEnv* env,
        jobject /* this */) {
    // Placeholder integrity signal for RASP layer
    return JNI_TRUE;
}

}
