// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("ClearUrls") @file:Suppress("unused")

package top.madkarma.shared.clearurls

import android.content.Context
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.util.Log
import app.reseam.runtime.Export

private val LINK = Regex("""https?://[^\s<>"]+""", RegexOption.IGNORE_CASE)
private const val TRAILING_PUNCTUATION = ".,;:!?"
private val TEXT_EXTRAS = listOf(Intent.EXTRA_TEXT, Intent.EXTRA_HTML_TEXT, Intent.EXTRA_SUBJECT)

/** Loads the cached rules and refreshes them in the background. Calls after the first do nothing. */
@Export
fun start(context: Context?) {
    context?.let(RulesStore::start)
}

/** The web link without tracking. Other links, and every link while no rules are loaded, stay unchanged. */
@Export
fun clean(url: String?): String? = url?.let(::cleanLink)

private fun cleanLink(url: String): String {
    // The rules are written for web requests, and a pattern like ".*" would also reach content:// links.
    val scheme = url.substringBefore("://", "").lowercase()
    if (scheme != "http" && scheme != "https") return url
    val rules = RulesStore.current() ?: return url

    return try {
        rules.clean(url)
    } catch (exception: RuntimeException) {
        Log.w(TAG, "Cannot clean $url", exception)
        url
    }
}

/** The text with tracking removed from every link in it. */
@Export
fun cleanText(text: String?): String? {
    if (text == null) return null

    return LINK.replace(text) {
        val link = it.value.trimEnd { character -> character in TRAILING_PUNCTUATION }
        cleanLink(link) + it.value.substring(link.length)
    }
}

/** The URI without tracking, or the same URI when nothing changes. */
@Suppress("UseKtx")
@Export
fun cleanUri(uri: Uri?): Uri? {
    val link = uri?.toString() ?: return uri
    val cleaned = cleanLink(link)

    return if (cleaned == link) uri else Uri.parse(cleaned)
}

/** Cleans the links an intent carries in its text extras and its data, in place. */
@Export
fun cleanIntent(intent: Intent?): Intent? {
    if (intent == null) return null

    for (extra in TEXT_EXTRAS) {
        val text = intent.getCharSequenceExtra(extra)?.toString() ?: continue
        val cleaned = cleanText(text)
        if (cleaned != text) intent.putExtra(extra, cleaned)
    }

    val data = intent.data ?: return intent
    val cleaned = cleanUri(data)
    if (cleaned != data) intent.setDataAndType(cleaned, intent.type)

    return intent
}

/** Stands in for `Intent.createChooser(target, title)`, and cleans the intent first. */
@Export
fun createChooser(target: Intent?, title: CharSequence?): Intent? =
    Intent.createChooser(cleanIntent(target), title)

/** Stands in for `Intent.createChooser(target, title, sender)`, and cleans the intent first. */
@Export
fun createChooserWithCallback(
    target: Intent?, title: CharSequence?, sender: IntentSender?
): Intent? = Intent.createChooser(cleanIntent(target), title, sender)
