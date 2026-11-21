// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if !defined(__arm__)
#error This file describes the armeabi-v7a license check
#endif

// Read-only matching data for the pipeline's license verdict check (A32 code):
//     cmp  rStatus, #228
//     bgt  accept
//     sub  r0, rStatus, #3
//     cmp  r0, #1
//     bls  accept
//     ...                    // falls through to "FAIL 109" and marks the pipeline unlicensed
// Wildcards differ from the pattern in the status register and branch displacement bits.
.section .rodata.poweramp_playback,"a",%progbits
.p2align 2

.equ register_bits, 0x000f0000
.equ branch_bits, 0x00ffffff

license_check_pattern:
    .long 0xe35000e4                    // cmp r0, #228
.Lpatch:
    .long 0xca000000                    // bgt
    .long 0xe2400003                    // sub r0, r0, #3
    .long 0xe3500001                    // cmp r0, #1
    .long 0x9a000000                    // bls
.Lpattern_end:

license_check_wildcards:
    .long 0xe35000e4 ^ register_bits
    .long 0xca000000 ^ branch_bits
    .long 0xe2400003 ^ register_bits
    .long 0xe3500001
    .long 0x9a000000 ^ branch_bits
.Lwildcards_end:

// The patched instruction becomes (original & keep) | replacement: the bgt keeps its accept
// target and becomes unconditional.
license_check_keep:
    .long 0x0fffffff
license_check_replacement:
    .long 0xe0000000

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
