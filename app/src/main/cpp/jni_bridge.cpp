#include <jni.h>

#include <string>
#include <vector>

#include "engine.h"
#include "hook_registry.h"
#include "module_manager.h"

using moonlight::Engine;

namespace {

std::string fromJava(JNIEnv* env, jstring value) {
    if (value == nullptr) {
        return {};
    }
    const char* chars = env->GetStringUTFChars(value, nullptr);
    if (chars == nullptr) {
        return {};
    }
    std::string result(chars);
    env->ReleaseStringUTFChars(value, chars);
    return result;
}

jstring toJava(JNIEnv* env, const std::string& value) {
    return env->NewStringUTF(value.c_str());
}

}  // namespace

// Symbol names follow the JNI mangling of com.moonlight.bedrockclient.core.NativeEngine.
// Kotlin declares these as instance members of an object, so the second argument is
// a jobject rather than a jclass; the mangled name is identical either way.
extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeStart(JNIEnv*, jobject) {
    return Engine::instance().start() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT void JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeStop(JNIEnv*, jobject) {
    Engine::instance().stop();
}

JNIEXPORT jboolean JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeIsRunning(JNIEnv*, jobject) {
    return Engine::instance().running() ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jstring JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeVersion(JNIEnv* env, jobject) {
    return toJava(env, Engine::instance().version());
}

JNIEXPORT jstring JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeAbi(JNIEnv* env, jobject) {
    return toJava(env, Engine::instance().abi());
}

JNIEXPORT jboolean JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeRegisterModule(
        JNIEnv* env, jobject, jstring id) {
    return Engine::instance().modules().add(fromJava(env, id)) ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeRegisterModules(
        JNIEnv* env, jobject, jobjectArray ids) {
    if (ids == nullptr) {
        return 0;
    }
    jint added = 0;
    const jsize total = env->GetArrayLength(ids);
    for (jsize index = 0; index < total; ++index) {
        auto item = static_cast<jstring>(env->GetObjectArrayElement(ids, index));
        if (item == nullptr) {
            continue;
        }
        if (Engine::instance().modules().add(fromJava(env, item))) {
            ++added;
        }
        env->DeleteLocalRef(item);
    }
    return added;
}

JNIEXPORT jboolean JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeSetEnabled(
        JNIEnv* env, jobject, jstring id, jboolean enabled) {
    return Engine::instance().modules().setEnabled(fromJava(env, id), enabled == JNI_TRUE)
                   ? JNI_TRUE
                   : JNI_FALSE;
}

JNIEXPORT jint JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeEnabledCount(JNIEnv*, jobject) {
    return static_cast<jint>(Engine::instance().modules().enabledCount());
}

JNIEXPORT jint JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeModuleCount(JNIEnv*, jobject) {
    return static_cast<jint>(Engine::instance().modules().count());
}

JNIEXPORT jobjectArray JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeEnabledIds(JNIEnv* env, jobject) {
    const std::vector<std::string> ids = Engine::instance().modules().enabledIds();
    jclass stringClass = env->FindClass("java/lang/String");
    if (stringClass == nullptr) {
        return nullptr;
    }
    auto array = env->NewObjectArray(static_cast<jsize>(ids.size()), stringClass, nullptr);
    if (array == nullptr) {
        return nullptr;
    }
    for (jsize index = 0; index < static_cast<jsize>(ids.size()); ++index) {
        jstring item = toJava(env, ids[index]);
        if (item == nullptr) {
            continue;
        }
        env->SetObjectArrayElement(array, index, item);
        env->DeleteLocalRef(item);
    }
    return array;
}

/**
 * Layout: [0] ticks, [1] mean micros, [2] peak micros, [3] uptime ms,
 *         [4] overruns, [5] module count, [6] enabled count, [7] tick rate.
 */
JNIEXPORT jlongArray JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeStats(JNIEnv* env, jobject) {
    const moonlight::Engine::Stats stats = Engine::instance().stats();
    const std::vector<jlong> values = {
            static_cast<jlong>(stats.ticks),
            static_cast<jlong>(stats.meanMicros),
            static_cast<jlong>(stats.peakMicros),
            static_cast<jlong>(stats.uptimeMs),
            static_cast<jlong>(stats.overruns),
            static_cast<jlong>(stats.modules),
            static_cast<jlong>(stats.enabled),
            static_cast<jlong>(Engine::kTickHz),
    };
    jlongArray array = env->NewLongArray(static_cast<jsize>(values.size()));
    if (array == nullptr) {
        return nullptr;
    }
    env->SetLongArrayRegion(array, 0, static_cast<jsize>(values.size()), values.data());
    return array;
}

JNIEXPORT jint JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeHookState(JNIEnv*, jobject) {
    return static_cast<jint>(Engine::instance().hooks().state());
}

JNIEXPORT jstring JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeHookDescribe(JNIEnv* env, jobject) {
    return toJava(env, Engine::instance().hooks().describe());
}

JNIEXPORT void JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeHookHeartbeat(
        JNIEnv* env, jobject, jint pid, jstring version) {
    Engine::instance().hooks().attach(static_cast<int32_t>(pid), fromJava(env, version));
}

JNIEXPORT void JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeHookDetach(JNIEnv*, jobject) {
    Engine::instance().hooks().detach();
}

JNIEXPORT jstring JNICALL
Java_com_moonlight_bedrockclient_core_NativeEngine_nativeHookVersion(JNIEnv* env, jobject) {
    return toJava(env, Engine::instance().hooks().bridgeVersion());
}

}  // extern "C"
