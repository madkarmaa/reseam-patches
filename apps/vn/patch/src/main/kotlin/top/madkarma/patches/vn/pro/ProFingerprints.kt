// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.patches.vn.pro

import app.reseam.patch.method

internal val proGate = method("proGate") {
    strings("FORCE_PRO")
    params()
}
