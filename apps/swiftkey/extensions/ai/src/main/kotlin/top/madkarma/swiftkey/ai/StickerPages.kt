// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("StickerPages")
@file:Suppress("unused")

package top.madkarma.swiftkey.ai

import app.reseam.runtime.Export

// The sticker panel's built-in pages, by pack id, and the view type each page uses. Every other
// pack is a sticker pack page.
private val builtInPageTypes =
    mapOf("Recents" to 0, "Dynamic Stickers" to 1, "More Packs" to 2, "Sticker Generation" to 3)

private const val STICKER_PACK_PAGE = 4

/**
 * The view type of the sticker panel page showing [packId]. The app picks built-in pages by their
 * position, which no longer holds once the sticker generation page is gone.
 */
@Export fun pageType(packId: String): Int = builtInPageTypes[packId] ?: STICKER_PACK_PAGE
