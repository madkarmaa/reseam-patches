// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.settings

import app.reseam.patch.klass
import app.reseam.patch.method

internal val settingsCategoriesCreated =
    klass("com.truecaller.settings.impl.ui.categories.CategoriesFragment").method("onViewCreated")
