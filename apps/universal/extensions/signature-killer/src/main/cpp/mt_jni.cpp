// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

#include <jni.h>
#include <android/log.h>
#include <dlfcn.h>
#include <fcntl.h>
#include <link.h>
#include <atomic>
#include <cstdarg>
#include <cstdint>
#include <cstdio>
#include <cstring>
#include <memory>
#include <mutex>
#include <optional>
#include <string>
#include <vector>
#include "bytehook.h"
#if defined(__aarch64__) || defined(__arm__)
#include "shadowhook.h"
#endif

#if defined(__aarch64__)
#define PROCESS_ABI "arm64-v8a"
#elif defined(__arm__)
#define PROCESS_ABI "armeabi-v7a"
#elif defined(__x86_64__)
#define PROCESS_ABI "x86_64"
#else
#define PROCESS_ABI "x86"
#endif

namespace {

// Set from the patch's diagnostics option right after loading. When off, nothing is logged.
std::atomic<bool> diagnostics{false};

bool logging() { return diagnostics.load(std::memory_order_relaxed); }

}  // namespace

#define LOG(priority, ...)                                                          \
    do {                                                                            \
        if (logging()) __android_log_print(priority, "SignatureKiller", __VA_ARGS__); \
    } while (false)
#define LOG_DEBUG(...) LOG(ANDROID_LOG_DEBUG, __VA_ARGS__)
#define LOG_INFO(...) LOG(ANDROID_LOG_INFO, __VA_ARGS__)
#define LOG_WARN(...) LOG(ANDROID_LOG_WARN, __VA_ARGS__)
#define LOG_ERROR(...) LOG(ANDROID_LOG_ERROR, __VA_ARGS__)

