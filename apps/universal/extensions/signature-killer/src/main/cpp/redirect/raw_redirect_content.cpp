// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <cerrno>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        long read_original(const int descriptor, const long buffer, const long count, const int64_t offset) {
#ifdef __arm__
            return signature_raw_syscall(
                __NR_pread64, descriptor, buffer, count, 0, static_cast<long>(static_cast<uint32_t>(offset)),
                static_cast<long>(static_cast<uint32_t>(static_cast<uint64_t>(offset) >> 32U)));
#else
            return signature_raw_syscall(__NR_pread64, descriptor, buffer, count, offset, 0, 0);
#endif
        }

        std::expected<int64_t, long> current_position(const long descriptor) {
#ifdef __arm__
            int64_t position = 0;
            const long result = signature_raw_syscall(__NR__llseek, descriptor, 0, 0,
                                                      reinterpret_cast<long>(&position), SEEK_CUR, 0);
            if (result < 0) return std::unexpected(result);
            return position;
#else
            const long position = signature_raw_syscall(__NR_lseek, descriptor, 0, SEEK_CUR, 0, 0, 0);
            if (position < 0) return std::unexpected(position);
            return position;
#endif
        }

        void advance_position(const long descriptor, const long count) {
#ifdef __arm__
            int64_t position = 0;
            static_cast<void>(signature_raw_syscall(__NR__llseek, descriptor, 0, count,
                                                    reinterpret_cast<long>(&position), SEEK_CUR, 0));
#else
            static_cast<void>(signature_raw_syscall(__NR_lseek, descriptor, count, SEEK_CUR, 0, 0, 0));
#endif
        }

        // Reads the original bytes behind a replaced descriptor, at the same absolute offset.
        std::optional<long> read_replaced_bytes(const redirect_state &state, const int number,
                                                const syscall_arguments &arguments) {
            const long descriptor = arguments.first;

            if (descriptor < 0 || !std::cmp_less(descriptor, state.descriptor_count)) return std::nullopt;

            const auto slot = static_cast<size_t>(descriptor);
            const int snapshot =
                number == __NR_close
                    ? state.proc_descriptors.slot(slot).exchange(-1, std::memory_order_acq_rel)
                    : state.proc_descriptors.slot(slot).load(std::memory_order_acquire);

            if (snapshot >= 0 && number == __NR_close)
                signature_raw_syscall(__NR_close, snapshot, 0, 0, 0, 0, 0);

            if (snapshot >= 0 && number == __NR_pread64)
                return signature_raw_syscall(number, snapshot, arguments.second, arguments.third,
                                             arguments.fourth, arguments.fifth, arguments.sixth);

            if (snapshot >= 0 && number == __NR_read) {
                const auto position = current_position(descriptor);
                if (!position) return position.error();

                const long result = read_original(snapshot, arguments.second, arguments.third, *position);

                if (result > 0) advance_position(descriptor, result);

                return result;
            }

            return std::nullopt;
        }

    } // namespace

    long redirect_content_io(const redirect_state &state, const int number,
                             const syscall_arguments &arguments, int &mapping) {
        if (is_mmap_syscall(number)) {
            // Anonymous mappings ignore the descriptor. Only file mappings receive original bytes.
            if (const auto flags = static_cast<unsigned long>(arguments.fourth);
                (flags & static_cast<unsigned long>(MAP_ANONYMOUS)) == 0)
                mapping = find_descriptor_mapping(state, arguments.fifth);

            const long descriptor =
                mapping < 0 ? arguments.fifth
                            : element_at(state.identities, static_cast<size_t>(mapping)).original_descriptor;

            return signature_raw_syscall(number, arguments.first, arguments.second, arguments.third,
                                         arguments.fourth, descriptor, arguments.sixth);
        }

        if (const std::optional<long> handled = read_replaced_bytes(state, number, arguments);
            handled.has_value())
            return handled.value();

        const long descriptor = arguments.first;

        // Resolve each use so descriptors opened before installation, aliases and dup() work,
        // and a close/reuse outside the protected library cannot leave a stale mapping.
        mapping = find_descriptor_mapping(state, descriptor);
        if (mapping < 0 || number == __NR_close)
            return signature_raw_syscall(number, arguments.first, arguments.second, arguments.third,
                                         arguments.fourth, arguments.fifth, arguments.sixth);

        const auto &identity = element_at(state.identities, static_cast<size_t>(mapping));

        if (number == __NR_pread64)
            return signature_raw_syscall(number, identity.original_descriptor, arguments.second,
                                         arguments.third, arguments.fourth, arguments.fifth, arguments.sixth);
#ifdef __NR__llseek
        if (number == __NR__llseek) {
            if (arguments.fifth != SEEK_END)
                return signature_raw_syscall(number, descriptor, arguments.second, arguments.third,
                                             arguments.fourth, arguments.fifth, 0);

            const uint64_t offset = (static_cast<uint64_t>(static_cast<uint32_t>(arguments.second)) << 32U) |
                                    static_cast<uint32_t>(arguments.third);
            const uint64_t position = static_cast<uint64_t>(identity.replacement.st_size) + offset;
            return signature_raw_syscall(
                number, descriptor, static_cast<long>(static_cast<uint32_t>(position >> 32U)),
                static_cast<long>(static_cast<uint32_t>(position)), arguments.fourth, SEEK_SET, 0);
        }
#endif
#ifdef __NR_lseek
        if (number == __NR_lseek) {
            if (arguments.third != SEEK_END)
                return signature_raw_syscall(number, descriptor, arguments.second, arguments.third, 0, 0, 0);

            return signature_raw_syscall(number, descriptor,
                                         static_cast<long>(identity.replacement.st_size + arguments.second),
                                         SEEK_SET, 0, 0, 0);
        }

        const auto position = current_position(descriptor);
        if (!position) return position.error();

        const long result =
            read_original(identity.original_descriptor, arguments.second, arguments.third, *position);

        if (result > 0) advance_position(descriptor, result);

        return result;
#else
        return -ENOSYS;
#endif
    }

} // namespace signature::redirect
