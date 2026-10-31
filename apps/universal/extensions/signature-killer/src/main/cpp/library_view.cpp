// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "library_view.h"
#include "logging.h"
#include "shadowhook.h"

#include <fcntl.h>
#include <link.h>
#include <linux/memfd.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <sys/mman.h>
#include <unistd.h>

#include <array>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <csignal>
#include <charconv>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <memory>
#include <string_view>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace {
    std::atomic<int> requests{-1};
    
    void *module_hook = nullptr;
    std::atomic<int> module_hook_status{-1};
    using module_callback = int (*)(dl_phdr_info *, size_t, void *);
    
    int (*iterate_modules)(module_callback, void *) = nullptr;
    
    uintptr_t target_start = UINTPTR_MAX;
    uintptr_t target_end = 0;
    
    unsigned original_no_new_privileges = 0;
    unsigned long long original_blocked_signals = 0;
    unsigned long long original_caught_signals = 0;

    bool capture_process_status() {
        std::unique_ptr<FILE, decltype(&fclose)> status(fopen("/proc/self/status", "re"), fclose);
        if (!status) return false;
        
        char *line = nullptr;
        size_t capacity = 0;
        unsigned found = 0;
        
        while (getline(&line, &capacity, status.get()) >= 0) {
            if (sscanf(line, "NoNewPrivs: %u", &original_no_new_privileges) == 1) found |= 1;
            if (sscanf(line, "SigBlk: %llx", &original_blocked_signals) == 1) found |= 2;
            if (sscanf(line, "SigCgt: %llx", &original_caught_signals) == 1) found |= 4;
        }
        
        free(line);
        return found == 7 && !ferror(status.get());
    }

    bool extension_library(const char *path) {
        if (path == nullptr) return false;
        
        const char *slash = strrchr(path, '/');
        std::string_view name(slash == nullptr ? path : slash + 1);
        
        if (name.ends_with('\n')) name.remove_suffix(1);
        if (name.ends_with(" (deleted)")) name.remove_suffix(sizeof(" (deleted)") - 1);
        
        return name == "libSignatureKiller.so" || name == "libshadowhook.so" ||
               name == "libshadowhook_nothing.so";
    }

    struct module_query {
        int (*callback)(dl_phdr_info *, size_t, void *);
        void *data;
    };

    int original_module(dl_phdr_info *info, size_t size, void *data) {
        if (extension_library(info->dlpi_name)) return 0;
        
        const auto &query = *static_cast<module_query *>(data);
        return query.callback(info, size, query.data);
    }

    int iterate_original_modules(int (*callback)(dl_phdr_info *, size_t, void *), void *data) {
        const auto address = reinterpret_cast<uintptr_t>(callback);
        if (address < target_start || address >= target_end)
            return iterate_modules(callback, data);
        
        module_query query{callback, data};
        return iterate_modules(original_module, &query);
    }

    struct address_range { uintptr_t start; uintptr_t end; };
    std::vector<address_range> hidden_ranges;

    int collect_extension_ranges(dl_phdr_info *info, size_t, void *data) {
        if (!extension_library(info->dlpi_name)) return 0;
        
        auto &ranges = *static_cast<std::vector<address_range> *>(data);
        
        uintptr_t first = UINTPTR_MAX;
        uintptr_t last = 0;
        
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD) continue;
            
            // The kernel reports complete pages, including the ELF segment's leading padding.
            const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
            const uintptr_t start = (info->dlpi_addr + segment.p_vaddr) & ~(page_size - 1);
            const uintptr_t end = (info->dlpi_addr + segment.p_vaddr + segment.p_memsz +
                                   page_size - 1) & ~(page_size - 1);
            
            first = std::min(first, start);
            last = std::max(last, end);
        }
        
        if (first < last) ranges.push_back({first, last});
        return 0;
    }

    int create_proc_snapshot(const proc_file file) {
        const bool is_maps = file == proc_file::maps;
        
        std::unique_ptr<FILE, decltype(&fclose)> maps(
                fopen(is_maps ? "/proc/self/maps" : "/proc/self/status", "re"), fclose);
        
        if (!maps) return -errno;
        
        const int result = static_cast<int>(syscall(__NR_memfd_create, is_maps ? "maps" : "status", MFD_CLOEXEC));
        if (result < 0) return -errno;

        char *line = nullptr;
        size_t capacity = 0;
        int failure = 0;
        unsigned excluded_lines = 0;
        
        while (true) {
            const ssize_t length = getline(&line, &capacity, maps.get());
            if (length < 0) break;
            
            const char *output = line;
            ssize_t output_length = length;
            std::array<char, 80> adjusted{};
            
            if (!is_maps) {
                unsigned value = 0;
                unsigned long long signals = 0;
                constexpr auto own_signal = 1ULL << (SIGSYS - 1);
                
                if (sscanf(line, "NoNewPrivs: %u", &value) == 1)
                    output_length = snprintf(adjusted.data(), adjusted.size(), "NoNewPrivs:\t%u\n",
                                             original_no_new_privileges);
                
                else if (sscanf(line, "Seccomp_filters: %u", &value) == 1 && value > 0)
                    output_length = snprintf(adjusted.data(), adjusted.size(), "Seccomp_filters:\t%u\n", value - 1);
                
                else if (sscanf(line, "SigBlk: %llx", &signals) == 1)
                    output_length = snprintf(adjusted.data(), adjusted.size(), "SigBlk:\t%016llx\n",
                            (signals & ~own_signal) | (original_blocked_signals & own_signal));
                
                else if (sscanf(line, "SigCgt: %llx", &signals) == 1)
                    output_length = snprintf(adjusted.data(), adjusted.size(), "SigCgt:\t%016llx\n",
                            (signals & ~own_signal) | (original_caught_signals & own_signal));
                
                if (adjusted[0] != '\0') output = adjusted.data();
            }
            
            uintptr_t start = 0;
            const auto [end, error] = std::from_chars(line, line + length, start, 16);
            
            // ByteHook's trampoline pages are separate mappings of our library file, outside
            // its ELF image. Exclude those copies as well as the loader's module mappings.
            bool excluded = is_maps && extension_library(line);
            
            if (is_maps && error == std::errc{} && end < line + length && *end == '-') {
                uintptr_t limit = 0;
                const auto parsed = std::from_chars(end + 1, line + length, limit, 16);
                
                // Android's page-size compatibility output can retain an empty segment after
                // mprotect splits a code page. It names no memory and has no map_files entry.
                if (parsed.ec == std::errc{} && start == limit) excluded = true;
                
                for (const auto &range : hidden_ranges)
                    if (start >= range.start && start < range.end) { excluded = true; break; }
            }
            
            if (excluded) { excluded_lines++; continue; }

            ssize_t written = 0;
            while (written < output_length) {
                const ssize_t count = write(result, output + written, output_length - written);
            
                if (count < 0 && errno == EINTR) continue;
                if (count <= 0) { failure = count < 0 ? errno : EIO; break; }
                
                written += count;
            }
            
            if (failure != 0) break;
        }
        
        free(line);
        
        if (failure == 0 && ferror(maps.get())) failure = EIO;
        if (failure == 0) {
            if (is_maps)
                LOG_DEBUG("Maps snapshot excludes %u extension or empty mappings in %zu images",
                          excluded_lines, hidden_ranges.size());
            return result;
        }
        
        close(result);
        return -failure;
    }

    struct snapshot_request { int reply; proc_file file; };

    void *snapshot_worker(void *data) {
        const int descriptor = static_cast<int>(reinterpret_cast<intptr_t>(data));
        snapshot_request request{};
        
        while (true) {
            const ssize_t count = read(descriptor, &request, sizeof(request));
            
            if (count < 0 && errno == EINTR) continue;
            if (count != sizeof(request)) break;
            
            const int result = create_proc_snapshot(request.file);
            
            ssize_t sent;
            do { sent = write(request.reply, &result, sizeof(result)); } while (sent < 0 && errno == EINTR);
            
            if (sent != sizeof(result) && result >= 0) close(result);
        }
        
        close(descriptor);
        return nullptr;
    }
}

