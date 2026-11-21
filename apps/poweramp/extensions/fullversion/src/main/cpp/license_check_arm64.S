// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if !defined(__aarch64__)
#error This file describes the arm64 license check
#endif

// Read-only matching data for the pipeline's license verdict check:
//     sub  w8, wStatus, #3
//     cmp  xStatus, #228
//     ccmp w8, #1, #0, le
//     b.hi reject            // logs "FAIL 109" and marks the pipeline unlicensed
// Complementary operands mask the status register and the branch displacement.
.section .rodata.poweramp_playback,"a",%progbits
.p2align 2

license_check_pattern:
    sub w8, w0, #3
    cmp x0, #228
    ccmp w8, #1, #0, le
.Lpatch:
    b.hi .
.Lpattern_end:

license_check_wildcards:
    sub w8, wsp, #3
    cmp sp, #228
    ccmp w8, #1, #0, le
    b.hi . - 4
.Lwildcards_end:

// The patched instruction becomes (original & keep) | replacement: a nop that falls through to
// the accepted verdict.
license_check_keep:
    .long 0
license_check_replacement:
    nop

.global license_check_pattern, license_check_wildcards, license_check_keep, license_check_replacement
.hidden license_check_pattern, license_check_wildcards, license_check_keep, license_check_replacement
.type license_check_pattern, %object
.type license_check_wildcards, %object
.type license_check_keep, %object
.type license_check_replacement, %object
.size license_check_pattern, .Lpattern_end - license_check_pattern
.size license_check_wildcards, .Lwildcards_end - license_check_wildcards
.size license_check_keep, 4
.size license_check_replacement, 4

.p2align 2
.global license_check_size, license_check_wildcards_size, license_check_patch_offset
.hidden license_check_size, license_check_wildcards_size, license_check_patch_offset
.type license_check_size, %object
.type license_check_wildcards_size, %object
.type license_check_patch_offset, %object
license_check_size:
    .long .Lpattern_end - license_check_pattern
.size license_check_size, 4
license_check_wildcards_size:
    .long .Lwildcards_end - license_check_wildcards
.size license_check_wildcards_size, 4
license_check_patch_offset:
    .long .Lpatch - license_check_pattern
.size license_check_patch_offset, 4

.section .note.GNU-stack,"",%progbits
