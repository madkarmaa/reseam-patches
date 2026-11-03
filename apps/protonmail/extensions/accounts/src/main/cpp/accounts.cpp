// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "account_limit.h"

#include <android/log.h>
#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstring>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    constexpr auto tag = "ProtonAccounts";
    uint32_t *installed_instruction = nullptr;

    bool disable_account_limit(const account_limit &limit) {
        const long page_size = sysconf(_SC_PAGESIZE);
        if (page_size <= 0) return false;

        auto *page = reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(limit.instruction) &
            ~static_cast<uintptr_t>(page_size - 1));

        if (mprotect(page, page_size, limit.protection | PROT_WRITE) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, tag, "Cannot write account-limit code: %s", strerror(errno));
            return false;
        }

        // Disable only the branch to FreeAccountLimitExceeded before any login flow starts.
        __atomic_store_n(limit.instruction, proton_account_limit_nop, __ATOMIC_RELEASE);
        auto *code = reinterpret_cast<char *>(limit.instruction);

        __builtin___clear_cache(code, code + sizeof(uint32_t));

        if (mprotect(page, page_size, limit.protection) != 0) {
            __android_log_print(ANDROID_LOG_ERROR, tag, "Cannot restore account-limit code protection: %s", strerror(errno));
            return false;
        }

        installed_instruction = limit.instruction;

        return true;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_protonmail_accounts_Accounts_installNative(JNIEnv *, jclass) {
    if (installed_instruction != nullptr)
        return __atomic_load_n(installed_instruction, __ATOMIC_ACQUIRE) == proton_account_limit_nop ? JNI_TRUE : JNI_FALSE;

    const auto started = std::chrono::steady_clock::now();
    if (const auto limit = find_account_limit(); !limit || !disable_account_limit(*limit)) return JNI_FALSE;

    const auto elapsed = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started);
    __android_log_print(ANDROID_LOG_INFO, tag, "Enabled multiple free accounts; native discovery and installation took %.2f ms", elapsed.count());
    return JNI_TRUE;
}