namespace {

// Admits only the first events of a frequent kind, so hot paths cannot flood logcat.
bool admit_event(std::atomic<unsigned> &count, unsigned limit, const char *kind) {
    if (!logging()) return false;

    unsigned number = count.fetch_add(1, std::memory_order_relaxed) + 1;
    if (number == limit + 1) LOG_DEBUG("Later %s are not logged", kind);
    return number <= limit;
}

struct code_location {
    const char *library;
    size_t offset;
};

// Names the library an address belongs to, so logs show who opened a file.
code_location locate(const void *address) {
    Dl_info info{};
    if (address == nullptr || dladdr(address, &info) == 0 || info.dli_fname == nullptr)
        return {"unknown", 0};

    const char *basename = strrchr(info.dli_fname, '/');
    return {basename == nullptr ? info.dli_fname : basename + 1,
            static_cast<size_t>(static_cast<const char *>(address) -
                                static_cast<const char *>(info.dli_fbase))};
}

struct path_mapping {
    // Android's installed base or split path.
    std::string installed;
    // The extracted original file for that component.
    std::string replacement;
};

using path_mappings = std::vector<path_mapping>;

// Readers use the table without locking. It is installed once, never changes and is never freed,
// since file reads can run until the process exits.
std::atomic<const path_mappings *> mappings{nullptr};

const char *find_replacement(const char *pathname) {
    // Pair with the release exchange after all path copies finish.
    const path_mappings *current = mappings.load(std::memory_order_acquire);
    // Let libc handle null paths and calls before installation.
    if (pathname == nullptr || current == nullptr) return nullptr;

    // Compare paths as supplied, without resolving aliases or symlinks.
    for (const path_mapping &mapping : *current) {
        if (mapping.installed == pathname) return mapping.replacement.c_str();
    }

    return nullptr;
}

std::atomic<unsigned> file_redirections{0};
std::atomic<unsigned> unmatched_apk_opens{0};

bool is_apk(const char *pathname) {
    size_t length = strlen(pathname);
    return length >= 4 && strcmp(pathname + length - 4, ".apk") == 0;
}

// Returns the path a hooked libc call should open, logging what the hooks saw.
const char *redirect_file(const char *pathname, const char *function, const void *caller) {
    const char *replacement = find_replacement(pathname);
    if (replacement != nullptr) {
        if (admit_event(file_redirections, 64, "file redirections")) {
            code_location location = locate(caller);
            LOG_INFO("%s(%s) from %s+0x%zx -> %s", function, pathname, location.library,
                     location.offset, replacement);
        }
        return replacement;
    }

    // An APK that is not redirected may reach the installed files through another path form.
    if (pathname != nullptr && logging() && is_apk(pathname) &&
        admit_event(unmatched_apk_opens, 32, "unmatched APK opens")) {
        code_location location = locate(caller);
        LOG_DEBUG("%s(%s) from %s+0x%zx is not an installed component; left unchanged",
                  function, pathname, location.library, location.offset);
    }
    return pathname;
}

std::optional<std::string> read_string(JNIEnv *env, jstring value) {
    if (value == nullptr) return std::nullopt;

    const char *characters = env->GetStringUTFChars(value, nullptr);
    if (characters == nullptr) return std::nullopt;

    std::string copy = characters;
    env->ReleaseStringUTFChars(value, characters);
    return copy;
}

std::optional<std::string> read_element(JNIEnv *env, jobjectArray values, jsize index) {
    auto value = static_cast<jstring>(env->GetObjectArrayElement(values, index));
    std::optional<std::string> copy = read_string(env, value);
    // Release the local reference on each loop iteration.
    env->DeleteLocalRef(value);
    return copy;
}

#if defined(__aarch64__) || defined(__arm__)
struct private_open_hook {
    std::string library_name;
    uintptr_t offset;
    uint32_t instruction;
    unsigned int path_register;
    std::atomic<unsigned> redirections{0};
    std::atomic<unsigned> unmatched_opens{0};
};

struct private_open_target {
    const char *library_name;
    uintptr_t offset;
    uintptr_t address;
    unsigned int libraries_found;
};

// The host app's ShadowHook v2, resolved and initialized on first use.
struct {
    decltype(&shadowhook_intercept_instr_addr) intercept;
    decltype(&shadowhook_get_errno) get_error;
    // Missing from some 2.0 builds.
    decltype(&shadowhook_to_errmsg) describe_error;
} host_shadowhook;

std::mutex private_open_mutex;
// Interceptors can run while the process exits, so the hook records are never destroyed.
auto &private_open_hooks = *new std::vector<std::unique_ptr<private_open_hook>>();

template<typename Function>
Function *find_symbol(void *library, const char *name) {
    return reinterpret_cast<Function *>(dlsym(library, name));
}

const char *describe_shadowhook_error(int error) {
    if (host_shadowhook.describe_error == nullptr) return "no description";
    return host_shadowhook.describe_error(error);
}

bool load_shadowhook() {
    if (host_shadowhook.intercept != nullptr) return true;

    void *library = dlopen("libshadowhook.so", RTLD_NOW | RTLD_LOCAL);
    if (library == nullptr) {
        LOG_ERROR("Host ShadowHook is unavailable: %s", dlerror());
        return false;
    }

    auto initialize = find_symbol<decltype(shadowhook_init)>(library, "shadowhook_init");
    auto intercept = find_symbol<decltype(shadowhook_intercept_instr_addr)>(
            library, "shadowhook_intercept_instr_addr");
    auto get_error = find_symbol<decltype(shadowhook_get_errno)>(library, "shadowhook_get_errno");
    auto get_initialization_error =
            find_symbol<decltype(shadowhook_get_init_errno)>(library, "shadowhook_get_init_errno");
    auto get_version =
            find_symbol<decltype(shadowhook_get_version)>(library, "shadowhook_get_version");
    host_shadowhook.describe_error =
            find_symbol<decltype(shadowhook_to_errmsg)>(library, "shadowhook_to_errmsg");
    if (initialize == nullptr || intercept == nullptr || get_error == nullptr) {
        LOG_ERROR("Host ShadowHook v2 API is unavailable (init %s, intercept_instr_addr %s, "
                  "get_errno %s)", initialize ? "found" : "missing",
                  intercept ? "found" : "missing", get_error ? "found" : "missing");
        return false;
    }
    LOG_INFO("Using host ShadowHook %s", get_version == nullptr ? "(unknown version)" : get_version());

    // The first initialization fixes the process-wide default mode, so keep any choice the host
    // already made. Older 2.0 builds cannot report it; ShadowHook's own defaults then match a plain
    // host init.
    int result = get_initialization_error == nullptr ? SHADOWHOOK_ERRNO_UNINIT
                                                     : get_initialization_error();
    if (result == SHADOWHOOK_ERRNO_UNINIT) {
        result = initialize(SHADOWHOOK_MODE_SHARED, false);
        LOG_DEBUG("Initialized ShadowHook in shared mode: %d", result);
    } else {
        LOG_DEBUG("ShadowHook was already initialized by the host: %d", result);
    }
    if (result != 0) {
        LOG_ERROR("ShadowHook initialization failed: %d (%s)", result,
                  describe_shadowhook_error(result));
        return false;
    }

    host_shadowhook.intercept = intercept;
    host_shadowhook.get_error = get_error;
    return true;
}

void intercept_private_open(shadowhook_cpu_context_t *context, void *data) {
    auto hook = static_cast<private_open_hook *>(data);
    auto pathname = reinterpret_cast<const char *>(context->regs[hook->path_register]);
    const char *replacement = find_replacement(pathname);
    if (replacement == nullptr) {
        // Seeing which paths a call site opens confirms the patch chose the right register.
        if (admit_event(hook->unmatched_opens, 16, "unredirected private opens"))
            LOG_DEBUG("%s+0x%zx opens %s; not an installed component", hook->library_name.c_str(),
                      static_cast<size_t>(hook->offset), pathname);
        return;
    }

    context->regs[hook->path_register] = reinterpret_cast<uintptr_t>(replacement);
    if (admit_event(hook->redirections, 16, "private open redirections"))
        LOG_INFO("%s+0x%zx opens %s -> %s", hook->library_name.c_str(),
                 static_cast<size_t>(hook->offset), pathname, replacement);
}

int find_private_open(dl_phdr_info *info, size_t, void *data) {
    auto target = static_cast<private_open_target *>(data);
    const char *basename = strrchr(info->dlpi_name, '/');
    basename = basename == nullptr ? info->dlpi_name : basename + 1;
    if (strcmp(basename, target->library_name) != 0) return 0;

    target->libraries_found++;
    LOG_DEBUG("Found %s loaded at %p", info->dlpi_name, reinterpret_cast<void *>(info->dlpi_addr));
    // Thumb function pointers retain bit zero, but the instruction bytes do not.
    uintptr_t instruction_offset = target->offset;
#if defined(__arm__)
    instruction_offset &= ~uintptr_t{1};
#endif
    for (ElfW(Half) index = 0; index < info->dlpi_phnum; index++) {
        const ElfW(Phdr) &segment = info->dlpi_phdr[index];
        if (segment.p_type != PT_LOAD || (segment.p_flags & PF_X) == 0) continue;
        if (instruction_offset < segment.p_vaddr || segment.p_memsz < sizeof(uint32_t)) continue;
        uintptr_t relative = instruction_offset - segment.p_vaddr;
        if (relative > segment.p_memsz - sizeof(uint32_t)) continue;
        target->address = info->dlpi_addr + target->offset;
        break;
    }
    return 0;
}

bool install_private_open(const char *library_name, uintptr_t offset, uint32_t instruction,
                          unsigned int path_register) {
    for (const auto &hook : private_open_hooks) {
        if (hook->offset != offset || hook->library_name != library_name) continue;

        bool same = hook->instruction == instruction && hook->path_register == path_register;
        if (same)
            LOG_DEBUG("%s+0x%zx is already redirected", library_name, static_cast<size_t>(offset));
        else
            LOG_ERROR("%s+0x%zx is already redirected for instruction 0x%08x and register %u, "
                      "not 0x%08x and register %u", library_name, static_cast<size_t>(offset),
                      hook->instruction, hook->path_register, instruction, path_register);
        return same;
    }

    private_open_target target = {library_name, offset, 0, 0};
    dl_iterate_phdr(find_private_open, &target);
    if (target.libraries_found != 1 || target.address == 0) {
        LOG_ERROR("Private open target is missing or ambiguous in %s: loaded %u time(s), offset "
                  "0x%zx %s an executable segment", library_name, target.libraries_found,
                  static_cast<size_t>(offset), target.address == 0 ? "outside" : "inside");
        return false;
    }

    uintptr_t instruction_address = target.address;
#if defined(__arm__)
    instruction_address &= ~uintptr_t{1};
#endif
    uint32_t actual_instruction;
    memcpy(&actual_instruction, reinterpret_cast<const void *>(instruction_address),
           sizeof(actual_instruction));
    if (actual_instruction != instruction) {
        LOG_ERROR("Private open instruction differs in %s+0x%zx: expected 0x%08x, found 0x%08x",
                  library_name, static_cast<size_t>(offset), instruction, actual_instruction);
        return false;
    }

    if (!load_shadowhook()) return false;

    auto hook = std::make_unique<private_open_hook>();
    hook->library_name = library_name;
    hook->offset = offset;
    hook->instruction = instruction;
    hook->path_register = path_register;
    if (host_shadowhook.intercept(reinterpret_cast<void *>(target.address), intercept_private_open,
                                  hook.get(), SHADOWHOOK_INTERCEPT_DEFAULT) == nullptr) {
        int error = host_shadowhook.get_error();
        LOG_ERROR("Private open interception failed at %s+0x%zx: %d (%s)", library_name,
                  static_cast<size_t>(offset), error, describe_shadowhook_error(error));
        return false;
    }

    private_open_hooks.push_back(std::move(hook));
    LOG_INFO("Redirecting private APK opens at %s+0x%zx (%p) using register %u", library_name,
             static_cast<size_t>(offset), reinterpret_cast<void *>(target.address), path_register);
    return true;
}

// ARM64 instructions are word aligned and pass paths in x0-x30. ARM32 call sites are Thumb
// addresses with bit zero set and use r0-r12.
bool valid_call_site(jlong offset, jint path_register) {
    if (offset < 0 || static_cast<uint64_t>(offset) > UINTPTR_MAX || path_register < 0)
        return false;
#if defined(__aarch64__)
    return path_register <= 30 && (offset & 3) == 0;
#else
    return path_register <= 12 && (offset & 1) != 0;
#endif
}
#endif

bool needs_mode(int flags) {
    // Only creation flags require the optional mode argument.
    if ((flags & O_CREAT) != 0) return true;
#ifdef O_TMPFILE
    // O_TMPFILE is a combined flag, so require every bit.
    if ((flags & O_TMPFILE) == O_TMPFILE) return true;
#endif
    return false;
}

// bytehook replaces each caller's import and forwards through the function it replaced there,
// so hooks other libraries placed on the same import keep running.
//
// Each proxy also serves the matching large-file symbol, which has the same signature. bytehook
// keeps one hook chain per import slot, so forwarding still reaches the symbol that was called.
int open_proxy(const char *pathname, int flags, ...) {
    BYTEHOOK_STACK_SCOPE();
    // Like bionic, read the mode only when the flags require it and forward zero otherwise.
    mode_t mode = 0;
    if (needs_mode(flags)) {
        va_list arguments;
        va_start(arguments, flags);
        mode = static_cast<mode_t>(va_arg(arguments, int));
        va_end(arguments);
    }

    const char *path = redirect_file(pathname, "open", BYTEHOOK_RETURN_ADDRESS());
    return BYTEHOOK_CALL_PREV(open_proxy, path, flags, mode);
}

// Preserve the directory descriptor when forwarding relative paths.
int openat_proxy(int fd, const char *pathname, int flags, ...) {
    BYTEHOOK_STACK_SCOPE();
    mode_t mode = 0;
    if (needs_mode(flags)) {
        va_list arguments;
        va_start(arguments, flags);
        mode = static_cast<mode_t>(va_arg(arguments, int));
        va_end(arguments);
    }

    const char *path = redirect_file(pathname, "openat", BYTEHOOK_RETURN_ADDRESS());
    return BYTEHOOK_CALL_PREV(openat_proxy, fd, path, flags, mode);
}

// FORTIFY builds call these checked entry points when the flags are not compile-time constants.
int open_2_proxy(const char *pathname, int flags) {
    BYTEHOOK_STACK_SCOPE();
    const char *path = redirect_file(pathname, "__open_2", BYTEHOOK_RETURN_ADDRESS());
    return BYTEHOOK_CALL_PREV(open_2_proxy, path, flags);
}

int openat_2_proxy(int fd, const char *pathname, int flags) {
    BYTEHOOK_STACK_SCOPE();
    const char *path = redirect_file(pathname, "__openat_2", BYTEHOOK_RETURN_ADDRESS());
    return BYTEHOOK_CALL_PREV(openat_2_proxy, fd, path, flags);
}

// Preserve stdio mode strings while redirecting the path.
FILE *fopen_proxy(const char *pathname, const char *mode) {
    BYTEHOOK_STACK_SCOPE();
    const char *path = redirect_file(pathname, "fopen", BYTEHOOK_RETURN_ADDRESS());
    return BYTEHOOK_CALL_PREV(fopen_proxy, path, mode);
}

struct file_hook {
    const char *symbol;
    void *proxy;
};

// Some libraries import the large-file symbols directly.
const file_hook file_hooks[] = {
        {"open",       reinterpret_cast<void *>(open_proxy)    },
        {"open64",     reinterpret_cast<void *>(open_proxy)    },
        {"openat",     reinterpret_cast<void *>(openat_proxy)  },
        {"openat64",   reinterpret_cast<void *>(openat_proxy)  },
        {"__open_2",   reinterpret_cast<void *>(open_2_proxy)  },
        {"__openat_2", reinterpret_cast<void *>(openat_2_proxy)},
        {"fopen",      reinterpret_cast<void *>(fopen_proxy)   },
        {"fopen64",    reinterpret_cast<void *>(fopen_proxy)   },
};

// Reports each library whose import bytehook replaced, now or when the library loads later.
void log_hook_result(bytehook_stub_t, int status, const char *caller_path_name,
                     const char *symbol, void *, void *previous, void *) {
    // bytehook reports the resolved original separately after a successful hook.
    if (status == BYTEHOOK_STATUS_CODE_ORIG_ADDR) return;

    if (status == BYTEHOOK_STATUS_CODE_OK) {
        // A previous function outside libc means another hooker already replaced this import.
        LOG_DEBUG("Hooked %s in %s (forwarding to %s)", symbol, caller_path_name,
                  locate(previous).library);
        return;
    }
    LOG_WARN("Hooking %s in %s failed with bytehook status %d", symbol, caller_path_name, status);
}

bool register_file_hooks() {
    int status = bytehook_init(BYTEHOOK_MODE_AUTOMATIC, false);
    if (status != BYTEHOOK_STATUS_CODE_OK) {
        LOG_ERROR("bytehook initialization failed: %d", status);
        return false;
    }

    // The proxies forward through bytehook, so this library's own imports stay original.
    if (bytehook_add_ignore("libSignatureKiller.so") != 0) {
        LOG_ERROR("bytehook cannot ignore libSignatureKiller.so");
        return false;
    }

    // Match imports by name rather than by libc's address, so entries another hooker already
    // replaced are chained too. Automatic mode also hooks every library loaded later; direct
    // syscalls stay unchanged.
    bytehook_hooked_t report = logging() ? log_hook_result : nullptr;
    for (const auto &[symbol, proxy] : file_hooks) {
        if (bytehook_hook_all(nullptr, symbol, proxy, report, nullptr) != nullptr) continue;
        LOG_ERROR("Hooking %s failed", symbol);
        return false;
    }
    LOG_INFO("File hooks registered for %zu libc symbols in every loaded and later library",
             sizeof(file_hooks) / sizeof(file_hooks[0]));
    return true;
}

}  // namespace

