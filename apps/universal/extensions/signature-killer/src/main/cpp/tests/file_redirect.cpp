// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#include "kernel_stat.h"
#include "logging.h"
#include "seccomp_open.h"

#include <fcntl.h>
#include <link.h>
#include <linux/memfd.h>
#include <sys/auxv.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

// Keep checks active in Release builds as well.
#ifdef NDEBUG
#undef NDEBUG
#endif

#include <array>
#include <atomic>
#include <cassert>
#include <cerrno>
#include <csignal>
#include <cstdarg>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <utility>
#include <vector>

extern "C" long signature_raw_syscall(long number, long first, long second, long third,
                                      long fourth, long fifth, long sixth);

// GNU ld's --wrap option requires these exact symbol names.
// NOLINTNEXTLINE(bugprone-reserved-identifier,cert-dcl37-c,cert-dcl51-cpp)
extern "C" int __real_dl_iterate_phdr(int (*callback)(dl_phdr_info *, size_t, void *), void *data);

// Static bionic leaves both the executable and VDSO names null. Give these real program-header
// records distinct names so the production lookup can select the executable unambiguously.
// NOLINTNEXTLINE(bugprone-reserved-identifier,cert-dcl37-c,cert-dcl51-cpp)
extern "C" int __wrap_dl_iterate_phdr(int (*const callback)(dl_phdr_info *, size_t, void *),
                                     void *const data) {
    struct callback_context {
        int (*callback)(dl_phdr_info *, size_t, void *);
        void *data;
    } context{.callback = callback, .data = data};
    return __real_dl_iterate_phdr([](dl_phdr_info *info, const size_t size, void *opaque) {
        const auto &context = *static_cast<callback_context *>(opaque);
        auto named = *info;
        if (named.dlpi_name == nullptr || named.dlpi_name[0] == '\0')
            named.dlpi_name = reinterpret_cast<uintptr_t>(info->dlpi_phdr) == getauxval(AT_PHDR)
                    ? "signature_file_tests" : "linux-vdso.so";
        return context.callback(&named, size, context.data);
    }, &context);
}

// Static host tests have no Android log service. Production logging remains unchanged.
extern "C" int __android_log_print(int /*priority*/, const char * /*tag*/, const char *const fmt, ...) {
    va_list arguments{};
    va_start(arguments, fmt);
    const int result = vfprintf(stderr, fmt, arguments);
    static_cast<void>(fputc('\n', stderr));
    va_end(arguments);
    return result;
}

namespace {
    auto protected_syscall = &signature_raw_syscall;
    constexpr int64_t large_offset = 0x100010000LL;
    constexpr char large_contents[] = "large offset";
#ifdef __NR_newfstatat
    constexpr int stat_at_number = __NR_newfstatat;
    using stat_at_metadata = signature_kernel_stat;
#else
    constexpr int stat_at_number = __NR_fstatat64;
    using stat_at_metadata = signature_kernel_stat64;
#endif

    volatile sig_atomic_t previous_signal = 0;

    void previous_handler(int /*signal*/) { previous_signal = 1; }

