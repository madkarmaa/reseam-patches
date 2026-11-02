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
#include <sys/socket.h>
#include <sys/uio.h>
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
#include <limits>
#include <memory>
#include <span>
#include <string_view>
#include <type_traits>
#include <utility>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace {
    std::atomic<int> requests{-1};
    std::atomic<int> worker_socket{-1};
    static_assert(std::atomic<int>::is_always_lock_free);
    bool fork_handler_registered = false;

    void *module_hook = nullptr;
    std::atomic<int> module_hook_status{-1};
    using module_callback = int (*)(dl_phdr_info *, size_t, void *);
    using module_iterator = int (*)(module_callback, void *);

    module_iterator iterate_modules = nullptr;

    struct target_query { const char *name; };
    uintptr_t target_start = UINTPTR_MAX;
    uintptr_t target_end = 0;
    library_view_options target_options;

    unsigned original_no_new_privileges = 0;
    unsigned long long original_blocked_signals = 0;
    unsigned long long original_caught_signals = 0;

    // /proc/self/status separates each label from its value with spaces or a tab.
    bool is_space(const char character) {
        return character == ' ' || character == '\t';
    }

    // Reads one /proc/self/status field, whose label is followed by spaces or a tab.
    template<typename Value>
    bool read_field(const char *const line, const char *const label, Value &value) {
        const auto length = static_cast<int>(strlen(label));
        if (strncmp(line, label, static_cast<size_t>(length)) != 0) return false;

        const char *text = line + length;
        while (is_space(*text)) text++;

        char *end = nullptr;
        errno = 0;

        if constexpr (std::is_same_v<Value, unsigned>) {
            const unsigned long parsed = strtoul(text, &end, 10);
            if (end == text || errno != 0 || parsed > std::numeric_limits<unsigned>::max()) return false;

            value = static_cast<unsigned>(parsed);
            return true;
        } else {
            const unsigned long long parsed = strtoull(text, &end, 16);
            if (end == text || errno != 0) return false;

            value = parsed;
            return true;
        }
    }

    // Owns the getline buffer, which reallocates as lines grow.
    using line_storage = std::unique_ptr<char, decltype(&std::free)>;

    ssize_t next_line(FILE *const file, line_storage &storage, size_t &capacity) {
        char *line = storage.release();
        const ssize_t length = getline(&line, &capacity, file);
        storage.reset(line);
        return length;
    }

    bool capture_process_status() {
        const std::unique_ptr<FILE, decltype(&fclose)> status(fopen("/proc/self/status", "re"), fclose);
        if (!status) return false;

        line_storage line(nullptr, &std::free);
        size_t capacity = 0;
        unsigned found = 0;

        while (next_line(status.get(), line, capacity) >= 0) {
            if (read_field(line.get(), "NoNewPrivs:", original_no_new_privileges)) found |= 1U;
            if (read_field(line.get(), "SigBlk:", original_blocked_signals)) found |= 2U;
            if (read_field(line.get(), "SigCgt:", original_caught_signals)) found |= 4U;
        }

        return found == 7U && ferror(status.get()) == 0;
    }

    bool extension_library(const char *const path) {
        if (path == nullptr) return false;

        const char *const slash = strrchr(path, '/');
        std::string_view name(slash == nullptr ? path : slash + 1);

        if (name.ends_with('\n')) name.remove_suffix(1);
        if (name.ends_with(" (deleted)")) name.remove_suffix(sizeof(" (deleted)") - 1);

        return name == "libSignatureKiller.so" || name == "libshadowhook.so" ||
               name == "libshadowhook_nothing.so";
    }

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
    const module_iterator iterate_original_modules = [](module_callback callback,
                                                        void *const data) -> int {
        const auto address = reinterpret_cast<uintptr_t>(callback);
        const bool target = (address >= target_start && address < target_end) ||
                (target_options.contains_address != nullptr && target_options.contains_address(address));
        if (!target)
            return iterate_modules(callback, data);

        module_query query{.callback = callback, .data = data};
        return iterate_modules(original_module, &query);
    };

    struct address_range { uintptr_t start; uintptr_t end; };
    std::vector<address_range> hidden_ranges;

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
            const uintptr_t end = (image.dlpi_addr + segment.p_vaddr + segment.p_memsz +
                                   page_size - 1) & ~(page_size - 1);

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
            target_end = std::max<uintptr_t>(target_end, image.dlpi_addr + segment.p_vaddr + segment.p_memsz);
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
    const module_callback collect_extension_ranges = [](dl_phdr_info *const info, size_t /*size*/,
                                                        void *const data) {
        if (!extension_library(info->dlpi_name)) return 0;

        collect_image_range(*info, *static_cast<std::vector<address_range> *>(data));
        return 0;
    };

    // Rewrites a /proc/self/status field that reports this extension's own state.
    // Returns the length to write, or -1 when the line stays as the kernel printed it.
    ssize_t adjust_status_line(const char *const line, const std::span<char> adjusted,
                               const unsigned filters) {
        unsigned value = 0;
        if (read_field(line, "NoNewPrivs:", value))
            return snprintf(adjusted.data(), adjusted.size(), "NoNewPrivs:\t%u\n",
                            original_no_new_privileges);

        if (read_field(line, "Seccomp_filters:", value)) {
            return value < filters ? -1
                    : snprintf(adjusted.data(), adjusted.size(), "Seccomp_filters:\t%u\n", value - filters);
        }

        // The filter both blocks and catches SIGSYS, so restore the process's original bit.
        constexpr auto own_signal = 1ULL << static_cast<unsigned>(SIGSYS - 1);
        unsigned long long signals = 0;

        if (read_field(line, "SigBlk:", signals))
            return snprintf(adjusted.data(), adjusted.size(), "SigBlk:\t%016llx\n",
                            (signals & ~own_signal) | (original_blocked_signals & own_signal));

        if (read_field(line, "SigCgt:", signals))
            return snprintf(adjusted.data(), adjusted.size(), "SigCgt:\t%016llx\n",
                            (signals & ~own_signal) | (original_caught_signals & own_signal));

        return -1;
    }

    // ByteHook's trampoline pages are separate mappings of our library file, outside its ELF
    // image. Exclude those copies as well as the loader's module mappings.
    bool hidden_mapping(const char *const line, const ssize_t length) {
        uintptr_t start = 0;
        const auto [end, error] = std::from_chars(line, line + length, start, 16);
        if (error != std::errc{} || end >= line + length || *end != '-') return false;

        // Android's page-size compatibility output can retain an empty segment after
        // mprotect splits a code page. It names no memory and has no map_files entry.
        if (uintptr_t limit = 0;
            std::from_chars(end + 1, line + length, limit, 16).ec == std::errc{} && start == limit)
            return true;

        return std::ranges::any_of(hidden_ranges, [start](const address_range &range) {
            return start >= range.start && start < range.end;
        });
    }

    // Writes every byte of one snapshot line, resuming after interruptions.
    int write_all(const int descriptor, const std::span<const char> data) {
        size_t written = 0;
        while (written < data.size()) {
            const ssize_t count = write(descriptor, data.data() + written, data.size() - written);

            if (count < 0 && errno == EINTR) continue;
            if (count <= 0) return count < 0 ? errno : EIO;

            written += static_cast<size_t>(count);
        }

        return 0;
    }

    int create_proc_snapshot(const proc_file file, const pid_t process, const unsigned filters) {
        const bool is_maps = file == proc_file::maps;

        std::array<char, 64> path{};
        const int formatted = snprintf(path.data(), path.size(), "/proc/%ld/%s",
                                       static_cast<long>(process), is_maps ? "maps" : "status");
        if (formatted <= 0 || !std::cmp_less(formatted, path.size())) return -EINVAL;

        const std::unique_ptr<FILE, decltype(&fclose)> maps(
                fopen(path.data(), "re"), fclose);

        if (!maps) return -errno;

        const int result = static_cast<int>(syscall(__NR_memfd_create, is_maps ? "maps" : "status", MFD_CLOEXEC));
        if (result < 0) return -errno;

        line_storage line(nullptr, &std::free);
        size_t capacity = 0;
        int failure = 0;
        unsigned excluded_lines = 0;

        ssize_t length = next_line(maps.get(), line, capacity);
        while (length >= 0) {
            if (is_maps && (extension_library(line.get()) || hidden_mapping(line.get(), length))) {
                excluded_lines++;
                length = next_line(maps.get(), line, capacity);
                continue;
            }

            std::array<char, 80> adjusted{};
            if (const ssize_t adjusted_length = is_maps ? -1 : adjust_status_line(line.get(), adjusted, filters);
                adjusted_length < 0)
                failure = write_all(result, {line.get(), static_cast<size_t>(length)});
            else
                failure = write_all(result, {adjusted.data(),
                                             std::min(static_cast<size_t>(adjusted_length), adjusted.size())});

            if (failure != 0) break;

            length = next_line(maps.get(), line, capacity);
        }

        if (failure == 0 && ferror(maps.get()) != 0) failure = EIO;
        if (failure == 0) {
            if (is_maps)
                LOG_DEBUG("Maps snapshot excludes %u extension or empty mappings in %zu images",
                          excluded_lines, hidden_ranges.size());
            return result;
        }

        close(result);
        return -failure;
    }

    struct snapshot_request { pid_t process; unsigned filters; proc_file file; };

    void close_raw(const int descriptor) {
        if (descriptor >= 0)
            signature_raw_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0);
    }

    // A child keeps only the request client. Otherwise its inherited server endpoint could
    // keep a dead parent's channel open forever, even though that child has no worker thread.
    void close_inherited_worker() {
        close_raw(worker_socket.exchange(-1, std::memory_order_relaxed));
    }

    // Each packet transfers at most one descriptor. The kernel consumes these bounded stack
    // buffers directly, including when the caller is running on the SIGSYS signal stack.
    long send_packet(const int socket, void *const payload, const size_t size, const int descriptor) {
        iovec data{.iov_base = payload, .iov_len = size};
        alignas(cmsghdr) std::array<unsigned char, CMSG_SPACE(sizeof(int))> control{};
        msghdr message{};
        message.msg_iov = &data;
        message.msg_iovlen = 1;

        if (descriptor >= 0) {
            message.msg_control = control.data();
            message.msg_controllen = control.size();
            cmsghdr *const header = CMSG_FIRSTHDR(&message);
            header->cmsg_level = SOL_SOCKET;
            header->cmsg_type = SCM_RIGHTS;
            header->cmsg_len = CMSG_LEN(sizeof(int));
            *reinterpret_cast<int *>(CMSG_DATA(header)) = descriptor;
        }

        long result = signature_raw_syscall(__NR_sendmsg, socket, reinterpret_cast<long>(&message),
                                             MSG_NOSIGNAL, 0, 0, 0);
        while (result == -EINTR)
            result = signature_raw_syscall(__NR_sendmsg, socket, reinterpret_cast<long>(&message),
                                           MSG_NOSIGNAL, 0, 0, 0);
        return result;
    }

    struct received_packet { long length; int descriptor; bool valid; };

    received_packet receive_packet(const int socket, void *const payload, const size_t size) {
        iovec data{.iov_base = payload, .iov_len = size};
        alignas(cmsghdr) std::array<unsigned char, CMSG_SPACE(sizeof(int))> control{};
        msghdr message{};
        message.msg_iov = &data;
        message.msg_iovlen = 1;
        message.msg_control = control.data();
        message.msg_controllen = control.size();

        long length = signature_raw_syscall(__NR_recvmsg, socket, reinterpret_cast<long>(&message),
                                             MSG_CMSG_CLOEXEC, 0, 0, 0);
        while (length == -EINTR) {
            message.msg_controllen = control.size();
            message.msg_flags = 0;
            length = signature_raw_syscall(__NR_recvmsg, socket, reinterpret_cast<long>(&message),
                                           MSG_CMSG_CLOEXEC, 0, 0, 0);
        }

        int descriptor = -1;
        unsigned descriptors = 0;
        bool valid = length >= 0 &&
                (static_cast<unsigned>(message.msg_flags) &
                 static_cast<unsigned>(MSG_TRUNC | MSG_CTRUNC)) == 0;

        // This control buffer has room for one cmsghdr only. On LP64 its alignment padding
        // can still contain a second int, so close every received FD before rejecting extras.
        if (const cmsghdr *const header = CMSG_FIRSTHDR(&message); header != nullptr) {
            if (const bool header_valid = CMSG_OK(&message, header);
                header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS ||
                !header_valid) {
                valid = false;
            } else {
                const size_t bytes = header->cmsg_len - CMSG_LEN(0);
                if (bytes % sizeof(int) != 0) valid = false;
                const std::span<const int> transferred(
                        reinterpret_cast<const int *>(CMSG_DATA(header)), bytes / sizeof(int));
                for (const int received : transferred) {
                    if (descriptors++ == 0) descriptor = received;
                    else close_raw(received);
                }
            }
        }

        if (descriptors > 1) valid = false;
        if (!valid) {
            close_raw(descriptor);
            descriptor = -1;
        }
        return {.length = length, .descriptor = descriptor, .valid = valid};
    }

    void *serve_snapshots(const int descriptor) {
        if (target_options.enter_worker != nullptr) target_options.enter_worker();

        while (true) {
            snapshot_request request{};
            const auto [received_length, reply_socket, valid] = receive_packet(descriptor, &request, sizeof(request));
            if (received_length <= 0) {
                close_raw(reply_socket);
                break;
            }
            if (!valid || reply_socket < 0) continue;

            int snapshot = -EINVAL;
            if (std::cmp_equal(received_length, sizeof(request)) && request.process > 0 &&
                (request.file == proc_file::maps || request.file == proc_file::status))
                snapshot = create_proc_snapshot(request.file, request.process, request.filters);

            int error = snapshot < 0 ? snapshot : 0;
            static_cast<void>(send_packet(reply_socket, &error, sizeof(error), snapshot));
            close_raw(snapshot);
            close_raw(reply_socket);
        }

        // Shutdown reaches every remaining inherited client even if an unexpected receive
        // error ends the worker. A waiter then observes EOF instead of waiting indefinitely.
        signature_raw_syscall(__NR_shutdown, descriptor, SHUT_RDWR, 0, 0, 0, 0);
        worker_socket.store(-1, std::memory_order_relaxed);
        close_raw(descriptor);
        return nullptr;
    }
}

