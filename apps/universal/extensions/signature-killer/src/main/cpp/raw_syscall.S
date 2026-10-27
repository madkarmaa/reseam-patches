// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// long signature_raw_syscall(long number, long first, long second, long third, long fourth,
//                            long fifth, long sixth)
//
// Issues the syscall directly and returns the kernel's result, a negative errno on failure. Unlike
// libc's syscall(), it never touches errno, so the SIGSYS handler can call it safely.

.text
.global signature_raw_syscall
.hidden signature_raw_syscall
.type signature_raw_syscall, %function
#if defined(__aarch64__)
.p2align 2
signature_raw_syscall:
    mov x8, x0
    mov x0, x1
    mov x1, x2
    mov x2, x3
    mov x3, x4
    mov x4, x5
    mov x5, x6
    svc #0
    ret
#elif defined(__arm__)
.syntax unified
.arm
.p2align 2
signature_raw_syscall:
    push {r4, r5, r7, lr}
    mov r7, r0
    mov r0, r1
    mov r1, r2
    mov r2, r3
    ldr r3, [sp, #16]
    ldr r4, [sp, #20]
    ldr r5, [sp, #24]
    svc #0
    pop {r4, r5, r7, pc}
#elif defined(__x86_64__)
.p2align 4
signature_raw_syscall:
    mov %rdi, %rax
    mov %rsi, %rdi
    mov %rdx, %rsi
    mov %rcx, %rdx
    mov %r8, %r10
    mov %r9, %r8
    mov 8(%rsp), %r9
    syscall
    ret
#elif defined(__i386__)
.p2align 4
signature_raw_syscall:
    push %ebx
    push %esi
    push %edi
    push %ebp
    mov 20(%esp), %eax
    mov 24(%esp), %ebx
    mov 28(%esp), %ecx
    mov 32(%esp), %edx
    mov 36(%esp), %esi
    mov 40(%esp), %edi
    mov 44(%esp), %ebp
    int $0x80
    pop %ebp
    pop %edi
    pop %esi
    pop %ebx
    ret
#else
#error Unsupported raw syscall architecture
#endif
.size signature_raw_syscall, .-signature_raw_syscall
.section .note.GNU-stack,"",%progbits
