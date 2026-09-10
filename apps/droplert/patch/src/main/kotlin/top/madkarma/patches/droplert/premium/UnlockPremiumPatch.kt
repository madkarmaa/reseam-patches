// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.droplert.premium

import app.reseam.patch.Type
import app.reseam.patch.invoke
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch

val CustomerInfo_getEntitlements = klass("com.revenuecat.purchases.CustomerInfo").method("getEntitlements")
val EntitlementInfos_get = klass("com.revenuecat.purchases.EntitlementInfos").method("get")
val EntitlementInfo_isActive = klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val customerInfoIsPremiumActive =
    method("CustomerInfo.getEntitlements().get(\"premium\").isActive()") {
        returns(Type.Boolean)
        params("com.revenuecat.purchases.CustomerInfo")
        strings("premium")
        calls(CustomerInfo_getEntitlements)
        calls(EntitlementInfos_get)
        calls(EntitlementInfo_isActive)
    }

val unlockPremium =
    patch("Unlock Lifetime Premium") {
        description("Unlocks Premium-only features.")

        compatibleWith("com.shahzaman.pricetracker"("2.2.1"))

        execute {
            customerInfoIsPremiumActive.method.alwaysReturn(true)
        }
    }
