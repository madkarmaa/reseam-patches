// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/kernel_stat.h"
#include "redirect/seccomp_open.h"
#include <link.h>
#include <sys/stat.h>
#include <sys/syscall.h>
#include <array>
#include <csignal>
#include <string>

// Retain every assertion when building the device tests in Release mode.
#ifdef NDEBUG
#undef NDEBUG
#endif
#include <cassert>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace signature::tests {
    extern decltype(&signature_raw_syscall) protected_syscall;
    inline constexpr int64_t large_offset = 0x100010000LL;
    inline constexpr auto large_contents = std::to_array("large offset");
    inline constexpr size_t gateway_size = 64;
#ifdef __NR_newfstatat
    inline constexpr int stat_at_number = __NR_newfstatat;
    using stat_at_metadata = signature_kernel_stat;
#else
    inline constexpr int stat_at_number = __NR_fstatat64;
    using stat_at_metadata = signature_kernel_stat64;
#endif

    extern volatile sig_atomic_t previous_signal;
    void previous_handler(int signal);
    int find_executable(dl_phdr_info *info, size_t size, void *data);
    int raw_open(int number, const char *path);
    void expect_contents(int descriptor, const char *contents);
    void expect_content_offsets(int descriptor);
    void expect_mapped_contents(int descriptor, size_t page_size);
    int proc_snapshot(proc_file file, int process);
    void expect_proc_snapshot(const char *path, const char *contents, const char *file);
    void expect_large_file_contents(const char *path, size_t page_size);
    void expect_link(int number, const std::string &descriptor_path, const std::string &expected,
                     size_t capacity);
    long stat_at(int directory, const char *path, int flags, stat_at_metadata *value);
    stat_at_metadata snapshot_stat_at(int directory, const char *path, int flags);

    class file_redirect_test {
      public:
        void prepare_files(const char *parent, const char *mode);
        void capture_metadata();
        void install_redirect();
        [[nodiscard]] int check_contents() const;
        void check_metadata(int redirected);
        void clean_up() const;

      private:
        bool redirect_contents = false;
        bool preserve_identity = false;
        size_t page_size = 0;
        void *gateway = nullptr;
        std::string directory, installed_path, replacement_path, unrelated_path, symlink_path;
        std::string large_installed_path, large_replacement_path;
        int installed_descriptor = -1, replacement_descriptor = -1, directory_descriptor = -1;
        struct stat installed{}, replacement{};
        off_t replacement_size = 0;
        stat_at_metadata installed_at{}, replacement_at{}, unrelated_at{}, symlink_at{}, error_output{};
        long bad_stat_output = 0, bad_stat_path = 0, missing_stat_path = 0;
        long bad_stat_directory = 0, empty_stat_path = 0, bad_stat_flags = 0;
        signature_kernel_stat expected{};
        long expected_result = 0, bad_pointer_result = 0;
#ifdef __NR_fstat64
        signature_kernel_stat64 expected64{};
        long expected_result64 = 0;
#endif
    };
} // namespace signature::tests
