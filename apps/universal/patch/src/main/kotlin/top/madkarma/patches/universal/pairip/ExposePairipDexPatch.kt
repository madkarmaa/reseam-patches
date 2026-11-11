// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.universal.pairip

import app.reseam.patch.*
import app.reseam.patch.types.EncodedVal
import top.madkarma.patches.universal.UniversalPatchStrings
import top.madkarma.universal.pairip.bindings.PairipDex

private val importedDexEntries = RunList<String>()
private const val PATCHED_DEX_ASSET = "assets/reseam/pairip/patched.dex"
private const val PATCHED_CLASSES_ASSET = "assets/reseam/pairip/classes.txt"

val exposePairipDex =
    patch(UniversalPatchStrings.exposePairipDexName) {
        description(
            UniversalPatchStrings.exposePairipDexDescription,
        )

        val diagnostics =
            boolOption(
                "diagnostics",
                title = UniversalPatchStrings.exposePairipDexDiagnosticsTitle,
                default = false,
            )

        execute {
            val startup = pairipStartup.method.classDef

            val asset =
                startup.staticFields
                    .mapNotNull { (it.initialValue as? EncodedVal.StringVal)?.field0 }
                    .singleOrNull() ?: error("Pairip DEX: expected one startup asset name")
            require('/' !in asset && '\\' !in asset && asset.isNotBlank()) {
                "Pairip DEX: invalid startup asset name"
            }
            val path = "assets/$asset"

            val payload = extractEmbeddedDex(files.read(path) ?: error("Pairip DEX: missing $path"))
            val used = files.components().flatMap { files.component(it).list() }.toSet()

            var ordinal = 2
            while ("classes$ordinal.dex" in used) ordinal++

            val dexPath = "classes$ordinal.dex"
            for (descriptor in payload.classes) if (bytecode.findClass(descriptor) != null)
                error("Pairip DEX: class is already present: $descriptor")

            files.write(dexPath, payload.bytes)
            importedDexEntries.add(dexPath)
            files.write(
                PATCHED_CLASSES_ASSET,
                payload.classes
                    .sorted()
                    .joinToString("\n") {
                        it.substring(1, it.length - 1).replace('/', '.')
                    }
                    .encodeToByteArray(),
            )
            val assignments =
                payload.methods.map { body ->
                    val imported =
                        klass(body.owner).method(body.name) {
                            params(*body.parameters.toTypedArray())
                            returns(body.returnType)
                        }

                    check(imported.method.isStatic) {
                        "Pairip DEX: imported body is not static: ${imported.descriptor}"
                    }

                    val holder = liftedStub(body).field { type("java.lang.reflect.Method") }
                    holder.ref to imported
                }

            check(assignments.map { it.first }.distinct().size == assignments.size) {
                "Pairip DEX: multiple lifted methods use the same holder"
            }

            pairipStartup.after {
                call(PairipDex.initialize, call(pairipContext))
                for ((holder, body) in assignments) {
                    setStatic(
                        holder,
                        call(
                            PairipDex.resolveMethod,
                            string(body.owner),
                            string(body.name),
                            string(body.proto),
                            bool(options[diagnostics]),
                        ),
                    )
                }
            }
            log.info(
                "Pairip DEX: exposed ${payload.classes.size} classes and ${assignments.size} methods from $path as $dexPath; dependent patches use normal Reseam fingerprints and DSL edits.",
            )

            for ((holder, body) in assignments) log.info(
                "Pairip DEX: ${holder.definingClass}->${holder.name} uses ${body.descriptor} after ${pairipStartup.descriptor}.",
            )
        }

        afterDependents {
            val dexPath =
                importedDexEntries.values.singleOrNull()
                    ?: error("Pairip DEX: missing imported DEX entry")

            files.stream(dexPath)?.use { files.write(PATCHED_DEX_ASSET, it) }
                ?: error("Pairip DEX: imported DEX disappeared: $dexPath")

            files.delete(dexPath)

            log.info(
                "Pairip DEX: serialized Reseam's edited $dexPath as $PATCHED_DEX_ASSET for isolated loading.",
            )
        }
    }
