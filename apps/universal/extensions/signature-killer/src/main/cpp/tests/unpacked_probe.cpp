// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifdef NDEBUG
#undef NDEBUG
#endif

#include <array>
#include <utility>
#include <cassert>
#include <cerrno>
#include <cstdint>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);
extern "C" long signature_test_bridge(void *, long, long, long, long, long, long);
extern "C" char signature_test_bridge_end;

namespace {
    using raw_call = decltype(&signature_raw_syscall);
    using libc_call = decltype(&signature_test_bridge);

    struct unpacked_code {
        raw_call raw;
        libc_call libc;
    };

    std::array<unpacked_code, 2> copies{};
    using open_function = int (*)(const char *, int, ...);

    void expect_read(const unpacked_code &code, const char *const path, const bool libc,
                     const char *const expected = "original contents") {
        const long descriptor =
            libc ? code.libc(reinterpret_cast<void *>(static_cast<open_function>(open)),
                             reinterpret_cast<long>(path), O_RDONLY, 0, 0, 0, 0)
                 : code.raw(__NR_openat, AT_FDCWD, reinterpret_cast<long>(path), O_RDONLY, 0, 0, 0);
        assert(descriptor >= 0);
        std::array<char, 32> bytes{};
        const long length = libc ? code.libc(reinterpret_cast<void *>(read), descriptor,
                                             reinterpret_cast<long>(bytes.data()), bytes.size(), 0, 0, 0)
                                 : code.raw(__NR_read, descriptor, reinterpret_cast<long>(bytes.data()),
                                            bytes.size(), 0, 0, 0);
        assert(std::cmp_equal(length, strlen(expected)) && strcmp(bytes.data(), expected) == 0);
        assert(code.libc(reinterpret_cast<void *>(close), descriptor, 0, 0, 0, 0, 0) == 0);
    }

    void initialize_unpacked_code() __attribute__((constructor));

    void initialize_unpacked_code() {
        const auto page = static_cast<size_t>(sysconf(_SC_PAGESIZE));
        // The bridge's start and end are separate assembly symbols, so compare their addresses.
        const auto bridge_size = reinterpret_cast<uintptr_t>(&signature_test_bridge_end) -
                                 reinterpret_cast<uintptr_t>(&signature_test_bridge);
        assert(bridge_size < 128);
        // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
        const char *const path = getenv("SIGNATURE_UNPACKED_APK");
        assert(path != nullptr);

        for (auto &code : copies) {
            void *const allocation =
                mmap(nullptr, page * 3, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
            assert(allocation != MAP_FAILED);
            auto *const memory = static_cast<char *>(allocation) + page;
            memcpy(memory, reinterpret_cast<void *>(&signature_raw_syscall), 64);
            memcpy(memory + 128, reinterpret_cast<void *>(&signature_test_bridge), bridge_size);
            __builtin___clear_cache(memory, memory + 128 + bridge_size);
            assert(mprotect(memory + 1, page - 1, PROT_READ | PROT_EXEC) == -1 && errno == EINVAL);
            assert(mprotect(memory, page, PROT_READ | PROT_EXEC) == 0);
            // Unpackers keep their code writable while it runs; the hooks must still treat it as code.
            // NOLINTNEXTLINE(clang-analyzer-security.MmapWriteExec)
            assert(mprotect(memory, page, PROT_READ | PROT_WRITE | PROT_EXEC) == 0);
            code = {
                .raw = reinterpret_cast<raw_call>(memory),
                .libc = reinterpret_cast<libc_call>(memory + 128),
            };
            expect_read(code, path, false);
            expect_read(code, path, true);
        }

        // Changing a foreign code page's permissions must not make libc a target.
        const auto libc_page = reinterpret_cast<uintptr_t>(read) & ~(page - 1);
        assert(mprotect(reinterpret_cast<void *>(libc_page), page, PROT_READ | PROT_EXEC) == 0);
        // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
        const char *const foreign = getenv("SIGNATURE_FOREIGN_MAPPING");
        assert(foreign != nullptr);
        const auto foreign_page = static_cast<uintptr_t>(strtoull(foreign, nullptr, 10));
        assert(mprotect(reinterpret_cast<void *>(foreign_page), page, PROT_READ | PROT_EXEC) == 0);
    }
} // namespace

extern "C" __attribute__((visibility("default"))) void signature_verify_unpacked_reads() {
    // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
    const char *const path = getenv("SIGNATURE_UNPACKED_APK");
    assert(path != nullptr);
    for (const auto &code : copies) {
        expect_read(code, path, false);
        expect_read(code, path, true);
    }

    FILE *const file = fopen(path, "re");
    assert(file != nullptr);
    std::array<char, 32> bytes{};
    assert(fread(bytes.data(), 1, bytes.size(), file) == 17);
    assert(strcmp(bytes.data(), "original contents") == 0);
    assert(fseek(file, -8, SEEK_END) == 0);
    bytes.fill('\0');
    assert(fread(bytes.data(), 1, bytes.size(), file) == 8);
    assert(strcmp(bytes.data(), "contents") == 0);
    assert(fclose(file) == 0);
}

extern "C" __attribute__((visibility("default"))) const char *
signature_unpacked_status(const char *const path) {
    static std::string status;
    status.clear();
    const long descriptor = copies.front().libc(reinterpret_cast<void *>(static_cast<open_function>(open)),
                                                reinterpret_cast<long>(path), O_RDONLY, 0, 0, 0, 0);
    assert(descriptor >= 0);
    std::array<char, 1024> bytes{};
    while (true) {
        const long length = copies.front().libc(reinterpret_cast<void *>(read), descriptor,
                                                reinterpret_cast<long>(bytes.data()), bytes.size(), 0, 0, 0);
        assert(length >= 0);
        if (length == 0) break;
        status.append(bytes.data(), static_cast<size_t>(length));
    }
    assert(copies.front().libc(reinterpret_cast<void *>(close), descriptor, 0, 0, 0, 0, 0) == 0);
    return status.c_str();
}

extern "C" __attribute__((visibility("default"))) void signature_verify_internal_reads() {
    // NOLINTNEXTLINE(concurrency-mt-unsafe): setup is single-threaded; the environment stays read-only.
    const char *const path = getenv("SIGNATURE_UNPACKED_APK");
    expect_read(copies.front(), path, false, "installed");
    expect_read(copies.front(), path, true, "installed");
}

#ifdef __aarch64__
namespace {
    ssize_t (*forwarded_read)(int, void *, size_t) = nullptr;
}

extern "C" __attribute__((visibility("default"))) void signature_set_read_forwarder(void *const entry) {
    forwarded_read = reinterpret_cast<decltype(forwarded_read)>(entry);
}

extern "C" __attribute__((visibility("default"), noinline)) ssize_t
signature_forward_read(const int descriptor, void *const buffer, const size_t count) {
    // Keep a real forwarding frame so the old caller-only classifier sees this target image.
    const volatile ssize_t length = forwarded_read(descriptor, buffer, count);
    return length;
}
#endif
