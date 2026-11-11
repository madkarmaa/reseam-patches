// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_libc_forwarders.h"
#include "hooks/native_branch.h"
#include "redirect/seccomp_open.h"
#include "logging.h"
#include "shadowhook.h"
#include <sys/mman.h>
#include <algorithm>
#include <cstdlib>
#include <string_view>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    std::vector<resolved_hook> resolved_hooks;
    std::atomic<bool> forwarders_ready{false};

    namespace {
#ifdef __aarch64__
        std::atomic_flag intercepting_forwarders = ATOMIC_FLAG_INIT;
        std::vector<resolved_hook> intercepted_forwarders;

        std::string_view operation_name(const char *const name) {
            const std::string_view operation(name);
            // The view borrows the input string; it never points into the local view object.
            // ReSharper disable once CppDFALocalValueEscapesFunction
            return {name, operation.size() - (operation.ends_with("64") ? 2 : 0)};
        }
#endif

    } // namespace

    void intercept_libc_forwarders(const void *const address, const size_t size, const int protection) {
#ifdef __aarch64__
        if (!forwarders_ready.load(std::memory_order_acquire) ||
            (static_cast<unsigned>(protection) & static_cast<unsigned>(PROT_EXEC)) == 0 ||
            (static_cast<unsigned>(protection) & static_cast<unsigned>(PROT_WRITE)) != 0)
            return;
        if (const auto start = reinterpret_cast<uintptr_t>(address);
            std::ranges::none_of(resolved_hooks, [start, size](const resolved_hook &record) {
                return record.address >= start && record.address - start < size;
            }))
            return;
        // ShadowHook changes permissions itself. Never re-enter it through its mprotect calls.
        if (intercepting_forwarders.test_and_set(std::memory_order_acquire)) return;
        for (const auto &[name, entry_address, proxy] : resolved_hooks) {
            const auto [destination, literal] = forwarding_destination(entry_address);
            if ((destination & 3U) != 0 || !raw_redirect_contains_address(destination)) continue;
            if (literal != 0) {
                if (!retarget_literal(literal, proxy)) {
                    LOG_ERROR("Cannot retarget native libc %s forwarding literal %p", name,
                              reinterpret_cast<void *>(literal));
                    abort();
                }
                LOG_INFO("Native libc %s forwarding literal retargeted at %p", name,
                         reinterpret_cast<void *>(literal));
                continue;
            }
            const auto previous =
                std::ranges::find(intercepted_forwarders, destination, &resolved_hook::address);
            if (previous != intercepted_forwarders.end()) {
                if (operation_name(previous->name) == operation_name(name)) continue;
                LOG_ERROR("Native libc %s and %s share an incompatible forwarding entry %p", previous->name,
                          name, reinterpret_cast<void *>(destination));
                abort();
            }
            // Hook where LR still names the real caller, before the protector saves it and
            // forwards into our older libc hook. Its syscall proxy needs no original trampoline.
            if (shadowhook_hook_func_addr_2(reinterpret_cast<void *>(destination), proxy, nullptr,
                                            SHADOWHOOK_HOOK_WITH_UNIQUE_MODE) == nullptr) {
                LOG_ERROR("Cannot intercept native libc %s forwarding entry %p: %d", name,
                          reinterpret_cast<void *>(destination), shadowhook_get_errno());
                abort();
            }
            intercepted_forwarders.push_back({
                .name = name,
                .address = destination,
                .proxy = proxy,
            });
            LOG_INFO("Native libc %s forwarding entry intercepted at %p", name,
                     reinterpret_cast<void *>(destination));
        }
        intercepting_forwarders.clear(std::memory_order_release);
#else
        // ARM/Thumb forwarding layouts require runtime evidence before decoding them.
        static_cast<void>(address);
        static_cast<void>(size);
        static_cast<void>(protection);
#endif
    }
} // namespace signature::hooks
