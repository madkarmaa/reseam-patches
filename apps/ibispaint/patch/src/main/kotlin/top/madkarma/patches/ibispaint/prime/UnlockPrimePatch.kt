// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.ibispaint.prime

import app.reseam.patch.*
import top.madkarma.patches.ibispaint.IbisPaintPatchStrings
import top.madkarma.patches.shared.PATCH_ATTRIBUTION
import top.madkarma.patches.universal.signature.signatureKiller

// The Prime paywall is a bundled page per language.
private val paywallPage = Regex("""assets/htmls/Paywall/v4/Paywall_v4_[^/]+\.html""")

// Each page's header subtitle is one paragraph; right-to-left languages mark it with a class.
private val paywallSubtitle =
    Regex("""(<div id="sub-tittle">\s*)<p[^>]*>.*?</p>""", RegexOption.DOT_MATCHES_ALL)

val unlockPrime =
    patch(IbisPaintPatchStrings.unlockPrimeName) {
        description(IbisPaintPatchStrings.unlockPrimeDescription)
        compatibleWith("jp.ne.ibis.ibispaintx.app")
        // The native library denies Prime features when the signing certificate changes.
        dependsOn(signatureKiller)

        execute {
            ownsPaymentItem.before {
                whenEqual(param(0), staticField(primeYearly)) { returnTrue() }
            }
            log.info("Prime: ${ownsPaymentItem.descriptor} reports the yearly Prime plan as owned.")

            ownsAnyPaymentItem.alwaysReturn(true)
            log.info("Prime: ${ownsAnyPaymentItem.descriptor} reports an owned purchase.")

            val pages = files.list().filter { paywallPage.matches(it) }
            if (pages.isEmpty()) error("Prime: no paywall pages")

            for (page in pages) {
                val html = files.read(page)?.decodeToString() ?: error("Prime: cannot read $page")
                if (paywallSubtitle.findAll(html).count() != 1)
                    error("Prime: expected one subtitle in $page")

                val attributed =
                    paywallSubtitle.replace(html) {
                        "${it.groupValues[1]}<p><span>$PATCH_ATTRIBUTION</span></p>"
                    }
                files.write(page, attributed.encodeToByteArray())
            }
            log.info("Prime: ${pages.size} paywall pages show the shared patch attribution.")
        }
    }
