// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.gmdgestures.pro

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

// Startup check of the cached, signature-verified purchase in the "pro-entitlement" file.
internal val loadCachedEntitlement =
    method("loadCachedEntitlement") {
        strings("pro-entitlement")
        returns(Type.Boolean)
    }

// The only setter of the Pro flag; Play Billing results grant or revoke Pro through it.
internal val setProUnlocked =
    method("setProUnlocked") {
        inClass(klass(loadCachedEntitlement.owner))
        params(Type.Boolean)
        returns(Type.Void)
    }