extern "C" JNIEXPORT void JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_setNativeDiagnostics(JNIEnv *, jclass,
                                                                           jboolean enabled) {
    diagnostics.store(enabled == JNI_TRUE, std::memory_order_relaxed);
    LOG_INFO("Native library loaded for %s with bytehook %s", PROCESS_ABI, bytehook_get_version());
}

#if defined(__aarch64__) || defined(__arm__)
extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_hookPrivateOpen(
        JNIEnv *env, jclass, jstring library_name, jlong offset, jint instruction,
        jint path_register) {
    if (!valid_call_site(offset, path_register)) {
        LOG_ERROR("Rejected private open at offset 0x%llx with register %d: invalid for %s",
                  static_cast<unsigned long long>(offset), path_register, PROCESS_ABI);
        return JNI_FALSE;
    }
    if (mappings.load(std::memory_order_acquire) == nullptr) {
        LOG_ERROR("Rejected private open: file redirection is not installed");
        return JNI_FALSE;
    }

    std::optional<std::string> name = read_string(env, library_name);
    if (!name || name->empty() || name->find('/') != std::string::npos) {
        LOG_ERROR("Rejected private open: library name must be a non-empty file name");
        return JNI_FALSE;
    }

    std::lock_guard lock(private_open_mutex);
    return install_private_open(name->c_str(), static_cast<uintptr_t>(offset),
                                static_cast<uint32_t>(instruction),
                                static_cast<unsigned int>(path_register)) ? JNI_TRUE : JNI_FALSE;
}
#else
extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_hookPrivateOpen(JNIEnv *, jclass, jstring,
                                                                       jlong, jint, jint) {
    LOG_ERROR("Private open interception requires ARM or ARM64, not %s", PROCESS_ABI);
    return JNI_FALSE;
}
#endif

