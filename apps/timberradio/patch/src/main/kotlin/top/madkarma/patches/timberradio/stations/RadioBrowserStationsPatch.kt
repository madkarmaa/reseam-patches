// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.patches.timberradio.stations

import app.reseam.patch.ClassTarget
import app.reseam.patch.Type
import app.reseam.patch.before
import app.reseam.patch.patch
import top.madkarma.patches.timberradio.TimberRadioPatchStrings
import top.madkarma.timberradio.radiobrowser.bindings.RadioBrowser

private const val CALLBACK = "kotlin.jvm.functions.Function1"

// The station constructor takes the id, 24 text fields, the stream list, and the insertion time.
private val stationConstructor =
    "(" + Type.Int + Type.String.repeat(24) + Type.List + Type.Long + ")" + Type.Void

// Every app member the extension's stubs call, as name to prototype.
private val stubbedMembers =
    listOf(
        stationModel to ("<init>" to stationConstructor),
        stationModel to ("getName" to "()" + Type.String),
        stationModel to ("getListen" to "()" + Type.List),
        streamModel to
            ("<init>" to "(" + Type.Int + Type.String.repeat(4) + Type.Boolean + ")" + Type.Void),
        streamModel to ("getListen_url" to "()" + Type.String),
        successResult to ("<init>" to "(" + Type.Object + ")" + Type.Void),
        successResult to ("getData" to "()" + Type.Object),
    )

val radioBrowserStations =
    patch(TimberRadioPatchStrings.radioBrowserStationsName) {
        description(TimberRadioPatchStrings.radioBrowserStationsDescription)
        compatibleWith("com.msoft.timberradio")

        execute {
            // A changed model would only fail at runtime, with NoSuchMethodError.
            for ((model, member) in stubbedMembers) requireMember(
                model,
                member.first,
                member.second,
            )
            log.info("Stations: the Station, Stream, and Success models match the extension stubs.")

            searchStation.before {
                val callback = paramOfType(CALLBACK)
                callback.assign(
                    RadioBrowser.withRadioBrowserStations(
                        paramOfType(stationQueryType.descriptor),
                        paramOfType(Type.String),
                        paramOfType(Type.Int),
                        callback,
                    ),
                )
            }
            log.info(
                "Stations: ${searchStation.descriptor} appends radio-browser.info stations to its results.",
            )
        }
    }

private fun requireMember(model: ClassTarget, name: String, proto: String) {
    model.classDef.method(name, proto)
        ?: error("Stations: ${model.descriptor} has no $name$proto, which the extension calls")
}
