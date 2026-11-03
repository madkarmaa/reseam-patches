// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if !defined(__aarch64__)
#error Unsupported MotionCam architecture
#endif

// Read-only matching data. Complementary operands mask registers and branch displacement.
// Keep the 5,501,000,000 ns limit, instruction kinds, and branch condition fixed.
.section .rodata.motioncam_recording,"a",%progbits
.p2align 2

.equ trial_duration_ns, 5501000000
.equ halfword_radix, 65536

motioncam_limit_pattern:
    movz x0, #(trial_duration_ns % halfword_radix)
    sub x0, x0, x0
    movk x0, #((trial_duration_ns / halfword_radix) % halfword_radix), lsl #16
    movk x0, #(trial_duration_ns / halfword_radix / halfword_radix), lsl #32
.Lpatch:
    cmp x0, x0
    b.lt .
.Lpattern_end:

motioncam_limit_wildcards:
    movz xzr, #(trial_duration_ns % halfword_radix)
    sub xzr, xzr, xzr
    movk xzr, #((trial_duration_ns / halfword_radix) % halfword_radix), lsl #16
    movk xzr, #(trial_duration_ns / halfword_radix / halfword_radix), lsl #32
    cmp xzr, xzr
    b.lt . - 4
.Lwildcards_end:

motioncam_limit_replacement:
    // Always compare zero with one: LT is true, so the original branch skips the trial stop.
    // No registers or branch destinations change.
    ccmp xzr, #1, #0, al
.Lreplacement_end:

.global motioncam_limit_pattern, motioncam_limit_wildcards, motioncam_limit_replacement
.hidden motioncam_limit_pattern, motioncam_limit_wildcards, motioncam_limit_replacement
.type motioncam_limit_pattern, %object
.type motioncam_limit_wildcards, %object
.type motioncam_limit_replacement, %object
.size motioncam_limit_pattern, .Lpattern_end - motioncam_limit_pattern
.size motioncam_limit_wildcards, .Lwildcards_end - motioncam_limit_wildcards
.size motioncam_limit_replacement, .Lreplacement_end - motioncam_limit_replacement

.p2align 2
.global motioncam_limit_size, motioncam_limit_wildcards_size, motioncam_limit_patch_offset
.hidden motioncam_limit_size, motioncam_limit_wildcards_size, motioncam_limit_patch_offset
.type motioncam_limit_size, %object
.type motioncam_limit_wildcards_size, %object
.type motioncam_limit_patch_offset, %object
motioncam_limit_size:
    .long .Lpattern_end - motioncam_limit_pattern
.size motioncam_limit_size, 4
motioncam_limit_wildcards_size:
    .long .Lwildcards_end - motioncam_limit_wildcards
.size motioncam_limit_wildcards_size, 4
motioncam_limit_patch_offset:
    .long .Lpatch - motioncam_limit_pattern
.size motioncam_limit_patch_offset, 4

.section .note.GNU-stack,"",%progbits
