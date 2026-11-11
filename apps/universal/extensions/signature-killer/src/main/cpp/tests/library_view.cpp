// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view.h"

#include <dlfcn.h>
#include <link.h>
#include <linux/filter.h>
#include <linux/seccomp.h>
#include <sys/prctl.h>
#include <sys/syscall.h>
#include <unistd.h>

#ifdef NDEBUG
#undef NDEBUG
#endif

#include <array>
#include <cassert>
#include <csignal>
#include <cstdio>
#include <cstdlib>
#include <cstring>
#include <string>
#include <thread>
// ReSharper restore CppUnusedIncludeDirective

extern "C" int signature_count_support_modules();

namespace {
    std::string read_file(const int descriptor) {
        assert(descriptor >= 0);
        assert(lseek(descriptor, 0, SEEK_SET) == 0);
        std::string result;
        std::array<char, 4096> buffer{};
        ssize_t length = 0;
        while ((length = read(descriptor, buffer.data(), buffer.size())) > 0)
            result.append(buffer.data(), length);
        assert(length == 0);
        assert(close(descriptor) == 0);
        return result;
    }

    unsigned long long status_value(const std::string &status, const char *const name, const int base = 10) {
        const std::string prefix = std::string(name) + ":";
        const auto start = status.find(prefix);
        assert(start != std::string::npos);
        return strtoull(status.c_str() + start + prefix.size(), nullptr, base);
    }

    void expect_maps(const std::string &maps) {
        assert(!maps.contains("libSignatureKiller.so"));
        assert(!maps.contains("libshadowhook.so"));
        assert(maps.contains("libc.so"));
        assert(maps.contains("signature_view_tests"));
    }
} // namespace

int main(const int count, const char *const *const arguments) {
    assert(count == 1);
    assert(dlopen("libSignatureKiller.so", RTLD_NOW | RTLD_LOCAL) != nullptr);
    assert(signature_count_support_modules() == 1);
    FILE *const status = fopen("/proc/self/status", "re");
    assert(status != nullptr);
    const int status_descriptor = dup(fileno(status));
    assert(fclose(status) == 0);
    const auto baseline = read_file(status_descriptor);
    const char *const slash = strrchr(arguments[0], '/');
    assert(prepare_library_view(slash == nullptr ? arguments[0] : slash + 1));

    int visible_support_modules = 0;
    dl_iterate_phdr(
        [](dl_phdr_info *info, size_t, void *data) {
            if (strstr(info->dlpi_name, "libSignatureKiller.so") != nullptr ||
                strstr(info->dlpi_name, "libshadowhook.so") != nullptr)
                ++*static_cast<int *>(data);
            return 0;
        },
        &visible_support_modules);
    assert(visible_support_modules == 0);
    assert(signature_count_support_modules() == 1);
    int calls = 0;
    const int stopped = dl_iterate_phdr(
        [](dl_phdr_info *, size_t, void *data) {
            ++*static_cast<int *>(data);
            return 37;
        },
        &calls);
    assert(stopped == 37);
    assert(calls == 1);
    expect_maps(read_file(snapshot_process_file(proc_file::maps)));

    // Reproduce the state changed by installing the production syscall filter.
    struct sigaction action{};
    action.sa_handler = +[](int) {};
    sigemptyset(&action.sa_mask);
    assert(sigaction(SIGSYS, &action, nullptr) == 0);
    sigset_t blocked{};
    sigemptyset(&blocked);
    sigaddset(&blocked, SIGSYS);
    assert(pthread_sigmask(SIG_BLOCK, &blocked, nullptr) == 0);
    sock_filter allow = BPF_STMT(BPF_RET | BPF_K, SECCOMP_RET_ALLOW);
    sock_fprog program{.len = 1, .filter = &allow};
    assert(prctl(PR_SET_NO_NEW_PRIVS, 1, 0, 0, 0) == 0);
    assert(syscall(__NR_seccomp, SECCOMP_SET_MODE_FILTER, SECCOMP_FILTER_FLAG_TSYNC, &program) == 0);
    const auto snapshot = read_file(snapshot_process_file(proc_file::status));
    assert(status_value(snapshot, "NoNewPrivs") == status_value(baseline, "NoNewPrivs"));
    assert(status_value(snapshot, "Seccomp_filters") == status_value(baseline, "Seccomp_filters"));
    constexpr auto own_signal = 1ULL << static_cast<unsigned>(SIGSYS - 1);
    for (const char *const field : {"SigBlk", "SigCgt"})
        assert((status_value(snapshot, field, 16) & own_signal) ==
               (status_value(baseline, field, 16) & own_signal));

    std::array<std::thread, 4> readers;
    for (auto &reader : readers)
        reader = std::thread([] {
            for (int index = 0; index < 4; index++) {
                expect_maps(read_file(snapshot_process_file(proc_file::maps)));
                assert(read_file(snapshot_process_file(proc_file::status)).contains("NoNewPrivs:"));
            }
        });
    for (auto &reader : readers) reader.join();
    puts("Native module and process view checks passed");
}
