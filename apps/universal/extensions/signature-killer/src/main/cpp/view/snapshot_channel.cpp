// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view_internal.h"
#include <sys/socket.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <array>
#include <cerrno>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::view {
    void close_raw(const int descriptor) {
        if (descriptor >= 0) signature_raw_syscall(__NR_close, descriptor, 0, 0, 0, 0, 0);
    }

    // Each packet transfers at most one descriptor. The kernel consumes these bounded stack
    // buffers directly, including when the caller is running on the SIGSYS signal stack.
    long send_packet(const int socket, void *const payload, const size_t size, const int descriptor) {
        iovec data{.iov_base = payload, .iov_len = size};
        alignas(cmsghdr) std::array<unsigned char, CMSG_SPACE(sizeof(int))> control{};
        msghdr message{};
        message.msg_iov = &data;
        message.msg_iovlen = 1;

        if (descriptor >= 0) {
            message.msg_control = control.data();
            message.msg_controllen = control.size();
            // The control buffer is aligned and has room for this header and its descriptor.
            auto *const header = reinterpret_cast<cmsghdr *>(control.data());
            header->cmsg_level = SOL_SOCKET;
            header->cmsg_type = SCM_RIGHTS;
            header->cmsg_len = CMSG_LEN(sizeof(int));
            *reinterpret_cast<int *>(CMSG_DATA(header)) = descriptor;
        }

        long result = signature_raw_syscall(__NR_sendmsg, socket, reinterpret_cast<long>(&message),
                                            MSG_NOSIGNAL, 0, 0, 0);
        while (result == -EINTR)
            result = signature_raw_syscall(__NR_sendmsg, socket, reinterpret_cast<long>(&message),
                                           MSG_NOSIGNAL, 0, 0, 0);
        return result;
    }

    received_packet receive_packet(const int socket, void *const payload, const size_t size) {
        iovec data{.iov_base = payload, .iov_len = size};
        alignas(cmsghdr) std::array<unsigned char, CMSG_SPACE(sizeof(int))> control{};
        msghdr message{};
        message.msg_iov = &data;
        message.msg_iovlen = 1;
        message.msg_control = control.data();
        message.msg_controllen = control.size();

        long length = signature_raw_syscall(__NR_recvmsg, socket, reinterpret_cast<long>(&message),
                                            MSG_CMSG_CLOEXEC, 0, 0, 0);
        while (length == -EINTR) {
            message.msg_controllen = control.size();
            message.msg_flags = 0;
            length = signature_raw_syscall(__NR_recvmsg, socket, reinterpret_cast<long>(&message),
                                           MSG_CMSG_CLOEXEC, 0, 0, 0);
        }

        int descriptor = -1;
        unsigned descriptors = 0;
        bool valid = length >= 0 && (static_cast<unsigned>(message.msg_flags) &
                                     static_cast<unsigned>(MSG_TRUNC | MSG_CTRUNC)) == 0;

        // This control buffer has room for one cmsghdr only. On LP64 its alignment padding
        // can still contain a second int, so close every received FD before rejecting extras.
        if (const // The control buffer is aligned and has room for this header and its descriptor.
            auto *const header = reinterpret_cast<cmsghdr *>(control.data());
            header != nullptr) {
            if (const bool header_valid = CMSG_OK(&message, header);
                header->cmsg_level != SOL_SOCKET || header->cmsg_type != SCM_RIGHTS || !header_valid) {
                valid = false;
            } else {
                const size_t bytes = header->cmsg_len - CMSG_LEN(0);
                if (bytes % sizeof(int) != 0) valid = false;
                const std::span<const int> transferred(reinterpret_cast<const int *>(CMSG_DATA(header)),
                                                       bytes / sizeof(int));
                for (const int received : transferred) {
                    if (descriptors++ == 0) descriptor = received;
                    else close_raw(received);
                }
            }
        }

        if (descriptors > 1) valid = false;
        if (!valid) {
            close_raw(descriptor);
            descriptor = -1;
        }
        return {.length = length, .descriptor = descriptor, .valid = valid};
    }
} // namespace signature::view
