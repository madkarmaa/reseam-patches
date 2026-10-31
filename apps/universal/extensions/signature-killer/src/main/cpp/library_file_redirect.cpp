// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "seccomp_open.h"
#include "logging.h"

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

    void library_initializing(dl_phdr_info *const info, size_t /*size*/, void *const data) {
        auto &[library, paths, installed, redirect_contents] = *static_cast<pending_redirect *>(data);
        if (installed.load(std::memory_order_acquire) || info->dlpi_name == nullptr) return;
        
        const char *const slash = strrchr(info->dlpi_name, '/');
        if (library != (slash == nullptr ? info->dlpi_name : slash + 1)) return;

        raw_redirect_options options{.start = UINTPTR_MAX, .preserve_descriptor_identity = true};
        options.redirect_file_contents = redirect_contents;
        
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;
        
            options.start = std::min<uintptr_t>(options.start, info->dlpi_addr + segment.p_vaddr);
            options.end = std::max<uintptr_t>(options.end, info->dlpi_addr + segment.p_vaddr + segment.p_memsz);
        }
        
        if (!install_raw_open_redirect(library.c_str(), paths, options)) {
            LOG_ERROR("Cannot install APK file redirection for %s before constructors", library.c_str());
            abort();
        }
        
        installed.store(true, std::memory_order_release);
        LOG_INFO("APK file redirection installed for %s before constructors", library.c_str());
    }
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
    
    const int initialized = shadowhook_init(SHADOWHOOK_MODE_SHARED, false);
    if (initialized != 0) {
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
