// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// Raw fstat and fstat64 use kernel layouts. On 32-bit Android, libc's stat instead has
// the fstat64 layout. Import the NDK's UAPI definitions without colliding with libc's types
// or its timestamp accessor macros.
#pragma push_macro("stat")
#pragma push_macro("stat64")
#pragma push_macro("st_atime")
#pragma push_macro("st_mtime")
#pragma push_macro("st_ctime")
#undef stat
#undef stat64
#undef st_atime
#undef st_mtime
#undef st_ctime
#define stat signature_kernel_stat
#define stat64 signature_kernel_stat64
#include <asm/stat.h>
#pragma pop_macro("st_ctime")
#pragma pop_macro("st_mtime")
#pragma pop_macro("st_atime")
#pragma pop_macro("stat64")
#pragma pop_macro("stat")
