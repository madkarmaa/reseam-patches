// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "file_test.h"
#include "logging.h"
#include <fcntl.h>
#include <sys/mman.h>
#include <unistd.h>
#include <array>
#include <cstring>
#include <utility>
#include <sys/auxv.h>
#include <cstdarg>
#include <cstdio>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::tests {
    decltype(&signature_raw_syscall) protected_syscall = &signature_raw_syscall;
    volatile sig_atomic_t previous_signal = 0;

    void previous_handler(int /*signal*/) {
        previous_signal = 1;
    }

    int raw_open(const int number, const char *const path) {
        const long result =
            number == __NR_openat
                ? protected_syscall(number, AT_FDCWD, reinterpret_cast<long>(path), O_RDONLY | O_CLOEXEC, 0,
                                    0, 0)
                : protected_syscall(number, reinterpret_cast<long>(path), O_RDONLY | O_CLOEXEC, 0, 0, 0, 0);
        assert(result >= 0);
        return static_cast<int>(result);
    }

    void expect_contents(const int descriptor, const char *const contents) {
        std::array<char, 64> data{};
        const long size = protected_syscall(__NR_read, descriptor, reinterpret_cast<long>(data.data()),
                                            data.size(), 0, 0, 0);
        assert(std::cmp_equal(size, strlen(contents)));
        assert(memcmp(data.data(), contents, size) == 0);
    }

    // dl_iterate_phdr fixes the callback's non-const parameter types.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    // NOLINTNEXTLINE(misc-const-correctness)
    int find_executable(dl_phdr_info *const info, size_t /*info_size*/, void *const data) {
        const auto address = reinterpret_cast<uintptr_t>(&signature_raw_syscall);
        for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
            const auto &segment = info->dlpi_phdr[index];
            if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;
            if (address < info->dlpi_addr + segment.p_vaddr ||
                address >= info->dlpi_addr + segment.p_vaddr + segment.p_memsz)
                continue;
            const char *const filename = info->dlpi_name == nullptr ? "" : info->dlpi_name;
            const char *const slash = strrchr(filename, '/');
            *static_cast<std::string *>(data) = slash == nullptr ? filename : slash + 1;
            return 1;
        }
        return 0;
    }
} // namespace signature::tests

// GNU ld's --wrap option requires these exact symbol names.
// NOLINTNEXTLINE(bugprone-reserved-identifier,cert-dcl37-c,cert-dcl51-cpp)
extern "C" int __real_dl_iterate_phdr(int (*callback)(dl_phdr_info *, size_t, void *), void *data);

// Static bionic leaves both the executable and VDSO names null. Give these real program-header
// records distinct names so the production lookup can select the executable unambiguously.
// NOLINTNEXTLINE(bugprone-reserved-identifier,cert-dcl37-c,cert-dcl51-cpp)
extern "C" int __wrap_dl_iterate_phdr(int (*const callback_function)(dl_phdr_info *, size_t, void *),
                                      void *const callback_context_data) {
    struct callback_context {
        int (*callback)(dl_phdr_info *, size_t, void *);
        void *data;
    } context{.callback = callback_function, .data = callback_context_data};

    return __real_dl_iterate_phdr(
        [](dl_phdr_info *info, const size_t size, void *opaque) {
            const auto &[callback, data] = *static_cast<callback_context *>(opaque);
            auto named = *info;
            if (named.dlpi_name == nullptr || named.dlpi_name[0] == '\0')
                named.dlpi_name = reinterpret_cast<uintptr_t>(info->dlpi_phdr) == getauxval(AT_PHDR)
                                      ? "signature_file_tests"
                                      : "linux-vdso.so";
            return callback(&named, size, data);
        },
        &context);
}

// Static host tests have no Android log service. Production logging remains unchanged.
extern "C" int __android_log_print(int /*priority*/, const char * /*tag*/, const char *const fmt, ...) {
    va_list arguments{};
    va_start(arguments, fmt);
    const int result = vfprintf(stderr, fmt, arguments);
    static_cast<void>(fputc('\n', stderr));
    va_end(arguments);
    return result;
}
