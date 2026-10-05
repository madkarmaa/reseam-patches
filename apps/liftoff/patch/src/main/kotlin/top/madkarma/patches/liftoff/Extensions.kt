package top.madkarma.patches.liftoff

import app.reseam.patch.ExtClass

internal object ProEntitlements : ExtClass("top.madkarma.liftoff.extensions.ProEntitlements") {
    val applyProEntitlements by static("java.util.Map")
}