    void create_file(const std::string &path, const char *const contents) {
        const int descriptor = open(path.c_str(), O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
        assert(descriptor >= 0);
        const auto size = strlen(contents);
        assert(write(descriptor, contents, size) == static_cast<ssize_t>(size));
        assert(close(descriptor) == 0);
    }

    int raw_open(const int number, const char *const path) {
        const long result = number == __NR_openat
                ? protected_syscall(number, AT_FDCWD, reinterpret_cast<long>(path),
                                        O_RDONLY | O_CLOEXEC, 0, 0, 0)
                : protected_syscall(number, reinterpret_cast<long>(path),
                                        O_RDONLY | O_CLOEXEC, 0, 0, 0, 0);
        assert(result >= 0);
        return static_cast<int>(result);
    }

    void expect_contents(const int descriptor, const char *const contents) {
        std::array<char, 64> data{};
        const long size = protected_syscall(__NR_read, descriptor,
                reinterpret_cast<long>(data.data()), data.size(), 0, 0, 0);
        assert(std::cmp_equal(size, strlen(contents)));
        assert(memcmp(data.data(), contents, size) == 0);
    }

    // dl_iterate_phdr fixes the callback's non-const parameter types.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    int find_executable(dl_phdr_info *const info, size_t /*info_size*/, void *const data) {
        const auto address = reinterpret_cast<uintptr_t>(&signature_raw_syscall);
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;
            if (address < info->dlpi_addr + segment.p_vaddr ||
                address >= info->dlpi_addr + segment.p_vaddr + segment.p_memsz) continue;
            const char *const filename = info->dlpi_name == nullptr ? "" : info->dlpi_name;
            const char *const slash = strrchr(filename, '/');
            *static_cast<std::string *>(data) = slash == nullptr ? filename : slash + 1;
            return 1;
        }
        return 0;
    }

    template<typename Metadata>
    void expect_metadata(const int number, const int descriptor, const Metadata &original,
                         const long original_result, const struct stat &installed,
                         const bool preserve_identity) {
        struct guarded_metadata {
            std::array<unsigned char, 16> before;
            Metadata value;
            std::array<unsigned char, 16> after;
        } observed{};
        observed.before.fill(0x5a);
        observed.after.fill(0xa5);
        const long result = protected_syscall(number, descriptor,
                reinterpret_cast<long>(&observed.value), 0, 0, 0, 0);
        assert(result == original_result);
        for (const auto value : observed.before) assert(value == 0x5a);
        for (const auto value : observed.after) assert(value == 0xa5);
        if (result < 0) return;

        // Retain padding bytes too. A memberwise copy need not preserve the kernel ABI layout.
        Metadata expected{};
        memcpy(&expected, &original, sizeof(expected));
        if (preserve_identity) {
            expected.st_uid = static_cast<decltype(expected.st_uid)>(installed.st_uid);
            expected.st_gid = static_cast<decltype(expected.st_gid)>(installed.st_gid);
        }
        // This test deliberately verifies every ABI byte, including initialized padding.
        // NOLINTNEXTLINE(bugprone-suspicious-memory-comparison,cert-exp42-c,cert-flp37-c)
        assert(memcmp(&observed.value, &expected, sizeof(expected)) == 0);
    }

    void expect_link(const int number, const std::string &descriptor_path,
                     const std::string &expected, const size_t capacity) {
        std::array<char, 4096> result{};
        const long length = number == __NR_readlinkat
                ? protected_syscall(number, AT_FDCWD,
                        reinterpret_cast<long>(descriptor_path.c_str()),
                        reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0)
                : protected_syscall(number, reinterpret_cast<long>(descriptor_path.c_str()),
                        reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0, 0);
        const size_t wanted = capacity < expected.size() ? capacity : expected.size();
        assert(std::cmp_equal(length, wanted));
        assert(memcmp(result.data(), expected.data(), wanted) == 0);
    }

    long stat_at(const int directory, const char *const path, const int flags,
                 stat_at_metadata *const value) {
        return protected_syscall(stat_at_number, directory, reinterpret_cast<long>(path),
                                     reinterpret_cast<long>(value), flags, 0, 0);
    }

    stat_at_metadata snapshot_stat_at(const int directory, const char *const path, const int flags) {
        stat_at_metadata value{};
        assert(stat_at(directory, path, flags, &value) == 0);
        return value;
    }

    void expect_stat_at(const char *const label, const int directory, const char *const path,
                         const int flags, const stat_at_metadata &expected) {
        struct guarded_metadata {
            std::array<unsigned char, 16> before;
            stat_at_metadata value;
            std::array<unsigned char, 16> after;
        } observed{};
        observed.before.fill(0x5a);
        observed.after.fill(0xa5);
        assert(stat_at(directory, path, flags, &observed.value) == 0);
        for (const auto value : observed.before) assert(value == 0x5a);
        for (const auto value : observed.after) assert(value == 0xa5);
        if (memcmp(&observed.value, &expected, sizeof(expected)) != 0) {
            fprintf(stderr, "stat-at metadata mismatch: %s\n", label);
            abort();
        }
    }

    void expect_content_offsets(const int descriptor) {
#ifdef __NR_lseek
        std::array<char, 32> data{};
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_SET, 0, 0, 0) == 0);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()),
                                  4, 0, 0, 0) == 4);
        assert(memcmp(data.data(), "orig", 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 4);
#if defined(__arm__)
        const long positioned = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(data.data()), 8, 0, 9, 0);
