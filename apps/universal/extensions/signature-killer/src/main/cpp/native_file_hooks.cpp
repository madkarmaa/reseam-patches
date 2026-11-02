// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "native_file_hooks.h"
#include "seccomp_open.h"
#include "logging.h"
#include "shadowhook.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <pthread.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>

#include <array>
#include <algorithm>
#include <atomic>
#include <cerrno>
#include <charconv>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <limits>
#include <memory>
#include <mutex>
#include <optional>
#include <type_traits>
#include <utility>
#include <string_view>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace {
    constinit std::mutex hooks_mutex;
    bool installed = false;
    constinit std::once_flag thread_context_once;
    std::atomic<bool> thread_context_ready{false};
    pthread_key_t stdio_caller_key{};
    // Real code addresses cannot be this sentinel.
    const auto internal_worker = reinterpret_cast<void *>(uintptr_t{1});
    const auto foreign_stdio = reinterpret_cast<void *>(uintptr_t{2});

    void prepare_thread_context() {
        std::call_once(thread_context_once, [] {
            if (pthread_key_create(&stdio_caller_key, nullptr) != 0) abort();
            thread_context_ready.store(true, std::memory_order_release);
        });
    }
    struct allocation_range { uintptr_t start; uintptr_t end; };
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
        target_allocations.push_back({.start = start,
                                      .end = (start + size + page_size - 1) & ~(page_size - 1),
        });
    }

    // A target's stdio operation can enter read/open/lseek from inside libc. Keep that
    // operation's owner while libc handles its FILE buffer and validates its mode string.
    // Android 21's emulated C++ TLS allocates on first access, which would recurse through
    // the mmap hook while malloc is starting up. Bionic's pthread key lookup does not allocate.
    uintptr_t effective_caller(const uintptr_t direct) {
        const void *const context = pthread_getspecific(stdio_caller_key);
        if (context == internal_worker || context == foreign_stdio) return 0;
        if (context != nullptr) return reinterpret_cast<uintptr_t>(context);
        return direct;
    }

    class stdio_scope {
    public:
        explicit stdio_scope(const uintptr_t direct) : previous(pthread_getspecific(stdio_caller_key)) {
            // The outer stdio operation owns nested libc work, including protector wrappers.
            if (previous != nullptr) return;
            if (pthread_setspecific(stdio_caller_key, raw_redirect_contains_address(direct)
                    ? reinterpret_cast<void *>(direct) : foreign_stdio) != 0)
                abort();
        }

        ~stdio_scope() {
            if (pthread_setspecific(stdio_caller_key, previous) != 0) abort();
        }
        stdio_scope(const stdio_scope &) = delete;
        stdio_scope &operator=(const stdio_scope &) = delete;
        stdio_scope(stdio_scope &&) = delete;
        stdio_scope &operator=(stdio_scope &&) = delete;

    private:
        void *previous;
    };

    // This must expand inside the proxy itself, before any helper changes the stack frame.
