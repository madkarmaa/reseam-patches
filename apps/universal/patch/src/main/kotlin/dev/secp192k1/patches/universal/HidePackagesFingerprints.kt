// SPDX-FileCopyrightText: 2026 secp192k1 <contact@alphexo.dev>
// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package dev.secp192k1.patches.universal

import app.reseam.patch.ExtMethod
import app.reseam.patch.MethodRefMatch
import app.reseam.patch.dex.parameterTypes
import app.reseam.patch.dex.returnType
import app.reseam.patch.methods
import app.reseam.patch.points
import dev.secp192k1.universal.hidepackages.bindings.HiddenPackages

private val extensionDescriptorPrefix = HiddenPackages.descriptor.substringBeforeLast('/') + "/"

internal fun packageManagerCallSites(wrapper: ExtMethod) =
    methods("callers of PackageManager.${wrapper.name}") {
        calls { wrappedBy(wrapper) }
        includeExtensions()
        // Keep the extension's calls to Android intact to avoid recursion.
        custom { !owner.startsWith(extensionDescriptorPrefix) }
    }.points("PackageManager.${wrapper.name}") {
        invokeVirtual { wrappedBy(wrapper) }
    }.all

private fun MethodRefMatch.wrappedBy(wrapper: ExtMethod) {
    owner(PACKAGE_MANAGER)
    name(wrapper.name)
    params(*wrapper.ref.parameterTypes.drop(1).toTypedArray())
    returns(wrapper.ref.returnType)
}
