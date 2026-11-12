// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "file_test.h"
#include "logging.h"
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <array>
#include <cstring>
#include <utility>
#include <linux/memfd.h>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    void expect_content_offsets(const int descriptor) {
#ifdef __NR_lseek
        std::array<char, 32> data{};
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_SET, 0, 0, 0) == 0);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()), 4, 0, 0, 0) ==
               4);
        assert(memcmp(data.data(), "orig", 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 4);
#ifdef __arm__
        const long positioned =
            protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(data.data()), 8, 0, 9, 0);
#else
        const long positioned =
            protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(data.data()), 8, 9, 0, 0);
#endif
        assert(positioned == 8);
        assert(memcmp(data.data(), "contents", 8) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 4);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()), 4, 0, 0, 0) ==
               4);
        assert(memcmp(data.data(), "inal", 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, -8, SEEK_END, 0, 0, 0) == 9);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()), data.size(), 0,
                                 0, 0) == 8);
        assert(memcmp(data.data(), "contents", 8) == 0);
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()), data.size(), 0,
                                 0, 0) == 0);
#endif
    }

    void expect_mapped_contents(const int descriptor, const size_t page_size) {
#ifdef __aarch64__
        constexpr int mapping_syscall = __NR_mmap;
#else
        constexpr int mapping_syscall = __NR_mmap2;
#endif
        const long result = protected_syscall(mapping_syscall, 0, static_cast<long>(page_size), PROT_READ,
                                              MAP_PRIVATE, descriptor, 0);
        assert(result < -4095 || result >= 0);
        auto *const mapped = reinterpret_cast<void *>(result);
        assert(memcmp(mapped, "original contents", 17) == 0);
        assert(munmap(mapped, page_size) == 0);

        // A mapped descriptor is ignored for MAP_ANONYMOUS, even when it names an APK.
        const long anonymous = protected_syscall(mapping_syscall, 0, static_cast<long>(page_size), PROT_READ,
                                                 MAP_PRIVATE | MAP_ANONYMOUS, descriptor, 0);
        assert(anonymous < -4095 || anonymous >= 0);
        assert(*reinterpret_cast<const char *>(anonymous) == '\0');
        assert(munmap(reinterpret_cast<void *>(anonymous), page_size) == 0);
    }

    int proc_snapshot(const proc_file file, int /*process*/) {
        const char *const content = file == proc_file::maps ? "maps snapshot" : "status snapshot";
        const long descriptor = signature_raw_syscall(__NR_memfd_create, reinterpret_cast<long>("proc-test"),
                                                      MFD_CLOEXEC, 0, 0, 0, 0);
        if (descriptor < 0) return static_cast<int>(descriptor);
        const long result = signature_raw_syscall(__NR_write, descriptor, reinterpret_cast<long>(content),
                                                  static_cast<long>(strlen(content)), 0, 0, 0);
        assert(std::cmp_equal(result, strlen(content)));
        return static_cast<int>(descriptor);
    }

    void expect_proc_snapshot(const char *const path, const char *const contents, const char *const file) {
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
        assert(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(buffer.data()), 3, 0, 0, 0) ==
               3);
        assert(memcmp(buffer.data(), contents, 3) == 0);
#ifdef __arm__
        const long result =
            protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(buffer.data()), 4, 0, 2, 0);
#else
        const long result =
            protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(buffer.data()), 4, 2, 0, 0);
#endif
        assert(result == 4 && memcmp(buffer.data(), contents + 2, 4) == 0);
        assert(protected_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0) == 3);
        expect_contents(descriptor, contents + 3);
#endif
        assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
    }

    // Raw syscalls update integer-encoded output pointers; CLion cannot model the seek result.
    // ReSharper disable CppDFAConstantParameter, CppDFAUnreachableCode
    namespace {
        int64_t seek_large_file(const int descriptor, const int64_t offset, const int origin) {
#ifdef __arm__
            int64_t position = -1;
            assert(protected_syscall(__NR__llseek, descriptor, static_cast<uint64_t>(offset) >> 32U,
                                     static_cast<uint32_t>(offset), reinterpret_cast<long>(&position), origin,
                                     0) == 0);
            return position;
#else
            const long position = protected_syscall(__NR_lseek, descriptor, offset, origin, 0, 0, 0);
            assert(position >= 0);
            return position;
#endif
        }

    } // namespace

    void expect_large_file_contents(const char *const path, const size_t page_size) {
        const int descriptor = raw_open(__NR_openat, path);
        std::array<char, large_contents.size()> buffer{};
        const auto read_contents = [&] {
            assert(std::cmp_equal(protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(buffer.data()),
                                                    buffer.size(), 0, 0, 0),
                                  large_contents.size()));
            assert(memcmp(buffer.data(), large_contents.data(), large_contents.size()) == 0);
        };
        assert(seek_large_file(descriptor, large_offset, SEEK_SET) == large_offset);
        read_contents();
        assert(seek_large_file(descriptor, -4096, SEEK_END) == large_offset);
        read_contents();
#ifdef __arm__
        const long result =
            protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(buffer.data()), buffer.size(),
                              0, static_cast<uint32_t>(large_offset), static_cast<uint64_t>(large_offset) >> 32U);
        constexpr int mapping_syscall = __NR_mmap2;
        // mmap2 takes the offset in 4096-byte units.
        constexpr long mapping_offset = large_offset / 4096;
#else
        const long result = protected_syscall(__NR_pread64, descriptor, reinterpret_cast<long>(buffer.data()),
                                              buffer.size(), large_offset, 0, 0);
        constexpr int mapping_syscall = __NR_mmap;
        constexpr long mapping_offset = large_offset;
#endif
        assert(std::cmp_equal(result, large_contents.size()));
        assert(memcmp(buffer.data(), large_contents.data(), large_contents.size()) == 0);
        const long mapping = protected_syscall(mapping_syscall, 0, static_cast<long>(page_size), PROT_READ,
                                               MAP_PRIVATE, descriptor, mapping_offset);
        assert(mapping < -4095 || mapping >= 0);
        assert(memcmp(reinterpret_cast<void *>(mapping), large_contents.data(), large_contents.size()) == 0);
        assert(munmap(reinterpret_cast<void *>(mapping), page_size) == 0);
        assert(protected_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0) == 0);
    }

    // ReSharper restore CppDFAConstantParameter, CppDFAUnreachableCode
} // namespace signature::tests
