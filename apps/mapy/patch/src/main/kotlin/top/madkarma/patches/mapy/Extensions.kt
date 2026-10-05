package top.madkarma.patches.mapy

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object Premium : ExtClass("top.madkarma.extensions.mapy.Premium") {
    val unlockFeatures = static("unlockFeatures", Type.Object, Type.Object, Type.Object)
    val enabled = static("enabled", returns = Type.Object)
    val premiumTitle = static("premiumTitle", Type.String, Type.String, returns = Type.String)
}
