// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <fcntl.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <algorithm>
#include <array>
#include <charconv>
#include <climits>
#include <cstring>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        // Reading through a syscall only needs the address; the kernel reads through these words.
        // process_vm_readv and process_vm_writev take this layout by value in registers only, so a
        // read-only mirror of iovec avoids discarding constness for buffers the kernel never writes.
        struct readable_iovec {
            const void *base;
            std::size_t length;
        };

        static_assert(sizeof(readable_iovec) == sizeof(iovec));
        static_assert(alignof(readable_iovec) == alignof(iovec));
        static_assert(offsetof(readable_iovec, base) == offsetof(iovec, iov_base));
        static_assert(offsetof(readable_iovec, length) == offsetof(iovec, iov_len));

    } // namespace

    // The kernel copies the path, so an invalid pointer returns EFAULT instead of faulting in the
    // signal handler. A partial copy is enough when it includes a matching path's terminating NUL.
    long read_path(const char *const path, const std::span<char> buffer) {
        iovec local{.iov_base = buffer.data(), .iov_len = buffer.size()};
        const readable_iovec remote{.base = path, .length = buffer.size()};

        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_readv, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    // The handler relies on read_path, which a sandbox may forbid.
    bool path_copying_available(const char *const sample) {
        char copied = 0;
        if (read_path(sample, std::span(&copied, 1)) != 1) return false;
        return copied == sample[0];
    }

    namespace {
        // Volatile reads keep the compiler from replacing this loop with an out-of-line strcmp.
        bool copied_path_equals(const volatile char *const copied, const size_t length,
                                const char *const expected) {
            for (size_t offset = 0; offset < length && copied[offset] == expected[offset]; offset++) {
                if (copied[offset] == '\0') return true;
            }
            return false;
        }

    } // namespace

    // Returns the index of the mapping for the copied path, or -1 when nothing matches.
    int find_mapping(const redirect_state &state, const char *const copied, const long length) {
        if (length <= 0) return -1;

        const auto copied_length = static_cast<size_t>(length);
        const auto mapping = std::ranges::find_if(state.paths, [&](const raw_open_mapping &candidate) {
            return copied_path_equals(copied, copied_length, candidate.installed);
        });

        // ptrdiff_t is wider than int on 64-bit ABIs.
        return mapping == state.paths.end() ? -1
                                            // ReSharper disable once CppRedundantCastExpression
                                            : static_cast<int>(std::distance(state.paths.begin(), mapping));
    }

    long write_memory(const std::span<char> destination, const std::span<const char> source) {
        const readable_iovec local{.base = source.data(), .length = source.size()};
        iovec remote{.iov_base = destination.data(), .iov_len = destination.size()};

        const long pid = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
        return signature_raw_syscall(__NR_process_vm_writev, pid, reinterpret_cast<long>(&local), 1,
                                     reinterpret_cast<long>(&remote), 1, 0);
    }

    int find_replacement_mapping(const redirect_state &state, const char *const path, const size_t length) {
        const auto identity =
            std::ranges::find_if(state.identities, [&](const descriptor_identity &candidate) {
                return copied_path_equals(path, length, candidate.resolved_replacement.c_str());
            });

        // ptrdiff_t is wider than int on 64-bit ABIs.
        return identity == state.identities.end()
                   ? -1
                   // ReSharper disable once CppRedundantCastExpression
                   : static_cast<int>(std::distance(state.identities.begin(), identity));
    }

    int find_descriptor_mapping(const redirect_state &state, const long descriptor) {
        if (descriptor < 0) return -1;

        constexpr size_t descriptor_prefix_length = sizeof("/proc/self/fd/") - 1;
        std::array<char, 64> path{};
        constexpr auto descriptor_prefix = std::to_array("/proc/self/fd/");

        memcpy(path.data(), descriptor_prefix.data(), descriptor_prefix_length);

        const auto [number_end, number_error] =
            std::to_chars(path.data() + descriptor_prefix_length, path.data() + path.size() - 1, descriptor);

        if (number_error != std::errc{}) return -1;

        *number_end = '\0';
        // readlinkat fills the used prefix. Avoid zeroing PATH_MAX bytes on the signal stack.
        std::array<char, PATH_MAX> resolved;
        const long length = signature_raw_syscall(
            __NR_readlinkat, AT_FDCWD, reinterpret_cast<long>(path.data()),
            reinterpret_cast<long>(resolved.data()), resolved.size() - 1, replay_marker, 0);

        if (length <= 0) return -1;

        // The syscall's capacity reserves this terminator inside the array.
        element_at(resolved, static_cast<size_t>(length)) = '\0';

        return state.redirect_file_contents ? find_mapping(state, resolved.data(), length + 1)
                                            : find_replacement_mapping(state, resolved.data(), length + 1);
    }

    int find_stat_path_mapping(const redirect_state &state, const long directory, const char *const path,
                               const long length) {
        if (length <= 0 || path[0] == '\0') return -1;
        if (path[0] == '/') return find_mapping(state, path, length);

        std::array<char, 64> descriptor_path{};
        constexpr auto prefix = std::to_array("/proc/self/fd/");

        memcpy(descriptor_path.data(), prefix.data(), prefix.size() - 1);
        const auto [number_end, number_error] =
            std::to_chars(descriptor_path.data() + prefix.size() - 1,
                          descriptor_path.data() + descriptor_path.size() - 1, directory);

        if (number_error != std::errc{}) return -1;

        *number_end = '\0';

        // Resolve the directory only; the kernel still handles the caller's flags and errors.
        std::array<char, PATH_MAX> absolute;
        const long prefix_length = signature_raw_syscall(
            __NR_readlinkat, AT_FDCWD,
            reinterpret_cast<long>(directory == AT_FDCWD ? "/proc/self/cwd" : descriptor_path.data()),
            reinterpret_cast<long>(absolute.data()), absolute.size() - 1, replay_marker, 0);

        if (prefix_length <= 0) return -1;

        auto offset = static_cast<size_t>(prefix_length);

        if (element_at(absolute, offset - 1) != '/') element_at(absolute, offset++) = '/';

        for (size_t index = 0; std::cmp_less(index, length) && offset < absolute.size(); index++) {
            element_at(absolute, offset++) = path[index];

            if (path[index] == '\0') return find_mapping(state, absolute.data(), static_cast<long>(offset));
        }

        return -1;
    }

} // namespace signature::redirect
