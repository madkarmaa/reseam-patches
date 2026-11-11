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
#include <atomic>
#include <thread>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    namespace {
        int (*const open_file)(const char *, int, ...) = open;

    } // namespace

    void file_redirect_test::install_redirect() {
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
        if (redirect_contents)
            paths.push_back(
                {.installed = large_installed_path.c_str(), .replacement = large_replacement_path.c_str()});
        diagnostics.store(true, std::memory_order_relaxed);
        assert(
            install_raw_open_redirect(library.c_str(), paths,
                                      {.start = reinterpret_cast<uintptr_t>(gateway),
                                       .end = reinterpret_cast<uintptr_t>(gateway) + gateway_size,
                                       .preserve_descriptor_identity = preserve_identity,
                                       .redirect_file_contents = redirect_contents,
                                       .snapshot_proc_file = redirect_contents ? proc_snapshot : nullptr}));
        diagnostics.store(false, std::memory_order_relaxed);
        proceed.store(true, std::memory_order_release);
        existing_thread.join();
    }

    int file_redirect_test::check_contents() const {
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
            const int unrelated = open_file(unrelated_path.c_str(), O_RDONLY | O_CLOEXEC);
            assert(unrelated == recycled);
            expect_contents(unrelated, "unrelated contents");
            assert(close(unrelated) == 0);
        }
        return redirected;
    }
} // namespace signature::tests
