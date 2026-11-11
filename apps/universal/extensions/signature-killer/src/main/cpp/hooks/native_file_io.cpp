// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_hook_internal.h"
#include <dlfcn.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <sys/types.h>
#include <array>
#include <cstdarg>
#include <cstdlib>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    namespace {
        using open_function = int (*)(const char *, int, ...);
        using openat_function = int (*)(int, const char *, int, ...);
        open_function original_open = nullptr;
        open_function original_open64 = nullptr;
        openat_function original_openat = nullptr;
        openat_function original_openat64 = nullptr;

        bool needs_mode(const int flags) {
            const auto bits = static_cast<unsigned>(flags);
            return (bits & static_cast<unsigned>(O_CREAT)) != 0 ||
                   (bits & static_cast<unsigned>(O_TMPFILE)) == static_cast<unsigned>(O_TMPFILE);
        }

        int open_flags(const int flags) {
#ifdef __arm__
            return static_cast<int>(static_cast<unsigned>(flags) | static_cast<unsigned>(O_LARGEFILE));
#else
            return flags;
#endif
        }

        template <open_function &Original> int open_proxy(const char *const path, const int flags, ...) {
            mode_t mode = 0;
            if (needs_mode(flags)) {
                va_list arguments{};
                va_start(arguments, flags);
                mode = static_cast<mode_t>(va_arg(arguments, int));
                va_end(arguments);
            }
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, __NR_openat, AT_FDCWD, reinterpret_cast<long>(path),
                                         open_flags(flags), mode);
            return libc_result<int>(result, previous_errno);
        }

        template <openat_function &Original>
        int openat_proxy(const int directory, const char *const path, const int flags, ...) {
            mode_t mode = 0;
            if (needs_mode(flags)) {
                va_list arguments{};
                va_start(arguments, flags);
                mode = static_cast<mode_t>(va_arg(arguments, int));
                va_end(arguments);
            }
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, __NR_openat, directory, reinterpret_cast<long>(path),
                                         open_flags(flags), mode);
            return libc_result<int>(result, previous_errno);
        }

        int (*original_open_2)(const char *, int) = nullptr;
        int (*original_openat_2)(int, const char *, int) = nullptr;

        int open_2_proxy(const char *const path, const int flags) {
            if (needs_mode(flags)) abort();
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, __NR_openat, AT_FDCWD, reinterpret_cast<long>(path),
                                         open_flags(flags));
            return libc_result<int>(result, previous_errno);
        }

        int openat_2_proxy(const int directory, const char *const path, const int flags) {
            if (needs_mode(flags)) abort();
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, __NR_openat, directory, reinterpret_cast<long>(path),
                                         open_flags(flags));
            return libc_result<int>(result, previous_errno);
        }

        ssize_t (*original_read)(int, void *, size_t) = nullptr;
        int (*original_close)(int) = nullptr;
        int (*close_with_tag)(int, uint64_t) = nullptr;
        void (*exchange_owner_tag)(int, uint64_t, uint64_t) = nullptr;

        // The libc ABI requires a mutable output buffer, which the raw syscall fills.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // NOLINTNEXTLINE(misc-const-correctness)
        ssize_t read_proxy(const int descriptor, void *const buffer, const size_t count) {
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, __NR_read, descriptor, reinterpret_cast<long>(buffer),
                                         static_cast<long>(count));
            return libc_result<ssize_t>(result, previous_errno);
        }

        int close_proxy(const int descriptor) {
            const int previous_errno = errno;
            const auto caller = effective_caller(NATIVE_CALLER);

            // Keep the descriptor ownership check for target descriptors, whose close also releases any
            // proc snapshot tracked by the redirect. These exports are not our hook trampolines.
            if (exchange_owner_tag != nullptr && raw_redirect_contains_address(caller))
                exchange_owner_tag(descriptor, 0, 0);

            const auto redirected =
                redirect_native_file_syscall(caller, __NR_close, descriptor, 0, 0, 0, 0, 0);
            int closed = 0;
            if (redirected) closed = libc_result<int>(*redirected, previous_errno);
            else if (close_with_tag != nullptr) closed = close_with_tag(descriptor, 0);
            else
                closed = libc_result<int>(signature_raw_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0),
                                          previous_errno);
            return closed < 0 && errno == EINTR ? 0 : closed;
        }

        using pread_function = ssize_t (*)(int, void *, size_t, off_t);
        using pread64_function = ssize_t (*)(int, void *, size_t, off64_t);
        pread_function original_pread = nullptr;
        pread64_function original_pread64 = nullptr;

        template <typename Offset, auto &Original>
        ssize_t pread_proxy(const int descriptor, void *const buffer, const size_t count,
                            const Offset offset) {
            const int previous_errno = errno;
#ifdef __arm__
            const auto position = static_cast<uint64_t>(static_cast<int64_t>(offset));
            const auto result =
                redirect(NATIVE_CALLER, __NR_pread64, descriptor, reinterpret_cast<long>(buffer),
                         static_cast<long>(count), 0, static_cast<long>(static_cast<uint32_t>(position)),
                         static_cast<long>(static_cast<uint32_t>(position >> 32U)));
#else
            const auto result =
                redirect(NATIVE_CALLER, __NR_pread64, descriptor, reinterpret_cast<long>(buffer),
                         static_cast<long>(count), static_cast<long>(offset));
#endif
            return libc_result<ssize_t>(result, previous_errno);
        }

        off_t (*original_lseek)(int, off_t, int) = nullptr;
        off64_t (*original_lseek64)(int, off64_t, int) = nullptr;

        off_t lseek_proxy(const int descriptor, const off_t offset, const int whence) {
            const int previous_errno = errno;
            const auto result =
                redirect(NATIVE_CALLER, __NR_lseek, descriptor, static_cast<long>(offset), whence);
            return libc_result<off_t>(result, previous_errno);
        }

        off64_t lseek64_proxy(const int descriptor, const off64_t offset, const int whence) {
            const int previous_errno = errno;
#ifdef __arm__
            const auto position = static_cast<uint64_t>(offset);
            off64_t adjusted = 0;
            const auto result = redirect(NATIVE_CALLER, __NR__llseek, descriptor,
                                         static_cast<long>(static_cast<uint32_t>(position >> 32U)),
                                         static_cast<long>(static_cast<uint32_t>(position)),
                                         reinterpret_cast<long>(&adjusted), whence);
            return libc_result<int>(result, previous_errno) == 0 ? adjusted : static_cast<off64_t>(-1);
#else
            const auto result =
                redirect(NATIVE_CALLER, __NR_lseek, descriptor, static_cast<long>(offset), whence);
            return libc_result<off64_t>(result, previous_errno);
#endif
        }

    } // namespace

    std::span<const libc_hook> file_io_hooks() {
        static const auto hooks = std::to_array({
            hook("open", open_proxy<original_open>, original_open),
            hook("open64", open_proxy<original_open64>, original_open64),
            hook("openat", openat_proxy<original_openat>, original_openat),
            hook("openat64", openat_proxy<original_openat64>, original_openat64),
            hook("__open_2", open_2_proxy, original_open_2),
            hook("__openat_2", openat_2_proxy, original_openat_2),
            hook("read", read_proxy, original_read),
            hook("pread", pread_proxy<off_t, original_pread>, original_pread),
            hook("pread64", pread_proxy<off64_t, original_pread64>, original_pread64),
            hook("close", close_proxy, original_close),
            hook("lseek", lseek_proxy, original_lseek),
            hook("lseek64", lseek64_proxy, original_lseek64),
        });
        return hooks;
    }

    void prepare_file_io_hooks(void *const libc) {
        // FDSAN is absent on older Android releases. Resolve it before hooking close.
        close_with_tag =
            reinterpret_cast<decltype(close_with_tag)>(dlsym(libc, "android_fdsan_close_with_tag"));
        exchange_owner_tag =
            reinterpret_cast<decltype(exchange_owner_tag)>(dlsym(libc, "android_fdsan_exchange_owner_tag"));
    }
} // namespace signature::hooks