#else
        const long positioned = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(data.data()), 8, 9, 0, 0);
#endif
        assert(positioned == 8);
        assert(memcmp(data.data(), "contents", 8) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 4);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()),
                                  4, 0, 0, 0) == 4);
        assert(memcmp(data.data(), "inal", 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, -8, SEEK_END, 0, 0, 0) == 9);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()),
                                  data.size(), 0, 0, 0) == 8);
        assert(memcmp(data.data(), "contents", 8) == 0);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()),
                                  data.size(), 0, 0, 0) == 0);
#endif
    }

    void expect_mapped_contents(const int descriptor, const size_t page_size) {
#ifdef __aarch64__
        constexpr int mapping_syscall = __NR_mmap;
#else
        constexpr int mapping_syscall = __NR_mmap2;
#endif
        const long result = protected_syscall(mapping_syscall, 0, page_size, PROT_READ,
                                              MAP_PRIVATE, descriptor, 0);
        assert(result < -4095 || result >= 0);
        auto *const mapped = reinterpret_cast<void *>(result);
        assert(memcmp(mapped, "original contents", 17) == 0);
        assert(munmap(mapped, page_size) == 0);

        // A mapped descriptor is ignored for MAP_ANONYMOUS, even when it names an APK.
        const long anonymous = protected_syscall(mapping_syscall, 0, page_size, PROT_READ,
                                                 MAP_PRIVATE | MAP_ANONYMOUS, descriptor, 0);
        assert(anonymous < -4095 || anonymous >= 0);
        assert(*reinterpret_cast<const char *>(anonymous) == '\0');
        assert(munmap(reinterpret_cast<void *>(anonymous), page_size) == 0);
    }

    int proc_snapshot(const proc_file file) {
        const char *const content = file == proc_file::maps ? "maps snapshot" : "status snapshot";
        const long descriptor = signature_raw_syscall(__NR_memfd_create,
                reinterpret_cast<long>("proc-test"), MFD_CLOEXEC, 0, 0, 0, 0);
        if (descriptor < 0) return static_cast<int>(descriptor);
        const long result = signature_raw_syscall(__NR_write, descriptor,
                reinterpret_cast<long>(content), strlen(content), 0, 0, 0);
        assert(std::cmp_equal(result, strlen(content)));
        return static_cast<int>(descriptor);
    }

    void expect_proc_snapshot(const char *path, const char *contents, const char *file) {
        const int descriptor = raw_open(__NR_openat, path);
        struct stat metadata{};
        assert(fstat(descriptor, &metadata) == 0 && metadata.st_size == 0);
        expect_link(__NR_readlinkat, "/proc/self/fd/" + std::to_string(descriptor),
                    "/proc/" + std::to_string(getpid()) + "/" + file, 4096);
        expect_contents(descriptor, contents);
        expect_contents(descriptor, "");
#ifdef __NR_lseek
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_SET, 0, 0, 0) == 0);
        std::array<char, 32> buffer{};
        assert(protected_syscall(__NR_read, descriptor,
                reinterpret_cast<long>(buffer.data()), 3, 0, 0, 0) == 3);
        assert(memcmp(buffer.data(), contents, 3) == 0);
#if defined(__arm__)
        const long result = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(buffer.data()), 4, 0, 2, 0);
#else
        const long result = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(buffer.data()), 4, 2, 0, 0);