#define NATIVE_CALLER reinterpret_cast<uintptr_t>(__builtin_return_address(0))

    template<typename Result>
    Result libc_result(const long result, const int previous_errno) {
        const bool failed = result < 0 && result >= -4095;
        errno = failed ? static_cast<int>(-result) : previous_errno;
        if constexpr (std::is_pointer_v<Result>)
            return failed ? reinterpret_cast<Result>(-1) : reinterpret_cast<Result>(result);
        else
            return failed ? static_cast<Result>(-1) : static_cast<Result>(result);
    }

    long redirect(const uintptr_t caller, const int number,
                                const long first, const long second = 0, const long third = 0,
                                const long fourth = 0, const long fifth = 0, const long sixth = 0) {
        if (const auto result = redirect_native_file_syscall(effective_caller(caller), number,
                first, second, third, fourth, fifth, sixth); result)
            return *result;

        // The protector can replace libc's prologue after our hook. A relocated trampoline
        // would then return into the middle of its new branch; replay the syscall instead.
        return signature_raw_syscall(number, first, second, third, fourth, fifth, sixth);
    }

    void retain_executable_mapping(const uintptr_t caller, const void *const address,
                                   const size_t size, const int protection) {
        if ((static_cast<unsigned>(protection) & static_cast<unsigned>(PROT_EXEC)) == 0 ||
            size == 0 || native_file_hooks_bypassed() || !raw_redirect_contains_address(caller))
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

    using open_function = int (*)(const char *, int, ...);
    using openat_function = int (*)(int, const char *, int, ...);
    open_function original_open = nullptr;
    open_function original_open64 = nullptr;
    openat_function original_openat = nullptr;
    openat_function original_openat64 = nullptr;

    bool needs_mode(const int flags) {
        const auto bits = static_cast<unsigned>(flags);
        return (bits & static_cast<unsigned>(O_CREAT)) != 0 ||
               (bits & static_cast<unsigned>(O_TMPFILE)) == static_cast<unsigned>(O_TMPFILE);
    }

    int open_flags(const int flags) {
#ifdef __arm__
        return static_cast<int>(static_cast<unsigned>(flags) | static_cast<unsigned>(O_LARGEFILE));
#else
        return flags;
#endif
    }

    template<open_function &Original>
    int open_proxy(const char *const path, const int flags, ...) {
        mode_t mode = 0;
        if (needs_mode(flags)) {
            va_list arguments{};
            va_start(arguments, flags);
            mode = static_cast<mode_t>(va_arg(arguments, int));
            va_end(arguments);
        }
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_openat, AT_FDCWD,
                                     reinterpret_cast<long>(path), open_flags(flags), mode);
        return libc_result<int>(result, previous_errno);
    }

    template<openat_function &Original>
    int openat_proxy(const int directory, const char *const path, const int flags, ...) {
        mode_t mode = 0;
        if (needs_mode(flags)) {
            va_list arguments{};
            va_start(arguments, flags);
            mode = static_cast<mode_t>(va_arg(arguments, int));
            va_end(arguments);
        }
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_openat, directory,
                                     reinterpret_cast<long>(path), open_flags(flags), mode);
        return libc_result<int>(result, previous_errno);
    }

    int (*original_open_2)(const char *, int) = nullptr;
    int (*original_openat_2)(int, const char *, int) = nullptr;

    int open_2_proxy(const char *const path, const int flags) {
        if (needs_mode(flags)) abort();
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_openat, AT_FDCWD,
                                     reinterpret_cast<long>(path), open_flags(flags));
        return libc_result<int>(result, previous_errno);
    }

    int openat_2_proxy(const int directory, const char *const path, const int flags) {
        if (needs_mode(flags)) abort();
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_openat, directory,
                                     reinterpret_cast<long>(path), open_flags(flags));
        return libc_result<int>(result, previous_errno);
    }

    ssize_t (*original_read)(int, void *, size_t) = nullptr;
    int (*original_close)(int) = nullptr;
    int (*close_with_tag)(int, uint64_t) = nullptr;
    void (*exchange_owner_tag)(int, uint64_t, uint64_t) = nullptr;

    // The libc ABI requires a mutable output buffer, which the raw syscall fills.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    ssize_t read_proxy(const int descriptor, void *const buffer, const size_t count) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_read, descriptor, reinterpret_cast<long>(buffer),
                                     static_cast<long>(count));
        return libc_result<ssize_t>(result, previous_errno);
    }

    int close_proxy(const int descriptor) {
        const int previous_errno = errno;
        const auto caller = effective_caller(NATIVE_CALLER);

        // Keep the descriptor ownership check for target descriptors, whose close also releases any
        // proc snapshot tracked by the redirect. These exports are not our hook trampolines.
        if (exchange_owner_tag != nullptr && raw_redirect_contains_address(caller))
            exchange_owner_tag(descriptor, 0, 0);

        const auto redirected = redirect_native_file_syscall(caller, __NR_close, descriptor, 0, 0, 0, 0, 0);
        int closed = 0;
        if (redirected) closed = libc_result<int>(*redirected, previous_errno);
        else if (close_with_tag != nullptr) closed = close_with_tag(descriptor, 0);
        else closed = libc_result<int>(signature_raw_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0), previous_errno);
        return closed < 0 && errno == EINTR ? 0 : closed;
    }

    using pread_function = ssize_t (*)(int, void *, size_t, off_t);
    using pread64_function = ssize_t (*)(int, void *, size_t, off64_t);
    pread_function original_pread = nullptr;
    pread64_function original_pread64 = nullptr;

    template<typename Offset, auto &Original>
    ssize_t pread_proxy(const int descriptor, void *const buffer, const size_t count, const Offset offset) {
        const int previous_errno = errno;
#ifdef __arm__
        const auto position = static_cast<uint64_t>(static_cast<int64_t>(offset));
        const auto result = redirect(NATIVE_CALLER, __NR_pread64, descriptor, reinterpret_cast<long>(buffer),
                static_cast<long>(count), 0, static_cast<long>(static_cast<uint32_t>(position)),
                static_cast<long>(static_cast<uint32_t>(position >> 32U)));
#else
        const auto result = redirect(NATIVE_CALLER, __NR_pread64, descriptor, reinterpret_cast<long>(buffer),
                                     static_cast<long>(count), static_cast<long>(offset));
#endif
        return libc_result<ssize_t>(result, previous_errno);
    }

    off_t (*original_lseek)(int, off_t, int) = nullptr;
    off64_t (*original_lseek64)(int, off64_t, int) = nullptr;

    off_t lseek_proxy(const int descriptor, const off_t offset, const int whence) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_lseek, descriptor, static_cast<long>(offset), whence);
        return libc_result<off_t>(result, previous_errno);
    }

    off64_t lseek64_proxy(const int descriptor, const off64_t offset, const int whence) {
        const int previous_errno = errno;
#ifdef __arm__
        const auto position = static_cast<uint64_t>(offset);
        off64_t adjusted = 0;
        const auto result = redirect(NATIVE_CALLER, __NR__llseek, descriptor,
                static_cast<long>(static_cast<uint32_t>(position >> 32U)),
                static_cast<long>(static_cast<uint32_t>(position)), reinterpret_cast<long>(&adjusted), whence);
        return libc_result<int>(result, previous_errno) == 0 ? adjusted : static_cast<off64_t>(-1);
#else
        const auto result = redirect(NATIVE_CALLER, __NR_lseek, descriptor, static_cast<long>(offset), whence);
        return libc_result<off64_t>(result, previous_errno);
#endif
    }

