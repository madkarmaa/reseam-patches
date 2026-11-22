// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.homeworkout.tracking

import app.reseam.patch.Type
import app.reseam.patch.method

// Every app event, including the daily active user ping, goes through this sink, which copies the
// event's Bundle and hands it to Firebase Analytics.
internal val logEvent =
    method("logEvent") {
        calls {
            owner("com.google.firebase.analytics.FirebaseAnalytics")
            name("getInstance")
        }
        params(Type.Context, Type.String, "android.os.Bundle", Type.Boolean)
    }
