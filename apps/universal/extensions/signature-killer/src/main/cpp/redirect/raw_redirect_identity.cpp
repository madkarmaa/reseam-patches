// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include <fcntl.h>
#include <unistd.h>
#include <array>
#include <climits>
#include <cstdio>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    bool prepare_descriptor_identities(redirect_state &state) {
        // Select libc's variadic overload explicitly for these fixed read-only flags.
        int (*const open_file)(const char *, int, ...) = open;
        for (const auto &[installed_path, replacement_path] : state.paths) {
            descriptor_identity identity;
            const int installed = open_file(installed_path, O_RDONLY | O_CLOEXEC, 0);
            if (installed < 0) return false;

            const bool inspected = fstat(installed, &identity.installed) == 0;
            close(installed);
            if (!inspected) return false;

            const int replacement = open_file(replacement_path, O_RDONLY | O_CLOEXEC, 0);
            if (replacement < 0) return false;
            const bool replacement_inspected = fstat(replacement, &identity.replacement) == 0;

            std::array<char, 64> descriptor{};
            const int formatted =
                snprintf(descriptor.data(), descriptor.size(), "/proc/self/fd/%d", replacement);

            std::array<char, PATH_MAX> resolved{};

            const ssize_t length = formatted > 0 && std::cmp_less(formatted, descriptor.size())
                                       ? readlink(descriptor.data(), resolved.data(), resolved.size() - 1)
                                       : -1;

            if (state.redirect_file_contents) identity.original_descriptor = replacement;
            else close(replacement);

            if (!replacement_inspected || length <= 0) return false;

            identity.resolved_replacement.assign(resolved.data(), length);
            state.identities.push_back(std::move(identity));
        }

        return true;
    }

} // namespace signature::redirect
