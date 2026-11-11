// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/seccomp_open.h"
#include "logging.h"
#include "view/library_view.h"
#include "hooks/native_file_hooks.h"

#include "shadowhook.h"

#include <elf.h>
#include <link.h>

#include <algorithm>
#include <atomic>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <string>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    struct pending_redirect {
        std::string library;
        std::vector<raw_open_mapping> paths;
        std::atomic<bool> installed{false};
        bool redirect_contents = false;
    };

    constinit std::mutex pending_mutex;
    // Registered callbacks retain this request until process exit.
    pending_redirect *pending = nullptr;

    // Reads only the module's load segments, which is what the redirect range needs.
    void locate_executable_segments(const dl_phdr_info &info, raw_redirect_options &options) {
        for (ElfW(Half) index = 0; index < info.dlpi_phnum; index++) {
            const auto &segment = info.dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;

            options.start = std::min<uintptr_t>(options.start, info.dlpi_addr + segment.p_vaddr);
            options.end = std::max<uintptr_t>(options.end, info.dlpi_addr + segment.p_vaddr + segment.p_memsz);
        }
    }

    void install_for_module(const dl_phdr_info &info, pending_redirect &request) {
        auto &[library, paths, installed, redirect_contents] = request;
        if (installed.load(std::memory_order_acquire) || info.dlpi_name == nullptr) return;

        if (const char *const slash = strrchr(info.dlpi_name, '/');
            library != (slash == nullptr ? info.dlpi_name : slash + 1))
            return;

        raw_redirect_options options{.start = UINTPTR_MAX, .preserve_descriptor_identity = true};
        options.redirect_file_contents = redirect_contents;
        options.bypass_current_thread = native_file_hooks_bypassed;
        locate_executable_segments(info, options);

        if (redirect_contents) {
            constexpr library_view_options view{
                .contains_address = raw_redirect_contains_address,
                .filter_count = raw_redirect_filter_count,
                .enter_worker = bypass_native_file_hooks_for_thread,
            };
            if (!prepare_library_view(library.c_str(), view)) {
                LOG_ERROR("Cannot prepare process views for %s before constructors", library.c_str());
                abort();
            }
            options.snapshot_proc_file = snapshot_process_file;
        }

        if (!install_raw_open_redirect(library.c_str(), paths, options)) {
            LOG_ERROR("Cannot install APK file redirection for %s before constructors", library.c_str());
            abort();
        }

        if (!prepare_native_file_hooks()) {
            LOG_ERROR("Cannot cover unpacked native file access for %s", library.c_str());
            abort();
        }

        installed.store(true, std::memory_order_release);
        LOG_INFO("APK file redirection installed for %s before constructors", library.c_str());
    }

    // Registering for both constructor and destructor callbacks fixes this signature, and the
    // request arrives as the data argument.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    const shadowhook_dl_info_t library_initializing = [](dl_phdr_info *const info, size_t /*size*/,
                                                         void *const data) {
        install_for_module(*info, *static_cast<pending_redirect *>(data));
    };
} // namespace

bool prepare_library_file_redirect(const char *const library,
                                  const std::span<const raw_open_mapping> paths,
                                  const bool redirect_contents) {
    const std::scoped_lock lock(pending_mutex);
    
    if (pending != nullptr) return pending->library == library;
    if (paths.empty()) return false;

    auto request = std::make_unique<pending_redirect>();
    request->library = library;
    request->paths.assign_range(paths);
    request->redirect_contents = redirect_contents;
    
    if (const int initialized = shadowhook_init(SHADOWHOOK_MODE_SHARED, false); initialized != 0) {
        LOG_ERROR("ShadowHook initialization failed: %d", initialized);
        return false;
    }
    
    if (shadowhook_register_dl_init_callback(library_initializing, nullptr, request.get()) != 0) {
        LOG_ERROR("Registering native constructor callback failed: %d", shadowhook_get_errno());
        return false;
    }
    
    pending = request.release();
    
    LOG_INFO("Waiting for %s to install APK file redirection", library);
    return true;
}
