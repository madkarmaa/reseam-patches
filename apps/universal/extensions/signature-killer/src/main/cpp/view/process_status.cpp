// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view_internal.h"
#include <cerrno>
#include <csignal>
#include <cstring>
#include <limits>
#include <type_traits>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::view {
    namespace {
        unsigned original_no_new_privileges = 0;
        unsigned long long original_blocked_signals = 0;
        unsigned long long original_caught_signals = 0;

        // /proc/self/status separates each label from its value with spaces or a tab.
        bool is_space(const char character) {
            return character == ' ' || character == '\t';
        }

        // Reads one /proc/self/status field, whose label is followed by spaces or a tab.
        template <typename Value>
        bool read_field(const char *const line, const char *const label, Value &value) {
            const auto length = static_cast<int>(strlen(label));
            if (strncmp(line, label, static_cast<size_t>(length)) != 0) return false;

            const char *text = line + length;
            while (is_space(*text)) text++;

            char *end = nullptr;
            errno = 0;

            if constexpr (std::is_same_v<Value, unsigned>) {
                const unsigned long parsed = strtoul(text, &end, 10);
                if (end == text || errno != 0 || parsed > std::numeric_limits<unsigned>::max()) return false;

                value = static_cast<unsigned>(parsed);
                return true;
            } else {
                const unsigned long long parsed = strtoull(text, &end, 16);
                if (end == text || errno != 0) return false;

                value = parsed;
                return true;
            }
        }

    } // namespace

    bool capture_process_status() {
        const std::unique_ptr<FILE, decltype(&fclose)> status(fopen("/proc/self/status", "re"), fclose);
        if (!status) return false;

        line_storage line(nullptr, &std::free);
        size_t capacity = 0;
        unsigned found = 0;

        while (next_line(status.get(), line, capacity) >= 0) {
            if (read_field(line.get(), "NoNewPrivs:", original_no_new_privileges)) found |= 1U;
            if (read_field(line.get(), "SigBlk:", original_blocked_signals)) found |= 2U;
            if (read_field(line.get(), "SigCgt:", original_caught_signals)) found |= 4U;
        }

        return found == 7U && ferror(status.get()) == 0;
    }

    // Rewrites a /proc/self/status field that reports this extension's own state.
    // Returns the length to write, or -1 when the line stays as the kernel printed it.
    ssize_t adjust_status_line(const char *const line, const std::span<char> adjusted,
                               const unsigned filters) {
        unsigned value = 0;
        if (read_field(line, "NoNewPrivs:", value))
            return snprintf(adjusted.data(), adjusted.size(), "NoNewPrivs:\t%u\n",
                            original_no_new_privileges);

        if (read_field(line, "Seccomp_filters:", value)) {
            return value < filters ? -1
                                   : snprintf(adjusted.data(), adjusted.size(), "Seccomp_filters:\t%u\n",
                                              value - filters);
        }

        // The filter both blocks and catches SIGSYS, so restore the process's original bit.
        constexpr auto own_signal = 1ULL << static_cast<unsigned>(SIGSYS - 1);
        unsigned long long signals = 0;

        if (read_field(line, "SigBlk:", signals))
            return snprintf(adjusted.data(), adjusted.size(), "SigBlk:\t%016llx\n",
                            (signals & ~own_signal) | (original_blocked_signals & own_signal));

        if (read_field(line, "SigCgt:", signals))
            return snprintf(adjusted.data(), adjusted.size(), "SigCgt:\t%016llx\n",
                            (signals & ~own_signal) | (original_caught_signals & own_signal));

        return -1;
    }
} // namespace signature::view
