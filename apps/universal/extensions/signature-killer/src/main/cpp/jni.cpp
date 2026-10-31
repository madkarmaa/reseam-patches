// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Each file includes what it uses, even when another header already provides it.
// ReSharper disable CppUnusedIncludeDirective
#include <jni.h>

#include <atomic>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

#include "bytehook.h"
#include "file_hooks.h"
#include "logging.h"
#include "seccomp_open.h"
// ReSharper restore CppUnusedIncludeDirective

#ifdef __aarch64__
#define PROCESS_ABI "arm64-v8a"
#elifdef __arm__
#define PROCESS_ABI "armeabi-v7a"
#elifdef __x86_64__
#define PROCESS_ABI "x86_64"
#elifdef __i386__
#define PROCESS_ABI "x86"
#else
#error Unsupported Android ABI
#endif

namespace {
    std::optional<std::string> read_string(JNIEnv *const env, const jstring value) {
        if (value == nullptr) return std::nullopt;

        const char *const characters = env->GetStringUTFChars(value, nullptr);
        if (characters == nullptr) return std::nullopt;

        std::string copy = characters;
        env->ReleaseStringUTFChars(value, characters);
        return copy;
    }

    std::optional<std::string> read_element(JNIEnv *const env, const jobjectArray values,
                                            const jsize index) {
        auto *const value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
        std::optional<std::string> copy = read_string(env, value);
        // Callers read whole arrays, which could otherwise exhaust the local reference table.
        env->DeleteLocalRef(value);
        return copy;
    }

    std::vector<raw_open_mapping> raw_path_mappings(const path_mappings &mappings) {
        // The installed table is never freed, so these strings outlive either native redirect.
        return mappings | std::views::transform([](const path_mapping &mapping) {
            return raw_open_mapping{
                .installed = mapping.installed.c_str(),
                .replacement = mapping.replacement.c_str(),
            };
        }) | std::ranges::to<std::vector>();
    }
} // namespace

extern "C" JNIEXPORT void JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_setNativeDiagnostics(
    JNIEnv * /*env*/, jclass /*declaring_class*/, const jboolean enabled) {
    diagnostics.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    LOG_INFO("Native library loaded for %s with bytehook %s", PROCESS_ABI, bytehook_get_version());
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_trapRawApkOpens(
    JNIEnv *const env, jclass /*declaring_class*/, const jstring library_name) {
    const path_mappings *const mappings = installed_path_mappings();
    if (mappings == nullptr) return JNI_FALSE;

    const std::optional<std::string> name = read_string(env, library_name);
    if (!name || name->empty() || name->contains('/')) return JNI_FALSE;

    const auto paths = raw_path_mappings(*mappings);
    return install_raw_open_redirect(name->c_str(), paths) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_prepareApkFilesOnLibraryLoad(
    JNIEnv *const env, jclass /*declaring_class*/, const jstring library_name) {
    const path_mappings *const mappings = installed_path_mappings();
    if (mappings == nullptr) return JNI_FALSE;

    const auto name = read_string(env, library_name);
    if (!name || name->empty() || name->contains('/')) return JNI_FALSE;

    const auto paths = raw_path_mappings(*mappings);
    return prepare_library_file_redirect(name->c_str(), paths) ? JNI_TRUE : JNI_FALSE;
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_hookApkPaths(
    JNIEnv *const env, jclass /*declaring_class*/, const jobjectArray installed,
    const jobjectArray replacements) {
    // Both arrays come from the extension's component list.
    if (installed == nullptr || replacements == nullptr) {
        LOG_ERROR("Rejected path mappings: an array is null");
        return JNI_FALSE;
    }

    // Require one replacement for each installed component.
    const jsize count = env->GetArrayLength(installed);
    if (const jsize replacement_count = env->GetArrayLength(replacements);
        count <= 0 || count != replacement_count) {
        LOG_ERROR("Rejected path mappings: %d installed paths and %d replacements", count,
                  replacement_count);
        return JNI_FALSE;
    }

    // Readers must never see a partly copied table. Null paths, failed copies and pending JNI
    // exceptions all stop the copy.
    auto table = std::make_unique<path_mappings>();
    for (jsize index = 0; index < count; index++) {
        std::optional<std::string> installed_path = read_element(env, installed, index);
        std::optional<std::string> replacement =
                installed_path ? read_element(env, replacements, index) : std::nullopt;
        if (!installed_path || !replacement) {
            LOG_ERROR("Rejected path mappings: pair %d is null or unreadable", index);
            return JNI_FALSE;
        }

        LOG_DEBUG("Path mapping %d: %s -> %s", index, installed_path->c_str(),
                  replacement->c_str());
        table->push_back({
            .installed = std::move(*installed_path),
            .replacement = std::move(*replacement),
        });
    }

    return install_file_redirect(std::move(table)) ? JNI_TRUE : JNI_FALSE;
}
