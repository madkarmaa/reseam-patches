@file:Suppress("unused")

package top.madkarma.revisions

import app.reseam.patch.PatchBuilder
import app.reseam.patch.PatchRuntime
import app.reseam.patch.ReseamPatch
import app.reseam.patch.patch
import java.nio.charset.StandardCharsets

private const val APPLIED_PATH = "assets/reseam/applied.json"
private const val REVISIONS_RESOURCE = "/revisions.json"
private const val HELPER_FILE = "PatchRecords.kt"

/**
 * Records the patch inferred at declaration time as applied to the APK, with
 * its current revision. Ids are Kotlin package + property (slugs and dots)
 * and revisions hex, so neither needs JSON escaping.
 */
private fun PatchRuntime.recordAppliedId(id: String) {
    val hash = revisionEntry(revisionsText, id)

    val existing = files.read(APPLIED_PATH)?.toString(StandardCharsets.UTF_8).orEmpty()
    if (existing.contains("\"$id\":")) return

    val merged =
        if (existing.isBlank()) {
            "{\"$id\":\"$hash\"}"
        } else {
            existing.trimEnd().removeSuffix("}") + ",\"$id\":\"$hash\"}"
        }

    files.write(APPLIED_PATH, merged.toByteArray(StandardCharsets.UTF_8))
}

private fun revisionEntry(
    text: String,
    id: String,
): String {
    val key = "\"$id\":\""

    val hashStart =
        text.indexOf(key).takeIf { it >= 0 }?.plus(key.length)
            ?: error("patch $id has no revision entry")

    val hashEnd = text.indexOf('"', hashStart)
    if (hashEnd < 0) error("patch $id has a malformed revision entry")

    return text.substring(hashStart, hashEnd)
}

/**
 * Declares a patch that records itself as applied, with its current
 * revision, once its execute block succeeds. A drop-in replacement for
 * patch(): identity, options, and dependencies are untouched.
 *
 * The declaration id is inferred from the initializing file via the
 * generated file table, so one patch per file is required (enforced at
 * build time).
 */
fun recordedPatch(
    name: String,
    block: PatchBuilder.() -> Unit,
): ReseamPatch {
    val inner = patch(name, block)
    val id = inferDeclaringId()

    return object : ReseamPatch by inner {
        override fun execute(ctx: PatchRuntime) {
            inner.execute(ctx)
            ctx.recordAppliedId(id)
        }

        override fun toString() = inner.toString()
    }
}

private fun inferDeclaringId(): String {
    val text = revisionsText

    // Declaration time: the initializing file's facade is on the stack, so
    // the first frame naming a declared patch file is this patch.
    val caller =
        Thread.currentThread().stackTrace.firstOrNull {
            it.fileName != null && it.fileName != HELPER_FILE &&
                text.contains("\"${it.className.substringBeforeLast('.')}/${it.fileName}\":\"")
        } ?: error("recordedPatch() must initialize a top-level patch declaration")

    val key = "\"${caller.className.substringBeforeLast('.')}/${caller.fileName}\":\""

    val idStart = text.indexOf(key)
    val idEnd = text.indexOf('"', idStart + key.length)

    return text.substring(idStart + key.length, idEnd)
}

private val revisionsText: String by lazy {
    PatchRecords::class.java
        .getResourceAsStream(
            REVISIONS_RESOURCE,
        )?.readBytes()
        ?.toString(StandardCharsets.UTF_8)
        ?: error("revisions.json missing from patch bundle")
}

internal object PatchRecords