#ifdef __arm__
    constexpr int stat_number = __NR_fstat64;
    constexpr int stat_at_number = __NR_fstatat64;
#else
    constexpr int stat_number = __NR_fstat;
    constexpr int stat_at_number = __NR_newfstatat;
#endif

    template<typename Metadata>
    using fstat_function = int (*)(int, Metadata *);
    template<typename Metadata>
    using fstatat_function = int (*)(int, const char *, Metadata *, int);
    template<typename Metadata>
    using stat_function = int (*)(const char *, Metadata *);
    fstat_function<struct stat> original_fstat = nullptr;
    fstat_function<struct stat64> original_fstat64 = nullptr;
    fstatat_function<struct stat> original_fstatat = nullptr;
    fstatat_function<struct stat64> original_fstatat64 = nullptr;
    stat_function<struct stat> original_stat = nullptr;
    stat_function<struct stat64> original_stat64 = nullptr;
    stat_function<struct stat> original_lstat = nullptr;
    stat_function<struct stat64> original_lstat64 = nullptr;

    template<typename Metadata, auto &Original>
    int fstat_proxy(const int descriptor, Metadata *const value) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, stat_number, descriptor, reinterpret_cast<long>(value));
        return libc_result<int>(result, previous_errno);
    }

    template<typename Metadata, auto &Original>
    int fstatat_proxy(const int directory, const char *const path, Metadata *const value, const int flags) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, stat_at_number, directory, reinterpret_cast<long>(path),
                                     reinterpret_cast<long>(value), flags);
        return libc_result<int>(result, previous_errno);
    }

    template<typename Metadata, auto &Original, int Flags>
    int stat_proxy(const char *const path, Metadata *const value) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, stat_at_number, AT_FDCWD, reinterpret_cast<long>(path),
                                     reinterpret_cast<long>(value), Flags);
        return libc_result<int>(result, previous_errno);
    }

    ssize_t (*original_readlink)(const char *, char *, size_t) = nullptr;
    ssize_t (*original_readlinkat)(int, const char *, char *, size_t) = nullptr;

    // The kernel writes through this address, despite its integer syscall representation.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    ssize_t readlink_proxy(const char *const path, char *const buffer, const size_t size) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_readlinkat, AT_FDCWD, reinterpret_cast<long>(path),
                                     reinterpret_cast<long>(buffer), static_cast<long>(size));
        return libc_result<ssize_t>(result, previous_errno);
    }

    // The kernel writes through this address, despite its integer syscall representation.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    ssize_t readlinkat_proxy(const int directory, const char *const path, char *const buffer, const size_t size) {
        const int previous_errno = errno;
        const auto result = redirect(NATIVE_CALLER, __NR_readlinkat, directory, reinterpret_cast<long>(path),
                                     reinterpret_cast<long>(buffer), static_cast<long>(size));
        return libc_result<ssize_t>(result, previous_errno);
    }

    void intercept_libc_forwarders(const void *address, size_t size, int protection);

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
        const int result = libc_result<int>(signature_raw_syscall(__NR_mprotect,
                reinterpret_cast<long>(address), static_cast<long>(size), protection, 0, 0, 0), previous_errno);
        const int result_errno = errno;
        if (result == 0) {
            retain_executable_mapping(caller, address, size, protection);
            intercept_libc_forwarders(address, size, protection);
        }
        errno = result_errno;
        return result;
    }

    template<typename Offset, auto &Original>
    void *mmap_proxy(void *const address, const size_t size, const int protection,
                    const int flags, const int descriptor, const Offset offset) {
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
        const auto redirected = redirect(caller, __NR_mmap, reinterpret_cast<long>(address),
                static_cast<long>(size), protection, flags, descriptor, static_cast<long>(offset));
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

    using fopen_function = FILE *(*)(const char *, const char *);
    fopen_function original_fopen = nullptr;
    fopen_function original_fopen64 = nullptr;
    size_t (*original_fread)(void *, size_t, size_t, FILE *) = nullptr;
    char *(*original_fgets)(char *, int, FILE *) = nullptr;
    int (*original_fgetc)(FILE *) = nullptr;
    int (*original_getc)(FILE *) = nullptr;
    ssize_t (*original_getline)(char **, size_t *, FILE *) = nullptr;
    ssize_t (*original_getdelim)(char **, size_t *, int, FILE *) = nullptr;
    int (*original_fseek)(FILE *, long, int) = nullptr;
    int (*original_fseeko)(FILE *, off_t, int) = nullptr;
    int (*original_fseeko64)(FILE *, off64_t, int) = nullptr;
    int (*original_fclose)(FILE *) = nullptr;

    template<fopen_function &Original>
    FILE *fopen_proxy(const char *const path, const char *const mode) {
        const stdio_scope owner(NATIVE_CALLER);
        return Original(path, mode);
    }

    size_t fread_proxy(void *const buffer, const size_t size, const size_t count, FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return original_fread(buffer, size, count, stream);
    }

    char *fgets_proxy(char *const buffer, const int count, FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return original_fgets(buffer, count, stream);
    }

    template<auto &Original>
    int fgetc_proxy(FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return Original(stream);
    }

    ssize_t getline_proxy(char **const line, size_t *const capacity, FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return original_getline(line, capacity, stream);
    }

    ssize_t getdelim_proxy(char **const line, size_t *const capacity, const int delimiter, FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return original_getdelim(line, capacity, delimiter, stream);
    }

    template<typename Offset, auto &Original>
    int fseek_proxy(FILE *const stream, const Offset offset, const int whence) {
        const stdio_scope owner(NATIVE_CALLER);
        return Original(stream, offset, whence);
    }

    int fclose_proxy(FILE *const stream) {
        const stdio_scope owner(NATIVE_CALLER);
        return original_fclose(stream);
    }

#undef NATIVE_CALLER

    struct resolved_hook {
        const char *name;
        uintptr_t address;
        void *proxy;
    };
    // Published once all libc entry hooks exist. mprotect may enter during their installation.
    std::vector<resolved_hook> resolved_hooks;
    std::atomic<bool> forwarders_ready{false};

#ifdef __aarch64__
    std::atomic_flag intercepting_forwarders = ATOMIC_FLAG_INIT;
    std::vector<resolved_hook> intercepted_forwarders;

    bool read_code(const uintptr_t address, void *const buffer, const size_t size) {
        const iovec local{.iov_base = buffer, .iov_len = size};
        const iovec remote{.iov_base = reinterpret_cast<void *>(address), .iov_len = size};
        const long process = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        for (;;) {
            const long result = signature_raw_syscall(__NR_process_vm_readv, process,
                    reinterpret_cast<long>(&local), 1, reinterpret_cast<long>(&remote), 1, 0);
            if (result == -EINTR) continue;
            return std::cmp_equal(result, size);
        }
    }

    struct forwarding_branch { uintptr_t destination = 0; uintptr_t literal = 0; };

    forwarding_branch forwarding_destination(const uintptr_t entry) {
        std::array<uint32_t, 4> instructions{};
        if (!read_code(entry, instructions.data(), sizeof(instructions))) return {};
        constexpr auto branch_targets = std::to_array<uint32_t>({
            0xd503241fU, 0xd503245fU, 0xd503249fU, 0xd50324dfU,
        });
        const size_t index = std::ranges::contains(branch_targets, instructions.front()) ? 1 : 0;
        const auto position = entry + (index * sizeof(uint32_t));
        const uint32_t instruction = instructions.at(index);
        // B preserves the original LR. BL would make the wrapper itself the caller.
        if ((instruction & 0xfc000000U) == 0x14000000U) {
            int64_t displacement = instruction & 0x03ffffffU;
            if ((instruction & 0x02000000U) != 0) displacement -= int64_t{0x4000000};
            return {.destination = position + static_cast<uintptr_t>(displacement * 4)};
        }
        // Accept only a 64-bit literal load followed by BR through that same register.
        if (const uint32_t branch = instructions.at(index + 1); (instruction & 0xff000000U) != 0x58000000U ||
            (branch & 0xfffffc1fU) != 0xd61f0000U ||
            (instruction & 31U) == 31U || (instruction & 31U) != ((branch >> 5U) & 31U))
            return {};
        int64_t displacement = (instruction >> 5U) & 0x7ffffU;
        if ((instruction & 0x00800000U) != 0) displacement -= int64_t{0x80000};
        uintptr_t destination = 0;
        if (!read_code(position + static_cast<uintptr_t>(displacement * 4),
                       &destination, sizeof(destination)))
            return {};
        return {.destination = destination,
                .literal = position + static_cast<uintptr_t>(displacement * 4),
        };
    }

    bool retarget_literal(const uintptr_t literal, const void *const proxy) {
        if (literal % alignof(uintptr_t) != 0) return false;
        const std::unique_ptr<FILE, decltype(&fclose)> maps(fopen("/proc/self/maps", "re"), fclose);
        if (!maps) return false;
        std::array<char, 4096> line{};
        unsigned protection = 0;
        bool found = false;
        while (fgets(line.data(), static_cast<int>(line.size()), maps.get()) != nullptr) {
            const std::string_view text(line.data());
            const auto hyphen = text.find('-');
            const auto space = text.find(' ');
            if (hyphen == std::string_view::npos || space == std::string_view::npos ||
                hyphen >= space || text.size() - space < 5)
                continue;
            uintptr_t first = 0;
            uintptr_t last = 0;
            const auto [beginning, beginning_error] = std::from_chars(line.data(), line.data() + hyphen, first, 16);
            if (const auto [ending, ending_error] = std::from_chars(line.data() + hyphen + 1, line.data() + space, last, 16);
                beginning_error != std::errc{} || beginning != line.data() + hyphen ||
                ending_error != std::errc{} || ending != line.data() + space ||
                literal < first || literal >= last)
                continue;
            const auto permissions = text.substr(space + 1, 4);
            if (permissions.at(0) == 'r') protection |= static_cast<unsigned>(PROT_READ);
            if (permissions.at(1) == 'w') protection |= static_cast<unsigned>(PROT_WRITE);
            if (permissions.at(2) == 'x') protection |= static_cast<unsigned>(PROT_EXEC);
            found = true;
            break;
        }
        if (!found) return false;
        const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
        const auto page = literal & ~(page_size - 1);
        const bool writable = (protection & static_cast<unsigned>(PROT_WRITE)) != 0;
        if (!writable && signature_raw_syscall(__NR_mprotect, static_cast<long>(page),
                static_cast<long>(page_size), protection | static_cast<unsigned>(PROT_WRITE), 0, 0, 0) != 0)
            return false;
        // The branch instructions are unchanged. An aligned atomic pointer update cannot
        // expose a partially written destination to a concurrent libc caller.
        static_assert(std::atomic_ref<uintptr_t>::is_always_lock_free);
        std::atomic_ref(*reinterpret_cast<uintptr_t *>(literal)).store(
                reinterpret_cast<uintptr_t>(proxy), std::memory_order_release);
        return writable || signature_raw_syscall(__NR_mprotect, static_cast<long>(page),
                static_cast<long>(page_size), protection, 0, 0, 0) == 0;
    }

    std::string_view operation_name(const char *const name) {
        std::string_view operation(name);
        if (operation.ends_with("64")) operation.remove_suffix(2);
        return operation;
    }
#endif

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
            const auto previous = std::ranges::find(intercepted_forwarders, destination,
                                                    &resolved_hook::address);
            if (previous != intercepted_forwarders.end()) {
                if (operation_name(previous->name) == operation_name(name)) continue;
                LOG_ERROR("Native libc %s and %s share an incompatible forwarding entry %p",
                          previous->name, name, reinterpret_cast<void *>(destination));
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
            intercepted_forwarders.push_back({.name = name, .address = destination,
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

    struct libc_hook {
        const char *name;
        void *proxy;
        void **original;
        bool intercept_forwarder;
    };

    template<typename Function>
    libc_hook hook(const char *const name, Function proxy, Function &original,
                   const bool intercept_forwarder = true) {
        return {.name = name, .proxy = reinterpret_cast<void *>(proxy),
                .original = reinterpret_cast<void **>(&original),
                .intercept_forwarder = intercept_forwarder,
        };
    }
} // namespace

bool prepare_native_file_hooks() {
    const std::scoped_lock lock(hooks_mutex);
    if (installed) return true;
    prepare_thread_context();

    const auto hooks = std::to_array({
        hook("mprotect", mprotect_proxy, original_mprotect),
        hook("mmap", mmap_proxy<off_t, original_mmap>, original_mmap),
        hook("mmap64", mmap_proxy<off64_t, original_mmap64>, original_mmap64),
        hook("open", open_proxy<original_open>, original_open),
        hook("open64", open_proxy<original_open64>, original_open64),
        hook("openat", openat_proxy<original_openat>, original_openat),
        hook("openat64", openat_proxy<original_openat64>, original_openat64),
        hook("__open_2", open_2_proxy, original_open_2),
        hook("__openat_2", openat_2_proxy, original_openat_2),
        hook("read", read_proxy, original_read),
        hook("pread", pread_proxy<off_t, original_pread>, original_pread),
        hook("pread64", pread_proxy<off64_t, original_pread64>, original_pread64),
        hook("close", close_proxy, original_close),
        hook("lseek", lseek_proxy, original_lseek),
        hook("lseek64", lseek64_proxy, original_lseek64),
        hook("fstat", fstat_proxy<struct stat, original_fstat>, original_fstat),
        hook("fstat64", fstat_proxy<struct stat64, original_fstat64>, original_fstat64),
        hook("fstatat", fstatat_proxy<struct stat, original_fstatat>, original_fstatat),
        hook("fstatat64", fstatat_proxy<struct stat64, original_fstatat64>, original_fstatat64),
        hook("stat", stat_proxy<struct stat, original_stat, 0>, original_stat),
        hook("stat64", stat_proxy<struct stat64, original_stat64, 0>, original_stat64),
        hook("lstat", stat_proxy<struct stat, original_lstat, AT_SYMLINK_NOFOLLOW>, original_lstat),
        hook("lstat64", stat_proxy<struct stat64, original_lstat64, AT_SYMLINK_NOFOLLOW>, original_lstat64),
        hook("readlink", readlink_proxy, original_readlink),
        hook("readlinkat", readlinkat_proxy, original_readlinkat),
        hook("fopen", fopen_proxy<original_fopen>, original_fopen, false),
        hook("fopen64", fopen_proxy<original_fopen64>, original_fopen64, false),
        hook("fread", fread_proxy, original_fread, false),
        hook("fgets", fgets_proxy, original_fgets, false),
        hook("fgetc", fgetc_proxy<original_fgetc>, original_fgetc, false),
        hook("getc", fgetc_proxy<original_getc>, original_getc, false),
        hook("getline", getline_proxy, original_getline, false),
        hook("getdelim", getdelim_proxy, original_getdelim, false),
        hook("fseek", fseek_proxy<long, original_fseek>, original_fseek, false),
        hook("fseeko", fseek_proxy<off_t, original_fseeko>, original_fseeko, false),
        hook("fseeko64", fseek_proxy<off64_t, original_fseeko64>, original_fseeko64, false),
        hook("fclose", fclose_proxy, original_fclose, false),
    });

    void *const libc = dlopen("libc.so", RTLD_NOW | RTLD_LOCAL);
    if (libc == nullptr) return false;

    // FDSAN is absent on older Android releases. Resolve it before installing close's hook.
    close_with_tag = reinterpret_cast<decltype(close_with_tag)>(dlsym(libc, "android_fdsan_close_with_tag"));
    exchange_owner_tag = reinterpret_cast<decltype(exchange_owner_tag)>(
            dlsym(libc, "android_fdsan_exchange_owner_tag"));

    resolved_hooks.reserve(hooks.size());
    std::vector<void *> hooked_addresses;
    hooked_addresses.reserve(hooks.size());
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

        if (shadowhook_hook_func_addr_2(address, proxy, original,
                                        SHADOWHOOK_HOOK_WITH_UNIQUE_MODE) == nullptr) {
            LOG_ERROR("Cannot hook native libc %s: %d", name, shadowhook_get_errno());
            dlclose(libc);
            return false;
        }
        hooked_addresses.push_back(address);
        if (intercept_forwarder)
            resolved_hooks.push_back({.name = name, .address = reinterpret_cast<uintptr_t>(address),
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

void bypass_native_file_hooks_for_thread() {
    prepare_thread_context();
    if (pthread_setspecific(stdio_caller_key, internal_worker) != 0) abort();
}

bool native_file_hooks_bypassed() {
    if (!thread_context_ready.load(std::memory_order_acquire)) return false;
    const void *const context = pthread_getspecific(stdio_caller_key);
    return context == internal_worker || context == foreign_stdio;
}
