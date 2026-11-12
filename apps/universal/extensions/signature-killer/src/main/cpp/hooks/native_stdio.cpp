// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_hook_internal.h"
#include <array>
#include <atomic>
#include <cstdio>
#include <cstdlib>
#include <mutex>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    pthread_key_t stdio_caller_key{};

    namespace {
        constinit std::once_flag thread_context_once;
        std::atomic thread_context_ready{false};

    } // namespace

    void prepare_thread_context() {
        std::call_once(thread_context_once, [] {
            if (pthread_key_create(&stdio_caller_key, nullptr) != 0) abort();
            thread_context_ready.store(true, std::memory_order_release);
        });
    }

    namespace {
        class stdio_scope {
          public:
            explicit stdio_scope(const uintptr_t direct) : previous(pthread_getspecific(stdio_caller_key)) {
                // The outer stdio operation owns nested libc work, including protector wrappers.
                if (previous != nullptr) return;
                if (pthread_setspecific(stdio_caller_key, raw_redirect_contains_address(direct)
                                                              ? reinterpret_cast<void *>(direct)
                                                              : foreign_stdio) != 0)
                    abort();
            }

            ~stdio_scope() {
                if (pthread_setspecific(stdio_caller_key, previous) != 0) abort();
            }

            stdio_scope(const stdio_scope &) = delete;
            stdio_scope &operator=(const stdio_scope &) = delete;
            stdio_scope(stdio_scope &&) = delete;
            stdio_scope &operator=(stdio_scope &&) = delete;

          private:
            void *previous;
        };

        using fopen_function = FILE *(*)(const char *, const char *);
        fopen_function original_fopen = nullptr;
        fopen_function original_fopen64 = nullptr;
        size_t (*original_fread)(void *, size_t, size_t, FILE *) = nullptr;
        char *(*original_fgets)(char *, int, FILE *) = nullptr;
        int (*original_fgetc)(FILE *) = nullptr;
        int (*original_getc)(FILE *) = nullptr;
        ssize_t (*original_getline)(char **, size_t *, FILE *) = nullptr;
        ssize_t (*original_getdelim)(char **, size_t *, int, FILE *) = nullptr;
        int (*original_fseek)(FILE *, long, int) = nullptr;
        int (*original_fseeko)(FILE *, off_t, int) = nullptr;
        int (*original_fseeko64)(FILE *, off64_t, int) = nullptr;
        int (*original_fclose)(FILE *) = nullptr;

        template <fopen_function &Original>
        FILE *fopen_proxy(const char *const path, const char *const mode) {
            const stdio_scope owner(NATIVE_CALLER);
            return Original(path, mode);
        }

        size_t fread_proxy(void *const buffer, const size_t size, const size_t count, FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return original_fread(buffer, size, count, stream);
        }

        char *fgets_proxy(char *const buffer, const int count, FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return original_fgets(buffer, count, stream);
        }

        template <auto &Original> int fgetc_proxy(FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return Original(stream);
        }

        ssize_t getline_proxy(char **const line, size_t *const capacity, FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return original_getline(line, capacity, stream);
        }

        ssize_t getdelim_proxy(char **const line, size_t *const capacity, const int delimiter,
                               FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return original_getdelim(line, capacity, delimiter, stream);
        }

        template <typename Offset, auto &Original>
        int fseek_proxy(FILE *const stream, const Offset offset, const int whence) {
            const stdio_scope owner(NATIVE_CALLER);
            return Original(stream, offset, whence);
        }

        int fclose_proxy(FILE *const stream) {
            const stdio_scope owner(NATIVE_CALLER);
            return original_fclose(stream);
        }

    } // namespace

    std::span<const libc_hook> stdio_hooks() {
        static const auto hooks = std::to_array({
            hook("fopen", fopen_proxy<original_fopen>, original_fopen, false),
            hook("fopen64", fopen_proxy<original_fopen64>, original_fopen64, false),
            hook("fread", fread_proxy, original_fread, false),
            hook("fgets", fgets_proxy, original_fgets, false),
            hook("fgetc", fgetc_proxy<original_fgetc>, original_fgetc, false),
            hook("getc", fgetc_proxy<original_getc>, original_getc, false),
            hook("getline", getline_proxy, original_getline, false),
            hook("getdelim", getdelim_proxy, original_getdelim, false),
            hook("fseek", fseek_proxy<long, original_fseek>, original_fseek, false),
            hook("fseeko", fseek_proxy<off_t, original_fseeko>, original_fseeko, false),
            hook("fseeko64", fseek_proxy<off64_t, original_fseeko64>, original_fseeko64, false),
            hook("fclose", fclose_proxy, original_fclose, false),
        });
        return hooks;
    }
} // namespace signature::hooks

using signature::hooks::foreign_stdio;
using signature::hooks::internal_worker;
using signature::hooks::prepare_thread_context;
using signature::hooks::stdio_caller_key;
using signature::hooks::thread_context_ready;

void bypass_native_file_hooks_for_thread() {
    prepare_thread_context();
    if (pthread_setspecific(stdio_caller_key, internal_worker) != 0) abort();
}

bool native_file_hooks_bypassed() {
    if (!thread_context_ready.load(std::memory_order_acquire)) return false;
    const void *const context = pthread_getspecific(stdio_caller_key);
    return context == internal_worker || context == foreign_stdio;
}
