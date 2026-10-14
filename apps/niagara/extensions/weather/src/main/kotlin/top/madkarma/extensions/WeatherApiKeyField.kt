// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:JvmName("WeatherApiKeyField") @file:Suppress("unused")

package top.madkarma.extensions

import android.content.Context
import android.graphics.Typeface
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import app.reseam.runtime.Export

private const val MARKER = "madkarmaWeatherApiKey"
private const val METRIC_BUTTON = "use_metric_button"
private const val HEADER_TEXT = "header_text_view"
private const val CONTENT_TEXT = "content_text_view"
private const val LOCK_ICON = "ic_ip_act_lock"
private const val ON_BACKGROUND_ATTR = "onBackgroundColor"
private const val TEXT_START_DIMEN = "material52"
private const val ICON_SIZE_DIMEN = "small_icon_size"
private const val SPACING_DIMEN = "material8"
private const val LABEL_TEXT = "WeatherAPI.com key"
private const val HINT_TEXT = "a1b2c3d4"
private const val NOTE_TEXT = "Never share this key with anybody - keep it safe."

/** Adds the key field only to the weather settings sheet, identified by its units button. */
@Export
fun attach(content: Any?) {
    if (content !is LinearLayout) return
    if (content.findViewWithTag<View>(MARKER) != null) return

    val metricButton = findByName(content, METRIC_BUTTON) ?: return
    val api = WeatherApi.create(content.context)

    content.addView(
        buildRow(content, metricButton, api),
        content.indexOfChild(metricButton) + 1,
    )
}

@Suppress("DiscouragedApi")
private fun resId(context: Context, name: String, type: String): Int =
    context.resources.getIdentifier(name, type, context.packageName)

private fun findByName(root: View, name: String): View? {
    val id = resId(root.context, name, "id")
    if (id == 0) return null
    return root.findViewById(id)
}

private fun headerSample(metricButton: View): TextView? = sampleText(metricButton, HEADER_TEXT)

private fun contentSample(metricButton: View): TextView? = sampleText(metricButton, CONTENT_TEXT)

private fun sampleText(metricButton: View, name: String): TextView? =
    findByName(metricButton, name) as? TextView

private fun styleLike(target: TextView, sample: TextView) {
    target.setTextSize(TypedValue.COMPLEX_UNIT_PX, sample.textSize)
    target.setTextColor(sample.textColors)
}

private fun buildRow(sheet: LinearLayout, metricButton: View, api: WeatherApi): LinearLayout {
    val context = sheet.context

    val row = LinearLayout(context)
    row.tag = MARKER
    row.orientation = LinearLayout.HORIZONTAL
    row.gravity = Gravity.CENTER_VERTICAL
    row.layoutParams = rowParams(context, metricButton)
    row.setPadding(0, 0, 0, gapPx(context))

    val icon = buildIcon(context, metricButton)
    if (icon != null) row.addView(icon)

    val body = buildBody(context, icon != null)
    body.addView(buildLabel(context, metricButton))
    body.addView(buildInput(context, api))
    body.addView(buildNote(context, metricButton))

    row.addView(body)
    return row
}

private fun buildIcon(context: Context, metricButton: View): ImageView? {
    val iconId = resId(context, LOCK_ICON, "drawable")
    if (iconId == 0) return null
    val icon = ImageView(context)
    icon.setImageResource(iconId)
    val iconSize = dimenPx(context, ICON_SIZE_DIMEN, 24)
    val parameters = LinearLayout.LayoutParams(iconSize, iconSize)
    parameters.marginStart = dimenPx(context, SPACING_DIMEN, 8)
    icon.layoutParams = parameters
    icon.setColorFilter(iconColor(context, metricButton))
    return icon
}

private fun buildBody(context: Context, hasIcon: Boolean): LinearLayout {
    val iconWidth = if (hasIcon) dimenPx(context, ICON_SIZE_DIMEN, 24) else 0
    val indent =
        dimenPx(context, TEXT_START_DIMEN, 52) - dimenPx(context, SPACING_DIMEN, 8) - iconWidth
    val body = LinearLayout(context)
    body.orientation = LinearLayout.VERTICAL
    val parameters = LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f)
    parameters.marginStart = maxOf(0, indent)
    body.layoutParams = parameters
    return body
}

@Suppress("SetTextI18n")
private fun buildLabel(context: Context, metricButton: View): TextView {
    val label = TextView(context)
    label.text = LABEL_TEXT
    headerSample(metricButton)?.let { styleLike(label, it) }
    return label
}

@Suppress("SetTextI18n")
private fun buildNote(context: Context, metricButton: View): TextView {
    val note = TextView(context)
    note.text = NOTE_TEXT
    contentSample(metricButton)?.let { styleLike(note, it) }
    return note
}

@Suppress("SetTextI18n")
private fun buildInput(context: Context, api: WeatherApi): EditText {
    val input = EditText(context)
    input.setSingleLine(true)
    input.setSelectAllOnFocus(true)
    input.typeface = Typeface.MONOSPACE
    input.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_VARIATION_PASSWORD
    input.hint = HINT_TEXT
    input.setText(api.currentApiKey())
    input.addTextChangedListener(object : KeySaver() {
        override fun afterTextChanged(text: Editable?) {
            api.saveApiKey(checkNotNull(text) { "Weather key text is unavailable" }.toString())
        }
    })
    return input
}

private fun rowParams(context: Context, metricButton: View): LinearLayout.LayoutParams {
    val source = metricButton.layoutParams
    val parameters = LinearLayout.LayoutParams(source.width, source.height)
    if (source is ViewGroup.MarginLayoutParams) parameters.setMargins(
        source.leftMargin,
        gapPx(context),
        source.rightMargin,
        source.bottomMargin,
    )
    return parameters
}

private fun gapPx(context: Context): Int = dimenPx(context, SPACING_DIMEN, 8)

private fun dimenPx(context: Context, name: String, fallbackDp: Int): Int {
    val id = resId(context, name, "dimen")
    if (id != 0) {
        try {
            return context.resources.getDimensionPixelSize(id)
        } catch (exception: Exception) {
            // Use the dp fallback.
        }
    }
    return TypedValue.applyDimension(
        TypedValue.COMPLEX_UNIT_DIP,
        fallbackDp.toFloat(),
        context.resources.displayMetrics,
    ).toInt()
}

private fun iconColor(context: Context, metricButton: View): Int {
    val attribute = resId(context, ON_BACKGROUND_ATTR, "attr")
    if (attribute == 0) return headerColor(context, metricButton)
    return themeColor(context, attribute, headerColor(context, metricButton))
}

private fun headerColor(context: Context, metricButton: View): Int =
    headerSample(metricButton)?.textColors?.defaultColor ?: themeTextColor(context)

private fun themeTextColor(context: Context): Int =
    themeColor(context, android.R.attr.textColorPrimary, -1)

private fun themeColor(context: Context, attribute: Int, fallback: Int): Int {
    val value = TypedValue()

    if (!context.theme.resolveAttribute(attribute, value, true)) return fallback

    try {
        if (value.type in TypedValue.TYPE_FIRST_COLOR_INT..TypedValue.TYPE_LAST_COLOR_INT) return value.data
        return context.getColor(value.resourceId)
    } catch (exception: Exception) {
        return fallback
    }
}

private abstract class KeySaver : TextWatcher {
    override fun beforeTextChanged(text: CharSequence?, start: Int, count: Int, after: Int) {}

    override fun onTextChanged(text: CharSequence?, start: Int, before: Int, count: Int) {}
}
