// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/kernel_stat.h"
#include "redirect/raw_redirect_internal.h"
#include <fcntl.h>
#include <sys/syscall.h>
#include <algorithm>
#include <array>
#include <cerrno>
#include <climits>
#include <cstring>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    namespace {
        long redirect_readlink(const redirect_state &state, const int number,
                               const syscall_arguments &arguments, int &mapping) {
            const bool has_directory = number == __NR_readlinkat;

            const auto *const path =
                reinterpret_cast<const char *>(has_directory ? arguments.second : arguments.first);

            auto *const buffer = reinterpret_cast<char *>(has_directory ? arguments.third : arguments.second);
            const auto size = static_cast<size_t>(has_directory ? arguments.fourth : arguments.third);

            const auto replay = [&](char *const destination, const size_t capacity) {
                return has_directory
                           ? signature_raw_syscall(__NR_readlinkat, arguments.first,
                                                   reinterpret_cast<long>(path),
                                                   reinterpret_cast<long>(destination),
                                                   static_cast<long>(capacity), replay_marker, 0)
                           : signature_raw_syscall(number, reinterpret_cast<long>(path),
                                                   reinterpret_cast<long>(destination),
                                                   static_cast<long>(capacity), 0, replay_marker, 0);
            };

            const long result = replay(buffer, size);
            if (result <= 0 || result >= PATH_MAX) return result;

            // The kernel or read_path fills the used prefix, as in find_descriptor_mapping.
            std::array<char, PATH_MAX> resolved;
            long resolved_length = result;

            // A short caller buffer may contain only a prefix of the replacement path. Resolve the
            // full target for matching, while keeping the original result for unrelated descriptors.
            if (std::cmp_equal(result, size)) {
                resolved_length = replay(resolved.data(), resolved.size() - 1);
                if (resolved_length <= 0 || resolved_length >= PATH_MAX) return result;
            } else if (read_path(buffer, std::span(resolved).first(static_cast<size_t>(result))) != result) {
                return -EFAULT;
            }

            // Both reads are bounded above by PATH_MAX - 1.
            element_at(resolved, static_cast<size_t>(resolved_length)) = '\0';

            mapping = find_replacement_mapping(state, resolved.data(), resolved_length + 1);
            if (mapping < 0) return result;

            // find_replacement_mapping returns an index into the parallel path and identity tables.
            const char *const installed = element_at(state.paths, static_cast<size_t>(mapping)).installed;
            const size_t length = std::min(size, strlen(installed));

            if (write_memory(std::span(buffer, length), std::span(installed, length)) !=
                static_cast<long>(length))
                return -EFAULT;

            return static_cast<long>(length);
        }

        // Preserve the real inode and timestamps. Content mode exposes the original file's size.
        template <typename Metadata>
        void apply_descriptor_identity(Metadata &value, const descriptor_identity &identity,
                                       const bool redirect_contents) {
            value.st_uid = static_cast<decltype(value.st_uid)>(identity.installed.st_uid);
            value.st_gid = static_cast<decltype(value.st_gid)>(identity.installed.st_gid);
            if (redirect_contents) value.st_size = identity.replacement.st_size;
        }

        template <typename Metadata> long write_metadata(void *buffer, const Metadata &value) {
            const long written =
                write_memory(std::span(static_cast<char *>(buffer), sizeof(value)),
                             std::span(reinterpret_cast<const char *>(&value), sizeof(value)));
            return std::cmp_equal(written, sizeof(value)) ? 0 : -EFAULT;
        }

        template <typename Metadata>
        long redirect_fstat(const redirect_state &state, const int number, const long descriptor,
                            void *const buffer, int &mapping) {
            mapping = find_descriptor_mapping(state, descriptor);
            if (mapping < 0)
                return signature_raw_syscall(number, descriptor, reinterpret_cast<long>(buffer), 0, 0,
                                             replay_marker, 0);

            Metadata value{};
            const long result = signature_raw_syscall(number, descriptor, reinterpret_cast<long>(&value), 0,
                                                      0, replay_marker, 0);
            if (result < 0) return result;

            // find_descriptor_mapping validated the index into the parallel identity table.
            apply_descriptor_identity(value, element_at(state.identities, static_cast<size_t>(mapping)),
                                      state.redirect_file_contents);
            return write_metadata(buffer, value);
        }

        template <typename Metadata>
        long resolve_stat_mapping(const redirect_state &state, const int number,
                                  const syscall_arguments &arguments, Metadata &value, int &mapping) {
            // A no-follow query describes the symlink itself, including procfs descriptor links.
            if (S_ISLNK(value.st_mode)) return 0;

            std::array<char, PATH_MAX> copied;
            const long length =
                read_path(reinterpret_cast<const char *>(arguments.second), std::span(copied));
            mapping = find_stat_path_mapping(state, arguments.first, copied.data(), length);
            if (mapping >= 0 && !state.redirect_file_contents)
                return signature_raw_syscall(
                    number, AT_FDCWD,
                    reinterpret_cast<long>(element_at(state.paths, static_cast<size_t>(mapping)).replacement),
                    reinterpret_cast<long>(&value), arguments.fourth, replay_marker, 0);

            // AT_EMPTY_PATH, proc-fd paths and other aliases already resolve the real copy.
            for (const auto &identity : state.identities) {
                const auto &replacement =
                    state.redirect_file_contents ? identity.installed : identity.replacement;
                if (value.st_dev != replacement.st_dev || value.st_ino != replacement.st_ino) continue;
                // ptrdiff_t is wider than int on 64-bit ABIs.
                // ReSharper disable once CppRedundantCastExpression
                mapping = static_cast<int>(&identity - state.identities.data());
                break;
            }
            return 0;
        }

        template <typename Metadata>
        long redirect_fstatat(const redirect_state &state, const int number,
                              const syscall_arguments &arguments, int &mapping) {
            Metadata value{};
            const long result =
                signature_raw_syscall(number, arguments.first, arguments.second,
                                      reinterpret_cast<long>(&value), arguments.fourth, replay_marker, 0);
            if (result < 0) return result;
            if (const long resolved = resolve_stat_mapping(state, number, arguments, value, mapping);
                resolved < 0)
                return resolved;
            if (mapping >= 0)
                apply_descriptor_identity(value, element_at(state.identities, static_cast<size_t>(mapping)),
                                          state.redirect_file_contents);
            return write_metadata(reinterpret_cast<void *>(arguments.third), value);
        }

    } // namespace

    // Replays a descriptor metadata query through the identity table.
    long redirect_metadata(const redirect_state &state, const int number, const syscall_arguments &arguments,
                           int &mapping) {
        if (is_readlink_syscall(number)) return redirect_readlink(state, number, arguments, mapping);
#ifdef __NR_newfstatat
        if (number == __NR_newfstatat)
            return redirect_fstatat<signature_kernel_stat>(state, number, arguments, mapping);
#endif
#ifdef __NR_fstatat64
        if (number == __NR_fstatat64)
            return redirect_fstatat<signature_kernel_stat64>(state, number, arguments, mapping);
#endif
#ifdef __NR_fstat64
        if (number == __NR_fstat64)
            return redirect_fstat<signature_kernel_stat64>(
                state, number, arguments.first, reinterpret_cast<void *>(arguments.second), mapping);
#endif
        return redirect_fstat<signature_kernel_stat>(state, number, arguments.first,
                                                     reinterpret_cast<void *>(arguments.second), mapping);
    }

} // namespace signature::redirect
