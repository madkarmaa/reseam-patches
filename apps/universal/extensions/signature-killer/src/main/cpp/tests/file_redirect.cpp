// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#include "kernel_stat.h"
#include "logging.h"
#include "seccomp_open.h"

#include <fcntl.h>
#include <link.h>
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
                ? signature_raw_syscall(number, AT_FDCWD, reinterpret_cast<long>(path),
                                        O_RDONLY | O_CLOEXEC, 0, 0, 0)
                : signature_raw_syscall(number, reinterpret_cast<long>(path),
                                        O_RDONLY | O_CLOEXEC, 0, 0, 0, 0);
        assert(result >= 0);
        return static_cast<int>(result);
    }

    void expect_contents(const int descriptor, const char *const contents) {
        std::array<char, 64> data{};
        const ssize_t size = read(descriptor, data.data(), data.size());
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
        const long result = signature_raw_syscall(number, descriptor,
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
                ? signature_raw_syscall(number, AT_FDCWD,
                        reinterpret_cast<long>(descriptor_path.c_str()),
                        reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0)
                : signature_raw_syscall(number, reinterpret_cast<long>(descriptor_path.c_str()),
                        reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0, 0);
        const size_t wanted = capacity < expected.size() ? capacity : expected.size();
        assert(std::cmp_equal(length, wanted));
        assert(memcmp(result.data(), expected.data(), wanted) == 0);
    }
} // namespace

int main(const int count, const char *const *const arguments) {
    assert(count == 3);
    const bool preserve_identity = strcmp(arguments[2], "identity") == 0;
    std::string directory = std::string(arguments[1]) + "/signature-files-XXXXXX";
    assert(mkdtemp(directory.data()) != nullptr);
    const std::string installed_path = directory + "/base.apk";
    const std::string replacement_path = directory + "/original.apk";
    create_file(installed_path, "installed");
    create_file(replacement_path, "original contents");
    // A host user namespace may map only the caller's own UID even when geteuid() is zero.
    if (geteuid() == 0 && chown(installed_path.c_str(), 1000, 1000) == 0) {
        assert(chown(replacement_path.c_str(), 2000, 2000) == 0);
    }

    const int installed_descriptor = open(installed_path.c_str(), O_RDONLY | O_CLOEXEC);
    const int replacement_descriptor = open(replacement_path.c_str(), O_RDONLY | O_CLOEXEC);
    assert(installed_descriptor >= 0 && replacement_descriptor >= 0);
    expect_contents(replacement_descriptor, "original contents");
    assert(lseek(replacement_descriptor, 0, SEEK_SET) == 0);
    struct stat installed{};
    assert(fstat(installed_descriptor, &installed) == 0);
    struct stat replacement{};
    assert(fstat(replacement_descriptor, &replacement) == 0);
    signature_kernel_stat expected{};
    const long expected_result = signature_raw_syscall(__NR_fstat, replacement_descriptor,
            reinterpret_cast<long>(&expected), 0, 0, 0, 0);
    const long bad_pointer_result = signature_raw_syscall(__NR_fstat, replacement_descriptor,
            1, 0, 0, 0, 0);
#ifdef __NR_fstat64
    signature_kernel_stat64 expected64{};
    const long expected_result64 = signature_raw_syscall(__NR_fstat64, replacement_descriptor,
            reinterpret_cast<long>(&expected64), 0, 0, 0, 0);
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
        assert(close(descriptor) == 0);
    });
    while (!ready.load(std::memory_order_acquire)) std::this_thread::yield();

    std::string library;
    assert(dl_iterate_phdr(find_executable, &library) != 0);
    const std::array paths{
        raw_open_mapping{
            .installed = installed_path.c_str(),
            .replacement = replacement_path.c_str(),
        },
    };
    diagnostics.store(true, std::memory_order_relaxed);
    assert(install_raw_open_redirect(library.c_str(), paths,
                                    {.preserve_descriptor_identity = preserve_identity}));
    diagnostics.store(false, std::memory_order_relaxed);
    proceed.store(true, std::memory_order_release);
    existing_thread.join();

    assert(raise(SIGSYS) == 0);
    assert(previous_signal == 1);
    const int redirected = raw_open(__NR_openat, installed_path.c_str());
    expect_contents(redirected, "original contents");
    struct stat reported{};
    assert(fstat(redirected, &reported) == 0);
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
    assert(close(legacy) == 0);
#endif
    expect_metadata(__NR_fstat, redirected, expected, expected_result, installed, preserve_identity);
#ifdef __NR_fstat64
    expect_metadata(__NR_fstat64, redirected, expected64, expected_result64, installed, preserve_identity);
#endif
    assert(signature_raw_syscall(__NR_fstat, -1, reinterpret_cast<long>(&expected), 0, 0, 0, 0) == -EBADF);
    assert(signature_raw_syscall(__NR_fstat, redirected, 1, 0, 0, 0, 0) == bad_pointer_result);
    const std::string descriptor_path = "/proc/self/fd/" + std::to_string(redirected);
    const std::string &expected_path = preserve_identity ? installed_path : replacement_path;
    expect_link(__NR_readlinkat, descriptor_path, expected_path, 4096);
    expect_link(__NR_readlinkat, descriptor_path, expected_path, directory.size() + 2);
#ifdef __NR_readlink
    expect_link(__NR_readlink, descriptor_path, expected_path, 4096);
    expect_link(__NR_readlink, descriptor_path, expected_path, directory.size() + 2);
#endif
    assert(close(redirected) == 0);
    assert(close(installed_descriptor) == 0);
    assert(close(replacement_descriptor) == 0);
    assert(unlink(installed_path.c_str()) == 0);
    assert(unlink(replacement_path.c_str()) == 0);
    assert(rmdir(directory.c_str()) == 0);
    puts("Native file redirection checks passed");
}
