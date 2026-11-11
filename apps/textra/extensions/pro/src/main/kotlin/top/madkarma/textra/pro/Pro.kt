// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Pro")
@file:Suppress("unused")

package top.madkarma.textra.pro

import app.reseam.runtime.Export

// A nullable Int preserves the boxed Integer return type used by Textra's preference getter.
@Suppress("RedundantNullableReturnType") @Export fun paidState(): Int? = 1
