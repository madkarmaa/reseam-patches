// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.extensions

import android.app.Activity
import android.app.Application
import android.content.Context
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.SpannableStringBuilder
import android.text.style.RelativeSizeSpan
import android.view.Gravity
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.view.WindowInsets
import android.view.animation.AccelerateInterpolator
import android.view.animation.DecelerateInterpolator
import android.widget.FrameLayout
import android.widget.TextView
import android.window.OnBackInvokedCallback
import android.window.OnBackInvokedDispatcher

// Niagara's own sheet layout: a header, a message, a primary button and a text button.
private const val SHEET_LAYOUT = "dialog_sheet_simple"
private const val DIM_LAYOUT = "dialog_background_dimmed"
private const val HEADER = "header_text_view"
private const val MESSAGE = "content_text_view"
private const val PRIMARY_BUTTON = "button_positive"
private const val TEXT_BUTTON = "button_negative"

// Niagara shows a button's second line at this size.
private const val SUBTITLE_SCALE = 0.85714287f
private const val DISABLED_ALPHA = 0.5f
private const val ENTER_MS = 280L
private const val EXIT_MS = 200L
private const val TICK_MS = 1000L

/** Content of a pause sheet; [openLabel] and [countdownLabel] title the open button. */
internal class PauseContent(
    val title: CharSequence,
    val message: CharSequence,
    val openLabel: CharSequence,
    val countdownLabel: (Int) -> CharSequence,
    val dismissLabel: CharSequence,
    val countdownSeconds: Int,
)

/**
 * Shows [content] in Niagara's bottom sheet over [activity]. Like Niagara's own sheets it is a view
 * in the launcher's window, inflated from [themed] so it follows Niagara's colors and fonts.
 */
internal fun showPauseSheet(
    activity: Activity,
    themed: Context,
    content: PauseContent,
    onOpen: Runnable,
) {
    val inflater = LayoutInflater.from(themed)
    val root = FrameLayout(themed)

    val dim = inflater.inflate(layoutId(themed, DIM_LAYOUT), root, false)
    root.addView(dim, FrameLayout.LayoutParams(MATCH, MATCH))

    val sheet = inflater.inflate(layoutId(themed, SHEET_LAYOUT), root, false)
    root.addView(
        sheet,
        FrameLayout.LayoutParams(MATCH, WRAP, Gravity.BOTTOM or Gravity.CENTER_HORIZONTAL),
    )

    val overlay = PauseOverlay(activity, root, dim, sheet)

    sheet.text(HEADER).text = content.title
    sheet.text(MESSAGE).text = content.message

    val dismiss = sheet.text(TEXT_BUTTON)
    dismiss.text = content.dismissLabel
    dismiss.setOnClickListener { overlay.close() }

    val open = sheet.text(PRIMARY_BUTTON)
    open.setOnClickListener {
        overlay.remove()
        onOpen.run()
    }
    overlay.countDown(open, content)

    overlay.show()
}

