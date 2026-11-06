// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.truecaller.wear

import app.reseam.patch.*

private val settingCategory = klass("com.truecaller.settings.impl.ui.search.SettingCategory")

private val watchCategory = settingCategory.field("WATCH")

private val watchSearchCategory =
    method("watchSearchCategory") {
        accesses { name(watchCategory.name) }
        returns(settingCategory.descriptor)
    }

internal val watchSearchEntries =
    method("watchSearchEntries") {
        inClass(klass(watchSearchCategory.owner))
        returns(Type.Object)
    }

private val watchSearchModel =
    klass("watchSearchModel") {
        strings("Category(subcategories=")
    }

internal val watchSearchItems = watchSearchModel.fieldOfType("java.util.List")
