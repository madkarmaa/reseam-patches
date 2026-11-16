// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("RegistrationSteps")
@file:Suppress("unused")

package top.madkarma.extensions.strava

import app.reseam.runtime.Export
import java.io.Serializable

// The account needs a name, and age assurance asks for the birthday later anyway.
private val requiredSteps = setOf("NAME", "BIRTHDAY")

/**
 * Keeps only the required steps of a built new-registration step list. The suspending builder also
 * returns its suspension marker, which passes through unchanged.
 */
@Export
fun keepRequired(steps: Serializable?): Serializable? {
    if (steps !is List<*>) return steps
    return ArrayList(steps.filter { (it as? Enum<*>)?.name in requiredSteps })
}
