package top.madkarma.patches.droplert

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

object Prefs : ExtClass("top.madkarma.droplert.extensions.Prefs") {
    val putBoolean = static("putBoolean", Type.Context, Type.String, Type.Boolean)
}
