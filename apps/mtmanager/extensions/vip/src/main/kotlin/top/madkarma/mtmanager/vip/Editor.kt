// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Editor")
@file:Suppress("unused")

package top.madkarma.mtmanager.vip

import android.content.Context
import app.reseam.runtime.Export

// The app stores a boxed Integer and XORs it with its UID and this mask before showing completions.
@Suppress("RedundantNullableReturnType")
@Export
fun completionIdentity(context: Context, mask: Int): Int? = context.applicationInfo.uid xor mask
