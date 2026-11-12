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
#include <cstdio>
#include <print>
#include <cstdlib>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    namespace {
        template <int Number, typename Metadata>
        void expect_metadata(const int descriptor, const Metadata &original, const long original_result,
                             const struct stat &installed, const bool preserve_identity) {
            struct guarded_metadata {
                std::array<unsigned char, 16> before;
                Metadata value;
                std::array<unsigned char, 16> after;
            } observed{};

            observed.before.fill(0x5a);
            observed.after.fill(0xa5);
            const long result =
                protected_syscall(Number, descriptor, reinterpret_cast<long>(&observed.value), 0, 0, 0, 0);
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

    } // namespace

    void expect_link(const int number, const std::string &descriptor_path, const std::string &expected,
                     const size_t capacity) {
        std::array<char, 4096> result{};
        const long length =
            number == __NR_readlinkat
                ? protected_syscall(number, AT_FDCWD, reinterpret_cast<long>(descriptor_path.c_str()),
                                    reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0)
                : protected_syscall(number, reinterpret_cast<long>(descriptor_path.c_str()),
                                    reinterpret_cast<long>(result.data()), static_cast<long>(capacity), 0, 0,
                                    0);
        const size_t wanted = capacity < expected.size() ? capacity : expected.size();
        assert(std::cmp_equal(length, wanted));
        assert(memcmp(result.data(), expected.data(), wanted) == 0);
    }

    // The kernel writes through this output address after its syscall encoding.
    // NOLINTBEGIN(misc-const-correctness)
    long stat_at(const int directory, const char *const path, const int flags,
                 stat_at_metadata *const value) {
        return protected_syscall(stat_at_number, directory, reinterpret_cast<long>(path),
                                 reinterpret_cast<long>(value), flags, 0, 0);
    }

    // NOLINTEND(misc-const-correctness)

    stat_at_metadata snapshot_stat_at(const int directory, const char *const path, const int flags) {
        stat_at_metadata value{};
        assert(stat_at(directory, path, flags, &value) == 0);
        return value;
    }

    namespace {
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
            // The kernel writes every byte, padding included, and the redirect must return the same bytes.
            // NOLINTNEXTLINE(bugprone-suspicious-memory-comparison,cert-exp42-c,cert-flp37-c)
            if (memcmp(&observed.value, &expected, sizeof(expected)) != 0) {
                std::println(stderr, "stat-at metadata mismatch: {}", label);
                abort();
            }
        }

    } // namespace

    void file_redirect_test::check_metadata(const int redirected) {
        struct stat reported{};
        assert(fstat(redirected, &reported) == 0);
        if (redirect_contents) {
            // Outside the protected range, the descriptor and its mmap retain the installed file.
            // The kernel writes every byte, padding included, and the redirect must return the same bytes.
            // NOLINTNEXTLINE(bugprone-suspicious-memory-comparison,cert-exp42-c,cert-flp37-c)
            assert(memcmp(&reported, &installed, sizeof(installed)) == 0);
            void *const mapping = mmap(nullptr, page_size, PROT_READ, MAP_PRIVATE, redirected, 0);
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
        expect_metadata<__NR_fstat>(redirected, expected, expected_result, installed, preserve_identity);
#ifdef __NR_fstat64
        expect_metadata<__NR_fstat64>(redirected, expected64, expected_result64, installed,
                                      preserve_identity);
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
        assert(stat_at(AT_FDCWD, installed_path.c_str(), 0, reinterpret_cast<stat_at_metadata *>(1)) ==
               bad_stat_output);
        assert(stat_at(AT_FDCWD, reinterpret_cast<const char *>(1), 0, &error_output) == bad_stat_path);
        assert(stat_at(directory_descriptor, "missing", 0, &error_output) == missing_stat_path);
        assert(stat_at(-1, "base.apk", 0, &error_output) == bad_stat_directory);
        assert(stat_at(redirected, "", 0, &error_output) == empty_stat_path);
        assert(stat_at(AT_FDCWD, installed_path.c_str(), 0x40000000, &error_output) == bad_stat_flags);
        assert(protected_syscall(__NR_close, redirected, 0, 0, 0, 0, 0) == 0);
    }
} // namespace signature::tests
