// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.projectivy.premium

import app.reseam.patch.*
import top.madkarma.patches.projectivy.ProjectivyPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.shared.setStringEverywhere

val unlockPremium =
    patch(ProjectivyPatchStrings.unlockPremiumName) {
        description(ProjectivyPatchStrings.unlockPremiumDescription)
        compatibleWith("com.spocky.projengmenu")

        execute {
            // Purchase and license checks report through the writer, which then always unlocks.
            writePremiumLock.before { paramOfType(Type.Boolean).assign(bool(false)) }
            log.info("Premium: ${writePremiumLock.descriptor} always writes the unlocked state.")

            // The checks run asynchronously, so unlock the flow as soon as it exists.
            applicationConstructor.after {
                if (writePremiumLock.method.isStatic)
                    call(writePremiumLock, thisObject, bool(false))
                else thisObject.call(writePremiumLock, bool(false))
            }
            log.info(
                "Premium: ${applicationConstructor.descriptor} starts unlocked through " +
                    "${writePremiumLock.descriptor}.",
            )

            // The Premium dialog thanks registered users for their support.
            if (
                !resources.setStringEverywhere("dialog_register_registered_desc", PATCH_ATTRIBUTION)
            )
                error("Premium: dialog_register_registered_desc string missing")
            log.info("Premium: dialog_register_registered_desc shows the shared patch attribution.")
        }
    }
