// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// ReSharper disable CppUnusedIncludeDirective
#include <link.h>
#include <cstring>
// ReSharper restore CppUnusedIncludeDirective

// This callback lives outside the protected executable, so it must retain the complete view.
extern "C" __attribute__((visibility("default"))) int signature_count_support_modules() {
    int count = 0;
    // The dl_iterate_phdr callback type takes a mutable info pointer.
    // ReSharper disable once CppParameterMayBeConstPtrOrRef
    dl_iterate_phdr([](dl_phdr_info *info, size_t, void *data) {
        if (strstr(info->dlpi_name, "libSignatureKiller.so") != nullptr ||
            strstr(info->dlpi_name, "libshadowhook.so") != nullptr)
            ++*static_cast<int *>(data);
        return 0;
    }, &count);
    return count;
}