#endif
        assert(result == 4 && memcmp(buffer.data(), contents + 2, 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 3);
        expect_contents(descriptor, contents + 3);
#endif
        assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
    }

    int64_t seek_large_file(const int descriptor, const int64_t offset, const int origin) {
#ifdef __arm__
        int64_t position = -1;
        assert(protected_syscall(__NR__llseek, descriptor, static_cast<uint64_t>(offset) >> 32,
                static_cast<uint32_t>(offset), reinterpret_cast<long>(&position), origin, 0) == 0);
        return position;
#else
        const long position = protected_syscall(__NR_lseek, descriptor, offset, origin, 0, 0, 0);
        assert(position >= 0);
        return position;
#endif
    }

    void expect_large_file_contents(const char *path, const size_t page_size) {
        const int descriptor = raw_open(__NR_openat, path);
        std::array<char, sizeof(large_contents)> buffer{};
        const auto read_contents = [&] {
            assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(buffer.data()),
                    buffer.size(), 0, 0, 0) == sizeof(large_contents));
            assert(memcmp(buffer.data(), large_contents, sizeof(large_contents)) == 0);
        };
        assert(seek_large_file(descriptor, large_offset, SEEK_SET) == large_offset);
        read_contents();
        assert(seek_large_file(descriptor, -4096, SEEK_END) == large_offset);
        read_contents();
#ifdef __arm__
        const long result = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(buffer.data()), buffer.size(), 0,
                static_cast<uint32_t>(large_offset), large_offset >> 32);
        constexpr int mapping_syscall = __NR_mmap2;
        constexpr long mapping_offset = large_offset >> 12;
#else
        const long result = protected_syscall(__NR_pread64, descriptor,
                reinterpret_cast<long>(buffer.data()), buffer.size(), large_offset, 0, 0);
        constexpr int mapping_syscall = __NR_mmap;
        constexpr long mapping_offset = large_offset;
#endif
        assert(result == sizeof(large_contents));
        assert(memcmp(buffer.data(), large_contents, sizeof(large_contents)) == 0);
        const long mapping = protected_syscall(mapping_syscall, 0, page_size, PROT_READ,
                MAP_PRIVATE, descriptor, mapping_offset);
        assert(mapping < -4095 || mapping >= 0);
        assert(memcmp(reinterpret_cast<void *>(mapping), large_contents, sizeof(large_contents)) == 0);
        assert(munmap(reinterpret_cast<void *>(mapping), page_size) == 0);
        assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
    }
} // namespace

