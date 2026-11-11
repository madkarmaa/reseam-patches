// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view_internal.h"
#include <pthread.h>
#include <sys/socket.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <array>
#include <atomic>
#include <cerrno>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::view {
    library_view_options target_options;

    namespace {
        std::atomic<int> requests{-1};
        std::atomic<int> worker_socket{-1};
        static_assert(std::atomic<int>::is_always_lock_free);
        bool fork_handler_registered = false;

        struct snapshot_request {
            pid_t process;
            unsigned filters;
            proc_file file;
        };

        // A child keeps only the request client. Otherwise its inherited server endpoint could
        // keep a dead parent's channel open forever, even though that child has no worker thread.
        void close_inherited_worker() {
            close_raw(worker_socket.exchange(-1, std::memory_order_relaxed));
        }

        void *serve_snapshots(const int descriptor) {
            if (target_options.enter_worker != nullptr) target_options.enter_worker();

            while (true) {
                snapshot_request request{};
                const auto [received_length, reply_socket, valid] =
                    receive_packet(descriptor, &request, sizeof(request));
                if (received_length <= 0) {
                    close_raw(reply_socket);
                    break;
                }
                if (!valid || reply_socket < 0) continue;

                int snapshot = -EINVAL;
                if (std::cmp_equal(received_length, sizeof(request)) && request.process > 0 &&
                    (request.file == proc_file::maps || request.file == proc_file::status))
                    snapshot = create_proc_snapshot(request.file, request.process, request.filters);

                int error = snapshot < 0 ? snapshot : 0;
                static_cast<void>(send_packet(reply_socket, &error, sizeof(error), snapshot));
                close_raw(snapshot);
                close_raw(reply_socket);
            }

            // Shutdown reaches every remaining inherited client even if an unexpected receive
            // error ends the worker. A waiter then observes EOF instead of waiting indefinitely.
            signature_raw_syscall(__NR_shutdown, descriptor, SHUT_RDWR, 0, 0, 0, 0);
            worker_socket.store(-1, std::memory_order_relaxed);
            close_raw(descriptor);
            return nullptr;
        }
    } // namespace

} // namespace signature::view

using signature::view::capture_process_status;
using signature::view::close_inherited_worker;
using signature::view::close_raw;
using signature::view::fork_handler_registered;
using signature::view::install_module_view;
using signature::view::module_view_installed;
using signature::view::module_view_ready;
using signature::view::prepare_module_ranges;
using signature::view::receive_packet;
using signature::view::requests;
using signature::view::send_packet;
using signature::view::serve_snapshots;
using signature::view::snapshot_request;
using signature::view::target_options;
using signature::view::worker_socket;

bool prepare_library_view(const char *const library, const library_view_options &options) {
    if (module_view_installed()) return module_view_ready();

    if (!capture_process_status()) return false;
    target_options = options;

    if (!prepare_module_ranges(library)) return false;

    std::array<int, 2> sockets{};
    if (socketpair(AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0, sockets.data()) != 0) return false;

    if (!fork_handler_registered) {
        if (pthread_atfork(nullptr, nullptr, close_inherited_worker) != 0) {
            close(sockets.front());
            close(sockets.back());
            return false;
        }
        fork_handler_registered = true;
    }
    worker_socket.store(sockets.front(), std::memory_order_relaxed);

    pthread_t worker{};
    if (pthread_create(
            &worker, nullptr,
            [](void *const descriptor_argument) -> void * {
                return serve_snapshots(static_cast<int>(reinterpret_cast<intptr_t>(descriptor_argument)));
            },
            reinterpret_cast<void *>(static_cast<intptr_t>(sockets.front()))) != 0) {
        worker_socket.store(-1, std::memory_order_relaxed);
        close(sockets.front());
        close(sockets.back());
        return false;
    }

    pthread_detach(worker);

    requests.store(sockets.back(), std::memory_order_release);

    return install_module_view();
}

int snapshot_process_file(const proc_file file, const int requested_process) {
    const int request = requests.load(std::memory_order_acquire);
    if (request < 0) return -ENOSYS;
    std::array<int, 2> reply{};
    long result = signature_raw_syscall(__NR_socketpair, AF_UNIX, SOCK_SEQPACKET | SOCK_CLOEXEC, 0,
                                        reinterpret_cast<long>(reply.data()), 0, 0);

    if (result < 0) return static_cast<int>(result);

    const long process =
        requested_process > 0 ? requested_process : signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
    const unsigned filters = target_options.filter_count == nullptr ? 1 : target_options.filter_count();
    snapshot_request message{.process = static_cast<pid_t>(process), .filters = filters, .file = file};
    result = send_packet(request, &message, sizeof(message), reply.back());
    // Once sent, the worker owns a transferred copy of this endpoint. Keeping our own copy
    // would hide worker failure from recvmsg on the other end.
    close_raw(reply.back());

    if (!std::cmp_equal(result, sizeof(message))) {
        close_raw(reply.front());
        return result < 0 ? static_cast<int>(result) : -EIO;
    }

    int error = -EIO;
    const auto [received_length, snapshot, valid] = receive_packet(reply.front(), &error, sizeof(error));
    close_raw(reply.front());

    if (received_length < 0) return static_cast<int>(received_length);
    if (!valid || !std::cmp_equal(received_length, sizeof(error))) {
        close_raw(snapshot);
        return -EIO;
    }
    if (error == 0 && snapshot >= 0) return snapshot;

    close_raw(snapshot);
    return error < 0 && snapshot < 0 ? error : -EIO;
}
