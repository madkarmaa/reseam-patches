// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "license_check.h"

#include <link.h>
#include <sys/mman.h>

#include <cstring>
#include <span>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    bool matches_check(const uint32_t *instructions) {
        for (size_t index = 0; index < license_check_size / sizeof(uint32_t); ++index) {
            const auto expected = license_check_pattern[index];
            if (const auto mask = ~(expected ^ license_check_wildcards[index]);
                (instructions[index] & mask) != (expected & mask)) return false;
        }

        return true;
    }

    int search_library(const dl_phdr_info &library, std::vector<license_check> &checks) {
        const auto *name = strrchr(library.dlpi_name, '/');
        name = name == nullptr ? library.dlpi_name : name + 1;
        if (strcmp(name, "libpowerampcore.so") != 0) return 0;

        for (size_t index = 0; index < library.dlpi_phnum; ++index) {
            const auto &segment = library.dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & (PF_R | PF_X)) != (PF_R | PF_X))
                continue;

            const auto address = library.dlpi_addr + segment.p_vaddr;
            const std::span instructions{reinterpret_cast<uint32_t *>(address), segment.p_filesz / sizeof(uint32_t)};
            const int protection = PROT_READ | PROT_EXEC | ((segment.p_flags & PF_W) != 0 ? PROT_WRITE : 0);

            for (size_t offset = 0; offset + license_check_size / sizeof(uint32_t) <= instructions.size(); ++offset) {
                if (!matches_check(instructions.data() + offset)) continue;

                checks.push_back({
                    .instruction = instructions.data() + offset + license_check_patch_offset / sizeof(uint32_t),
                    .protection = protection,
                });
            }
        }

        return 1;
    }
}

std::vector<license_check> find_license_checks() {
    std::vector<license_check> checks;
    if (license_check_size != license_check_wildcards_size || license_check_size % sizeof(uint32_t) != 0 ||
        license_check_patch_offset + sizeof(uint32_t) > license_check_size) {
        LOG_ERROR("Invalid assembled license-check pattern");
        return checks;
    }

    // Android's callback type requires dl_phdr_info*, though this adapter only reads it.
    // ReSharper disable CppParameterMayBeConstPtrOrRef
    dl_iterate_phdr([](dl_phdr_info *const library, size_t, void *const data) {
        return search_library(*library, *static_cast<std::vector<license_check> *>(data));
    }, &checks);
    // ReSharper restore CppParameterMayBeConstPtrOrRef

    return checks;
}
