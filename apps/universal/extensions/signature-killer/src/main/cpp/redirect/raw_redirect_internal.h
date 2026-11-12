// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/seccomp_open.h"
#include <linux/audit.h>
#include <linux/filter.h>
#include <sys/stat.h>
#include <atomic>
#include <csignal>
#include <expected>
#include <memory>
#include <span>
#include <string>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

// Returns a kernel result without changing errno. Safe to call on the signal stack.
extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

struct sigcontext;
// Continues an interrupted context without rt_sigreturn, restoring the given floating-point state when
// it is not null. On arm64 it clobbers x16 and x17.
extern "C" [[noreturn]] void signature_resume_context(const sigcontext *registers, const void *floating_point);

namespace signature::redirect {
#ifdef __aarch64__
    inline constexpr uint32_t syscall_arch = AUDIT_ARCH_AARCH64;
#elifdef __arm__
    inline constexpr uint32_t syscall_arch = AUDIT_ARCH_ARM;
#else
#error Unsupported raw syscall architecture
#endif

    // Passed in the unused fifth argument of intercepted syscalls, so the filter lets the
    // handler's own replayed calls through.
    inline constexpr uint32_t replay_marker = 0x534b4150;
    // Reported in si_errno, so the handler recognizes traps raised by this filter.
    inline constexpr uint16_t trap_tag = 0x534b;

    struct descriptor_identity {
        std::string resolved_replacement;
        struct stat installed{};
        struct stat replacement{};
        int original_descriptor = -1;
    };

    // The filter indexes descriptors by number, so the table must stay a runtime-sized array.
    class descriptor_table {
      public:
        void reset(const size_t count) { entries.reset(new std::atomic<int>[count]); }

        [[nodiscard]] std::atomic<int> &slot(const size_t descriptor) const {
            return *(entries.get() + descriptor);
        }

      private:
        struct table_deleter {
            void operator()(const std::atomic<int> *const descriptors) const { delete[] descriptors; }
        };

        std::unique_ptr<std::atomic<int>, table_deleter> entries;
    };

    struct executable_range {
        uintptr_t start;
        uintptr_t end;
        const executable_range *next;
    };

    struct redirect_state {
        std::string library;
        // The span of the library's executable segments.
        uintptr_t start = UINTPTR_MAX;
        uintptr_t end = 0;
        // Published once per unpacked mapping. Readers never allocate or take a lock.
        std::atomic<const executable_range *> unpacked_ranges{nullptr};
        std::atomic<unsigned> filters{0};
        // The redirect filter held back until the library's first fork, while it still waits.
        std::vector<sock_filter> deferred_filter;
        std::atomic<bool> filter_deferred{false};
        // Loaded libraries with that basename. Exactly one is required.
        unsigned libraries_found = 0;
        // Set once the filter is in place. An installed state is never replaced.
        bool installed = false;
        std::vector<raw_open_mapping> paths;
        bool preserve_descriptor_identity = false;
        bool redirect_file_contents = false;
        int (*snapshot_proc_file)(proc_file, int) = nullptr;
        bool (*bypass_current_thread)() = nullptr;
        int installing_process = 0;
        size_t descriptor_count = 0;
        descriptor_table proc_descriptors;
        std::vector<descriptor_identity> identities;
        // Receives every SIGSYS this filter did not raise.
        struct sigaction previous_handler{};
        // The write end of the logger thread's pipe, or -1 when diagnostics are off.
        std::atomic<int> event_pipe{-1};
        std::atomic<unsigned> redirected_events{0};
        std::atomic<unsigned> preserved_events{0};
    };

    // Raw register values; each syscall handler assigns their syscall-specific meaning.
    struct syscall_arguments {
        long first;
        long second;
        long third;
        long fourth;
        long fifth;
        long sixth;
    };

    // Every index below was validated against its container's size, and at() could throw on the
    // signal stack, so iteration stays unchecked.
    template <typename Values> auto &element_at(Values &values, const size_t index) {
        auto position = values.begin() + static_cast<std::ptrdiff_t>(index);
        return *position;
    }

    extern std::atomic<redirect_state *> active;

    struct filter_failure {
        const char *cause;
        long value;
    };

    std::vector<sock_filter> build_filter(uintptr_t start, uintptr_t end, bool preserve_identity,
                                          bool redirect_contents);
    // Traps plain forks, so the redirect filter can be installed after the first one.
    std::vector<sock_filter> build_fork_filter();
    std::expected<void, filter_failure> install_program(std::span<sock_filter> instructions);
    std::expected<void, filter_failure> install_filter(uintptr_t start, uintptr_t end, bool preserve_identity,
                                                       bool redirect_contents);
    bool install_sigsys_handler();
    bool contains_address(const redirect_state &state, uintptr_t address);
    bool supports_syscall(const redirect_state &state, int number);
    bool is_open_syscall(int number);
    bool is_readlink_syscall(int number);
    bool is_fstat_syscall(int number);
    bool is_fstatat_syscall(int number);
    bool is_mmap_syscall(int number);
    bool prepare_descriptor_identities(redirect_state &state);
    bool path_copying_available(const char *sample);
    long read_path(const char *path, std::span<char> buffer);
    long write_memory(std::span<char> destination, std::span<const char> source);
    int find_mapping(const redirect_state &state, const char *copied, long length);
    int find_replacement_mapping(const redirect_state &state, const char *path, size_t length);
    int find_descriptor_mapping(const redirect_state &state, long descriptor);
    int find_stat_path_mapping(const redirect_state &state, long directory, const char *path, long length);
    long redirect_metadata(const redirect_state &state, int number, const syscall_arguments &arguments,
                           int &mapping);
    long redirect_content_io(const redirect_state &state, int number, const syscall_arguments &arguments,
                             int &mapping);
    long redirect_open(const redirect_state &state, int number, const syscall_arguments &arguments,
                       int &mapping);
    long redirect_file_syscall(redirect_state &state, int number, const syscall_arguments &arguments);
    void start_event_logger(redirect_state &state);
    void report_event(redirect_state &state, int syscall, long result, int mapping);
} // namespace signature::redirect
