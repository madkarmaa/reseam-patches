// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_hook_internal.h"
#include "hooks/native_libc_forwarders.h"
#include "logging.h"
#include "shadowhook.h"
#include <dlfcn.h>
#include <sys/mman.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <limits>
#include <mutex>
#include <ranges>
// ReSharper restore CppUnusedIncludeDirective

using signature::hooks::file_io_hooks;
using signature::hooks::file_metadata_hooks;
using signature::hooks::forwarders_ready;
using signature::hooks::intercept_libc_forwarders;
using signature::hooks::memory_hooks;
using signature::hooks::prepare_file_io_hooks;
using signature::hooks::prepare_thread_context;
using signature::hooks::resolved_hooks;
using signature::hooks::stdio_hooks;

namespace {
    constinit std::mutex hooks_mutex;
    bool installed = false;
} // namespace

bool prepare_native_file_hooks() {
    const std::scoped_lock lock(hooks_mutex);
    if (installed) return true;
    prepare_thread_context();

    const auto groups =
        std::to_array({memory_hooks(), file_io_hooks(), file_metadata_hooks(), stdio_hooks()});
    const auto hooks = groups | std::views::join;
    const auto hook_count = static_cast<size_t>(std::ranges::distance(hooks));

    void *const libc = dlopen("libc.so", RTLD_NOW | RTLD_LOCAL);
    if (libc == nullptr) return false;

    prepare_file_io_hooks(libc);

    resolved_hooks.reserve(hook_count);
    std::vector<void *> hooked_addresses;
    hooked_addresses.reserve(hook_count);
    const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
    for (const auto &[name, proxy, original, intercept_forwarder] : hooks) {
        void *const address = dlsym(libc, name);
        if (address == nullptr) {
            LOG_ERROR("Missing libc function for native file access: %s", name);
            dlclose(libc);
            return false;
        }

        // Bionic exports several large-file names at the same address. Hook that entry once.
        if (std::ranges::contains(hooked_addresses, address)) continue;

        if (shadowhook_hook_func_addr_2(address, proxy, original, SHADOWHOOK_HOOK_WITH_UNIQUE_MODE) ==
            nullptr) {
            LOG_ERROR("Cannot hook native libc %s: %d", name, shadowhook_get_errno());
            dlclose(libc);
            return false;
        }
        hooked_addresses.push_back(address);
        if (intercept_forwarder)
            resolved_hooks.push_back({
                .name = name,
                .address = reinterpret_cast<uintptr_t>(address),
                .proxy = proxy,
            });

        if (const auto page = reinterpret_cast<uintptr_t>(address) & ~(page_size - 1);
            mprotect(reinterpret_cast<void *>(page), page_size, PROT_READ | PROT_EXEC) != 0) {
            dlclose(libc);
            return false;
        }
    }
    dlclose(libc);
    forwarders_ready.store(true, std::memory_order_release);
    installed = true;
    LOG_INFO("Caller-scoped native file hooks installed for %zu libc entry points", hooked_addresses.size());
    return true;
}

void refresh_native_file_hooks() {
    // Some protectors keep libc writable after installing their forwarding branches.
    // The explicit library-load boundary is safe even when no RX restoration occurs.
    intercept_libc_forwarders(nullptr, std::numeric_limits<size_t>::max(), PROT_READ | PROT_EXEC);
}
