// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.mtmanager.shared

import app.reseam.patch.*

private val packageInfoProxy =
    klass("packageInfoProxy") {
        implements("java.lang.reflect.InvocationHandler")
        hasInstanceField($$"android.os.Parcelable$Creator")
    }

internal val packageCreatorConstructor = packageInfoProxy.method("<init>")

internal val protectionStartup =
    method("protectionStartup") {
        strings("mtprotect")
        calls {
            owner("java.lang.System")
            name("loadLibrary")
        }
    }
