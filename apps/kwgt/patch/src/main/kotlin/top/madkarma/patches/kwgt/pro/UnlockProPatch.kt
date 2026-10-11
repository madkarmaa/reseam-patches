// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.kwgt.pro

import app.reseam.patch.alwaysReturn
import app.reseam.patch.patch
import app.reseam.patch.replace
import top.madkarma.patches.shared.PATCH_ATTRIBUTION

val unlockPro = patch("Unlock Pro") {
    description("Unlocks Pro-only features.")
    compatibleWith("org.kustom.widget")

    execute {
        // Keep the validator's cache and listener updates without querying the external key.
        checkProKey.replace {
            thisObject.call(updateLicenseState, staticField(licensedState))
            returnVoid()
        }
        log.info("Pro: ${checkProKey.descriptor} reports a licensed key.")

        // Feature screens can read the Pro flag before the first billing refresh.
        proFeaturesAvailable.alwaysReturn(true)
        log.info("Pro: ${proFeaturesAvailable.descriptor} enables Pro features.")

        resources.editXml("layout", "k_settings_activity_app_info") {
            // Compiled XML stores LinearLayout.HORIZONTAL as the integer 0.
            check(root.tag == "LinearLayout" && root["android:orientation"] == "0") {
                "Unlock Pro: unsupported settings app-info layout"
            }

            val header = root.clone(deep = false)

            header.removeAttribute("android:id")
            header.removeAttribute("android:layout_marginStart")
            header.removeAttribute("android:layout_marginEnd")

            root.children.forEach { header.appendChild(it) }
            root["android:orientation"] = "vertical"
            root.removeAttribute("android:paddingTop")
            root.removeAttribute("android:paddingBottom")
            root.appendChild(header)

            root.appendChild(createElement("View").apply {
                this["android:layout_width"] = "match_parent"
                this["android:layout_height"] = "1px"
                this["android:layout_marginTop"] = "8dp"
                this["android:layout_marginBottom"] = "8dp"
                this["android:background"] = "?kColorCard"
            })

            root.appendChild(createElement("TextView").apply {
                this["android:layout_width"] = "match_parent"
                this["android:layout_height"] = "wrap_content"
                this["android:minHeight"] = "48dp"
                this["android:layout_marginStart"] = "40dp"
                this["android:paddingTop"] = "12dp"
                this["android:paddingBottom"] = "12dp"
                this["android:gravity"] = "center_vertical"
                this["android:textAppearance"] = "@style/KustomTheme.TextAppearance.Body1"
                this["android:text"] = PATCH_ATTRIBUTION
                // Consume taps so they do not activate the parent header's hidden debug shortcut.
                this["android:clickable"] = "true"
                this["android:focusable"] = "false"
            })
        }
    }
}
