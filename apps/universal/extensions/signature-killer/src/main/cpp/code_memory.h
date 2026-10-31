// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#pragma once

// Uses private executable pages backed by this library, falling back to anonymous pages when
// its file cannot be mapped. Returns MAP_FAILED if neither allocation succeeds.
#ifdef __cplusplus
#include <cstddef>
extern "C" void *signature_allocate_code_block(std::size_t size);
#else
#include <stddef.h>
void *signature_allocate_code_block(size_t size);
#endif
