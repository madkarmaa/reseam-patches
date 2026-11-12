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
#else
#error Unsupported raw syscall architecture
#endif
.size signature_raw_syscall, .-signature_raw_syscall

#if defined(__aarch64__)
// void signature_resume_context(const struct sigcontext *registers,
//                               const struct fpsimd_context *fpsimd)
//
// Continues an interrupted context without rt_sigreturn, which a forked child's own seccomp filter
// may refuse. The caller has already restored the signal mask. x16 carries the resume address and
// x17 the context, so both lose their interrupted values: only resume where they are dead.
.global signature_resume_context
.hidden signature_resume_context
.type signature_resume_context, %function
.p2align 2
signature_resume_context:
    cbz x1, 1f
    ldr w2, [x1, #8]
    msr fpsr, x2
    ldr w2, [x1, #12]
    msr fpcr, x2
    add x2, x1, #16
    ldp q0, q1, [x2, #0]
    ldp q2, q3, [x2, #32]
    ldp q4, q5, [x2, #64]
    ldp q6, q7, [x2, #96]
    ldp q8, q9, [x2, #128]
    ldp q10, q11, [x2, #160]
    ldp q12, q13, [x2, #192]
    ldp q14, q15, [x2, #224]
    ldp q16, q17, [x2, #256]
    ldp q18, q19, [x2, #288]
    ldp q20, q21, [x2, #320]
    ldp q22, q23, [x2, #352]
    ldp q24, q25, [x2, #384]
    ldp q26, q27, [x2, #416]
    ldp q28, q29, [x2, #448]
    ldp q30, q31, [x2, #480]
1:
    mov x17, x0
    ldr x2, [x17, #272]
    msr nzcv, x2
    ldr x2, [x17, #256]
    mov sp, x2
    ldr x16, [x17, #264]
    ldp x0, x1, [x17, #8]
    ldp x2, x3, [x17, #24]
    ldp x4, x5, [x17, #40]
    ldp x6, x7, [x17, #56]
    ldp x8, x9, [x17, #72]
    ldp x10, x11, [x17, #88]
    ldp x12, x13, [x17, #104]
    ldp x14, x15, [x17, #120]
    ldp x18, x19, [x17, #152]
    ldp x20, x21, [x17, #168]
    ldp x22, x23, [x17, #184]
    ldp x24, x25, [x17, #200]
    ldp x26, x27, [x17, #216]
    ldp x28, x29, [x17, #232]
    ldr x30, [x17, #248]
    // RET, unlike BR, needs no BTI landing pad at the resume address.
    ret x16
.size signature_resume_context, .-signature_resume_context
#elif defined(__arm__)
// void signature_resume_context(const struct sigcontext *registers, const void *vfp)
//
// Continues an interrupted context without sigreturn, which a forked child's own seccomp filter may
// refuse. The caller has already restored the signal mask and set the Thumb bit of the resume address.
// vfp points at the saved d0-d31 followed by fpscr, or is null.
.global signature_resume_context
.hidden signature_resume_context
.type signature_resume_context, %function
.fpu neon
.p2align 2
signature_resume_context:
    cmp r1, #0
    beq 1f
    vldmia r1!, {d0-d15}
    vldmia r1!, {d16-d31}
    ldr r2, [r1]
    vmsr fpscr, r2
1:
    ldr r1, [r0, #76]
    msr APSR_nzcvq, r1
    ldr sp, [r0, #64]
    ldr lr, [r0, #68]
    // Stage the resume address in the free stack below sp, then load r0-r12 from arm_r0 on.
    ldr r1, [r0, #72]
    push {r1}
    add r0, r0, #12
    ldmia r0, {r0-r12}
    // Popping pc interworks, so a Thumb resume address continues in Thumb state.
    pop {pc}
.size signature_resume_context, .-signature_resume_context
#endif
.section .note.GNU-stack,"",%progbits
