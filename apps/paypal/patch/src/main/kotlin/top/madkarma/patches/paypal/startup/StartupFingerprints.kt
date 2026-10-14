// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.paypal.startup

import app.reseam.patch.klass
import app.reseam.patch.method

internal val startupVerifier = klass("com.paypal.oslo.core.startup.AppVerifier").method("run")
