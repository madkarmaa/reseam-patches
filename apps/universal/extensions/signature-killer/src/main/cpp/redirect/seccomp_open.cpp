// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include "redirect/raw_redirect_internal.h"
#include "logging.h"
#include <link.h>
#include <unistd.h>
#include <algorithm>
#include <cstring>
#include <mutex>
#include <utility>
// ReSharper restore CppUnusedIncludeDirective

namespace signature::redirect {
    // Published before the signal handler is installed, and retained until process exit.
    constinit std::atomic<redirect_state *> active{nullptr};
    static_assert(decltype(active)::is_always_lock_free);
    static_assert(std::atomic<unsigned>::is_always_lock_free);
    static_assert(std::atomic<int>::is_always_lock_free);

    namespace {
        using dl_callback_t = int (*)(dl_phdr_info *, size_t, void *);
        constinit std::mutex install_mutex;
        // Records the executable segments of each loaded library with the configured name.
        // The loader requires a mutable module pointer in its callback signature.
        // ReSharper disable once CppParameterMayBeConstPtrOrRef
        // The loader ABI requires an int callback even when every module is visited.
        // ReSharper disable once CppDFAConstantFunctionResult
        const dl_callback_t find_library = [](dl_phdr_info *const info, size_t /*info_size*/,
                                              void *const data) {
            auto &state = *static_cast<redirect_state *>(data);

            const char *const filename = info->dlpi_name == nullptr ? "" : info->dlpi_name;
            const char *const slash = strrchr(filename, '/');

            if (const char *const basename = slash == nullptr ? filename : slash + 1;
                state.library != basename)
                return 0;

            state.libraries_found++;
            for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
                const auto &segment = info->dlpi_phdr[index];
                if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;

                const uintptr_t start = info->dlpi_addr + segment.p_vaddr;
                state.start = std::min(state.start, start);
                state.end = std::max(state.end, start + static_cast<uintptr_t>(segment.p_memsz));
            }

            return 0;
        };

    } // namespace

} // namespace signature::redirect

using signature::redirect::active;
using signature::redirect::build_filter;
using signature::redirect::build_fork_filter;
using signature::redirect::contains_address;
using signature::redirect::executable_range;
using signature::redirect::filter_failure;
using signature::redirect::find_library;
using signature::redirect::install_filter;
using signature::redirect::install_mutex;
using signature::redirect::install_program;
using signature::redirect::install_sigsys_handler;
using signature::redirect::path_copying_available;
using signature::redirect::prepare_descriptor_identities;
using signature::redirect::redirect_file_syscall;
using signature::redirect::redirect_state;
using signature::redirect::start_event_logger;
using signature::redirect::supports_syscall;
using signature::redirect::syscall_arguments;

bool install_raw_open_redirect(const char *const library, const std::span<const raw_open_mapping> paths,
                               const raw_redirect_options &options) {
    const std::scoped_lock lock(install_mutex);

    if (const redirect_state *const current = active.load(std::memory_order_acquire);
        current != nullptr && current->installed)
        return current->library == library;

    if (paths.empty()) return false;

    auto candidate = std::make_unique<redirect_state>();
    candidate->library = library;
    candidate->paths.assign_range(paths);

    dl_iterate_phdr(find_library, candidate.get());

    candidate->preserve_descriptor_identity = options.preserve_descriptor_identity;
    candidate->redirect_file_contents = options.redirect_file_contents;
    candidate->snapshot_proc_file = options.snapshot_proc_file;
    candidate->bypass_current_thread = options.bypass_current_thread;

    candidate->installing_process = getpid();

    if (candidate->snapshot_proc_file != nullptr) {
        const long limit = sysconf(_SC_OPEN_MAX);
        if (limit <= 0) return false;

        candidate->descriptor_count = static_cast<size_t>(limit);
        candidate->proc_descriptors.reset(candidate->descriptor_count);

        for (size_t index = 0; index < candidate->descriptor_count; index++)
            candidate->proc_descriptors.slot(index).store(-1, std::memory_order_relaxed);
    }

    if (options.start < options.end) {
        candidate->start = options.start;
        candidate->end = options.end;
    }

    if (const bool readable = path_copying_available(library);
        candidate->libraries_found != 1 || candidate->start >= candidate->end || !readable) {
        LOG_ERROR("Cannot trap %s: %u libraries, path copying %s", library, candidate->libraries_found,
                  readable ? "available" : "unavailable");
        return false;
    }

    if (candidate->preserve_descriptor_identity && !prepare_descriptor_identities(*candidate)) return false;

    // Publish before installing the handler, together with the previous disposition, so unrelated
    // SIGSYS signals can use it. A signal may still be using the published state, so it is
    // retained from here on, even when installation fails.
    if (sigaction(SIGSYS, nullptr, &candidate->previous_handler) != 0) return false;

    redirect_state *const state = candidate.release();
    active.store(state, std::memory_order_release);

    auto filter = build_filter(state->start, state->end, state->preserve_descriptor_identity,
                               state->redirect_file_contents);
    if (options.install_after_fork) {
        // The signal handler installs the held-back filter in the parent of the library's first fork.
        state->deferred_filter = std::move(filter);
        state->filter_deferred.store(true, std::memory_order_release);
        filter = build_fork_filter();
    }

    if (!install_sigsys_handler()) return false;

    if (const std::expected<void, filter_failure> installed = install_program(filter); !installed) {
        sigaction(SIGSYS, &state->previous_handler, nullptr);
        LOG_ERROR("Installing raw APK file seccomp filter failed: %s %ld", installed.error().cause,
                  installed.error().value);
        return false;
    }

    // Filter installation is irreversible. State and mapping strings live until process exit;
    // forked children inherit them, the signal disposition and the filter.
    state->installed = true;
    state->filters.fetch_add(1, std::memory_order_release);
    if (!diagnostics_enabled()) return true;

    start_event_logger(*state);
    LOG_INFO("Raw APK file seccomp filter %s for %s [%p, %p), all threads",
             options.install_after_fork ? "waits for the first fork" : "installed", library,
             reinterpret_cast<void *>(state->start), reinterpret_cast<void *>(state->end));
    return true;
}

