// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.ilovepdf.premium

import app.reseam.patch.klass
import app.reseam.patch.method

private val userDomainModel =
    klass("userDomainModel") {
        strings("UserDomainModel(id=")
    }

// Every Premium gate, from tool limits to the UI user model, reads the signed-in or guest user's
// flag through this getter.
internal val userPremium = userDomainModel.method("getPremium")

// At login, a Premium guest is offered to link its Premium to the new account.
internal val premiumLinkSuggestion =
    method("premiumLinkSuggestion") {
        calls(userPremium)
        name("shouldShowMailLinkingSuggestion")
    }
