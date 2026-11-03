// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "account_limit.h"
#include "logging.h"

#include <jni.h>
#include <sys/mman.h>
#include <unistd.h>

#include <cerrno>
#include <chrono>
#include <cstring>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    const uint8_t *installed_instruction = nullptr;

    bool disable_account_limit(const account_limit &limit) {
        const long page_size = sysconf(_SC_PAGESIZE);
        if (page_size <= 0) return false;

        const auto address = reinterpret_cast<uintptr_t>(limit.instruction);
        const auto page_address = address & ~static_cast<uintptr_t>(page_size - 1);
        auto *page = reinterpret_cast<void *>(page_address);
        const auto writable_size = address - page_address + proton_account_limit_replacement_size;

        if (mprotect(page, writable_size, limit.protection | PROT_WRITE) != 0) {
            LOG_ERROR("Cannot write account-limit code: %s", strerror(errno));
            return false;
        }

        // The Rust-load hook runs before any login flow. x86 instructions need not be aligned.
        std::memcpy(limit.instruction, proton_account_limit_replacement, proton_account_limit_replacement_size);
        auto *code = reinterpret_cast<char *>(limit.instruction);

        __builtin___clear_cache(code, code + proton_account_limit_replacement_size);

        if (mprotect(page, writable_size, limit.protection) != 0) {
            LOG_ERROR("Cannot restore account-limit code protection: %s", strerror(errno));
            return false;
        }

        installed_instruction = limit.instruction;

        return true;
    }
}

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_protonmail_accounts_Accounts_installNative(JNIEnv *, jclass, const jboolean enabled) {
    diagnostics.store(enabled == JNI_TRUE, std::memory_order_relaxed);

    if (installed_instruction != nullptr)
        return std::memcmp(installed_instruction, proton_account_limit_replacement,
            proton_account_limit_replacement_size) == 0 ? JNI_TRUE : JNI_FALSE;

    const auto started = std::chrono::steady_clock::now();
    if (const auto limit = find_account_limit(); !limit || !disable_account_limit(*limit)) return JNI_FALSE;

    const auto elapsed = std::chrono::duration<double, std::milli>(std::chrono::steady_clock::now() - started);
    LOG_INFO("Enabled unlimited accounts; native discovery and installation took %.2f ms", elapsed.count());
    return JNI_TRUE;
}
