// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.swiftkey.ai

import app.reseam.patch.FieldTarget
import app.reseam.patch.Type
import app.reseam.patch.field
import app.reseam.patch.fieldOfType
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.methods
import app.reseam.patch.point

// Whether Copilot, Designer, and Bing AI features may be offered on this device: false on
// unsupported device makers and when an OEM build turns Copilot off.
internal val aiFeaturesAvailable =
    method("aiFeaturesAvailable") { strings("app_updated_from_gp", "HUAWEI") }

// The same check, which also excludes the countries where Microsoft offers no AI features.
private val aiFeaturesAvailableHere =
    method("aiFeaturesAvailableHere") {
        inClass(klass(aiFeaturesAvailable.owner))
        calls(aiFeaturesAvailable)
    }

// The countries without AI features. The Copilot toolbar button checks only this list.
internal val aiUnavailableCountries =
    aiFeaturesAvailableHere.field("aiUnavailableCountries") { type("Ljava/util/Set;") }

internal val initializeAiUnavailableCountries =
    method("initializeAiUnavailableCountries") {
        name("<clinit>")
        accesses {
            owner(aiUnavailableCountries.owner)
            name(aiUnavailableCountries.name)
        }
    }

// Server-configured models switch Bing Image Creator and Camera Remix (AI images from photos) on.
// The Image Creator model holds a second boolean, appReferralAutoSuggestionsDisabled; only its
// toString, which labels the switch "BingImageCreatorParametersModel(enabled=", names the field.
internal val imageCreatorEnabled =
    method("describeImageCreatorModel") { strings("BingImageCreatorParametersModel(enabled=") }
        .point { string("BingImageCreatorParametersModel(enabled=") }
        .next { field { type(Type.Boolean) } }
        .field("imageCreatorEnabled")

// The Camera Remix model's only boolean is its switch.
internal val cameraRemixEnabled =
    klass("cameraRemixModel") { strings("CameraRemixModel(enabled=") }.fieldOfType(Type.Boolean)

// The default and deserializing constructors of a model.
internal fun FieldTarget.writers() =
    methods("${name}Writers") {
        name("<init>")
        accesses {
            owner(this@writers.owner)
            name(this@writers.name)
        }
    }

// Lists the sticker panel's pages: its built-in pages, among them AI sticker generation, then the
// installed sticker packs.
internal val listStickerPages = method("listStickerPages") { strings("getGenerationPack(...)") }

private val stickerPager =
    klass("stickerPager") {
        strings(
            "null cannot be cast to non-null type com.touchtype.keyboard.view.richcontent.sticker.StickerPageAdapter",
        )
    }

// How many built-in pages precede the sticker packs; a downloaded pack is inserted at this index.
internal val builtInStickerPages = stickerPager.fieldOfType(Type.Int)

internal val createStickerPager = stickerPager.method("<init>")

// The view type of the page at a position. It assumes the built-in pages' positions.
internal val stickerPageType =
    method("stickerPageType") {
        inClass(stickerPager)
        params(Type.Int)
        returns(Type.Int)
    }

// The id of the sticker pack shown at a position.
internal val stickerPackId =
    method("stickerPackId") {
        inClass(stickerPager)
        params(Type.Int)
        returns(Type.String)
    }
