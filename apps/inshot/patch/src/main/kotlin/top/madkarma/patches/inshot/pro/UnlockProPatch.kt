// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.inshot.pro

import app.reseam.patch.Type
import app.reseam.patch.after
import app.reseam.patch.alwaysReturn
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.inshot.pro.bindings.SubscriptionCard
import top.madkarma.patches.inshot.InShotPatchStrings
import top.madkarma.patches.universal.signature.signatureKiller

val unlockPro =
    patch(InShotPatchStrings.unlockProName) {
        description(InShotPatchStrings.unlockProDescription)
        compatibleWith("com.camerasideas.instashot")
        // The native video engine kills the app when its signing certificate changes.
        dependsOn(signatureKiller)

        execute {
            isPro.alwaysReturn(true)
            log.info("Pro: ${isPro.descriptor} reports an active Pro subscription.")

            val descriptionId =
                resources.id("id", "item_desc") ?: error("Pro: missing id resource item_desc")
            val arrowId =
                resources.id("id", "image_more") ?: error("Pro: missing id resource image_more")

            // The card would otherwise show the plan name and open the subscription screen.
            bindSubscriptionSetting.after {
                SubscriptionCard.showAttribution(
                    paramOfType(VIEW_HOLDER).field(itemView),
                    int(descriptionId.toInt()),
                    int(arrowId.toInt()),
                )
            }
            log.info(
                "Pro: ${bindSubscriptionSetting.descriptor} shows the attribution on the " +
                    "Settings subscription card.",
            )

            settingClick.before {
                whenTrue(
                    SubscriptionCard.isAttributionCard(
                        paramOfType(Type.View),
                        int(descriptionId.toInt()),
                    ),
                ) {
                    returnVoid()
                }
            }
            log.info(
                "Pro: ${settingClick.descriptor} ignores taps on the subscription card.",
            )
        }
    }
