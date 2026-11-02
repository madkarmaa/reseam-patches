// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.launch

import app.reseam.patch.Type
import app.reseam.patch.method

internal val afterCallScreenEnabled = method("afterCallScreenEnabled") {
    strings("afterCallForPbContacts")
    returns(Type.Boolean)
}
