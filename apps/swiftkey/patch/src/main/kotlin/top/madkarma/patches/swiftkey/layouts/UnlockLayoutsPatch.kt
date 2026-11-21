// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.swiftkey.layouts

import app.reseam.patch.*
import top.madkarma.patches.swiftkey.SwiftKeyPatchStrings
import top.madkarma.swiftkey.layouts.bindings.Layouts

val unlockLayouts =
    patch(SwiftKeyPatchStrings.unlockLayoutsName) {
        description(SwiftKeyPatchStrings.unlockLayoutsDescription)
        compatibleWith("com.touchtype.swiftkey")

        execute {
            // The extended layouts are collected from each installed pack's layout list, inline in
            // the loop over the packs; the list's getter also serves the current language and five
            // other callers. The list is the first one read after the loop takes the next pack.
            listLayoutNames
                .point { invokeVirtual { name("next") } }
                .next { invokeVirtual { returns("Ljava/util/List;") } }
                .next { resultOf("Ljava/util/List;") }
                .captureAs("packLayouts")
                .after {
                    val packLayouts = capture("packLayouts")
                    packLayouts.assign(Layouts.everyLayout(packLayouts))
                }
            log.info(
                "Layouts: ${listLayoutNames.descriptor} offers every extended Latin layout, not only those of installed packs.",
            )

            // Language packs list only the redesigned layouts, such as Arabic's, and the app keeps
            // the replaced ones working only for people who had already picked them.
            packLayouts.after { returnValue(Layouts.withLegacyLayouts(result)) }
            log.info(
                "Layouts: ${packLayouts.descriptor} adds the legacy layouts its redesigned layouts replaced.",
            )
        }
    }
