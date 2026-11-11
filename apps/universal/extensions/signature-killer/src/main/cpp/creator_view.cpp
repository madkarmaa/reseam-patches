// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include <jni.h>
#include <mutex>

#include "logging.h"
#include "redirect/seccomp_open.h"
#include "shadowhook.h"
// ReSharper restore CppUnusedIncludeDirective

namespace {
    constinit std::mutex creator_mutex;
    jobject original_creator = nullptr;
    jclass package_info = nullptr;
    jfieldID creator_field = nullptr;
    void *creator_hook = nullptr;
    jobject (*original_get_static_field)(JNIEnv *, jclass, jfieldID) = nullptr;

    jobject get_static_field(JNIEnv *const env, const jclass owner, const jfieldID field) {
        const auto caller = reinterpret_cast<uintptr_t>(
                __builtin_extract_return_addr(__builtin_return_address(0)));
        if (field == creator_field && raw_redirect_contains_address(caller) &&
            env->IsSameObject(owner, package_info) == JNI_TRUE) {
            // GetStaticObjectField returns a local reference, which callers may delete.
            return env->NewLocalRef(original_creator);
        }
        return original_get_static_field(env, owner, field);
    }

    void release_creator(JNIEnv *const env) {
        if (original_creator != nullptr) env->DeleteGlobalRef(original_creator);
        if (package_info != nullptr) env->DeleteGlobalRef(package_info);
        original_creator = nullptr;
        package_info = nullptr;
        creator_field = nullptr;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_NativeSignatures_installCreatorView(
        JNIEnv *const env, jclass /*declaring_class*/, const jobject creator) {
    const std::scoped_lock lock(creator_mutex);
    if (creator_hook != nullptr) return JNI_TRUE;
    if (creator == nullptr) return JNI_FALSE;

    const jclass owner = env->FindClass("android/content/pm/PackageInfo");
    if (owner == nullptr) return JNI_FALSE;

    creator_field = env->GetStaticFieldID(owner, "CREATOR", "Landroid/os/Parcelable$Creator;");
    if (creator_field == nullptr) {
        env->DeleteLocalRef(owner);
        return JNI_FALSE;
    }

    original_creator = env->NewGlobalRef(creator);
    package_info = static_cast<jclass>(env->NewGlobalRef(owner));
    env->DeleteLocalRef(owner);
    if (original_creator == nullptr || package_info == nullptr) {
        release_creator(env);
        return JNI_FALSE;
    }

    creator_hook = shadowhook_hook_func_addr_2(
            reinterpret_cast<void *>(env->functions->GetStaticObjectField),
            reinterpret_cast<void *>(get_static_field),
            reinterpret_cast<void **>(&original_get_static_field),
            SHADOWHOOK_HOOK_WITH_UNIQUE_MODE);
    if (creator_hook == nullptr) {
        LOG_ERROR("Cannot preserve the native package creator: %d", shadowhook_get_errno());
        release_creator(env);
        return JNI_FALSE;
    }

    LOG_INFO("JNI field reads preserve PackageInfo.CREATOR for the protected native library");
    return JNI_TRUE;
}
