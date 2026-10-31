// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "seccomp_open.h"
#include "logging.h"

#include "bytehook.h"

#include <dlfcn.h>
#include <elf.h>
#include <link.h>

#include <algorithm>
#include <atomic>
#include <charconv>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    struct pending_redirect {
        std::string library;
        std::vector<raw_open_mapping> paths;
        std::atomic<bool> installed{false};
    };

    constinit std::mutex pending_mutex;
    // Registered callbacks retain this request until process exit.
    pending_redirect *pending = nullptr;
    thread_local bool resolving_library = false;

    std::optional<raw_redirect_options> library_executable_range(const void *const entry_point) {
        const auto close_maps = [](FILE *const file) { static_cast<void>(fclose(file)); };
        std::unique_ptr<FILE, decltype(close_maps)> maps(fopen("/proc/self/maps", "re"), close_maps);
        if (!maps) return std::nullopt;

        char *line = nullptr;
        size_t capacity = 0;
        uintptr_t mapping_start = 0;
        uintptr_t mapping_end = 0;
#ifdef __arm__
        // Function pointers carry the Thumb-state bit, which is not part of the code address.
        const auto address = reinterpret_cast<uintptr_t>(entry_point) & ~uintptr_t{1};
#else
        const auto address = reinterpret_cast<uintptr_t>(entry_point);
#endif
        
        while (getline(&line, &capacity, maps.get()) >= 0) {
            unsigned long start = 0;
            unsigned long end = 0;

            const char *const line_end = line + strlen(line);
            const auto [start_end, start_error] = std::from_chars(line, line_end, start, 16);
            if (start_error != std::errc{} || start_end == line_end || *start_end != '-') continue;
            const auto [end_end, end_error] = std::from_chars(start_end + 1, line_end, end, 16);
            if (end_error != std::errc{} || address < start || address >= end) continue;

            // procfs separates the hexadecimal range from its four permission characters.
            if (const std::string_view permissions(end_end, line_end);
                permissions.size() < 5 || !permissions.starts_with(" r") || permissions.substr(3, 1) != "x")
                continue;
            
            mapping_start = start;
            mapping_end = end;
            
            break;
        }
        
        // getline owns a reallocatable buffer; adopt it once it can no longer change.
        std::unique_ptr<char, decltype(&free)> line_memory(line, free);
        line_memory.reset();
        maps.reset();
        
        if (mapping_start == 0) return std::nullopt;

        // Resolve the complete ELF image from the mapping containing the actual entry point.
        // This also covers anonymous images absent from the named library's program headers.
        const auto *const elf_header = reinterpret_cast<const ElfW(Ehdr) *>(mapping_start);
        if (memcmp(elf_header->e_ident, ELFMAG, SELFMAG) != 0 ||
            elf_header->e_phentsize != sizeof(ElfW(Phdr)) || elf_header->e_phnum > 1024 ||
            elf_header->e_phoff > mapping_end - mapping_start ||
            elf_header->e_phnum > (mapping_end - mapping_start - elf_header->e_phoff) / sizeof(ElfW(Phdr)))
            return std::nullopt;

        const auto *const segments = reinterpret_cast<const ElfW(Phdr) *>(mapping_start + elf_header->e_phoff);
        uintptr_t load_bias = mapping_start;
        
        for (unsigned index = 0; index < elf_header->e_phnum; index++) {
            const auto &segment = segments[index];
            if (segment.p_type != PT_LOAD || segment.p_offset != 0) continue;
            
            load_bias = mapping_start - segment.p_vaddr;
            break;
        }

        raw_redirect_options options{.start = UINTPTR_MAX, .preserve_descriptor_identity = true};
        for (unsigned index = 0; index < elf_header->e_phnum; index++) {
            const auto &segment = segments[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;
            
            options.start = std::min<uintptr_t>(options.start, load_bias + segment.p_vaddr);
            options.end = std::max<uintptr_t>(options.end, load_bias + segment.p_vaddr + segment.p_memsz);
        }
        
        if (address < options.start || address >= options.end) return std::nullopt;
        return options;
    }

    void library_loaded(const char *const filename, const int result, void *const data) {
        auto &[library, paths, installed] = *static_cast<pending_redirect *>(data);
        if (result != 0 || filename == nullptr || resolving_library) return;
        if (installed.load(std::memory_order_acquire)) return;

        if (const char *const slash = strrchr(filename, '/');
            library != (slash == nullptr ? filename : slash + 1)) return;

        // RTLD_NOLOAD also passes through the load monitor. Suppress this callback's recursion.
        resolving_library = true;
        
        void *const handle = dlopen(filename, RTLD_NOW | RTLD_NOLOAD);
        
        const void *const entry_point = handle == nullptr ? nullptr : dlsym(handle, "JNI_OnLoad");
        
        const auto executable_range = entry_point == nullptr
                ? std::nullopt : library_executable_range(entry_point);
        
        const bool redirected = executable_range && install_raw_open_redirect(
                library.c_str(), paths, *executable_range);
        
        if (handle != nullptr) dlclose(handle);
        
        resolving_library = false;

        if (!redirected) {
            LOG_ERROR("Cannot install APK file redirection for %s before JNI_OnLoad",
                      library.c_str());
            abort();
        }
        installed.store(true, std::memory_order_release);
        LOG_INFO("APK file redirection installed for %s before JNI_OnLoad at %p",
                 library.c_str(), entry_point);
    }
} // namespace

bool prepare_library_file_redirect(const char *const library,
                                  const std::span<const raw_open_mapping> paths) {
    const std::scoped_lock lock(pending_mutex);
    
    if (pending != nullptr) return pending->library == library;
    if (paths.empty()) return false;

    auto request = std::make_unique<pending_redirect>();
    request->library = library;
    request->paths.assign_range(paths);
    
    pending = request.release();
    bytehook_add_dlopen_callback(nullptr, library_loaded, pending);
    
    LOG_INFO("Waiting for %s to install APK file redirection", library);
    return true;
}
