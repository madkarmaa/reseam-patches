// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Each file includes what it uses, even when another header already provides it.
// ReSharper disable CppUnusedIncludeDirective
#include "file_hooks.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <sys/syscall.h>
#include <sys/types.h>

#include <algorithm>
#include <array>
#include <atomic>
#include <cstdarg>
#include <cstddef>
#include <cstdio>
#include <cstring>
#include <memory>
#include <string_view>

#include "bytehook.h"
#include "logging.h"
// ReSharper restore CppUnusedIncludeDirective

namespace {
    // Readers use the table without locking. It is installed once, never changes and is never
    // freed, since file reads can run until the process exits.
    constinit std::atomic<const path_mappings *> mappings{nullptr};

    constinit std::atomic<unsigned> file_redirections{0};
    constinit std::atomic<unsigned> unmatched_apk_opens{0};

    // Admits only the first events of a frequent kind, so hot paths cannot flood logcat.
    bool admit_event(std::atomic<unsigned> &count, const unsigned limit, const char *const kind) {
        if (!diagnostics_enabled()) return false;

        const unsigned number = count.fetch_add(1, std::memory_order_relaxed) + 1;
        if (number == limit + 1) LOG_DEBUG("Later %s are not logged", kind);
        return number <= limit;
    }

    struct code_location {
        const char *library;
        size_t offset;
    };

    // Names the library an address belongs to, so logs show who opened a file.
    code_location locate(const void *const address) {
        Dl_info info{};
        if (address == nullptr || dladdr(address, &info) == 0 || info.dli_fname == nullptr)
            return {.library = "unknown", .offset = 0};

        const char *const basename = strrchr(info.dli_fname, '/');
        return {
            .library = basename == nullptr ? info.dli_fname : basename + 1,
            .offset = static_cast<size_t>(static_cast<const char *>(address) -
                                          static_cast<const char *>(info.dli_fbase)),
        };
    }

    const char *find_replacement(const char *const pathname) {
        // Pair with the release exchange after all path copies finish.
        const path_mappings *const current = mappings.load(std::memory_order_acquire);
        // Let libc handle null paths and calls before installation.
        if (pathname == nullptr || current == nullptr) return nullptr;

        // Compare paths as supplied, without resolving aliases or symlinks.
        const auto match =
                std::ranges::find(*current, std::string_view(pathname), &path_mapping::installed);
        return match == current->end() ? nullptr : match->replacement.c_str();
    }

    bool is_apk(const char *const pathname) { return std::string_view(pathname).ends_with(".apk"); }

    // Returns the path a hooked libc call should open, logging what the hooks saw.
    const char *redirect_file(const char *const pathname, const char *const function,
                              const void *const caller) {
        if (const char *const replacement = find_replacement(pathname); replacement != nullptr) {
            if (admit_event(file_redirections, 64, "file redirections")) {
                const auto [library, offset] = locate(caller);
                LOG_INFO("%s(%s) from %s+0x%zx -> %s", function, pathname, library, offset,
                         replacement);
            }
            return replacement;
        }

        // An APK that is not redirected may reach the installed files through another path form.
        if (pathname != nullptr && diagnostics_enabled() && is_apk(pathname) &&
            admit_event(unmatched_apk_opens, 32, "unmatched APK opens")) {
            const auto [library, offset] = locate(caller);
            LOG_DEBUG("%s(%s) from %s+0x%zx is not an installed component; left unchanged",
                      function, pathname, library, offset);
        }
        return pathname;
    }

    bool needs_mode(const unsigned flags) {
        // Only creation flags require the optional mode argument.
        if ((flags & O_CREAT) != 0) return true;
#ifdef O_TMPFILE
        // O_TMPFILE is a combined flag, so require every bit.
        if ((flags & O_TMPFILE) == O_TMPFILE) return true;
#endif
        return false;
    }

