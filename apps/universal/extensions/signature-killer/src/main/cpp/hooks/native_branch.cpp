// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "hooks/native_branch.h"
#include <sys/mman.h>
#include <sys/syscall.h>
#include <sys/uio.h>
#include <unistd.h>
#include <algorithm>
#include <array>
#include <atomic>
#include <cerrno>
#include <charconv>
#include <cstdio>
#include <memory>
#include <string_view>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace signature::hooks {
#ifdef __aarch64__
    namespace {
        bool read_code(const uintptr_t address, void *const buffer, const size_t size) {
            const iovec local{.iov_base = buffer, .iov_len = size};
            const iovec remote{.iov_base = reinterpret_cast<void *>(address), .iov_len = size};
            const long process = signature_raw_syscall(__NR_getpid, 0, 0, 0, 0, 0, 0);
            for (;;) {
                const long result =
                    signature_raw_syscall(__NR_process_vm_readv, process, reinterpret_cast<long>(&local), 1,
                                          reinterpret_cast<long>(&remote), 1, 0);
                if (result == -EINTR) continue;
                return std::cmp_equal(result, size);
            }
        }

    } // namespace

    forwarding_branch forwarding_destination(const uintptr_t entry) {
        std::array<uint32_t, 4> instructions{};
        if (!read_code(entry, instructions.data(), sizeof(instructions))) return {};
        constexpr auto branch_targets = std::to_array<uint32_t>({
            0xd503241fU,
            0xd503245fU,
            0xd503249fU,
            0xd50324dfU,
        });
        const size_t index = std::ranges::contains(branch_targets, instructions.front()) ? 1 : 0;
        const auto position = entry + (index * sizeof(uint32_t));
        const uint32_t instruction = instructions.at(index);
        // B preserves the original LR. BL would make the wrapper itself the caller.
        if ((instruction & 0xfc000000U) == 0x14000000U) {
            int64_t displacement = instruction & 0x03ffffffU;
            if ((instruction & 0x02000000U) != 0) displacement -= int64_t{0x4000000};
            return {.destination = position + static_cast<uintptr_t>(displacement * 4)};
        }
        // Accept only a 64-bit literal load followed by BR through that same register.
        if (const uint32_t branch = instructions.at(index + 1);
            (instruction & 0xff000000U) != 0x58000000U || (branch & 0xfffffc1fU) != 0xd61f0000U ||
            (instruction & 31U) == 31U || (instruction & 31U) != ((branch >> 5U) & 31U))
            return {};
        int64_t displacement = (instruction >> 5U) & 0x7ffffU;
        if ((instruction & 0x00800000U) != 0) displacement -= int64_t{0x80000};
        uintptr_t destination = 0;
        if (!read_code(position + static_cast<uintptr_t>(displacement * 4), &destination,
                       sizeof(destination)))
            return {};
        return {
            .destination = destination,
            .literal = position + static_cast<uintptr_t>(displacement * 4),
        };
    }

    bool retarget_literal(const uintptr_t literal, const void *const proxy) {
        if (literal % alignof(uintptr_t) != 0) return false;
        const std::unique_ptr<FILE, decltype(&fclose)> maps(fopen("/proc/self/maps", "re"), fclose);
        if (!maps) return false;
        std::array<char, 4096> line{};
        unsigned protection = 0;
        bool found = false;
        while (fgets(line.data(), static_cast<int>(line.size()), maps.get()) != nullptr) {
            const std::string_view text(line.data());
            const auto hyphen = text.find('-');
            const auto space = text.find(' ');
            if (hyphen == std::string_view::npos || space == std::string_view::npos || hyphen >= space ||
                text.size() - space < 5)
                continue;
            uintptr_t first = 0;
            uintptr_t last = 0;
            const auto [beginning, beginning_error] =
                std::from_chars(line.data(), line.data() + hyphen, first, 16);
            if (const auto [ending, ending_error] =
                    std::from_chars(line.data() + hyphen + 1, line.data() + space, last, 16);
                beginning_error != std::errc{} || beginning != line.data() + hyphen ||
                ending_error != std::errc{} || ending != line.data() + space || literal < first ||
                literal >= last)
                continue;
            const auto permissions = text.substr(space + 1, 4);
            if (permissions.at(0) == 'r') protection |= static_cast<unsigned>(PROT_READ);
            if (permissions.at(1) == 'w') protection |= static_cast<unsigned>(PROT_WRITE);
            if (permissions.at(2) == 'x') protection |= static_cast<unsigned>(PROT_EXEC);
            found = true;
            break;
        }
        if (!found) return false;
        const auto page_size = static_cast<uintptr_t>(sysconf(_SC_PAGESIZE));
        const auto page = literal & ~(page_size - 1);
        const bool writable = (protection & static_cast<unsigned>(PROT_WRITE)) != 0;
        if (!writable &&
            signature_raw_syscall(__NR_mprotect, static_cast<long>(page), static_cast<long>(page_size),
                                  protection | static_cast<unsigned>(PROT_WRITE), 0, 0, 0) != 0)
            return false;
        // The branch instructions are unchanged. An aligned atomic pointer update cannot
        // expose a partially written destination to a concurrent libc caller.
        static_assert(std::atomic_ref<uintptr_t>::is_always_lock_free);
        std::atomic_ref(*reinterpret_cast<uintptr_t *>(literal))
            .store(reinterpret_cast<uintptr_t>(proxy), std::memory_order_release);
        return writable || signature_raw_syscall(__NR_mprotect, static_cast<long>(page),
                                                 static_cast<long>(page_size), protection, 0, 0, 0) == 0;
    }

#endif
} // namespace signature::hooks
