package top.madkarma.patches.accuweather

import app.reseam.patch.ExtClass
import app.reseam.patch.Type

internal object MembershipAttribution :
    ExtClass("top.madkarma.extensions.accuweather.MembershipAttribution") {

    val install = static("install", "android.webkit.WebView", Type.String)
}
