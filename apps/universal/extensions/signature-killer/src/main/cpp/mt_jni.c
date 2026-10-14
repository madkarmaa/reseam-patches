// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#include <jni.h>
#include <sys/types.h>
#include <sys/stat.h>
#include <fcntl.h>
#include <stdarg.h>
#include <stdatomic.h>
#include <stdio.h>
#include <stdlib.h>
#include <stdbool.h>
#include <string.h>
#include <pthread.h>
#include "xhook.h"

struct path_mapping {
    // Android's installed base or split path.
    char *installed;
    // The extracted original file for that component.
    char *replacement;
};

struct path_mappings {
    jsize count;
    // Store all component pairs in the same allocation.
    struct path_mapping paths[];
};

// Readers use the table without locking. Its contents never change.
static _Atomic(struct path_mappings *) mappings;
// xHook registration and refresh share this lock.
static pthread_mutex_t install_mutex = PTHREAD_MUTEX_INITIALIZER;
static bool hooks_registered;

static const char *redirect_path(const char *pathname) {
    // Pair with the release store after all path copies finish.
    const struct path_mappings *current = atomic_load_explicit(&mappings, memory_order_acquire);
    // Let libc handle null paths and calls before installation.
    if (pathname == NULL || current == NULL) return pathname;

    // Compare paths as supplied, without resolving aliases or symlinks.
    for (jsize index = 0; index < current->count; index++) {
        if (strcmp(pathname, current->paths[index].installed) == 0)
            return current->paths[index].replacement;
    }

    return pathname;
}

static bool needs_mode(int flags) {
    // Only creation flags require the optional mode argument.
    if ((flags & O_CREAT) != 0) return true;
#ifdef O_TMPFILE
    // O_TMPFILE is a combined flag, so require every bit.
    if ((flags & O_TMPFILE) == O_TMPFILE) return true;
#endif
    return false;
}

// xHook saves the original entry points for forwarding.
static int (*old_open)(const char *, int, ...);
static int openImpl(const char *pathname, int flags, ...) {
    const char *path = redirect_path(pathname);

    if (!needs_mode(flags)) return old_open(path, flags);

    // The caller may omit the mode when these flags are absent.
    va_list arguments;
    va_start(arguments, flags);
    mode_t mode = va_arg(arguments, int);
    va_end(arguments);

    return old_open(path, flags, mode);
}

// Some libraries call the large-file symbols directly.
static int (*old_open64)(const char *, int, ...);
static int open64Impl(const char *pathname, int flags, ...) {
    const char *path = redirect_path(pathname);

    if (!needs_mode(flags)) return old_open64(path, flags);

    va_list arguments;
    va_start(arguments, flags);
    mode_t mode = va_arg(arguments, int);
    va_end(arguments);

    return old_open64(path, flags, mode);
}

// Preserve the directory descriptor when forwarding relative paths.
static int (*old_openat)(int, const char *, int, ...);
static int openatImpl(int fd, const char *pathname, int flags, ...) {
    const char *path = redirect_path(pathname);

    // Keep fd and flags even when the path changes.
    if (!needs_mode(flags)) return old_openat(fd, path, flags);

    va_list arguments;
    va_start(arguments, flags);
    mode_t mode = va_arg(arguments, int);
    va_end(arguments);

    return old_openat(fd, path, flags, mode);
}

static int (*old_openat64)(int, const char *, int, ...);
static int openat64Impl(int fd, const char *pathname, int flags, ...) {
    const char *path = redirect_path(pathname);

    if (!needs_mode(flags)) return old_openat64(fd, path, flags);

    va_list arguments;
    va_start(arguments, flags);
    mode_t mode = va_arg(arguments, int);
    va_end(arguments);

    return old_openat64(fd, path, flags, mode);
}

// Preserve stdio mode strings while redirecting the path.
static FILE *(*old_fopen)(const char *, const char *);
static FILE *fopenImpl(const char *pathname, const char *mode) {
    return old_fopen(redirect_path(pathname), mode);
}

static FILE *(*old_fopen64)(const char *, const char *);
static FILE *fopen64Impl(const char *pathname, const char *mode) {
    return old_fopen64(redirect_path(pathname), mode);
}

// Free tables that readers have never received.
static void free_mappings(struct path_mappings *value) {
    if (value == NULL) return;

    // free accepts null slots left by an interrupted copy.
    for (jsize index = 0; index < value->count; index++) {
        free(value->paths[index].installed);
        free(value->paths[index].replacement);
    }

    free(value);
}

