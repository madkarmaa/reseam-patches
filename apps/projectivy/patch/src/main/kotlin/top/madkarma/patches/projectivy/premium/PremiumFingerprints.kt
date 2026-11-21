// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.projectivy.premium

import app.reseam.patch.Type
import app.reseam.patch.klass
import app.reseam.patch.method

// The application owns the Premium lock flow every Premium screen reads, seeded locked.
private val application = klass("com.spocky.projengmenu.PTApplication")

internal val applicationConstructor = application.method("<init>")

// The only writer of the lock flow, called with whether the app is still unpaid: an instance
// method in newer releases, static with the application first in older ones.
internal val writePremiumLock =
    method("writePremiumLock") {
        inClass(application)
        hasParam(Type.Boolean)
        returns(Type.Void)
    }