    // bytehook replaces each caller's import and forwards through the function it replaced there,
    // so hooks other libraries placed on the same import keep running.
    //
    // Each proxy also serves the matching large-file symbol, which has the same signature. bytehook
    // keeps one hook chain per import slot, so forwarding still reaches the symbol that was called.
    // The exported libc ABI requires C varargs.
    // NOLINTNEXTLINE(cert-dcl50-cpp)
    int open_proxy(const char *const pathname, const int flags, ...) {
        BYTEHOOK_STACK_SCOPE();
        // Like bionic, read the mode only when the flags require it and forward zero otherwise.
        mode_t mode = 0;
        if (needs_mode(flags)) {
            va_list arguments{};
            va_start(arguments, flags);
            mode = static_cast<mode_t>(va_arg(arguments, int));
            va_end(arguments);
        }

        const char *const path = redirect_file(pathname, "open", BYTEHOOK_RETURN_ADDRESS());
        return BYTEHOOK_CALL_PREV(open_proxy, path, flags, mode);
    }

    // Preserve the directory descriptor when forwarding relative paths.
    // NOLINTNEXTLINE(cert-dcl50-cpp)
    int openat_proxy(const int directory, const char *const pathname, const int flags, ...) {
        BYTEHOOK_STACK_SCOPE();
        mode_t mode = 0;
        if (needs_mode(flags)) {
            va_list arguments{};
            va_start(arguments, flags);
            mode = static_cast<mode_t>(va_arg(arguments, int));
            va_end(arguments);
        }

        const char *const path = redirect_file(pathname, "openat", BYTEHOOK_RETURN_ADDRESS());
        return BYTEHOOK_CALL_PREV(openat_proxy, directory, path, flags, mode);
    }

    // FORTIFY builds call these checked entry points when the flags are not compile-time constants.
    int open_2_proxy(const char *const pathname, const int flags) {
        BYTEHOOK_STACK_SCOPE();
        const char *const path = redirect_file(pathname, "__open_2", BYTEHOOK_RETURN_ADDRESS());
        return BYTEHOOK_CALL_PREV(open_2_proxy, path, flags);
    }

    int openat_2_proxy(const int directory, const char *const pathname, const int flags) {
        BYTEHOOK_STACK_SCOPE();
        const char *const path = redirect_file(pathname, "__openat_2", BYTEHOOK_RETURN_ADDRESS());
        return BYTEHOOK_CALL_PREV(openat_2_proxy, directory, path, flags);
    }

    // Preserve stdio mode strings while redirecting the path.
    FILE *fopen_proxy(const char *const pathname, const char *const mode) {
        BYTEHOOK_STACK_SCOPE();
        const char *const path = redirect_file(pathname, "fopen", BYTEHOOK_RETURN_ADDRESS());
        return BYTEHOOK_CALL_PREV(fopen_proxy, path, mode);
    }

    // Some libraries open files through libc's syscall() to bypass the open wrappers.
    // NOLINTNEXTLINE(cert-dcl50-cpp)
    long syscall_proxy(const long number, ...) {
        BYTEHOOK_STACK_SCOPE();
        // Like bionic, forward six arguments, since the caller's count is unknown.
        std::array<long, 6> arguments{};
        va_list list{};
        va_start(list, number);
        for (long &argument: arguments) argument = va_arg(list, long);
        va_end(list);

        const auto redirect = [&](long &pathname, const char *const function) {
            pathname = reinterpret_cast<long>(redirect_file(
                    reinterpret_cast<const char *>(pathname), function, BYTEHOOK_RETURN_ADDRESS()));
        };
        if (number == __NR_openat) redirect(std::get<1>(arguments), "syscall(openat)");
#ifdef __NR_open
        if (number == __NR_open) redirect(std::get<0>(arguments), "syscall(open)");
#endif

        const auto [first, second, third, fourth, fifth, sixth] = arguments;
        return BYTEHOOK_CALL_PREV(syscall_proxy, number, first, second, third, fourth, fifth,
                                  sixth);
    }

