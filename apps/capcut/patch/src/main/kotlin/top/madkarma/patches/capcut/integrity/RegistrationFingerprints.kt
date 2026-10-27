// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.capcut.integrity

import app.reseam.patch.klass
import app.reseam.patch.method

private val deviceRegisterManager = klass("com.ss.android.deviceregister.DeviceRegisterManager")

internal val deviceRegistrationInit = deviceRegisterManager.method("init")

internal val deviceRegistrationStart = deviceRegisterManager.method("onCreate")

internal val deviceRegistrationRequest = method("deviceRegistrationRequest") {
    strings("RegisterServiceController app_log_config: ")
}

internal val excludeAppRequestHeaders = method("excludeAppRequestHeaders") {
    strings("x-tt-web-proxy")
    inClass(klass("com.lm.components.network.extra.NetworkRuntimeConfig"))
}

internal val networkCommonParameters =
    klass("com.lm.components.core.network.NetworkDevice").method("addCommonParams")
