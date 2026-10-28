// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.shared.clearurls

import org.json.JSONArray
import org.json.JSONObject
import java.net.URLDecoder
import java.util.regex.Pattern
import java.util.regex.PatternSyntaxException

/**
 * The provider list of the ClearURLs project (https://gitlab.com/ClearURLs/rules), applied the way
 * its add-on applies it. Format: https://docs.clearurls.xyz/specs/rules
 */
internal class Rules private constructor(private val providers: List<Provider>) {
    val size get() = providers.size

    // The add-on reloads a changed URL, so every provider sees the result again.
    fun clean(url: String): String {
        var current = url

        repeat(MAX_PASSES) {
            val next = cleanOnce(current) ?: return current
            current = next
        }

        return current
    }

    // Like the add-on's request handler, this returns the change of the first provider that makes one.
    private fun cleanOnce(url: String): String? {
        for (provider in providers) {
            if (!provider.matches(url)) continue

            val redirect = provider.redirect(url)
            if (redirect != null && redirect != url) return redirect

            val cleaned = provider.removeTracking(url)
            if (cleaned != url) return cleaned
        }

        return null
    }

    companion object {
        private const val MAX_PASSES = 8

        fun parse(json: String): Rules {
            val providers = JSONObject(json).getJSONObject("providers")
            val parsed = providers.keys().asSequence()
                .mapNotNull { Provider.parse(providers.getJSONObject(it)) }

            return Rules(parsed.toList())
        }
    }
}

private class Provider(
    private val urlPattern: Pattern,
    private val exceptions: List<Pattern>,
    private val redirections: List<Pattern>,
    private val rawRules: List<Pattern>,
    private val fieldRules: Pattern?,
) {
    fun matches(url: String) =
        urlPattern.matcher(url).find() && exceptions.none { it.matcher(url).find() }

    fun redirect(url: String): String? {
        for (redirection in redirections) {
            val matcher = redirection.matcher(url)
            if (!matcher.find() || matcher.groupCount() < 1) continue

            val target = matcher.group(1) ?: continue
            return decodeRedirect(target)
        }

        return null
    }

    fun removeTracking(url: String): String {
        var cleaned = url
        for (rule in rawRules) cleaned = rule.matcher(cleaned).replaceAll("")

        val fieldRules = fieldRules ?: return cleaned
        val isTracking = { name: String -> name.isNotEmpty() && fieldRules.matcher(name).matches() }

        val fragmentStart = cleaned.indexOf('#').takeIf { it >= 0 } ?: cleaned.length
        val queryStart =
            cleaned.indexOf('?').takeIf { it in 0 until fragmentStart } ?: fragmentStart
        val query = cleaned.substring(queryStart, fragmentStart).removePrefix("?")
        val fragment = cleaned.substring(fragmentStart).removePrefix("#")
        if (query.isEmpty() && fragment.isEmpty()) return cleaned

        // Query names are compared decoded, fragment names as written, like URLSearchParams and URLHashParams.
        val keptQuery =
            query.split('&').filter { it.isNotEmpty() && !isTracking(decodeName(nameOf(it))) }
        val keptFragment = fragment.split('&').filter { it.isNotEmpty() && !isTracking(nameOf(it)) }

        val keptQueryText = keptQuery.joinToString("&")
        val keptFragmentText = keptFragment.joinToString("&")
        if (keptQueryText == query && keptFragmentText == fragment) return cleaned

        return buildString {
            append(cleaned, 0, queryStart)
            if (keptQuery.isNotEmpty()) append('?').append(keptQueryText)
            if (keptFragment.isNotEmpty()) append('#').append(keptFragmentText)
        }
    }

    companion object {
        // A complete provider blocks whole sites, which a link cannot do, so it is left out.
        fun parse(provider: JSONObject): Provider? {
            if (provider.optBoolean("completeProvider")) return null

            val urlPattern = compile(provider.optString("urlPattern")) ?: return null
            // The add-on removes referral parameters too, unless the user allows them.
            val fieldNames = strings(provider, "rules") + strings(provider, "referralMarketing")
            val validNames = fieldNames.filter { compile(it) != null }
            val fieldRules = validNames.takeIf { it.isNotEmpty() }?.joinToString("|", "(?:", ")")

            return Provider(
                urlPattern,
                patterns(provider, "exceptions"),
                patterns(provider, "redirections"),
                patterns(provider, "rawRules"),
                fieldRules?.let(::compile),
            )
        }

        private fun patterns(provider: JSONObject, key: String) =
            strings(provider, key).mapNotNull(::compile)

        private fun strings(provider: JSONObject, key: String): List<String> {
            val values = provider.optJSONArray(key) ?: JSONArray()
            return (0 until values.length()).map { values.getString(it) }
        }

        // The list is written for JavaScript; skip the rare pattern Java cannot compile.
        private fun compile(pattern: String): Pattern? = try {
            Pattern.compile(pattern, Pattern.CASE_INSENSITIVE)
        } catch (_: PatternSyntaxException) {
            null
        }
    }
}

private fun nameOf(parameter: String) = parameter.substringBefore('=')

private fun decodeName(name: String) = try {
    URLDecoder.decode(name, "UTF-8")
} catch (_: IllegalArgumentException) {
    name
}

private const val MAX_DECODES = 8

// The add-on decodes the target until it stops changing. Decoding a decoded target leaves it as it is,
// so the extra passes change nothing. A target without a scheme gets https here, where the add-on uses
// http, because the link is shared rather than opened.
private fun decodeRedirect(target: String): String {
    var decoded = target
    repeat(MAX_DECODES) { decoded = decodeComponent(decoded) }

    return if (decoded.startsWith("http")) decoded else "https://$decoded"
}

// decodeURIComponent leaves '+' alone, unlike URLDecoder.
private fun decodeComponent(value: String) = try {
    URLDecoder.decode(value.replace("+", "%2B"), "UTF-8")
} catch (_: IllegalArgumentException) {
    value
}
