// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Premium") @file:Suppress("unused")

package top.madkarma.extensions.mapy

import android.util.Log
import app.reseam.runtime.Export
import java.lang.reflect.Modifier

@Export
fun unlockFeatures(features: Any?, offlineMaps: Any?, featureAccess: Any?) {
    try {
        for (field in checkNotNull(features) { "Premium features are unavailable" }.javaClass.declaredFields) {
            if (Modifier.isStatic(field.modifiers)) continue

            val type = field.type
            val value = when {
                type == Boolean::class.javaPrimitiveType -> true
                type.isInstance(offlineMaps) -> offlineMaps
                type.isInstance(featureAccess) -> featureAccess
                else -> continue
            }

            field.isAccessible = true
            field.set(features, value)
        }
    } catch (exception: ReflectiveOperationException) {
        Log.w("ReseamMapy", "Could not normalize Premium features", exception)
    } catch (exception: RuntimeException) {
        Log.w("ReseamMapy", "Could not normalize Premium features", exception)
    }
}

@Export
fun enabled(): Any = true

@Export
fun premiumTitle(text: String?, attribution: String?): String? =
    if (text == "Mapy.com Premium" || text == "Mapy.com פרימיום") attribution else text
