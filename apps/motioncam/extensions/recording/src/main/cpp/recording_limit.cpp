// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "recording_limit.h"
#include "logging.h"

#include <dlfcn.h>
#include <link.h>
#include <sys/mman.h>

#include <cstring>
#include <span>
#include <string_view>
// ReSharper restore CppUnusedIncludeDirective

namespace {
    struct search_result {
        std::optional<recording_limit> limit;
        unsigned matches = 0;
    };

    bool matches_limit(const uint32_t *instructions) {
        for (size_t index = 0; index < motioncam_limit_size / sizeof(uint32_t); ++index) {
            const auto expected = motioncam_limit_pattern[index];
            if (const auto mask = ~(expected ^ motioncam_limit_wildcards[index]);
                (instructions[index] & mask) != (expected & mask)) return false;
        }
        
        return true;
    }

    std::optional<recording_limit> resolve_limit(uint32_t *const instruction, const int protection) {
        Dl_info renderer{};
        if (dladdr(instruction, &renderer) == 0 || renderer.dli_sname == nullptr ||
            !std::string_view(renderer.dli_sname).starts_with("_ZN9motioncam16RecordingSession8doRenderE"))
            return std::nullopt;

        return recording_limit{
            .instruction = instruction,
            .protection = protection,
        };
    }

    int search_library(const dl_phdr_info &library, search_result &result) {
        const auto *name = strrchr(library.dlpi_name, '/');
        name = name == nullptr ? library.dlpi_name : name + 1;
        if (strcmp(name, "libnative-camera-host.so") != 0) return 0;

        for (size_t index = 0; index < library.dlpi_phnum; ++index) {
            const auto &segment = library.dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & (PF_R | PF_X)) != (PF_R | PF_X))
                continue;

            const auto address = library.dlpi_addr + segment.p_vaddr;
            const std::span instructions{reinterpret_cast<uint32_t *>(address), segment.p_filesz / sizeof(uint32_t)};
            
            for (size_t offset = 0; offset + motioncam_limit_size / sizeof(uint32_t) <= instructions.size(); ++offset) {
                if (!matches_limit(instructions.data() + offset)) continue;

                auto *instruction = instructions.data() + offset + motioncam_limit_patch_offset / sizeof(uint32_t);
                const int protection = PROT_READ | PROT_EXEC | ((segment.p_flags & PF_W) != 0 ? PROT_WRITE : 0);
                
                const auto limit = resolve_limit(instruction, protection);
                if (!limit) continue;
                
                result.limit = limit;
                ++result.matches;
            }
        }
        
        return 0;
    }
}

std::optional<recording_limit> find_recording_limit() {
    if (motioncam_limit_size != motioncam_limit_wildcards_size || motioncam_limit_size % sizeof(uint32_t) != 0 ||
        motioncam_limit_patch_offset + sizeof(uint32_t) > motioncam_limit_size) {
        LOG_ERROR("Invalid assembled recording-limit pattern");
        return std::nullopt;
    }

    search_result result;
    // Android's callback type requires dl_phdr_info*, though this adapter only reads it.
    // ReSharper disable CppParameterMayBeConstPtrOrRef
    dl_iterate_phdr([](dl_phdr_info *const library, size_t, void *const data) {
        return search_library(*library, *static_cast<search_result *>(data));
    }, &result);
    // ReSharper restore CppParameterMayBeConstPtrOrRef

    if (result.matches != 1) {
        LOG_ERROR("Expected one native recording cutoff, found %u", result.matches);
        return std::nullopt;
    }
    
    return result.limit;
}
