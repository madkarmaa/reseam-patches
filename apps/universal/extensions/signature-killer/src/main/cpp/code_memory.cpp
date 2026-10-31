// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#include "code_memory.h"

#include <dlfcn.h>
#include <fcntl.h>
#include <sys/mman.h>
#include <sys/stat.h>
#include <unistd.h>

#include <utility>

namespace {
    void *map_library_code(const size_t size) {
        Dl_info library{};
        if (dladdr(reinterpret_cast<void *>(&signature_allocate_code_block), &library) == 0 ||
            library.dli_fname == nullptr)
            return MAP_FAILED;

        // Select libc's variadic overload explicitly for these fixed read-only flags.
        int (*const open_file)(const char *, int, ...) = open;
        const int descriptor = open_file(library.dli_fname, O_RDONLY | O_CLOEXEC, 0);
        if (descriptor < 0) return MAP_FAILED;

        // Each block gets a private copy of our library's first pages. The file stays unchanged,
        // while /proc/self/maps names a real file whose inode agrees with stat().
        struct stat metadata{};
        auto *memory = MAP_FAILED;
        
        if (fstat(descriptor, &metadata) == 0 && metadata.st_size >= 0 &&
            std::cmp_greater_equal(metadata.st_size, size))
            // ByteHook writes and reuses these trampoline pages while other threads execute them.
            // NOLINTNEXTLINE(clang-analyzer-security.MmapWriteExec)
            memory = mmap(nullptr, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                          MAP_PRIVATE, descriptor, 0);
        
        close(descriptor);
        return memory;
    }
} // namespace

extern "C" void *signature_allocate_code_block(const size_t size) {
    if (auto *const memory = map_library_code(size); memory != MAP_FAILED) return memory;

    // Libraries loaded directly from an APK may have no readable filesystem path. Preserve
    // ByteHook's original allocator when file-backed allocation is unavailable.
    // NOLINTNEXTLINE(clang-analyzer-security.MmapWriteExec)
    return mmap(nullptr, size, PROT_READ | PROT_WRITE | PROT_EXEC,
                MAP_PRIVATE | MAP_ANONYMOUS, -1, 0);
}
