// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

.text
.global signature_test_bridge
.hidden signature_test_bridge
.type signature_test_bridge, %function
#ifdef __aarch64__
.p2align 2
signature_test_bridge:
    stp x29, x30, [sp, #-16]!
    mov x8, x0
    mov x0, x1
    mov x1, x2
    mov x2, x3
    mov x3, x4
    mov x4, x5
    mov x5, x6
    blr x8
    ldp x29, x30, [sp], #16
    ret
#elif defined(__arm__)
.syntax unified
.arm
.p2align 2
signature_test_bridge:
    push {r4, r5, r7, lr}
    mov r7, r0
    mov r0, r1
    mov r1, r2
    mov r2, r3
    ldr r3, [sp, #16]
    ldr r4, [sp, #20]
    ldr r5, [sp, #24]
    sub sp, sp, #8
    str r4, [sp]
    str r5, [sp, #4]
    blx r7
    add sp, sp, #8
    pop {r4, r5, r7, pc}
#endif
.size signature_test_bridge, .-signature_test_bridge
.global signature_test_bridge_end
.hidden signature_test_bridge_end
signature_test_bridge_end:

.section .note.GNU-stack,"",%progbits
