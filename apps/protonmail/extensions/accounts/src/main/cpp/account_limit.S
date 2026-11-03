// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Read-only matching data, never executed. Each entry pairs a zero-valued wildcard example
// with an all-one wildcard example. Their differing bits identify the operands to ignore.
// Keep fixed immediates and conditions identical in each pair.
// These six entries identify the post-login free-account maximum in 7.11.11.
.section .rodata.proton_accounts,"a",%progbits
.p2align 2

tag .req x0
options .req x0
maximum .req x0
free_count .req x0

.global proton_account_limit_pattern
.hidden proton_account_limit_pattern
.type proton_account_limit_pattern, %object
proton_account_limit_pattern:
    ldr tag, [options]
    ldr xzr, [sp]                // Wildcard tag and options registers

    cmp tag, #1                  // Some(maximum)
    cmp sp, #1                   // Wildcard tag register

    b.ne .                       // Success when no maximum is configured
    b.ne . - 4                   // Wildcard branch distance

    ldr maximum, [options, #8]
    ldr xzr, [sp, #8]            // Wildcard maximum and options registers

    cmp maximum, free_count
    cmp xzr, xzr                 // Wildcard maximum and free_count registers

    b.ls .                       // Free-account limit exceeded
    b.ls . - 4                   // Wildcard branch distance
.size proton_account_limit_pattern, .-proton_account_limit_pattern

.unreq tag
.unreq options
.unreq maximum
.unreq free_count

.global proton_account_limit_nop
.hidden proton_account_limit_nop
.type proton_account_limit_nop, %object
proton_account_limit_nop:
    nop
.size proton_account_limit_nop, .-proton_account_limit_nop

.section .note.GNU-stack,"",%progbits