private class PauseOverlay(
    private val activity: Activity,
    private val root: ViewGroup,
    private val dim: View,
    private val sheet: View,
) {
    private val handler = Handler(Looper.getMainLooper())
    private var backCallback: OnBackInvokedCallback? = null
    private var closing = false

    // Leaving the launcher, such as by pressing home, pauses it and drops the sheet.
    private val pauseWatcher =
        object : Application.ActivityLifecycleCallbacks {
            override fun onActivityPaused(paused: Activity) {
                if (paused === activity) remove()
            }

            override fun onActivityCreated(created: Activity, state: Bundle?) {}

            override fun onActivityStarted(started: Activity) {}

            override fun onActivityResumed(resumed: Activity) {}

            override fun onActivityStopped(stopped: Activity) {}

            override fun onActivitySaveInstanceState(saved: Activity, state: Bundle) {}

            override fun onActivityDestroyed(destroyed: Activity) {}
        }

    fun show() {
        dim.setOnClickListener { close() }
        padForNavigationBar()
        (activity.window.decorView as ViewGroup).addView(root, MATCH, MATCH)
        root.requestApplyInsets()
        listenForBack()
        activity.application.registerActivityLifecycleCallbacks(pauseWatcher)
        enterOnFirstDraw()
    }

    fun countDown(button: TextView, content: PauseContent) {
        var remaining = content.countdownSeconds
        val tick =
            object : Runnable {
                override fun run() {
                    val locked = remaining > 0

                    button.text =
                        if (locked)
                            withSubtitle(content.openLabel, content.countdownLabel(remaining))
                        else content.openLabel
                    button.isEnabled = !locked
                    button.alpha = if (locked) DISABLED_ALPHA else 1f

                    if (!locked) return

                    remaining--
                    handler.postDelayed(this, TICK_MS)
                }
            }
        tick.run()
    }

    fun close() {
        if (closing) return

        closing = true
        dim.animate().alpha(0f).setDuration(EXIT_MS).start()
        sheet
            .animate()
            .translationY(sheet.height.toFloat())
            .setDuration(EXIT_MS)
            .setInterpolator(AccelerateInterpolator())
            .withEndAction { remove() }
            .start()
    }

    fun remove() {
        handler.removeCallbacksAndMessages(null)
        stopListeningForBack()
        activity.application.unregisterActivityLifecycleCallbacks(pauseWatcher)
        (root.parent as? ViewGroup)?.removeView(root)
    }

    private fun listenForBack() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val callback = OnBackInvokedCallback { close() }
            activity.onBackInvokedDispatcher.registerOnBackInvokedCallback(
                OnBackInvokedDispatcher.PRIORITY_OVERLAY,
                callback,
            )
            backCallback = callback
            return
        }

        // Before Android 13 back arrives as a key event at the focused view.
        root.isFocusableInTouchMode = true
        root.requestFocus()
        root.setOnKeyListener { _, keyCode, event ->
            if (keyCode != KeyEvent.KEYCODE_BACK) return@setOnKeyListener false
            if (event.action == KeyEvent.ACTION_UP) close()
            true
        }
    }

    private fun stopListeningForBack() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val callback = backCallback ?: return

        activity.onBackInvokedDispatcher.unregisterOnBackInvokedCallback(callback)
        backCallback = null
    }

    private fun padForNavigationBar() {
        val bottom = sheet.paddingBottom
        root.setOnApplyWindowInsetsListener { _, insets ->
            sheet.setPadding(
                sheet.paddingLeft,
                sheet.paddingTop,
                sheet.paddingRight,
                bottom + insets.navigationBarHeight(),
            )
            insets
        }
    }

    private fun enterOnFirstDraw() {
        dim.alpha = 0f
        sheet.viewTreeObserver.addOnPreDrawListener(
            object : ViewTreeObserver.OnPreDrawListener {
                override fun onPreDraw(): Boolean {
                    sheet.viewTreeObserver.removeOnPreDrawListener(this)
                    sheet.translationY = sheet.height.toFloat()
                    dim.animate().alpha(1f).setDuration(ENTER_MS).start()
                    sheet
                        .animate()
                        .translationY(0f)
                        .setDuration(ENTER_MS)
                        .setInterpolator(DecelerateInterpolator())
                        .start()
                    return true
                }
            },
        )
    }
}

private fun WindowInsets.navigationBarHeight(): Int {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R)
        return getInsets(WindowInsets.Type.navigationBars()).bottom

    // Android 8 to 10 have no typed insets; this getter is their only source.
    @Suppress("DEPRECATION")
    return systemWindowInsetBottom
}

private const val MATCH = ViewGroup.LayoutParams.MATCH_PARENT
private const val WRAP = ViewGroup.LayoutParams.WRAP_CONTENT

private fun withSubtitle(title: CharSequence, subtitle: CharSequence): CharSequence =
    SpannableStringBuilder()
        .append(title)
        .append('\n')
        .append(subtitle, RelativeSizeSpan(SUBTITLE_SCALE), 0)

private fun layoutId(context: Context, name: String): Int {
    val id = context.resources.getIdentifier(name, "layout", context.packageName)
    check(id != 0) { "Niagara layout $name is missing" }
    return id
}

private fun View.text(name: String): TextView {
    val id = context.resources.getIdentifier(name, "id", context.packageName)
    return checkNotNull(findViewById(id)) { "Niagara sheet view $name is missing" }
}
