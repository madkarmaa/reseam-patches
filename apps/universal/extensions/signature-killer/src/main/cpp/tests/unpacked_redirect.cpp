// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/seccomp_open.h"
#include "hooks/native_file_hooks.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/wait.h>
#include <unistd.h>

#ifdef NDEBUG
#undef NDEBUG
#endif

#include <array>
#include <charconv>
#include <cassert>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace {
    void create_file(const std::string &path, const char *const contents) {
        FILE *const file = fopen(path.c_str(), "w");
        assert(file != nullptr);
        assert(fputs(contents, file) >= 0);
        assert(fclose(file) == 0);
    }

    unsigned read_unsigned_field(const char *value) {
        value += strspn(value, " \t");
        unsigned result = 0;
        const auto [end, error] = std::from_chars(value, value + strcspn(value, "\n"), result);
        assert(error == std::errc{} && end != value);
        return result;
    }

    unsigned filters(const char *const status) {
        const char *const field = strstr(status, "Seccomp_filters:");
        assert(field != nullptr);
        return read_unsigned_field(field + sizeof("Seccomp_filters:") - 1);
    }

#ifdef __aarch64__
    // A second protector can replace libc's prologue after ShadowHook saved its first
    // instruction. Its old trampoline must never resume inside that rewritten prologue.
    void verify_replaced_open_prologue(const std::string &installed) {
        auto *const entry = static_cast<uint32_t *>(dlsym(RTLD_DEFAULT, "open"));
        assert(entry != nullptr && (*entry & 0xfc000000U) == 0x14000000U);
        const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
        auto *const page = reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(entry) & ~(page_size - 1));
        const uint32_t saved = entry[1];
        assert(signature_raw_syscall(__NR_mprotect, reinterpret_cast<long>(page), page_size,
                                     PROT_READ | PROT_WRITE | PROT_EXEC, 0, 0, 0) == 0);
        entry[1] = 0xd4200000U; // BRK if an old trampoline resumes at libc open + 4.
        __builtin___clear_cache(reinterpret_cast<char *>(entry), reinterpret_cast<char *>(entry + 2));
        assert(signature_raw_syscall(__NR_mprotect, reinterpret_cast<long>(page), page_size,
                                     PROT_READ | PROT_EXEC, 0, 0, 0) == 0);
        FILE *const file = fopen(installed.c_str(), "re");
        assert(file != nullptr);
        std::array<char, 32> contents{};
        assert(fread(contents.data(), 1, contents.size(), file) == 9);
        assert(strcmp(contents.data(), "installed") == 0 && fclose(file) == 0);
        assert(signature_raw_syscall(__NR_mprotect, reinterpret_cast<long>(page), page_size,
                                     PROT_READ | PROT_WRITE | PROT_EXEC, 0, 0, 0) == 0);
        entry[1] = saved;
        __builtin___clear_cache(reinterpret_cast<char *>(entry), reinterpret_cast<char *>(entry + 2));
        assert(signature_raw_syscall(__NR_mprotect, reinterpret_cast<long>(page), page_size,
                                     PROT_READ | PROT_EXEC, 0, 0, 0) == 0);
    }
#endif

#ifdef __aarch64__
    void verify_forwarded_read(void *const probe, const std::string &installed, void (*const verify)()) {
        auto *const entry = static_cast<uint32_t *>(dlsym(RTLD_DEFAULT, "read"));
        assert(entry != nullptr && (*entry & 0xfc000000U) == 0x14000000U);
        const auto immediate = *entry & 0x03ffffffU;
        const auto displacement = (static_cast<int64_t>(immediate ^ 0x02000000U) - 0x02000000) * 4;
        const auto set_forwarder =
            reinterpret_cast<void (*)(void *)>(dlsym(probe, "signature_set_read_forwarder"));
        const void *const forwarder = dlsym(probe, "signature_forward_read");
        assert(set_forwarder != nullptr && forwarder != nullptr);
        set_forwarder(reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(entry) + displacement));
        std::array<uint32_t, 4> saved{};
        memcpy(saved.data(), entry, sizeof(saved));
        const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
        auto *const page = reinterpret_cast<void *>(reinterpret_cast<uintptr_t>(entry) & ~(page_size - 1));
        assert(mprotect(page, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) == 0);
        entry[0] = 0x58000050U; // LDR x16, literal at +8.
        entry[1] = 0xd61f0200U; // BR x16 preserves the actual caller's LR.
        const auto destination = reinterpret_cast<uintptr_t>(forwarder);
        memcpy(entry + 2, &destination, sizeof(destination));
        __builtin___clear_cache(reinterpret_cast<char *>(entry), reinterpret_cast<char *>(entry + 4));
        // Model a protector that never restores RX after writing the branch.
        refresh_native_file_hooks();
        int (*const open_file)(const char *, int, ...) = open;
        const int descriptor = open_file(installed.c_str(), O_RDONLY);
        assert(descriptor >= 0);
        std::array<char, 32> contents{};
        assert(read(descriptor, contents.data(), contents.size()) == 9);
        assert(strcmp(contents.data(), "installed") == 0 && close(descriptor) == 0);
        verify();
        assert(mprotect(page, page_size, PROT_READ | PROT_WRITE | PROT_EXEC) == 0);
        memcpy(entry, saved.data(), sizeof(saved));
        __builtin___clear_cache(reinterpret_cast<char *>(entry), reinterpret_cast<char *>(entry + 4));
        assert(mprotect(page, page_size, PROT_READ | PROT_EXEC) == 0);
    }
