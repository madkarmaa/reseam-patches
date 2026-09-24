// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.gradle.revisions

import org.gradle.api.DefaultTask
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.*
import java.io.File

/**
 * Discovers every patch declaration, validates its recording, and hashes
 * each patch's inputs into revisions.json. One patch per file is assumed.
 */
abstract class GeneratePatchRevisionsTask : DefaultTask() {
    @get:InputFiles
    @get:PathSensitive(PathSensitivity.RELATIVE)
    abstract val sources: ConfigurableFileCollection

    // Project dir only relativizes paths for stable hashes; every hashed
    // byte is tracked through sources, so this stays out of up-to-date checks.
    @get:Internal
    abstract val projectDir: DirectoryProperty

    @get:OutputDirectory
    abstract val outputDir: DirectoryProperty

    @get:OutputDirectory
    abstract val recorderDir: DirectoryProperty

    data class Declared(
        val id: String,
        val relativePath: String,
        val recorded: Boolean,
    )

    @TaskAction
    fun generate() {
        val root = projectDir.get().asFile
        val files = sources.files

        // Wiped first: a renamed recorder or revision must not linger next
        // to the fresh outputs.
        outputDir.get().asFile.deleteRecursively()
        recorderDir.get().asFile.deleteRecursively()

        val declaration = Regex("""val\s+([A-Za-z0-9_]+)\s*=\s*(recordedPatch|patch)\(""")
        val packageDecl = Regex("""(?m)^package\s+([A-Za-z0-9_.]+)""")
        val recorder = Regex("""recordApplied\(\)""")

        // One patch per file: discover every declaration.
        val declared = mutableListOf<Declared>()
        files.filter { it.extension == "kt" }.sortedBy { it.relativeTo(root).path }
            .forEach { file ->
                val relativePath = file.relativeTo(root).path
                val text = file.readText()

                val matches = declaration.findAll(text).toList()

                if (matches.isEmpty()) return@forEach
                if (matches.size > 1) error("$relativePath: one patch per file, found ${matches.size}")

                val pkg = packageDecl.find(
                    text,
                )?.groupValues?.get(1) ?: error("$relativePath: no package declaration")

                declared += Declared(
                    "$pkg.${matches[0].groupValues[1]}",
                    relativePath,
                    matches[0].groupValues[2] == "recordedPatch"
                )
            }
        if (declared.isEmpty()) error("no patch declarations found")

        // Recording is automatic via the constructor: a bare patch() never
        // records, and a manual call next to the decorator is redundant.
        declared.forEach { (id, relativePath, recorded) ->
            if (!recorded) error("$relativePath: declare with recordedPatch(), not patch(), so the patch records itself")
            if (recorder.containsMatchIn(
                    File(
                        root, relativePath
                    ).readText()
                )
            ) error("$relativePath: recording is automatic via recordedPatch(); drop the recordApplied() call")
        }
        val declaringPaths = declared.map { it.relativePath }.toSet()

        fun hashFiles(relativePaths: List<String>): String {
            val digest = java.security.MessageDigest.getInstance("SHA-256")

            relativePaths.forEach { relativePath ->
                digest.update(relativePath.toByteArray())
                digest.update(byteArrayOf(0))
                digest.update(File(root, relativePath).readBytes())
                digest.update(byteArrayOf(0))
            }

            return digest.digest().joinToString("") { "%02x".format(it) }
        }

        val entries = declared.sortedBy { it.id }.joinToString(",") { (id, relativePath, _) ->
            // Layout is apps/<app>/...: segment 0 is the apps dir, 1 the app.
            val app = relativePath.split(File.separatorChar).getOrNull(1).orEmpty()

            val inputs = files.map { it.relativeTo(root).path }.filter { rel ->
                rel.startsWith("$SHARED_DIR/") || rel == "settings.gradle.kts" || rel == "manifest.toml" || (app.isNotEmpty() && rel.startsWith(
                    "$APPS_DIR/$app/"
                ) && rel !in declaringPaths) || rel == relativePath
            }.sorted()

            "\"$id\":\"${hashFiles(inputs)}\""
        }

        val fileEntries = declared.sortedBy { it.id }.joinToString(",") { (id, relativePath, _) ->
            "\"${
                id.substringBeforeLast(
                    '.',
                )
            }/${relativePath.substringAfterLast(File.separatorChar)}\":\"$id\""
        }

        outputDir.get().asFile.apply {
            mkdirs()
            resolve("revisions.json").writeText("{\"files\":{$fileEntries},\"patches\":{$entries}}")
        }
        recorderDir.get().asFile.apply {
            mkdirs()
            resolve(RECORDER_PATH).apply {
                parentFile.mkdirs()
                writeBytes(recorderTemplate())
            }
        }
        logger.lifecycle("patch revisions: ${declared.size} patches")
    }

    private fun recorderTemplate(): ByteArray =
        javaClass.getResourceAsStream("/recorder/PatchRecords.kt")?.readBytes()
            ?: error("recorder template missing from plugin resources")

    private companion object {
        /** Package path of the recorder helper inside the recorder output dir. */
        const val RECORDER_PATH = "top/madkarma/revisions/PatchRecords.kt"
    }
}