bool prepare_library_view(const char *const library, const library_view_options &options) {
    if (module_hook != nullptr)
        return module_hook_status.load(std::memory_order_acquire) == SHADOWHOOK_ERRNO_OK;

    if (!capture_process_status()) return false;
    target_options = options;

    target_query query{.name = library};
    dl_iterate_phdr(find_target_module, &query);

    if (target_start >= target_end) return false;
    if (shadowhook_init(SHADOWHOOK_MODE_SHARED, false) != SHADOWHOOK_ERRNO_OK) return false;

    // Capture on the installing thread. A constructor may hold the linker lock while it waits
    // for a snapshot, so the worker must never call back into the loader.
    dl_iterate_phdr(collect_extension_ranges, &hidden_ranges);

    std::array<int, 2> sockets{};
    if (socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0, sockets.data()) != 0) return false;

    if (!fork_handler_registered) {
        if (pthread_atfork(nullptr, nullptr, close_inherited_worker) != 0) {
            close(sockets.front()); close(sockets.back());
            return false;
        }
        fork_handler_registered = true;
    }
    worker_socket.store(sockets.front(), std::memory_order_relaxed);

    pthread_t worker{};
    if (pthread_create(&worker, nullptr, [](void *const descriptor_argument) -> void * {
            return serve_snapshots(static_cast<int>(reinterpret_cast<intptr_t>(descriptor_argument)));
        }, reinterpret_cast<void *>(static_cast<intptr_t>(sockets.front()))) != 0) {
        worker_socket.store(-1, std::memory_order_relaxed);
        close(sockets.front()); close(sockets.back());
        return false;
    }

    pthread_detach(worker);

    requests.store(sockets.back(), std::memory_order_release);

    module_hook = shadowhook_hook_func_addr_2(reinterpret_cast<void *>(dl_iterate_phdr),
            reinterpret_cast<void *>(iterate_original_modules), reinterpret_cast<void **>(&iterate_modules),
            SHADOWHOOK_HOOK_WITH_UNIQUE_MODE);

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