static char *copy_path(JNIEnv *env, jobjectArray paths, jsize index) {
    jstring value = (*env)->GetObjectArrayElement(env, paths, index);
    if (value == NULL) return NULL;

    // Copy the bytes before releasing JNI's string buffer.
    const char *characters = (*env)->GetStringUTFChars(env, value, NULL);
    char *copy = characters == NULL ? NULL : strdup(characters);

    if (characters != NULL) (*env)->ReleaseStringUTFChars(env, value, characters);
    // Release the local reference on each loop iteration.
    (*env)->DeleteLocalRef(env, value);
    return copy;
}

JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_refreshApkPaths(
        __attribute__((unused)) JNIEnv *env, __attribute__((unused)) jclass clazz) {
    // Apply hooklets to libraries loaded since the last refresh.
    pthread_mutex_lock(&install_mutex);
    int result = xhook_refresh(0);
    pthread_mutex_unlock(&install_mutex);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}

JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_hookApkPaths(JNIEnv *env,
        __attribute__((unused)) jclass clazz, jobjectArray installed, jobjectArray replacements) {
    // Both arrays come from the extension's component list.
    if (installed == NULL || replacements == NULL) return JNI_FALSE;

    // Require one replacement for each installed component.
    jsize count = (*env)->GetArrayLength(env, installed);
    if (count <= 0 || count != (*env)->GetArrayLength(env, replacements)) return JNI_FALSE;

    // Allocate the header and path pairs together, with null slots.
    struct path_mappings *candidate = calloc(1, sizeof(*candidate) + (size_t)count * sizeof(candidate->paths[0]));
    if (candidate == NULL) return JNI_FALSE;

    // Readers must never see a partly copied table.
    candidate->count = count;
    for (jsize index = 0; index < count; index++) {
        candidate->paths[index].installed = copy_path(env, installed, index);

        // Leave pending JNI exceptions for the caller.
        if ((*env)->ExceptionCheck(env)) {
            free_mappings(candidate);
            return JNI_FALSE;
        }

        candidate->paths[index].replacement = copy_path(env, replacements, index);

        // Reject null paths and failed string copies.
        if ((*env)->ExceptionCheck(env) || candidate->paths[index].installed == NULL || candidate->paths[index].replacement == NULL) {
            free_mappings(candidate);
            return JNI_FALSE;
        }
    }

    // Serialize mapping installation with registration and refresh.
    pthread_mutex_lock(&install_mutex);
    const struct path_mappings *current = atomic_load_explicit(&mappings, memory_order_acquire);
    if (current != NULL) {
        // Repeated initialization must use the same mappings.
        bool same = current->count == candidate->count;

        // Compare each installed path and its replacement in array order.
        for (jsize index = 0; same && index < count; index++)
            same = strcmp(current->paths[index].installed, candidate->paths[index].installed) == 0 &&
                   strcmp(current->paths[index].replacement, candidate->paths[index].replacement) == 0;

        // Readers keep using the original published table.
        free_mappings(candidate);

        if (!same) {
            // Reject changes while file reads may use the existing table.
            pthread_mutex_unlock(&install_mutex);
            return JNI_FALSE;
        }
    } else {
        // Keep this table until process exit. File reads can outlive this call.
        atomic_store_explicit(&mappings, candidate, memory_order_release);
    }

    // Register once, then refresh on every initialization.
    if (!hooks_registered) {
        // Replace imported libc calls in shared libraries. Direct syscalls stay unchanged.
        int result = xhook_register(".*\\.so$", "openat64", openat64Impl, (void **) &old_openat64);
        // Try every symbol and retain any registration error.
        result |= xhook_register(".*\\.so$", "openat", openatImpl, (void **) &old_openat);
        result |= xhook_register(".*\\.so$", "open64", open64Impl, (void **) &old_open64);
        result |= xhook_register(".*\\.so$", "open", openImpl, (void **) &old_open);
        result |= xhook_register(".*\\.so$", "fopen", fopenImpl, (void **) &old_fopen);
        result |= xhook_register(".*\\.so$", "fopen64", fopen64Impl, (void **) &old_fopen64);

        if (result != 0) {
            pthread_mutex_unlock(&install_mutex);
            return JNI_FALSE;
        }

        // Registration records the replacements; refresh applies them.
        hooks_registered = true;
    }

    // Refresh synchronously before reporting success.
    int result = xhook_refresh(0);
    pthread_mutex_unlock(&install_mutex);
    return result == 0 ? JNI_TRUE : JNI_FALSE;
}
