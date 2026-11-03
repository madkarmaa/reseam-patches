// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#if defined(__aarch64__)

// Read-only matching data, never executed. Complementary register and branch operands
// in the two examples identify wildcard bits. Fixed immediates and conditions stay equal.
.section .rodata.proton_accounts,"a",%progbits
.p2align 2

tag .req x0
options .req x0
maximum .req x0
free_count .req x0

proton_account_limit_pattern:
    ldr tag, [options]
    cmp tag, #1                  // Some(maximum)
    b.ne .                       // Success when no maximum is configured
    ldr maximum, [options, #8]
    cmp maximum, free_count
.Lpatch:
    b.ls .                       // FreeAccountLimitExceeded
.Lpattern_end:

proton_account_limit_wildcards:
    ldr xzr, [sp]
    cmp sp, #1
    b.ne . - 4
    ldr xzr, [sp, #8]
    cmp xzr, xzr
    b.ls . - 4
.Lwildcards_end:

proton_account_limit_replacement:
    nop                          // Fall through to success
.Lreplacement_end:

.unreq tag
.unreq options
.unreq maximum
.unreq free_count

#elif defined(__arm__)

// Read-only Thumb matching data. Complementary operands identify wildcard bits;
// the maximum and logged-in free-account count are both 64-bit on this ABI.
.syntax unified
.thumb
.section .rodata.proton_accounts,"a",%progbits
.p2align 2

proton_account_limit_pattern:
    ldr r0, [r0]                 // Option tag
.Lpatch:
    lsls r0, r0, #31             // Test Some(maximum)
    beq.w #0                     // Success when no maximum is configured
    ldrd r3, r4, [r1, #8]        // Maximum, low and high words
    subs r0, r0, r0              // Free count minus maximum, low word
    sbcs.w r1, r1, r1            // High word with borrow
    blo.w #0                     // Success while free count < maximum
.Lpattern_end:

proton_account_limit_wildcards:
    ldr r7, [r7]
    lsls r7, r7, #31
    beq.w #-2
    ldrd r12, r11, [lr, #8]
    subs r7, r7, r7
    sbcs.w lr, lr, lr
    blo.w #-2
.Lwildcards_end:

proton_account_limit_replacement:
    cmp r0, r0                   // Set Z without changing registers; take the success BEQ
.Lreplacement_end:

#elif defined(__x86_64__)

// Read-only matching data. Complementary operands identify wildcard bits.
// The short JNE and near JBE forms are intentional parts of this code shape.
.section .rodata.proton_accounts,"a",%progbits
.p2align 2

proton_account_limit_pattern:
    cmpl $1, (%rax)              // Some(maximum)
    jne . + 2                    // Success when no maximum is configured
    movq 8(%rax), %rax           // Maximum
    cmpq %rax, %rax              // Compare maximum with free count
.Lpatch:
    {disp32} jbe . + 6           // FreeAccountLimitExceeded
.Lpattern_end:

proton_account_limit_wildcards:
    cmpl $1, (%rdi)
    jne . + 1
    movq 8(%r15), %r15
    cmpq %r15, %r15
    {disp32} jbe . + 5
.Lwildcards_end:

proton_account_limit_replacement:
    jmp . + 6                   // Skip the entire near JBE, including its four displacement bytes
.Lreplacement_end:

#else
#error Unsupported Proton Mail architecture
#endif

// Derive every byte count and patch position from assembly labels.
.global proton_account_limit_pattern, proton_account_limit_wildcards, proton_account_limit_replacement
.hidden proton_account_limit_pattern, proton_account_limit_wildcards, proton_account_limit_replacement
.type proton_account_limit_pattern, %object
.type proton_account_limit_wildcards, %object
.type proton_account_limit_replacement, %object
.size proton_account_limit_pattern, .Lpattern_end - proton_account_limit_pattern
.size proton_account_limit_wildcards, .Lwildcards_end - proton_account_limit_wildcards
.size proton_account_limit_replacement, .Lreplacement_end - proton_account_limit_replacement

.p2align 2
.global proton_account_limit_size, proton_account_limit_wildcards_size, proton_account_limit_patch_offset, proton_account_limit_replacement_size
.hidden proton_account_limit_size, proton_account_limit_wildcards_size, proton_account_limit_patch_offset, proton_account_limit_replacement_size
.type proton_account_limit_size, %object
.type proton_account_limit_wildcards_size, %object
.type proton_account_limit_patch_offset, %object
.type proton_account_limit_replacement_size, %object
proton_account_limit_size:
    .long .Lpattern_end - proton_account_limit_pattern
.size proton_account_limit_size, 4
proton_account_limit_wildcards_size:
    .long .Lwildcards_end - proton_account_limit_wildcards
.size proton_account_limit_wildcards_size, 4
proton_account_limit_patch_offset:
    .long .Lpatch - proton_account_limit_pattern
.size proton_account_limit_patch_offset, 4
proton_account_limit_replacement_size:
    .long .Lreplacement_end - proton_account_limit_replacement
.size proton_account_limit_replacement_size, 4

.section .note.GNU-stack,"",%progbits
