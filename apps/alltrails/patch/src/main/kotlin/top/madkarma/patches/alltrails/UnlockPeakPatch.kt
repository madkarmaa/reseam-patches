package top.madkarma.patches.alltrails

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method
import app.reseam.patch.patch

// Backend user model (Gson User.java): toString keys plus @SerializedName
// keys pin it; the kept getter names resolve off the found class.
val appUser = klass("alltrails user") {
    strings("User [remoteId=", ", subscriptionTier=", ", featureEntitlements=")
}

val userIsPro = method("user is pro") {
    inClass(appUser)
    name("isPro")
    returns(Type.Boolean)
    paramCount(0)
}

// Raw backend tier string on the user model. Declared once (User);
// "peak" is the backend serial.
val userSubscriptionTier = method("user subscription tier") {
    inClass(appUser)
    name("getSubscriptionTier")
    returns(Type.String)
    paramCount(0)
}

val unlockPeak = patch("Unlock Peak") {
    description("Unlocks Peak-only features.")
    compatibleWith("com.alltrails.alltrails")

    execute {
        userIsPro.method.alwaysReturn(true)
        log.info("Peak: ${userIsPro.descriptor} forced true.")

        userSubscriptionTier.method.alwaysReturn("peak")
        log.info("Peak: ${userSubscriptionTier.descriptor} forced to peak.")

        val peakName = resources.getString("peak_display_name")
            ?: error("Unlock Peak: peak_display_name string missing")

        if (!resources.setString(
                "peak_display_name",
                "$peakName | Patched with ❤ by MadKarma ;)",
            )
        ) {
            error("Unlock Peak: peak_display_name string not writable")
        }

        log.info("Peak: plan label tagged.")
    }
}
