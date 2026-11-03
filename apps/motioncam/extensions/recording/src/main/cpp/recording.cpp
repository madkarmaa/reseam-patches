// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "recording_limit.h"
#include "logging.h"

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstring>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    uint32_t *installed_instruction = nullptr;

    bool disable_recording_limit(const recording_limit &limit) {
        const long page_size = sysconf(_SC_PAGESIZE);
        if (page_size <= 0) return false;

        const auto address = reinterpret_cast<uintptr_t>(limit.instruction);
        const auto page_address = address & ~static_cast<uintptr_t>(page_size - 1);
        auto *page = reinterpret_cast<void *>(page_address);
        const auto writable_size = address - page_address + sizeof(uint32_t);
        
        if (mprotect(page, writable_size, limit.protection | PROT_WRITE) != 0) {
            LOG_ERROR("Cannot write recording-limit code: %s", strerror(errno));
            return false;
        }

        // Installed immediately after the camera library loads, before any camera session starts.
        __atomic_store_n(limit.instruction, motioncam_limit_replacement, __ATOMIC_RELEASE);
        auto *code = reinterpret_cast<char *>(limit.instruction);
        __builtin___clear_cache(code, code + sizeof(uint32_t));
        
        if (mprotect(page, writable_size, limit.protection) != 0) {
            LOG_ERROR("Cannot restore recording-limit code protection: %s", strerror(errno));
            return false;
        }

        installed_instruction = limit.instruction;
        LOG_INFO("Removed recording cutoff at %p", static_cast<void *>(limit.instruction));
        return true;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_motioncam_recording_Recording_installNative(JNIEnv *, jclass, const jboolean enabled) {
    diagnostics.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    if (installed_instruction != nullptr)
        return __atomic_load_n(installed_instruction, __ATOMIC_ACQUIRE) == motioncam_limit_replacement ? JNI_TRUE : JNI_FALSE;

    const auto started = std::chrono::steady_clock::now();
    if (const auto limit = find_recording_limit(); !limit || !disable_recording_limit(*limit)) return JNI_FALSE;

    const auto elapsed = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started);
    LOG_INFO("Enabled unlimited recording; native discovery and installation took %.2f ms", elapsed.count());
    return JNI_TRUE;
}
