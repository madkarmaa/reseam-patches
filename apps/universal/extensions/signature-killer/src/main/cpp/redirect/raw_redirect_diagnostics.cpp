// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include "logging.h"
#include <fcntl.h>
#include <pthread.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <array>
#include <cerrno>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        // Bound redirected and preserved events independently.
        constexpr unsigned reported_event_limit = 256;

        struct syscall_event {
            int syscall;
            long result;
            // A negative index means the original path was preserved.
            int mapping;
        };

        // Names a trapped syscall for the log, which runs on the logger thread.
        const char *syscall_name(const int number) {
            if (number == __NR_readlinkat) return "readlinkat";
            if (number == __NR_read) return "read";
            if (number == __NR_pread64) return "pread64";
            if (number == __NR_close) return "close";
#ifdef __NR_mmap
            if (number == __NR_mmap) return "mmap";
#endif
#ifdef __NR_mmap2
            if (number == __NR_mmap2) return "mmap2";
#endif
#ifdef __NR_lseek
            if (number == __NR_lseek) return "lseek";
#endif
#ifdef __NR__llseek
            if (number == __NR__llseek) return "_llseek";
#endif
            if (number == __NR_fstat) return "fstat";
#ifdef __NR_open
            if (number == __NR_open) return "open";
#endif
#ifdef __NR_readlink
            if (number == __NR_readlink) return "readlink";
#endif
#ifdef __NR_fstat64
            if (number == __NR_fstat64) return "fstat64";
#endif
#ifdef __NR_newfstatat
            if (number == __NR_newfstatat) return "newfstatat";
#endif
#ifdef __NR_fstatat64
            if (number == __NR_fstatat64) return "fstatat64";
#endif
            return "openat";
        }

        // Logs the events the signal handler writes to the pipe, since the handler itself cannot log.
        void log_syscall_events(const int descriptor) {
            syscall_event event{};
            while (true) {
                const ssize_t length = read(descriptor, &event, sizeof(event));
                if (length < 0 && errno == EINTR) continue;
                if (length != sizeof(event)) break;

                LOG_INFO("Raw %s %s: mapping %d, kernel result %ld", syscall_name(event.syscall),
                         event.mapping >= 0 ? "redirected" : "preserved", event.mapping, event.result);
            }

            close(descriptor);
        }

    } // namespace

    // Without a logger thread the handler reports nothing.
    void start_event_logger(redirect_state &state) {
        std::array<int, 2> descriptors{};
        if (pipe2(descriptors.data(), O_CLOEXEC) != 0) return;

        // A full pipe must drop events rather than block the trapped thread.
        pthread_t logger{};
        const auto [read_pipe, write_pipe] = descriptors;
        if (fcntl(write_pipe, F_SETFL, O_NONBLOCK) == 0 &&
            pthread_create(
                &logger, nullptr,
                [](void *const descriptor_argument) -> void * {
                    log_syscall_events(static_cast<int>(reinterpret_cast<intptr_t>(descriptor_argument)));
                    return nullptr;
                },
                reinterpret_cast<void *>(static_cast<intptr_t>(read_pipe))) == 0) {

            pthread_detach(logger);

            state.event_pipe.store(write_pipe, std::memory_order_relaxed);

            return;
        }

        close(read_pipe);
        close(write_pipe);
    }

    // Hands the event to the logger thread. A full pipe drops it.
    void report_event(redirect_state &state, const int syscall, const long result, const int mapping) {
        const int event_pipe = state.event_pipe.load(std::memory_order_relaxed);
        if (event_pipe < 0) return;

        if (auto &counter = mapping >= 0 ? state.redirected_events : state.preserved_events;
            counter.fetch_add(1, std::memory_order_relaxed) >= reported_event_limit)
            return;

        syscall_event event{.syscall = syscall, .result = result, .mapping = mapping};
        signature_raw_syscall(__NR_write, event_pipe, reinterpret_cast<long>(&event), sizeof(event), 0, 0, 0);
    }

} // namespace signature::redirect
