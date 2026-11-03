// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "account_limit.h"

#include <android/log.h>
#include <link.h>
#include <sys/mman.h>

#include <cstring>
#include <iterator>
#include <span>
#include <string_view>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    struct search_result {
        std::optional<account_limit> limit;
        unsigned matches = 0;
        bool has_account_validation = false;
    };

    bool matches_account_limit(const uint32_t *instructions) {
        // Only wildcard operands differ between each pair of assembled examples.
        for (const auto &[expected, wildcard_example] : proton_account_limit_pattern)
            if (const auto mask = ~(expected ^ wildcard_example);
                (*instructions++ & mask) != (expected & mask)) return false;
        
        return true;
    }

    int search_library(const dl_phdr_info *const library, size_t, void *const data) {
        const auto *name = strrchr(library->dlpi_name, '/');
        name = name == nullptr ? library->dlpi_name : name + 1;
        if (strcmp(name, "libmail_uniffi.so") != 0) return 0;

        auto &[limit, matches, has_account_validation] = *static_cast<search_result *>(data);
        for (size_t index = 0; index < library->dlpi_phnum; ++index) {
            const auto &segment = library->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_R) == 0) continue;

            const auto address = library->dlpi_addr + segment.p_vaddr;

            if (!has_account_validation && (segment.p_flags & PF_W) == 0) {
                const std::string_view contents{reinterpret_cast<const char *>(address), segment.p_filesz};
                has_account_validation = contents.contains("Logged-in free accounts: ");
            }

            if ((segment.p_flags & PF_X) == 0) continue;

            const std::span instructions{reinterpret_cast<uint32_t *>(address), segment.p_filesz / sizeof(uint32_t)};
            
            for (size_t offset = 0; offset + std::size(proton_account_limit_pattern) <= instructions.size(); ++offset) {
                if (!matches_account_limit(instructions.data() + offset)) continue;

                const int protection = PROT_READ | PROT_EXEC | ((segment.p_flags & PF_W) != 0 ? PROT_WRITE : 0);

                limit = account_limit{
                    .instruction = &instructions[offset + std::size(proton_account_limit_pattern) - 1],
                    .protection = protection,
                };

                ++matches;
            }
        }
        
        return 0;
    }
}

std::optional<account_limit> find_account_limit() {
    search_result result;

    // Android's callback type requires dl_phdr_info*, even though this adapter only reads it.
    // ReSharper disable CppParameterMayBeConstPtrOrRef
    dl_iterate_phdr([](dl_phdr_info *const library, const size_t size, void *const data) {
        return search_library(library, size, data);
    }, &result);
    // ReSharper restore CppParameterMayBeConstPtrOrRef

    if (!result.has_account_validation || result.matches != 1) {
        __android_log_print(ANDROID_LOG_ERROR, "ProtonAccounts",
            "Expected one native free-account limit, found %u; validation anchor present: %s",
            result.matches, result.has_account_validation ? "yes" : "no");
        return std::nullopt;
    }

    return result.limit;
}
