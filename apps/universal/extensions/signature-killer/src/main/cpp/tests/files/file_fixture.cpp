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
#include <cstdlib>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    namespace {
        int (*const open_file)(const char *, int, ...) = open;

        void create_file(const std::string &path, const char *const contents) {
            const int descriptor = open_file(path.c_str(), O_CREAT | O_TRUNC | O_RDWR | O_CLOEXEC, 0600);
            assert(descriptor >= 0);
            const auto size = strlen(contents);
            assert(write(descriptor, contents, size) == static_cast<ssize_t>(size));
            assert(close(descriptor) == 0);
        }

    } // namespace

    void file_redirect_test::prepare_files(const char *parent, const char *mode) {
        install_after_fork = strcmp(mode, "fork") == 0;
        redirect_contents = install_after_fork || strcmp(mode, "contents") == 0;
        preserve_identity = redirect_contents || strcmp(mode, "identity") == 0;
        page_size = static_cast<size_t>(sysconf(_SC_PAGESIZE));
        // Production puts the protected library and handler in separate executable ranges. Keep
        // that distinction in a static test, whose bionic and handler otherwise share one ELF.
        gateway = mmap(nullptr, page_size, PROT_READ | PROT_WRITE, MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
        assert(gateway != MAP_FAILED);
        memcpy(gateway, reinterpret_cast<const void *>(&signature_raw_syscall), gateway_size);
        __builtin___clear_cache(static_cast<char *>(gateway), static_cast<char *>(gateway) + gateway_size);
        assert(mprotect(gateway, page_size, PROT_READ | PROT_EXEC) == 0);
        protected_syscall = reinterpret_cast<decltype(protected_syscall)>(gateway);
        directory = std::string(parent) + "/signature-files-XXXXXX";
        assert(mkdtemp(directory.data()) != nullptr);
        installed_path = directory + "/base.apk";
        replacement_path = directory + "/original.apk";
        unrelated_path = directory + "/unrelated";
        symlink_path = directory + "/base.link";
        large_installed_path = directory + "/large.apk";
        large_replacement_path = directory + "/large-original.apk";
        create_file(installed_path, "installed");
        create_file(replacement_path, "original contents");
        create_file(unrelated_path, "unrelated contents");
        if (redirect_contents) {
            create_file(large_installed_path, "installed");
            const int descriptor =
                open_file(large_replacement_path.c_str(), O_CREAT | O_RDWR | O_CLOEXEC, 0600);
            assert(descriptor >= 0);
            assert(ftruncate64(descriptor, large_offset + 4096) == 0);
            assert(pwrite64(descriptor, large_contents.data(), large_contents.size(), large_offset) ==
                   large_contents.size());
            assert(close(descriptor) == 0);
        }
        assert(symlink(installed_path.c_str(), symlink_path.c_str()) == 0);
        // A host user namespace may map only the caller's own UID even when geteuid() is zero.
        if (geteuid() == 0 && chown(installed_path.c_str(), 1000, 1000) == 0) {
            assert(chown(replacement_path.c_str(), 2000, 2000) == 0);
        }

        installed_descriptor = open_file(installed_path.c_str(), O_RDONLY | O_CLOEXEC);
        replacement_descriptor = open_file(replacement_path.c_str(), O_RDONLY | O_CLOEXEC);
        directory_descriptor = open_file(directory.c_str(), O_RDONLY | O_DIRECTORY | O_CLOEXEC);
        assert(installed_descriptor >= 0 && replacement_descriptor >= 0);
        assert(directory_descriptor >= 0);
        expect_contents(replacement_descriptor, "original contents");
        assert(lseek(replacement_descriptor, 0, SEEK_SET) == 0);
    }

    void file_redirect_test::capture_metadata() {

        assert(fstat(installed_descriptor, &installed) == 0);
        assert(fstat(replacement_descriptor, &replacement) == 0);
        replacement_size = replacement.st_size;
        installed_at = snapshot_stat_at(AT_FDCWD, installed_path.c_str(), 0);
        replacement_at = snapshot_stat_at(AT_FDCWD, replacement_path.c_str(), 0);
        unrelated_at = snapshot_stat_at(AT_FDCWD, unrelated_path.c_str(), 0);
        symlink_at = snapshot_stat_at(AT_FDCWD, symlink_path.c_str(), AT_SYMLINK_NOFOLLOW);
        bad_stat_output =
            stat_at(AT_FDCWD, installed_path.c_str(), 0, reinterpret_cast<stat_at_metadata *>(1));
        bad_stat_path = stat_at(AT_FDCWD, reinterpret_cast<const char *>(1), 0, &error_output);
        missing_stat_path = stat_at(directory_descriptor, "missing", 0, &error_output);
        bad_stat_directory = stat_at(-1, "base.apk", 0, &error_output);
        empty_stat_path = stat_at(installed_descriptor, "", 0, &error_output);
        bad_stat_flags = stat_at(AT_FDCWD, installed_path.c_str(), 0x40000000, &error_output);
        const int metadata_descriptor = redirect_contents ? installed_descriptor : replacement_descriptor;
        expected_result =
            protected_syscall(__NR_fstat, metadata_descriptor, reinterpret_cast<long>(&expected), 0, 0, 0, 0);
        if (redirect_contents && expected_result == 0) expected.st_size = replacement_size;
        bad_pointer_result = protected_syscall(__NR_fstat, replacement_descriptor, 1, 0, 0, 0, 0);
#ifdef __NR_fstat64
        expected_result64 = protected_syscall(__NR_fstat64, metadata_descriptor,
                                              reinterpret_cast<long>(&expected64), 0, 0, 0, 0);
        if (redirect_contents && expected_result64 == 0) expected64.st_size = replacement_size;
#endif
    }

    void file_redirect_test::clean_up() const {
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
    }
} // namespace signature::tests
