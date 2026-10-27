// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("UnsafePackageNotice") @file:Suppress("unused")

package top.madkarma.capcut.integrity

import android.net.Uri
import app.reseam.runtime.Export

@Suppress("UseKtx")
@Export
fun isUnsafePackageModal(url: String?): Boolean {
    if (url.isNullOrEmpty()) return false

    val uri = Uri.parse(url)
    return uri.isHierarchical && uri.getQueryParameter("channel") == "image_lynx_global_unsafe_pkg_modal"
}
