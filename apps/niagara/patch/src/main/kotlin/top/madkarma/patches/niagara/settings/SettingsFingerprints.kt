// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.patches.niagara.settings

import app.reseam.patch.*

// Every settings row is the row class or a subclass of it, and binds itself to a view holder.
private val bindRow =
    method("bindRow") {
        strings("Trying to bind view for a preference not attached to a screen!")
    }

internal val settingsRow =
    classTarget("settingsRow") {
        bytecode.findClass(bindRow.owner) ?: error("Settings row class is missing")
    }

// The binder reads one CharSequence field, the title, and shows the icon via setImageResource.
internal val rowTitle = bindRow.field("rowTitle") { type(Type.CharSequence) }

internal val rowIcon =
    fieldTarget("rowIcon") {
        bindRow.point { invokeVirtual { name("setImageResource") } }.writer(1).field().ref
    }

// The row's only CharSequence getter resolves its summary.
private val rowSummaryText =
    method("rowSummaryText") {
        inClass(settingsRow)
        returns(Type.CharSequence)
    }

internal val rowSummary = rowSummaryText.field("rowSummary") { type(Type.CharSequence) }

// The binder takes the same view holder; the click callback is the row's empty hook.
internal val rowClicked =
    method("rowClicked") {
        inClass(settingsRow)
        params(bindRow.parameterTypes.single())
        returns(Type.Void)
        custom { instructionCount == 1 }
    }

// The first section of Niagara's main settings; it adds its entry row to the screen it is given.
internal val productivitySettings =
    method("productivitySettings") {
        strings("features_category_digital_wellbeing")
    }

internal val addRow =
    method("addRow") {
        calledBy(productivitySettings)
        params(settingsRow.descriptor)
    }
