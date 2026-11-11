// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include <android/dlext.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <linux/ashmem.h>
#include <linux/memfd.h>
#include <sys/ioctl.h>
#include <sys/mman.h>
#include <sys/syscall.h>
#include <unistd.h>

#include <array>
#include <climits>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <string>
// ReSharper restore CppUnusedIncludeDirective

extern "C" {
    #include "sh_util.h"
    #include "xdl.h"
    extern const unsigned char signature_shadowhook_calibration_start[];
    extern const unsigned char signature_shadowhook_calibration_end[];
}

namespace {
    constexpr auto calibration_name = "libshadowhook_nothing.so";
    thread_local std::string calibration_path;

    int create_calibration_file(const size_t size) {
        // Use the syscall on older Android versions that do not export memfd_create.
        if (const int memory = static_cast<int>(syscall(
                    __NR_memfd_create, calibration_name, MFD_CLOEXEC)); memory >= 0) {
            if (ftruncate(memory, static_cast<off_t>(size)) == 0) return memory;
            close(memory);
            return -1;
        }

        // Android 5 devices can have kernels predating memfd support.
        // Taking its address selects libc instead of the NDK's fortified overloads.
        int (*const open_file)(const char *, int, ...) = &open;
        const int shared_memory = open_file("/dev/ashmem", O_RDWR | O_CLOEXEC);
        if (shared_memory < 0) return -1;
        if (ioctl(shared_memory, ASHMEM_SET_NAME, calibration_name) == 0 &&
            ioctl(shared_memory, ASHMEM_SET_SIZE, size) == 0)
            return shared_memory;
        close(shared_memory);
        return -1;
    }
}

// Android names a library opened from a descriptor after its backing memory file.
// ShadowHook must inspect that same module while its constructor callback calibrates soinfo.
extern "C" void *signature_shadowhook_xdl_open(const char *const name, const int flags) {
    return xdl_open(!calibration_path.empty() && strcmp(name, calibration_name) == 0 ? calibration_path.c_str() : name, flags);
}

extern "C" bool signature_shadowhook_ends_with(const char *const value, const char *const suffix) {
    if (!calibration_path.empty() && strcmp(suffix, calibration_name) == 0)
        return strcmp(value, calibration_path.c_str()) == 0;
    return sh_util_ends_with(value, suffix);
}

extern "C" void *signature_shadowhook_dlopen(const char *const name, const int flags) {
    if (name == nullptr || strcmp(name, calibration_name) != 0) return dlopen(name, flags);

    // Assembly labels bound an ELF payload, rather than elements of one C++ array.
    const auto start = reinterpret_cast<uintptr_t>(signature_shadowhook_calibration_start);
    const auto end = reinterpret_cast<uintptr_t>(signature_shadowhook_calibration_end);
    const auto size = end - start;
    const int descriptor = create_calibration_file(size);
    if (descriptor < 0) return nullptr;

    void *const contents = mmap(nullptr, size, PROT_READ | PROT_WRITE, MAP_SHARED, descriptor, 0);
    if (contents == MAP_FAILED) {
        close(descriptor);
        return nullptr;
    }
    memcpy(contents, signature_shadowhook_calibration_start, size);
    munmap(contents, size);

    std::array<char, 64> descriptor_link{};
    const int written = snprintf(descriptor_link.data(), descriptor_link.size(),
                                 "/proc/self/fd/%d", descriptor);
    if (written < 0 || static_cast<size_t>(written) >= descriptor_link.size()) {
        close(descriptor);
        return nullptr;
    }
    std::array<char, PATH_MAX> link_contents{};
    const ssize_t length = readlink(descriptor_link.data(), link_contents.data(),
                                   link_contents.size() - 1);
    if (length < 0 || static_cast<size_t>(length) >= link_contents.size()) {
        close(descriptor);
        return nullptr;
    }
    link_contents.at(static_cast<size_t>(length)) = '\0';

    const android_dlextinfo information{
        .flags = ANDROID_DLEXT_USE_LIBRARY_FD,
        .library_fd = descriptor,
    };
    calibration_path.assign(link_contents.data(), static_cast<size_t>(length));
    void *const handle = android_dlopen_ext(calibration_name, flags, &information);
    calibration_path.clear();
    close(descriptor);
    return handle;
}