int main(const int count, const char *const *const arguments) {
    assert(count == 3);
    const bool redirect_contents = strcmp(arguments[2], "contents") == 0;
    const bool preserve_identity = redirect_contents || strcmp(arguments[2], "identity") == 0;
    const auto page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    // Production puts the protected library and handler in separate executable ranges. Keep
    // that distinction in a static test, whose bionic and handler otherwise share one ELF.
    void *gateway = mmap(nullptr, page_size, PROT_READ | PROT_WRITE,
                         MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(gateway != MAP_FAILED);
    constexpr size_t gateway_size = 64;
    memcpy(gateway, reinterpret_cast<const void *>(&signature_raw_syscall), gateway_size);
    __builtin___clear_cache(static_cast<char *>(gateway), static_cast<char *>(gateway) + gateway_size);
    assert(mprotect(gateway, page_size, PROT_READ | PROT_EXEC) == 0);
    protected_syscall = reinterpret_cast<decltype(protected_syscall)>(gateway);
    std::string directory = std::string(arguments[1]) + "/signature-files-XXXXXX";
    assert(mkdtemp(directory.data()) != nullptr);
    const std::string installed_path = directory + "/base.apk";
    const std::string replacement_path = directory + "/original.apk";
    const std::string unrelated_path = directory + "/unrelated";
    const std::string symlink_path = directory + "/base.link";
    const std::string large_installed_path = directory + "/large.apk";
    const std::string large_replacement_path = directory + "/large-original.apk";
    create_file(installed_path, "installed");
    create_file(replacement_path, "original contents");
    create_file(unrelated_path, "unrelated contents");
    if (redirect_contents) {
        create_file(large_installed_path, "installed");
        const int descriptor = open(large_replacement_path.c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
        assert(descriptor >= 0);
        assert(ftruncate64(descriptor, large_offset + 4096) == 0);
        assert(pwrite64(descriptor, large_contents, sizeof(large_contents), large_offset) == sizeof(large_contents));
        assert(close(descriptor) == 0);
    }
    assert(symlink(installed_path.c_str(), symlink_path.c_str()) == 0);
    // A host user namespace may map only the caller's own UID even when geteuid() is zero.
    if (geteuid() == 0 && chown(installed_path.c_str(), 1000, 1000) == 0) {
        assert(chown(replacement_path.c_str(), 2000, 2000) == 0);
    }

    const int installed_descriptor = open(installed_path.c_str(), O_RDONLY | O_CLOEXEC);
    const int replacement_descriptor = open(replacement_path.c_str(), O_RDONLY | O_CLOEXEC);
    const int directory_descriptor = open(directory.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC);
    assert(installed_descriptor >= 0 && replacement_descriptor >= 0);
    assert(directory_descriptor >= 0);
    expect_contents(replacement_descriptor, "original contents");
    assert(lseek(replacement_descriptor, 0, SEEK_SET) == 0);
    struct stat installed{};
    assert(fstat(installed_descriptor, &installed) == 0);
    struct stat replacement{};
    assert(fstat(replacement_descriptor, &replacement) == 0);
    const auto replacement_size = replacement.st_size;
    auto installed_at = snapshot_stat_at(AT_FDCWD, installed_path.c_str(), 0);
    auto replacement_at = snapshot_stat_at(AT_FDCWD, replacement_path.c_str(), 0);
    const auto unrelated_at = snapshot_stat_at(AT_FDCWD, unrelated_path.c_str(), 0);
    const auto symlink_at = snapshot_stat_at(AT_FDCWD, symlink_path.c_str(), AT_SYMLINK_NOFOLLOW);
    stat_at_metadata error_output{};
    const long bad_stat_output = stat_at(AT_FDCWD, installed_path.c_str(), 0,
                                        reinterpret_cast<stat_at_metadata *>(1));
    const long bad_stat_path = stat_at(AT_FDCWD, reinterpret_cast<const char *>(1), 0, &error_output);
    const long missing_stat_path = stat_at(directory_descriptor, "missing", 0, &error_output);
    const long bad_stat_directory = stat_at(-1, "base.apk", 0, &error_output);
    const long empty_stat_path = stat_at(installed_descriptor, "", 0, &error_output);
    const long bad_stat_flags = stat_at(AT_FDCWD, installed_path.c_str(), 0x40000000, &error_output);
    const int metadata_descriptor = redirect_contents ? installed_descriptor : replacement_descriptor;
    signature_kernel_stat expected{};
    const long expected_result = protected_syscall(__NR_fstat, metadata_descriptor,
            reinterpret_cast<long>(&expected), 0, 0, 0, 0);
    if (redirect_contents && expected_result == 0) expected.st_size = replacement_size;
    const long bad_pointer_result = protected_syscall(__NR_fstat, replacement_descriptor,
            1, 0, 0, 0, 0);
#ifdef __NR_fstat64
    signature_kernel_stat64 expected64{};
    const long expected_result64 = protected_syscall(__NR_fstat64, metadata_descriptor,
            reinterpret_cast<long>(&expected64), 0, 0, 0, 0);
    if (redirect_contents && expected_result64 == 0) expected64.st_size = replacement_size;
#endif

    struct sigaction previous{};
    previous.sa_handler = previous_handler;
    sigemptyset(&previous.sa_mask);
    assert(sigaction(SIGSYS, &previous, nullptr) == 0);
    std::atomic<bool> ready{false};
    std::atomic<bool> proceed{false};
    std::thread existing_thread([&] {
        ready.store(true, std::memory_order_release);
        while (!proceed.load(std::memory_order_acquire)) std::this_thread::yield();
        const int descriptor = raw_open(__NR_openat, installed_path.c_str());
        expect_contents(descriptor, "original contents");
        assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
    });
    while (!ready.load(std::memory_order_acquire)) std::this_thread::yield();

    std::string library;
    assert(dl_iterate_phdr(find_executable, &library) != 0);
    std::vector paths{
        raw_open_mapping{
            .installed = installed_path.c_str(),
            .replacement = replacement_path.c_str(),
        },
    };
    if (redirect_contents) paths.push_back({large_installed_path.c_str(), large_replacement_path.c_str()});
    diagnostics.store(true, std::memory_order_relaxed);
    assert(install_raw_open_redirect(library.c_str(), paths,
                                    {.start = reinterpret_cast<uintptr_t>(gateway),
                                     .end = reinterpret_cast<uintptr_t>(gateway) + gateway_size,
                                     .preserve_descriptor_identity = preserve_identity,
                                     .redirect_file_contents = redirect_contents,
                                     .snapshot_proc_file = redirect_contents ? proc_snapshot : nullptr}));
    diagnostics.store(false, std::memory_order_relaxed);
    proceed.store(true, std::memory_order_release);
    existing_thread.join();

    assert(raise(SIGSYS) == 0);
    assert(previous_signal == 1);
    const int redirected = raw_open(__NR_openat, installed_path.c_str());
    expect_contents(redirected, "original contents");
    expect_content_offsets(redirected);
    if (redirect_contents) {
        expect_large_file_contents(large_installed_path.c_str(), page_size);
        expect_proc_snapshot("/proc/self/maps", "maps snapshot", "maps");
        const auto status = "/proc/" + std::to_string(getpid()) + "/status";
        expect_proc_snapshot(status.c_str(), "status snapshot", "status");
        expect_mapped_contents(redirected, page_size);
        expect_contents(installed_descriptor, "original contents");
        const int duplicate = dup(installed_descriptor);
        assert(duplicate >= 0);
        assert(lseek(duplicate, 0, SEEK_SET) == 0);
        expect_contents(duplicate, "original contents");
        expect_mapped_contents(duplicate, page_size);
        assert(close(duplicate) == 0);

        const std::string alias = "/proc/self/fd/" + std::to_string(installed_descriptor);
        const int reopened = raw_open(__NR_openat, alias.c_str());
        expect_contents(reopened, "original contents");
        assert(close(reopened) == 0);

        // Reuse a previously redirected descriptor through libc, outside the trapped range.
        const int recycled = raw_open(__NR_openat, installed_path.c_str());
        assert(close(recycled) == 0);
        const int unrelated = open(unrelated_path.c_str(), O_RDONLY | O_CLOEXEC);
        assert(unrelated == recycled);
        expect_contents(unrelated, "unrelated contents");
        assert(close(unrelated) == 0);
    }
    struct stat reported{};
    assert(fstat(redirected, &reported) == 0);
    if (redirect_contents) {
        // Outside the protected range, the descriptor and its mmap retain the installed file.
        assert(memcmp(&reported, &installed, sizeof(installed)) == 0);
        void *mapping = mmap(nullptr, page_size, PROT_READ, MAP_PRIVATE, redirected, 0);
        assert(mapping != MAP_FAILED);
        assert(memcmp(mapping, "installed", 9) == 0);
        assert(munmap(mapping, page_size) == 0);
        replacement = installed;
        replacement.st_size = replacement_size;
        reported.st_size = replacement_size;
        installed_at.st_size = replacement_size;
    }
    if (preserve_identity) {
        replacement.st_uid = installed.st_uid;
        replacement.st_gid = installed.st_gid;
    }
    // Compare complete, initialized kernel layouts, as in expect_metadata.
    // NOLINTNEXTLINE(bugprone-suspicious-memory-comparison,cert-exp42-c,cert-flp37-c)
    assert(memcmp(&reported, &replacement, sizeof(reported)) == 0);
#ifdef __NR_open
    const int legacy = raw_open(__NR_open, installed_path.c_str());
    expect_contents(legacy, "original contents");
    assert(protected_syscall(__NR_close, legacy, 0, 0, 0, 0, 0) == 0);
#endif
    expect_metadata(__NR_fstat, redirected, expected, expected_result, installed, preserve_identity);
#ifdef __NR_fstat64
    expect_metadata(__NR_fstat64, redirected, expected64, expected_result64, installed, preserve_identity);
#endif
    assert(protected_syscall(__NR_fstat, -1, reinterpret_cast<long>(&expected), 0, 0, 0, 0) == -EBADF);
    assert(protected_syscall(__NR_fstat, redirected, 1, 0, 0, 0, 0) == bad_pointer_result);
    const std::string descriptor_path = "/proc/self/fd/" + std::to_string(redirected);
    const std::string &expected_path = preserve_identity ? installed_path : replacement_path;
    expect_link(__NR_readlinkat, descriptor_path, expected_path, 4096);
    expect_link(__NR_readlinkat, descriptor_path, expected_path, directory.size() + 2);
#ifdef __NR_readlink
    expect_link(__NR_readlink, descriptor_path, expected_path, 4096);
    expect_link(__NR_readlink, descriptor_path, expected_path, directory.size() + 2);
#endif
    auto virtual_metadata = redirect_contents ? installed_at : replacement_at;
    if (preserve_identity) {
        virtual_metadata.st_uid = installed.st_uid;
        virtual_metadata.st_gid = installed.st_gid;
    }
    const auto &path_metadata = preserve_identity ? virtual_metadata : installed_at;
    expect_stat_at("absolute installed path", AT_FDCWD, installed_path.c_str(), 0, path_metadata);
    expect_stat_at("relative installed path", directory_descriptor, "base.apk", 0, path_metadata);
    expect_stat_at("absolute regular file no-follow", AT_FDCWD, installed_path.c_str(),
                   AT_SYMLINK_NOFOLLOW, path_metadata);
    expect_stat_at("descriptor empty path", redirected, "", AT_EMPTY_PATH, virtual_metadata);
    expect_stat_at("proc descriptor path", AT_FDCWD, descriptor_path.c_str(), 0, virtual_metadata);
    expect_stat_at("unrelated file", AT_FDCWD, unrelated_path.c_str(), 0, unrelated_at);
    expect_stat_at("symlink no-follow", AT_FDCWD, symlink_path.c_str(), AT_SYMLINK_NOFOLLOW, symlink_at);
    const auto descriptor_link = snapshot_stat_at(AT_FDCWD, descriptor_path.c_str(), AT_SYMLINK_NOFOLLOW);
    assert(S_ISLNK(descriptor_link.st_mode));
    assert(descriptor_link.st_uid == geteuid());
    assert(stat_at(AT_FDCWD, installed_path.c_str(), 0,
                   reinterpret_cast<stat_at_metadata *>(1)) == bad_stat_output);
    assert(stat_at(AT_FDCWD, reinterpret_cast<const char *>(1), 0, &error_output) == bad_stat_path);
    assert(stat_at(directory_descriptor, "missing", 0, &error_output) == missing_stat_path);
    assert(stat_at(-1, "base.apk", 0, &error_output) == bad_stat_directory);
    assert(stat_at(redirected, "", 0, &error_output) == empty_stat_path);
    assert(stat_at(AT_FDCWD, installed_path.c_str(), 0x40000000, &error_output) == bad_stat_flags);
    assert(protected_syscall(__NR_close, redirected, 0, 0, 0, 0, 0) == 0);
    assert(close(directory_descriptor) == 0);
    assert(close(installed_descriptor) == 0);
    assert(close(replacement_descriptor) == 0);
    assert(unlink(installed_path.c_str()) == 0);
    assert(unlink(replacement_path.c_str()) == 0);
    assert(unlink(unrelated_path.c_str()) == 0);
    assert(unlink(symlink_path.c_str()) == 0);
    if (redirect_contents) {
        assert(unlink(large_installed_path.c_str()) == 0);
        assert(unlink(large_replacement_path.c_str()) == 0);
    }
    assert(rmdir(directory.c_str()) == 0);
    assert(munmap(gateway, page_size) == 0);
    puts("Native file redirection checks passed");
}