int snapshot_process_file(const proc_file file, const int requested_process) {
    const int request = requests.load(std::memory_order_acquire);
    if (request < 0) return -ENOSYS;
    std::array<int, 2> reply{};
    long result = signature_raw_syscall(__NR_socketpair, AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC,
                                        0, reinterpret_cast<long>(reply.data()), 0, 0);

    if (result < 0) return static_cast<int>(result);

    const long process = requested_process > 0 ? requested_process :
            signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
    const unsigned filters = target_options.filter_count == nullptr ? 1 : target_options.filter_count();
    snapshot_request message{.process = static_cast<pid_t>(process), .filters = filters, .file = file};
    result = send_packet(request, &message, sizeof(message), reply.back());
    // Once sent, the worker owns a transferred copy of this endpoint. Keeping our own copy
    // would hide worker failure from recvmsg on the other end.
    close_raw(reply.back());

    if (!std::cmp_equal(result, sizeof(message))) {
        close_raw(reply.front());
        return result < 0 ? static_cast<int>(result) : -EIO;
    }

    int error = -EIO;
    const auto [received_length, snapshot, valid] = receive_packet(reply.front(), &error, sizeof(error));
    close_raw(reply.front());

    if (received_length < 0) return static_cast<int>(received_length);
    if (!valid || !std::cmp_equal(received_length, sizeof(error))) {
        close_raw(snapshot);
        return -EIO;
    }
    if (error == 0 && snapshot >= 0) return snapshot;

    close_raw(snapshot);
    return error < 0 && snapshot < 0 ? error : -EIO;
}
