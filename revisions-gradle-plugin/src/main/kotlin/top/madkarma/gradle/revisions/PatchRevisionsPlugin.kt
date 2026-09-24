// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later
@file:Suppress("unused")

package top.madkarma.gradle.revisions

import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.api.tasks.SourceSetContainer
import org.gradle.api.tasks.Sync
import org.jetbrains.kotlin.gradle.dsl.KotlinJvmProjectExtension

/** Patches live here, one patch per file under `<app>/patch/`. */
internal const val APPS_DIR = "apps"

/** Extensions shared across apps live here. */
internal const val SHARED_DIR = "shared"

/**
 * Per-patch content revisions for the update checker. Registers
 * `generatePatchRevisions` (discover, validate, hash), folds its
 * revisions.json into the engine's `stageRelease`, and provides every patch
 * module with the recorder helper and revisions.json - patch authors
 * declare with recordedPatch() instead of patch(), and recording is
 * automatic.
 */
class PatchRevisionsPlugin : Plugin<Project> {
    override fun apply(project: Project) {
        val generate = project.tasks.register(
            "generatePatchRevisions",
            GeneratePatchRevisionsTask::class.java,
        ) {
            group = "build"
            description = "Hashes patch inputs into revisions.json and validates patch recorders."

            projectDir.set(project.layout.projectDirectory)
            outputDir.set(project.layout.buildDirectory.dir("generated/reseam/revisions"))
            recorderDir.set(project.layout.buildDirectory.dir("generated/reseam/recorder"))

            sources.from(
                project.fileTree(
                    APPS_DIR,
                ) { exclude("**/build/**", "**/.gradle/**", "**/.git/**") },
                project.fileTree(
                    SHARED_DIR,
                ) { exclude("**/build/**", "**/.gradle/**", "**/.git/**") },
                project.layout.projectDirectory.files("settings.gradle.kts", "manifest.toml"),
            )
        }

        project.tasks.matching { it.name == "stageRelease" }.configureEach {
            dependsOn(generate)
            (this as? Sync)?.from(generate.map { it.outputDir.file("revisions.json") })
        }

        project.allprojects.forEach { module ->
            module.pluginManager.withPlugin("app.reseam.patches") {
                val kotlin =
                    module.extensions.getByType(KotlinJvmProjectExtension::class.java).sourceSets.getByName(
                        "main"
                    )

                kotlin.kotlin.srcDir(generate.map { it.recorderDir })

                val main = module.extensions.getByType(
                    SourceSetContainer::class.java,
                ).getByName("main")

                main.resources.srcDir(generate.map { it.outputDir })

                module.tasks.named("processResources") { dependsOn(generate) }
                module.tasks.named("compileKotlin") { dependsOn(generate) }
            }
        }
    }
}
