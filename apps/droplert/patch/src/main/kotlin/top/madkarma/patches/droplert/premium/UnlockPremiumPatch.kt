package top.madkarma.patches.droplert.premium

import app.reseam.patch.Type
import app.reseam.patch.dex.Opcode
import app.reseam.patch.invoke
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch
import top.madkarma.patches.universal.removePairip

val CustomerInfo_getEntitlements = klass("com.revenuecat.purchases.CustomerInfo").method("getEntitlements")
val EntitlementInfos_get = klass("com.revenuecat.purchases.EntitlementInfos").method("get")
val EntitlementInfo_isActive = klass("com.revenuecat.purchases.EntitlementInfo").method("isActive")

val customerInfoIsPremiumActive =
    method("customer premium active") {
        returns(Type.Boolean)
        params("com.revenuecat.purchases.CustomerInfo")
        strings("premium")
        calls(CustomerInfo_getEntitlements)
        calls(EntitlementInfos_get)
        calls(EntitlementInfo_isActive)
    }

// val System_currentTimeMillis = klass("java.lang.System").method("currentTimeMillis")
// val Long_longValue = klass("java.lang.Long").method("longValue")

val isPremium =
    method("premium gate") {
        returns(Type.Boolean)
        paramCount(0)
        // calls(System_currentTimeMillis)
        // calls(Long_longValue)
        opcode(Opcode.CMP_LONG, Opcode.IGET_BOOLEAN, Opcode.INSTANCE_OF, Opcode.SGET_OBJECT)
    }

val unlockPremium =
    patch("Unlock Lifetime Premium") {
        description("Unlocks Premium-only features.")
        compatibleWith("com.shahzaman.pricetracker"("2.2.1"))
        dependsOn(removePairip)

        execute {
            isPremium.method.alwaysReturn(true)
            customerInfoIsPremiumActive.method.alwaysReturn(true)
        }
    }