bool prepare_library_view(const char *library) {
    if (module_hook != nullptr)
        return module_hook_status.load(std::memory_order_acquire) == SHADOWHOOK_ERRNO_OK;
    
    if (!capture_process_status()) return false;
    
    struct target_query { const char *name; } query{library};
    
    dl_iterate_phdr([](dl_phdr_info *info, size_t, void *data) {
        const auto &query = *static_cast<target_query *>(data);
        const char *slash = strrchr(info->dlpi_name, '/');
        
        if (strcmp(slash == nullptr ? info->dlpi_name : slash + 1, query.name) != 0) return 0;
        
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD) continue;
            
            target_start = std::min<uintptr_t>(target_start, info->dlpi_addr + segment.p_vaddr);
            target_end = std::max<uintptr_t>(target_end, info->dlpi_addr + segment.p_vaddr + segment.p_memsz);
        }
        
        return 0;
    }, &query);
    
    if (target_start >= target_end) return false;
    if (shadowhook_init(SHADOWHOOK_MODE_SHARED, false) != SHADOWHOOK_ERRNO_OK) return false;
    
    // Capture on the installing thread. A constructor may hold the linker lock while it waits
    // for a snapshot, so the worker must never call back into the loader.
    dl_iterate_phdr(collect_extension_ranges, &hidden_ranges);
    
    std::array<int, 2> pipe{};
    if (pipe2(pipe.data(), O_CLOEXEC) != 0) return false;
    
    pthread_t worker{};
    if (pthread_create(&worker, nullptr, snapshot_worker,
                       reinterpret_cast<void *>(static_cast<intptr_t>(pipe[0]))) != 0) {
        close(pipe[0]); close(pipe[1]);
        return false;
    }
    
    pthread_detach(worker);
    
    requests.store(pipe[1], std::memory_order_release);
    
    module_hook = shadowhook_hook_func_addr_2(reinterpret_cast<void *>(dl_iterate_phdr),
            reinterpret_cast<void *>(iterate_original_modules), reinterpret_cast<void **>(&iterate_modules),
            SHADOWHOOK_HOOK_WITH_UNIQUE_MODE);
    
    module_hook_status.store(module_hook != nullptr ? SHADOWHOOK_ERRNO_OK : -1,
                             std::memory_order_release);
    
    LOG_INFO("Native module view inline hook status: %d", shadowhook_get_errno());
    
    if (module_hook != nullptr) {
        // ShadowHook leaves its patched code page writable. Restore the ELF code permissions.
        const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
        const auto address = reinterpret_cast<uintptr_t>(dl_iterate_phdr) & ~(page_size - 1);
    
        if (mprotect(reinterpret_cast<void *>(address), page_size, PROT_READ | PROT_EXEC) != 0)
            return false;
    }
    
    return module_hook != nullptr &&
           module_hook_status.load(std::memory_order_acquire) == SHADOWHOOK_ERRNO_OK;
}