bool raw_redirect_contains_address(const uintptr_t address) {
    const redirect_state *const state = active.load(std::memory_order_acquire);
    return state != nullptr && contains_address(*state, address);
}

unsigned raw_redirect_filter_count() {
    const redirect_state *const state = active.load(std::memory_order_acquire);
    return state == nullptr ? 0 : state->filters.load(std::memory_order_acquire);
}

bool extend_raw_open_redirect(const uintptr_t start, const uintptr_t end) {
    const std::scoped_lock lock(install_mutex);
    redirect_state *const state = active.load(std::memory_order_acquire);
    if (state == nullptr || !state->installed || start >= end) return false;

    // A filter installed now would reach the child that the held-back filter must not reach.
    if (state->filter_deferred.load(std::memory_order_acquire)) {
        LOG_ERROR("Cannot extend native APK redirection before %s forks", state->library.c_str());
        return false;
    }

    if (start >= state->start && end <= state->end) return true;
    for (const executable_range *range = state->unpacked_ranges.load(std::memory_order_acquire);
         range != nullptr; range = range->next)
        if (start >= range->start && end <= range->end) return true;

    // Every replay originates here. Covering this address would recursively trap the handler.
    if (const auto replay = reinterpret_cast<uintptr_t>(&signature_raw_syscall);
        replay >= start && replay <= end)
        return false;

    auto range = std::make_unique<executable_range>(executable_range{
        .start = start,
        .end = end,
        .next = state->unpacked_ranges.load(std::memory_order_acquire),
    });
    state->unpacked_ranges.store(range.release(), std::memory_order_release);

    if (const auto result =
            install_filter(start, end, state->preserve_descriptor_identity, state->redirect_file_contents);
        !result) {
        LOG_ERROR("Cannot extend native APK redirection: %s %ld", result.error().cause, result.error().value);
        return false;
    }

    state->filters.fetch_add(1, std::memory_order_release);
    LOG_INFO("Raw APK file redirection covers unpacked code [%p, %p)", reinterpret_cast<void *>(start),
             reinterpret_cast<void *>(end));
    return true;
}

std::optional<long> redirect_native_file_syscall(const uintptr_t caller, const int number, const long first,
                                                 const long second, const long third, const long fourth,
                                                 const long fifth, const long sixth) {
    redirect_state *const state = active.load(std::memory_order_acquire);
    if (state == nullptr || !contains_address(*state, caller) || !supports_syscall(*state, number) ||
        (state->bypass_current_thread != nullptr && state->bypass_current_thread()))
        return std::nullopt;

    const syscall_arguments arguments{
        .first = first,
        .second = second,
        .third = third,
        .fourth = fourth,
        .fifth = fifth,
        .sixth = sixth,
    };
    return redirect_file_syscall(*state, number, arguments);
}
