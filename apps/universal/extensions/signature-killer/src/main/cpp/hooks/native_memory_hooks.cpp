// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_hook_internal.h"
#include "hooks/native_libc_forwarders.h"
#include "logging.h"
#include <dlfcn.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <cstdlib>
#include <limits>
#include <mutex>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::hooks {
    namespace {
        struct allocation_range {
            uintptr_t start;
            uintptr_t end;
        };

        constinit std::mutex allocations_mutex;
        std::vector<allocation_range> target_allocations;

        bool target_allocation(const uintptr_t start, const uintptr_t end) {
            const std::scoped_lock lock(allocations_mutex);
            return std::ranges::any_of(target_allocations, [start, end](const allocation_range &range) {
                return start >= range.start && end <= range.end;
            });
        }

        void remember_allocation(const uintptr_t caller, const void *const address, const size_t size) {
            if (size == 0 || native_file_hooks_bypassed() || !raw_redirect_contains_address(caller)) return;
            const auto start = reinterpret_cast<uintptr_t>(address);
            const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
            if (constexpr auto maximum = std::numeric_limits<uintptr_t>::max();
                start > maximum - (page_size - 1) || size > maximum - start - (page_size - 1))
                abort();
            const std::scoped_lock lock(allocations_mutex);
            target_allocations.push_back({
                .start = start,
                .end = (start + size + page_size - 1) & ~(page_size - 1),
            });
        }

        void retain_executable_mapping(const uintptr_t caller, const void *const address, const size_t size,
                                       const int protection) {
            if ((static_cast<unsigned>(protection) & static_cast<unsigned>(PROT_EXEC)) == 0 || size == 0 ||
                native_file_hooks_bypassed() || !raw_redirect_contains_address(caller))
                return;

            const auto start = reinterpret_cast<uintptr_t>(address);
            if (size > std::numeric_limits<uintptr_t>::max() - start) abort();

            // A protector may change libc, ART or another hook's anonymous pages too. Permission
            // changes alone do not establish ownership of memory it did not allocate.
            Dl_info destination{};
            const bool loaded_image = dladdr(address, &destination) != 0;
            if (!loaded_image && !target_allocation(start, start + size)) return;
            if (loaded_image) {
                Dl_info owner{};
                if (dladdr(reinterpret_cast<const void *>(caller), &owner) == 0 ||
                    owner.dli_fbase != destination.dli_fbase)
                    return;
            }

            const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
            const auto first = reinterpret_cast<uintptr_t>(address) & ~(page_size - 1);
            if (constexpr auto maximum = std::numeric_limits<uintptr_t>::max();
                start > maximum - (page_size - 1) || size > maximum - start - (page_size - 1))
                abort();

            const auto end = (start + size + page_size - 1) & ~(page_size - 1);
            if (extend_raw_open_redirect(first, end)) return;

            LOG_ERROR("Cannot redirect newly executable native mapping [%p, %p)",
                      reinterpret_cast<void *>(first), reinterpret_cast<void *>(end));
            abort();
        }

        int (*original_mprotect)(void *, size_t, int) = nullptr;
        using mmap_function = void *(*)(void *, size_t, int, int, int, off_t);
        using mmap64_function = void *(*)(void *, size_t, int, int, int, off64_t);
        mmap_function original_mmap = nullptr;
        mmap64_function original_mmap64 = nullptr;

        // Match mprotect's fixed libc signature.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // NOLINTNEXTLINE(misc-const-correctness)
        int mprotect_proxy(void *const address, const size_t size, const int protection) {
            const auto caller = NATIVE_CALLER;
            const int previous_errno = errno;
            const int result =
                libc_result<int>(signature_raw_syscall(__NR_mprotect, reinterpret_cast<long>(address),
                                                       static_cast<long>(size), protection, 0, 0, 0),
                                 previous_errno);
            const int result_errno = errno;
            if (result == 0) {
                retain_executable_mapping(caller, address, size, protection);
                intercept_libc_forwarders(address, size, protection);
            }
            errno = result_errno;
            return result;
        }

        template <typename Offset, auto &Original>
        void *mmap_proxy(void *const address, const size_t size, const int protection, const int flags,
                         const int descriptor, const Offset offset) {
            const auto caller = NATIVE_CALLER;
            const int previous_errno = errno;
#ifdef __arm__
            // mmap2 always uses 4096-byte units, including on devices with larger pages.
            const auto position = static_cast<uint64_t>(offset);
            if (offset < 0 || (position & 4095U) != 0) return libc_result<void *>(-EINVAL, previous_errno);
            const auto page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
            const size_t rounded = (size + page_size - 1) & ~(page_size - 1);
            if (rounded < size || rounded > static_cast<size_t>(PTRDIFF_MAX))
                return libc_result<void *>(-ENOMEM, previous_errno);
            const auto redirected = redirect(caller, __NR_mmap2, reinterpret_cast<long>(address),
                                             static_cast<long>(size), protection, flags, descriptor,
                                             static_cast<long>(static_cast<uint32_t>(position >> 12U)));
#else
            const auto redirected =
                redirect(caller, __NR_mmap, reinterpret_cast<long>(address), static_cast<long>(size),
                         protection, flags, descriptor, static_cast<long>(offset));
#endif
            void *const result = libc_result<void *>(redirected, previous_errno);
            const int result_errno = errno;
            if (result != MAP_FAILED) {
                remember_allocation(caller, result, size);
                retain_executable_mapping(caller, result, size, protection);
            }
            errno = result_errno;
            return result;
        }

    } // namespace

    std::span<const libc_hook> memory_hooks() {
        static const auto hooks = std::to_array({
            hook("mprotect", mprotect_proxy, original_mprotect),
            hook("mmap", mmap_proxy<off_t, original_mmap>, original_mmap),
            hook("mmap64", mmap_proxy<off64_t, original_mmap64>, original_mmap64),
        });
        return hooks;
    }
} // namespace signature::hooks