    struct file_hook {
        const char *symbol;
        void *proxy;
    };

    // Some libraries import the large-file symbols directly.
    const auto file_hooks = std::to_array<file_hook>({
        {.symbol = "open", .proxy = reinterpret_cast<void *>(open_proxy)},
        {.symbol = "open64", .proxy = reinterpret_cast<void *>(open_proxy)},
        {.symbol = "openat", .proxy = reinterpret_cast<void *>(openat_proxy)},
        {.symbol = "openat64", .proxy = reinterpret_cast<void *>(openat_proxy)},
        {.symbol = "__open_2", .proxy = reinterpret_cast<void *>(open_2_proxy)},
        {.symbol = "__openat_2", .proxy = reinterpret_cast<void *>(openat_2_proxy)},
        {.symbol = "fopen", .proxy = reinterpret_cast<void *>(fopen_proxy)},
        {.symbol = "fopen64", .proxy = reinterpret_cast<void *>(fopen_proxy)},
        {.symbol = "syscall", .proxy = reinterpret_cast<void *>(syscall_proxy)},
    });

    // Reports each library whose import bytehook replaced, now or when the library loads later.
    // bytehook_hooked_t fixes the signature.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    void log_hook_result(bytehook_stub_t /*task*/, const int status,
                         const char *const caller_path_name, const char *const symbol,
                         void * /*proxy*/, void *const previous, void * /*argument*/) { // NOLINT(misc-const-correctness)
        // bytehook reports the resolved original separately after a successful hook.
        if (status == BYTEHOOK_STATUS_CODE_ORIG_ADDR) return;

        if (status == BYTEHOOK_STATUS_CODE_OK) {
            // A previous function outside libc means another hooker already replaced this import.
            LOG_DEBUG("Hooked %s in %s (forwarding to %s)", symbol, caller_path_name,
                      locate(previous).library);
            return;
        }
        LOG_WARN("Hooking %s in %s failed with bytehook status %d", symbol, caller_path_name,
                 status);
    }

    bool register_file_hooks() {
        if (const int status = bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);
            status != BYTEHOOK_STATUS_CODE_OK) {
            LOG_ERROR("bytehook initialization failed: %d", status);
            return false;
        }

        // The proxies forward through bytehook, so this library's own imports stay original.
        if (bytehook_add_ignore("libSignatureKiller.so") != 0) {
            LOG_ERROR("bytehook cannot ignore libSignatureKiller.so");
            return false;
        }

        // Match imports by name rather than by libc's address, so entries another hooker already
        // replaced are chained too. Automatic mode also hooks every library loaded later; direct
        // syscalls stay unchanged.
        const bytehook_hooked_t report = diagnostics_enabled() ? log_hook_result : nullptr;
        for (const auto &[symbol, proxy]: file_hooks) {
            if (bytehook_hook_all(nullptr, symbol, proxy, report, nullptr) != nullptr) continue;

            LOG_ERROR("Hooking %s failed", symbol);
            return false;
        }

        LOG_INFO("File hooks registered for %zu libc symbols in every loaded and later library",
                 file_hooks.size());
        return true;
    }
} // namespace

const path_mappings *installed_path_mappings() {
    return mappings.load(std::memory_order_acquire);
}

bool install_file_redirect(std::unique_ptr<path_mappings> table) {
    // The extension installs one table per process.
    if (const path_mappings *expected = nullptr;
        !mappings.compare_exchange_strong(expected, table.get(), std::memory_order_release,
                                          std::memory_order_relaxed)) {
        LOG_ERROR("Rejected path mappings: a table is already installed in this process");
        return false;
    }

    // The process now owns the table.
    const path_mappings *const installed = table.release();
    LOG_INFO("Installed %zu path mappings", installed->size());
    return register_file_hooks();
}
