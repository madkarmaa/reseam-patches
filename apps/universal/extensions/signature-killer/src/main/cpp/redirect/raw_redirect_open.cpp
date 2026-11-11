// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <sys/syscall.h>
#include <array>
#include <charconv>
#include <climits>
#include <string_view>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    // Replays a trapped open against the mapped replacement path.
    long redirect_open(const redirect_state &state, const int number, const syscall_arguments &arguments,
                       int &mapping) {
        const bool has_directory = number == __NR_openat;
        const auto *const requested_path =
            reinterpret_cast<const char *>(has_directory ? arguments.second : arguments.first);

        // Zeroing would cost a full PATH_MAX write per trapped open; read_path fills what is used.
        std::array<char, PATH_MAX> copied;
        const long length = read_path(requested_path, std::span(copied));
        mapping = find_mapping(state, copied.data(), length);

        // find_mapping returns a validated path-table index, or -1 for an unmatched path.
        const char *const path = mapping >= 0 && !state.redirect_file_contents
                                     ? element_at(state.paths, static_cast<size_t>(mapping)).replacement
                                     : requested_path;

        const long result =
            has_directory ? signature_raw_syscall(__NR_openat, arguments.first, reinterpret_cast<long>(path),
                                                  arguments.third, arguments.fourth, replay_marker, 0)
                          : signature_raw_syscall(number, reinterpret_cast<long>(path), arguments.second,
                                                  arguments.third, 0, replay_marker, 0);

        if (result < 0 || !std::cmp_less(result, state.descriptor_count)) return result;

        const auto slot = static_cast<size_t>(result);
        if (const auto previous = state.proc_descriptors.slot(slot).exchange(-1, std::memory_order_acq_rel);
            previous >= 0)
            signature_raw_syscall(__NR_close, previous, 0, 0, 0, 0, 0);

        const auto copied_size = length > 0 ? static_cast<size_t>(length) : 0;

        if (state.snapshot_proc_file == nullptr || copied_size == 0) return result;

        std::string_view proc_path(copied.data(), copied_size);
        const auto terminator = proc_path.find('\0');
        if (terminator == std::string_view::npos) return result;
        proc_path = proc_path.substr(0, terminator);
        if (!proc_path.starts_with("/proc/")) return result;
        proc_path.remove_prefix(6);
        const auto separator = proc_path.find('/');
        if (separator == std::string_view::npos) return result;
        const auto identity = proc_path.substr(0, separator);
        const auto file = proc_path.substr(separator + 1);
        if (file != "maps" && file != "status") return result;

        const auto current_process = static_cast<int>(signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0));
        int process = current_process;
        if (identity != "self") {
            if (const auto [parsed_end, parse_error] =
                    std::from_chars(identity.data(), identity.data() + identity.size(), process);
                parse_error != std::errc{} || parsed_end != identity.data() + identity.size() ||
                (process != current_process && process != state.installing_process))
                return result;
        }

        const int snapshot =
            state.snapshot_proc_file(file == "maps" ? proc_file::maps : proc_file::status, process);

        if (snapshot >= 0) {
            state.proc_descriptors.slot(slot).store(snapshot, std::memory_order_release);
            return result;
        }

        signature_raw_syscall(__NR_close, result, 0, 0, 0, 0, 0);
        return snapshot;
    }

} // namespace signature::redirect
