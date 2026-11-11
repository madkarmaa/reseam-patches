// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// ReSharper disable CppUnusedIncludeDirective
#include "view/library_view.h"
#include <sys/types.h>
#include <cstdio>
#include <cstdlib>
#include <memory>
#include <span>
#include <vector>
// ReSharper restore CppUnusedIncludeDirective

extern "C" long signature_raw_syscall(long, long, long, long, long, long, long);

namespace signature::view {
    extern library_view_options target_options;

    struct address_range {
        uintptr_t start;
        uintptr_t end;
    };

    extern std::vector<address_range> hidden_ranges;
    bool extension_library(const char *path);
    bool module_view_installed();
    bool module_view_ready();
    bool prepare_module_ranges(const char *library);
    bool install_module_view();
    bool capture_process_status();
    ssize_t adjust_status_line(const char *line, std::span<char> adjusted, unsigned filters);
    int create_proc_snapshot(proc_file file, pid_t process, unsigned filters);

    // getline can reallocate this storage on every call.
    using line_storage = std::unique_ptr<char, decltype(&std::free)>;

    inline ssize_t next_line(FILE *const file, line_storage &storage, size_t &capacity) {
        char *line = storage.release();
        const ssize_t length = getline(&line, &capacity, file);
        storage.reset(line);
        return length;
    }

    struct received_packet {
        long length;
        int descriptor;
        bool valid;
    };

    void close_raw(int descriptor);
    long send_packet(int socket, void *payload, size_t size, int descriptor);
    received_packet receive_packet(int socket, void *payload, size_t size);
} // namespace signature::view
