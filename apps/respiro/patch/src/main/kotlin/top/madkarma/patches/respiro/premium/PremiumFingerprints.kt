// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.respiro.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

private val launcherStateConstructor =
    method("launcherStateConstructor") {
        strings("home_backdrop")
        params(Type.Application)
    }

// This state's only Boolean helper taking the state itself reads the cached signing verdict.
internal val validSigningCertificate =
    method("validSigningCertificate") {
        inClass(klass(launcherStateConstructor.owner))
        params(launcherStateConstructor.owner)
        returns(Type.Boolean)
    }
