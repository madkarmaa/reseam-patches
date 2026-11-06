// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.googlephotos.maps

import app.reseam.patch.method

// The Maps client loads GmsCore's renderer through this module context; its class loader holds
// Mapbox.
internal val mapsModuleContext =
    method("mapsModuleContext") {
        strings("Unable to load maps module, maps container context is null")
    }

// Play services' lifecycle helper queues map callbacks in a LinkedList until the delegate exists
// and
// merges their saved state, so every callback that builds a map view or fragment runs through here.
internal val mapLifecycleCallback =
    method("mapLifecycleCallback") {
        calls {
            owner("java.util.LinkedList")
            name("<init>")
        }
        calls {
            owner("android.os.Bundle")
            name("putAll")
        }
    }