#endif

    std::string process_status() {
        FILE *const file = fopen("/proc/self/status", "re");
        assert(file != nullptr);
        std::string contents;
        std::array<char, 1024> bytes{};
        size_t length = 0;
        while ((length = fread(bytes.data(), 1, bytes.size(), file)) != 0)
            contents.append(bytes.data(), length);
        assert(fclose(file) == 0);
        return contents;
    }
} // namespace

int main(const int count, const char *const *const arguments) {
    assert(count == 2);
    const std::string directory = arguments[1];
    const std::string installed = directory + "/unpacked-installed.apk";
    const std::string original = directory + "/unpacked-original.apk";
    create_file(installed, "installed");
    create_file(original, "original contents");
    const unsigned baseline = filters(process_status().c_str());
    const auto page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
    void *const foreign =
        mmap(nullptr, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
    assert(foreign != MAP_FAILED);
    memcpy(foreign, reinterpret_cast<void *>(signature_raw_syscall), 64);
    __builtin___clear_cache(static_cast<char *>(foreign), static_cast<char *>(foreign) + 64);
    const std::string foreign_address = std::to_string(reinterpret_cast<uintptr_t>(foreign));
    // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
    assert(setenv("SIGNATURE_FOREIGN_MAPPING", foreign_address.c_str(), 1) == 0);
    // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
    assert(setenv("SIGNATURE_UNPACKED_APK", installed.c_str(), 1) == 0);
    const std::array mappings{
        raw_open_mapping{
            .installed = installed.c_str(),
            .replacement = original.c_str(),
        },
    };
    assert(prepare_library_file_redirect("libsignature_unpacked_probe.so", mappings, true));
    void *const probe = dlopen("libsignature_unpacked_probe.so", RTLD_NOW | RTLD_LOCAL);
    assert(probe != nullptr);
    auto verify = reinterpret_cast<void (*)()>(dlsym(probe, "signature_verify_unpacked_reads"));
    assert(verify != nullptr);
    verify();
    assert(raw_redirect_filter_count() == 3);
    auto status = reinterpret_cast<const char *(*)(const char *)>(dlsym(probe, "signature_unpacked_status"));
    assert(status != nullptr && filters(status("/proc/self/status")) == baseline);
    assert(filters(process_status().c_str()) == baseline + raw_redirect_filter_count());
    auto internal_reads = reinterpret_cast<void (*)()>(dlsym(probe, "signature_verify_internal_reads"));
    assert(internal_reads != nullptr);
    std::thread worker([internal_reads] {
        bypass_native_file_hooks_for_thread();
        assert(native_file_hooks_bypassed());
        internal_reads();
    });
    worker.join();
    assert(!native_file_hooks_bypassed());
    verify();
    const pid_t parent = getpid();
    const pid_t child = fork();
    assert(child >= 0);
    if (child == 0) {
        const std::string child_path = "/proc/" + std::to_string(getpid()) + "/status";
        const char *const child_status = status(child_path.c_str());
        assert(filters(child_status) == baseline);
        const char *identity = strstr(child_status, "\nPid:");
        assert(identity != nullptr);
        assert(std::cmp_equal(read_unsigned_field(identity + sizeof("\nPid:") - 1), getpid()));
        const std::string parent_path = "/proc/" + std::to_string(parent) + "/status";
        const char *const parent_status = status(parent_path.c_str());
        identity = strstr(parent_status, "\nPid:");
        assert(filters(parent_status) == baseline);
        assert(identity != nullptr);
        assert(std::cmp_equal(read_unsigned_field(identity + sizeof("\nPid:") - 1), parent));
        _exit(0);
    }
    int child_status = 0;
    assert(waitpid(child, &child_status, 0) == child);
    assert(WIFEXITED(child_status) && WEXITSTATUS(child_status) == 0);

    // Unrelated runtime calls must still read the installed, modified bytes.
    FILE *const file = fopen(installed.c_str(), "re");
    assert(file != nullptr);
    std::array<char, 32> bytes{};
    assert(fread(bytes.data(), 1, bytes.size(), file) == 9);
    assert(strcmp(bytes.data(), "installed") == 0);
    assert(fclose(file) == 0);
    const auto unowned_call = reinterpret_cast<decltype(&signature_raw_syscall)>(foreign);
    const long descriptor =
        unowned_call(__NR_openat, AT_FDCWD, reinterpret_cast<long>(installed.c_str()), O_RDONLY, 0, 0, 0);
    assert(descriptor >= 0);
    bytes.fill('\0');
    assert(unowned_call(__NR_read, descriptor, reinterpret_cast<long>(bytes.data()), bytes.size(), 0, 0, 0) ==
           9);
    assert(strcmp(bytes.data(), "installed") == 0);
    assert(unowned_call(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
#ifdef __aarch64__
    verify_replaced_open_prologue(installed);
    verify_forwarded_read(probe, installed, verify);
#endif
    assert(munmap(foreign, page_size) == 0);
    assert(unlink(installed.c_str()) == 0 && unlink(original.c_str()) == 0);
    puts("Unpacked native file-redirection checks passed");
}
