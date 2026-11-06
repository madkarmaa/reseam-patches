// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("Decisions")
@file:Suppress("unused")

package top.madkarma.truecaller.behavior

import app.reseam.runtime.Export
import com.truecaller.forcedupdate.UpdateType

@Suppress("RedundantNullableReturnType") @Export fun disabled(): Boolean? = false

@Export fun noUpdate(): UpdateType = UpdateType.NONE
