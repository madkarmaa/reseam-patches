// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view_internal.h"
#include "logging.h"
#include <linux/memfd.h>
#include <sys/syscall.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <cerrno>
#include <charconv>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::view {
    namespace {
        // ByteHook's trampoline pages are separate mappings of our library file, outside its ELF
        // image. Exclude those copies as well as the loader's module mappings.
        bool hidden_mapping(const char *const line, const ssize_t length) {
            uintptr_t start = 0;
            const auto [end, error] = std::from_chars(line, line + length, start, 16);
            if (error != std::errc{} || end >= line + length || *end != '-') return false;

            // Android's page-size compatibility output can retain an empty segment after
            // mprotect splits a code page. It names no memory and has no map_files entry.
            if (uintptr_t limit = 0;
                std::from_chars(end + 1, line + length, limit, 16).ec == std::errc{} && start == limit)
                return true;

            return std::ranges::any_of(hidden_ranges, [start](const address_range &range) {
                return start >= range.start && start < range.end;
            });
        }

        // Writes every byte of one snapshot line, resuming after interruptions.
        int write_all(const int descriptor, const std::span<const char> data) {
            size_t written = 0;
            while (written < data.size()) {
                const ssize_t count = write(descriptor, data.data() + written, data.size() - written);

                if (count < 0 && errno == EINTR) continue;
                if (count <= 0) return count < 0 ? errno : EIO;

                written += static_cast<size_t>(count);
            }

            return 0;
        }

    } // namespace

    int create_proc_snapshot(const proc_file file, const pid_t process, const unsigned filters) {
        const bool is_maps = file == proc_file::maps;

        std::array<char, 64> path{};
        const int formatted = snprintf(path.data(), path.size(), "/proc/%ld/%s", static_cast<long>(process),
                                       is_maps ? "maps" : "status");
        if (formatted <= 0 || !std::cmp_less(formatted, path.size())) return -EINVAL;

        const std::unique_ptr<FILE, decltype(&fclose)> maps(fopen(path.data(), "re"), fclose);

        if (!maps) return -errno;

        const int result =
            static_cast<int>(syscall(__NR_memfd_create, is_maps ? "maps" : "status", MFD_CLOEXEC));
        if (result < 0) return -errno;

        line_storage line(nullptr, &std::free);
        size_t capacity = 0;
        int failure = 0;
        unsigned excluded_lines = 0;

        ssize_t length = next_line(maps.get(), line, capacity);
        while (length >= 0) {
            if (is_maps && (extension_library(line.get()) || hidden_mapping(line.get(), length))) {
                excluded_lines++;
                length = next_line(maps.get(), line, capacity);
                continue;
            }

            std::array<char, 80> adjusted{};
            if (const ssize_t adjusted_length =
                    is_maps ? -1 : adjust_status_line(line.get(), adjusted, filters);
                adjusted_length < 0)
                failure = write_all(result, {line.get(), static_cast<size_t>(length)});
            else
                failure = write_all(result, {adjusted.data(), std::min(static_cast<size_t>(adjusted_length),
                                                                       adjusted.size())});

            if (failure != 0) break;

            length = next_line(maps.get(), line, capacity);
        }

        if (failure == 0 && ferror(maps.get()) != 0) failure = EIO;
        if (failure == 0) {
            if (is_maps)
                LOG_DEBUG("Maps snapshot excludes %u extension or empty mappings in %zu images",
                          excluded_lines, hidden_ranges.size());
            return result;
        }

        close(result);
        return -failure;
    }
} // namespace signature::view
