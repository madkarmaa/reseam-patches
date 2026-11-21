// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.swiftkey.ai

import app.reseam.patch.*
import top.madkarma.patches.swiftkey.SwiftKeyPatchStrings
import top.madkarma.swiftkey.ai.bindings.AiAvailability
import top.madkarma.swiftkey.ai.bindings.StickerPages

val removeAiFeatures =
    patch(SwiftKeyPatchStrings.removeAiFeaturesName) {
        description(SwiftKeyPatchStrings.removeAiFeaturesDescription)
        compatibleWith("com.touchtype.swiftkey")

        execute {
            // The app then behaves as on devices and in countries where Microsoft doesn't offer
            // its AI features.
            aiFeaturesAvailable.alwaysReturn(false)
            log.info("AI: ${aiFeaturesAvailable.descriptor} reports AI features as unavailable.")

            initializeAiUnavailableCountries.after {
                setStatic(aiUnavailableCountries, call(AiAvailability.everyCountry))
            }
            log.info(
                "AI: ${initializeAiUnavailableCountries.descriptor} lists every country in " +
                    "${aiUnavailableCountries.owner}->${aiUnavailableCountries.name}, which hides the Copilot toolbar button.",
            )

            for (enabled in listOf(imageCreatorEnabled, cameraRemixEnabled)) {
                val constructors = enabled.writers()
                if (constructors.all.isEmpty())
                    error("AI: no constructor sets ${enabled.owner}->${enabled.name}")

                constructors.forEach { after { thisObject.set(enabled, bool(false)) } }
                log.info(
                    "AI: ${constructors.all.joinToString { it.descriptor }} keep ${enabled.owner}->${enabled.name} off.",
                )
            }

            // The generation page is added to a local list, read from an inlined static field, and
            // the list goes straight to androidx's shared list differ, so its add call is the only
            // place to leave it out.
            listStickerPages
                .point { string("getGenerationPack(...)") }
                .next { invokeVirtual { name("add") } }
                .skipWhen { bool(true) }
            log.info(
                "AI: ${listStickerPages.descriptor} leaves out the AI sticker generation page.",
            )

            createStickerPager.after {
                thisObject.set(
                    builtInStickerPages,
                    thisObject.field(builtInStickerPages) - int(1),
                )
            }
            log.info(
                "AI: ${createStickerPager.descriptor} counts one built-in page less in ${builtInStickerPages.owner}->${builtInStickerPages.name}.",
            )

            stickerPageType.replace {
                returnValue(StickerPages.pageType(thisObject.call(stickerPackId, param(0))))
            }
            log.info(
                "AI: ${stickerPageType.descriptor} picks each page's view by its pack id from ${stickerPackId.descriptor}.",
            )
        }
    }