int snapshot_process_file(const proc_file file) {
    const int request = requests.load(std::memory_order_acquire);
    if (request < 0) return -ENOSYS;
    
    std::array<int, 2> reply{};
    long result = signature_raw_syscall(__NR_pipe2, reinterpret_cast<long>(reply.data()),
                                        O_CLOEXEC, 0, 0, 0, 0);
    
    if (result < 0) return static_cast<int>(result);
    
    const snapshot_request message{reply[1], file};
    do {
        result = signature_raw_syscall(__NR_write, request, reinterpret_cast<long>(&message),
                                        sizeof(message), 0, 0, 0);
    } while (result == -EINTR);
    
    int snapshot = -EIO;
    
    if (result == sizeof(message)) {
        do {
            result = signature_raw_syscall(__NR_read, reply[0], reinterpret_cast<long>(&snapshot),
                                            sizeof(snapshot), 0, 0, 0);
        } while (result == -EINTR);
    
        if (result != sizeof(snapshot)) snapshot = result < 0 ? static_cast<int>(result) : -EIO;
    } else snapshot = result < 0 ? static_cast<int>(result) : -EIO;
    
    signature_raw_syscall(__NR_close, reply[0], 0, 0, 0, 0, 0);
    signature_raw_syscall(__NR_close, reply[1], 0, 0, 0, 0, 0);
    
    return snapshot;
}
