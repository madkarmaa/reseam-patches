// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include <jni.h>
#include <string>
// ReSharper resume CppUnusedIncludeDirective

namespace {
jclass callbackClass;
jmethodID callbackMethod;

jboolean forwardBoolean(JNIEnv *const environment, jclass /*declaringClass*/) {
    return environment->CallStaticBooleanMethod(callbackClass, callbackMethod);
}
}

extern "C" JNIEXPORT void JNICALL
Java_top_madkarma_mtmanager_vip_NativeBridge_forwardBooleanMethod(
    JNIEnv *const environment, jclass /*declaringClass*/, const jclass owner, const jstring nativeMethod,
    const jstring replacement) {
    const char *const replacementName = environment->GetStringUTFChars(replacement, nullptr);
    if (replacementName == nullptr)
        return;

    const jmethodID method = environment->GetStaticMethodID(owner, replacementName, "()Z");
    environment->ReleaseStringUTFChars(replacement, replacementName);
    if (method == nullptr)
        return;

    auto *const ownerReference = static_cast<jclass>(environment->NewGlobalRef(owner));
    if (ownerReference == nullptr)
        return;

    const char *const nativeName = environment->GetStringUTFChars(nativeMethod, nullptr);
    if (nativeName == nullptr) {
        environment->DeleteGlobalRef(ownerReference);
        return;
    }

    callbackClass = ownerReference;
    callbackMethod = method;
    std::string registrationName(nativeName);
    environment->ReleaseStringUTFChars(nativeMethod, nativeName);
    char signature[] = "()Z";
    const JNINativeMethod bridge = {
        .name = registrationName.data(),
        .signature = signature,
        .fnPtr = reinterpret_cast<void *>(forwardBoolean),
    };
    environment->RegisterNatives(owner, &bridge, 1);
}
