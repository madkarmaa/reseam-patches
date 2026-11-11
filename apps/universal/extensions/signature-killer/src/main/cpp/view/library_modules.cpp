// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view_internal.h"
#include "logging.h"
#include "shadowhook.h"
#include <link.h>
#include <sys/mman.h>
#include <unistd.h>
#include <algorithm>
#include <atomic>
#include <cstring>
#include <string_view>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::view {
    std::vector<address_range> hidden_ranges;

    namespace {
        void *module_hook = nullptr;
        std::atomic<int> module_hook_status{-1};
        using module_callback = int (*)(dl_phdr_info *, size_t, void *);
        using module_iterator = int (*)(module_callback, void *);

        module_iterator iterate_modules = nullptr;

        struct target_query {
            const char *name;
        };

        uintptr_t target_start = UINTPTR_MAX;
        uintptr_t target_end = 0;

    } // namespace

    bool extension_library(const char *const path) {
        if (path == nullptr) return false;

        const char *const slash = strrchr(path, '/');
        std::string_view name(slash == nullptr ? path : slash + 1);

        if (name.ends_with('\n')) name.remove_suffix(1);
        if (name.ends_with(" (deleted)")) name.remove_suffix(sizeof(" (deleted)") - 1);

        return name == "libSignatureKiller.so" || name == "libshadowhook.so" ||
               name == "libshadowhook_nothing.so";
    }

    namespace {
        struct module_query {
            module_callback callback;
            void *data;
        };

        // The loader fixes the callback signature, and the query forwards the module it receives.
        const module_callback original_module = [](dl_phdr_info *const info, const size_t size,
                                                   void *const data) {
            if (extension_library(info->dlpi_name)) return 0;

            const auto &[callback, callback_data] = *static_cast<module_query *>(data);
            return callback(info, size, callback_data);
        };

        // Replaces dl_iterate_phdr itself, so it has the loader's signature.
        int iterate_original_modules(const module_callback callback, void *const data) {
            const auto address = reinterpret_cast<uintptr_t>(callback);
            const bool target =
                (address >= target_start && address < target_end) ||
                (target_options.contains_address != nullptr && target_options.contains_address(address));
            if (!target) return iterate_modules(callback, data);

            module_query query{.callback = callback, .data = data};
            return iterate_modules(original_module, &query);
        }

        // Collects the complete pages the kernel maps for one of the extension's images.
        void collect_image_range(const dl_phdr_info &image, std::vector<address_range> &ranges) {
            uintptr_t first = UINTPTR_MAX;
            uintptr_t last = 0;

            const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
            for (ElfW(Half) index = 0; index < image.dlpi_phnum; index++) {
                const auto &segment = image.dlpi_phdr[index];
                if (segment.p_type != PT_LOAD) continue;

                // The kernel reports complete pages, including the ELF segment's leading padding.
                const uintptr_t start = (image.dlpi_addr + segment.p_vaddr) & ~(page_size - 1);
                const uintptr_t end =
                    (image.dlpi_addr + segment.p_vaddr + segment.p_memsz + page_size - 1) & ~(page_size - 1);

                first = std::min(first, start);
                last = std::max(last, end);
            }

            if (first < last) ranges.push_back({.start = first, .end = last});
        }

        // Records the executable segments of the library to protect.
        void locate_target_segments(const dl_phdr_info &image, const target_query &target) {
            const char *const filename = image.dlpi_name == nullptr ? "" : image.dlpi_name;
            if (const char *const slash = strrchr(filename, '/');
                strcmp(slash == nullptr ? filename : slash + 1, target.name) != 0)
                return;

            for (ElfW(Half) index = 0; index < image.dlpi_phnum; index++) {
                const auto &segment = image.dlpi_phdr[index];
                if (segment.p_type != PT_LOAD) continue;

                target_start = std::min<uintptr_t>(target_start, image.dlpi_addr + segment.p_vaddr);
                target_end =
                    std::max<uintptr_t>(target_end, image.dlpi_addr + segment.p_vaddr + segment.p_memsz);
            }
        }

        // The loader fixes the callback signature for every module query, and each one reads only
        // the module it receives.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        const module_callback find_target_module = [](dl_phdr_info *const info, size_t /*size*/,
                                                      // ReSharper disable once CppParameterMayBeConstPtrOrRef
                                                      void *const data) {
            locate_target_segments(*info, *static_cast<const target_query *>(data));
            return 0;
        };

        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // The loader ABI requires an int callback even when every module is visited.
        // ReSharper disable once CppDFAConstantFunctionResult
        const module_callback collect_extension_ranges = [](dl_phdr_info *const info, size_t /*size*/,
                                                            void *const data) {
            if (!extension_library(info->dlpi_name)) return 0;

            collect_image_range(*info, *static_cast<std::vector<address_range> *>(data));
            return 0;
        };

    } // namespace

    bool module_view_installed() {
        return module_hook != nullptr;
    }

    bool module_view_ready() {
        return module_hook_status.load(std::memory_order_acquire) == SHADOWHOOK_ERRNO_OK;
    }

    bool prepare_module_ranges(const char *const library) {
        target_query query{.name = library};
        dl_iterate_phdr(find_target_module, &query);

        if (target_start >= target_end) return false;
        if (shadowhook_init(SHADOWHOOK_MODE_SHARED, false) != SHADOWHOOK_ERRNO_OK) return false;

        // Capture on the installing thread. A constructor may hold the linker lock while it waits
        // for a snapshot, so the worker must never call back into the loader.
        dl_iterate_phdr(collect_extension_ranges, &hidden_ranges);

        return true;
    }

    bool install_module_view() {
        module_hook = shadowhook_hook_func_addr_2(
            reinterpret_cast<void *>(dl_iterate_phdr), reinterpret_cast<void *>(iterate_original_modules),
            reinterpret_cast<void **>(&iterate_modules), SHADOWHOOK_HOOK_WITH_UNIQUE_MODE);

        module_hook_status.store(module_hook != nullptr ? SHADOWHOOK_ERRNO_OK : -1,
                                 std::memory_order_release);

        LOG_INFO("Native module view inline hook status: %d", shadowhook_get_errno());

        if (module_hook != nullptr) {
            // ShadowHook leaves its patched code page writable. Restore the ELF code permissions.
            const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
            if (const auto address = reinterpret_cast<uintptr_t>(dl_iterate_phdr) & ~(page_size - 1);
                mprotect(reinterpret_cast<void *>(address), static_cast<size_t>(page_size),
                         PROT_READ | PROT_EXEC) != 0)
                return false;
        }

        return module_hook != nullptr &&
               module_hook_status.load(std::memory_order_acquire) == SHADOWHOOK_ERRNO_OK;
    }
} // namespace signature::view
