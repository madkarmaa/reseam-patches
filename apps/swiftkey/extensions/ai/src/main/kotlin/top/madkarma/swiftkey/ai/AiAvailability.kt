// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("AiAvailability")
@file:Suppress("unused")

package top.madkarma.swiftkey.ai

import app.reseam.runtime.Export
import java.util.Locale

/** Every ISO 3166 country code, plus the empty code of a locale without a country. */
@Export fun everyCountry(): Set<String> = Locale.getISOCountries().toSet() + ""