extern "C" JNIEXPORT jboolean JNICALL
Java_top_madkarma_universal_signature_SignatureKiller_hookApkPaths(
        JNIEnv *env, jclass, jobjectArray installed, jobjectArray replacements) {
    // Both arrays come from the extension's component list.
    if (installed == nullptr || replacements == nullptr) {
        LOG_ERROR("Rejected path mappings: an array is null");
        return JNI_FALSE;
    }

    // Require one replacement for each installed component.
    jsize count = env->GetArrayLength(installed);
    jsize replacement_count = env->GetArrayLength(replacements);
    if (count <= 0 || count != replacement_count) {
        LOG_ERROR("Rejected path mappings: %d installed paths and %d replacements", count,
                  replacement_count);
        return JNI_FALSE;
    }

    // Readers must never see a partly copied table. Null paths, failed copies and pending JNI
    // exceptions all stop the copy.
    auto candidate = std::make_unique<path_mappings>();
    for (jsize index = 0; index < count; index++) {
        std::optional<std::string> installed_path = read_element(env, installed, index);
        std::optional<std::string> replacement =
                installed_path ? read_element(env, replacements, index) : std::nullopt;
        if (!installed_path || !replacement) {
            LOG_ERROR("Rejected path mappings: pair %d is null or unreadable", index);
            return JNI_FALSE;
        }

        LOG_DEBUG("Path mapping %d: %s -> %s", index, installed_path->c_str(),
                  replacement->c_str());
        candidate->push_back({std::move(*installed_path), std::move(*replacement)});
    }

    // The extension installs one table per process.
    const path_mappings *expected = nullptr;
    if (!mappings.compare_exchange_strong(expected, candidate.get(), std::memory_order_release,
                                          std::memory_order_relaxed)) {
        LOG_ERROR("Rejected path mappings: a table is already installed in this process");
        return JNI_FALSE;
    }
    candidate.release();
    LOG_INFO("Installed %d path mappings", count);

    // Hooks stay registered for the process lifetime and cover later libraries automatically.
    return register_file_hooks() ? JNI_TRUE : JNI_FALSE;
}
