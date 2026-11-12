// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_hook_internal.h"
#include <fcntl.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <array>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    namespace {
#ifdef __arm__
        constexpr int stat_number = __NR_fstat64;
        constexpr int stat_at_number = __NR_fstatat64;
#else
        constexpr int stat_number = __NR_fstat;
        constexpr int stat_at_number = __NR_newfstatat;
#endif

        template <typename Metadata> using fstat_function = int (*)(int, Metadata *);
        template <typename Metadata> using fstatat_function = int (*)(int, const char *, Metadata *, int);
        template <typename Metadata> using stat_function = int (*)(const char *, Metadata *);
        fstat_function<struct stat> original_fstat = nullptr;
        fstat_function<struct stat64> original_fstat64 = nullptr;
        fstatat_function<struct stat> original_fstatat = nullptr;
        fstatat_function<struct stat64> original_fstatat64 = nullptr;
        stat_function<struct stat> original_stat = nullptr;
        stat_function<struct stat64> original_stat64 = nullptr;
        stat_function<struct stat> original_lstat = nullptr;
        stat_function<struct stat64> original_lstat64 = nullptr;

        template <typename Metadata>
        int fstat_proxy(const int descriptor, Metadata *const value) {
            const int previous_errno = errno;
            const auto result =
                redirect(NATIVE_CALLER, stat_number, descriptor, reinterpret_cast<long>(value));
            return libc_result<int>(result, previous_errno);
        }

        template <typename Metadata>
        int fstatat_proxy(const int directory, const char *const path, Metadata *const value,
                          const int flags) {
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, stat_at_number, directory,
                                         reinterpret_cast<long>(path), reinterpret_cast<long>(value), flags);
            return libc_result<int>(result, previous_errno);
        }

        template <typename Metadata, int Flags>
        int stat_proxy(const char *const path, Metadata *const value) {
            const int previous_errno = errno;
            const auto result = redirect(NATIVE_CALLER, stat_at_number, AT_FDCWD,
                                         reinterpret_cast<long>(path), reinterpret_cast<long>(value), Flags);
            return libc_result<int>(result, previous_errno);
        }

        ssize_t (*original_readlink)(const char *, char *, size_t) = nullptr;
        ssize_t (*original_readlinkat)(int, const char *, char *, size_t) = nullptr;

        // The kernel writes through this address, despite its integer syscall representation.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // NOLINTNEXTLINE(misc-const-correctness)
        ssize_t readlink_proxy(const char *const path, char *const buffer, const size_t size) {
            const int previous_errno = errno;
            const auto result =
                redirect(NATIVE_CALLER, __NR_readlinkat, AT_FDCWD, reinterpret_cast<long>(path),
                         reinterpret_cast<long>(buffer), static_cast<long>(size));
            return libc_result<ssize_t>(result, previous_errno);
        }

        // The kernel writes through this address, despite its integer syscall representation.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // NOLINTNEXTLINE(misc-const-correctness)
        ssize_t readlinkat_proxy(const int directory, const char *const path, char *const buffer,
                                 const size_t size) {
            const int previous_errno = errno;
            const auto result =
                redirect(NATIVE_CALLER, __NR_readlinkat, directory, reinterpret_cast<long>(path),
                         reinterpret_cast<long>(buffer), static_cast<long>(size));
            return libc_result<ssize_t>(result, previous_errno);
        }

    } // namespace

    std::span<const libc_hook> file_metadata_hooks() {
        static const auto hooks = std::to_array({
            hook("fstat", fstat_proxy<struct stat>, original_fstat),
            hook("fstat64", fstat_proxy<struct stat64>, original_fstat64),
            hook("fstatat", fstatat_proxy<struct stat>, original_fstatat),
            hook("fstatat64", fstatat_proxy<struct stat64>, original_fstatat64),
            hook("stat", stat_proxy<struct stat, 0>, original_stat),
            hook("stat64", stat_proxy<struct stat64, 0>, original_stat64),
            hook("lstat", stat_proxy<struct stat, AT_SYMLINK_NOFOLLOW>, original_lstat),
            hook("lstat64", stat_proxy<struct stat64, AT_SYMLINK_NOFOLLOW>, original_lstat64),
            hook("readlink", readlink_proxy, original_readlink),
            hook("readlinkat", readlinkat_proxy, original_readlinkat),
        });
        return hooks;
    }
} // namespace signature::hooks
