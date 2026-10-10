package top.madkarma.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.InputType;
import android.text.TextWatcher;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Adds the WeatherAPI key field below the temperature-units button.
 * The content callback also handles other dialogs. The button identifies
 * the weather settings sheet, so dialogs without it receive no field.
 */
@SuppressWarnings("unused")
public final class WeatherApiKeyField {
    private static final String MARKER = "madkarmaWeatherApiKey";

    private static final String METRIC_BUTTON = "use_metric_button";
    private static final String HEADER_TEXT = "header_text_view";
    private static final String CONTENT_TEXT = "content_text_view";
    private static final String LOCK_ICON = "ic_ip_act_lock";
    private static final String ON_BACKGROUND_ATTR = "onBackgroundColor";

    private static final String TEXT_START_DIMEN = "material52";
    private static final String ICON_SIZE_DIMEN = "small_icon_size";
    private static final String SPACING_DIMEN = "material8";

    private static final String LABEL_TEXT = "WeatherAPI.com key";
    private static final String HINT_TEXT = "a1b2c3d4";
    private static final String NOTE_TEXT = "Never share this key with anybody - keep it safe.";

    private WeatherApiKeyField() {
    }

    /**
     * Adds the key row once to the weather sheet's {@code weather_dialog_content} root.
     * Ignores content from other dialogs.
     */
    public static void attach(Object content) {
        if (!(content instanceof LinearLayout sheet)) {
            return;
        }

        if (sheet.findViewWithTag(MARKER) != null) {
            return;
        }

        View metricButton = findByName(sheet, METRIC_BUTTON);
        if (metricButton == null) {
            return;
        }

        WeatherApi api = WeatherApi.create(sheet.getContext());
        sheet.addView(buildRow(sheet, metricButton, api), sheet.indexOfChild(metricButton) + 1);
    }

    /**
     * Returns the resource ID by name, or zero if the resource is missing.
     */
    @SuppressWarnings("DiscouragedApi")
    private static int resId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    /**
     * Returns the view by resource name, or null if it is missing.
     */
    private static View findByName(View root, String name) {
        int id = resId(root.getContext(), name, "id");
        if (id == 0) {
            return null;
        }

        return root.findViewById(id);
    }

    /**
     * Returns the temperature-units button's header view, or null if it is missing.
     */
    private static TextView headerSample(View metricButton) {
        return sampleText(metricButton, HEADER_TEXT);
    }

    /**
     * Returns the temperature-units button's value view, such as "Celsius".
     * Returns null if the view is missing.
     */
    private static TextView contentSample(View metricButton) {
        return sampleText(metricButton, CONTENT_TEXT);
    }

    /**
     * Returns the text view by resource name, or null if it is missing.
     */
    private static TextView sampleText(View metricButton, String name) {
        View sample = findByName(metricButton, name);
        if (!(sample instanceof TextView text)) {
            return null;
        }

        return text;
    }

    /**
     * Copies a sample's size and color onto a target.
     */
    private static void styleLike(TextView target, TextView sample) {
        target.setTextSize(TypedValue.COMPLEX_UNIT_PX, sample.getTextSize());
        target.setTextColor(sample.getTextColors());
    }

    /**
     * Builds a row with a lock icon, label, key input, and safety note.
     * Uses the layout of the existing settings rows.
     */
    private static LinearLayout buildRow(LinearLayout sheet, View metricButton, WeatherApi api) {
        Context context = sheet.getContext();

        LinearLayout row = new LinearLayout(context);
        row.setTag(MARKER);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setLayoutParams(rowParams(context, metricButton));
        row.setPadding(0, 0, 0, gapPx(context));

        ImageView icon = buildIcon(context, metricButton);
        if (icon != null) {
            row.addView(icon);
        }

        LinearLayout body = buildBody(context, icon != null);
        body.addView(buildLabel(context, metricButton));
        body.addView(buildInput(context, api));
        body.addView(buildNote(context, metricButton));
        row.addView(body);
        return row;
    }

    /**
     * Builds a lock icon with the settings icons' size and color.
     * Returns null if the drawable is missing.
     */
    private static ImageView buildIcon(Context context, View metricButton) {
        int iconId = resId(context, LOCK_ICON, "drawable");
        if (iconId == 0) {
            return null;
        }

        ImageView icon = new ImageView(context);
        icon.setImageResource(iconId);

        int iconSize = dimenPx(context, ICON_SIZE_DIMEN, 24);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(iconSize, iconSize);
        iconParams.setMarginStart(dimenPx(context, SPACING_DIMEN, 8));
        icon.setLayoutParams(iconParams);

        icon.setColorFilter(iconColor(context, metricButton));
        return icon;
    }

    /**
     * Stacks the label, input, and note vertically, aligned with the settings text.
     */
    private static LinearLayout buildBody(Context context, boolean hasIcon) {
        int iconWidth = hasIcon ? dimenPx(context, ICON_SIZE_DIMEN, 24) : 0;
        int indent = dimenPx(context, TEXT_START_DIMEN, 52) - dimenPx(context, SPACING_DIMEN, 8) - iconWidth;

        LinearLayout body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);

        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
        bodyParams.setMarginStart(Math.max(0, indent));
        body.setLayoutParams(bodyParams);
        return body;
    }

    /**
     * Builds the row label with the settings header's text size and color.
     */
    @SuppressWarnings("SetTextI18n")
    private static TextView buildLabel(Context context, View metricButton) {
        TextView label = new TextView(context);
        label.setText(LABEL_TEXT);

        TextView sample = headerSample(metricButton);
        if (sample != null) {
            styleLike(label, sample);
        }

        return label;
    }

    /**
     * Builds the safety note with the settings value's text size and color.
     */
    @SuppressWarnings("SetTextI18n")
    private static TextView buildNote(Context context, View metricButton) {
        TextView note = new TextView(context);
        note.setText(NOTE_TEXT);

        TextView sample = contentSample(metricButton);
        if (sample != null) {
            styleLike(note, sample);
        }

        return note;
    }

    /**
     * Builds a masked monospace key input that saves each edit to settings.
     * Sets the saved key before adding the watcher, so prefilling does not write.
     */
    @SuppressWarnings("SetTextI18n")
    private static EditText buildInput(Context context, WeatherApi api) {
        EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTypeface(Typeface.MONOSPACE);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD);
        input.setHint(HINT_TEXT);
        input.setText(api.currentApiKey());
        input.addTextChangedListener(new KeySaver() {
            @Override
            public void afterTextChanged(Editable s) {
                api.saveApiKey(s.toString());
            }
        });
        return input;
    }

    /**
     * Copies the temperature-units button's dimensions and adds a top gap.
     */
    private static LinearLayout.LayoutParams rowParams(Context context, View metricButton) {
        ViewGroup.LayoutParams source = metricButton.getLayoutParams();
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(source.width, source.height);

        if (source instanceof ViewGroup.MarginLayoutParams margins) {
            params.setMargins(margins.leftMargin, gapPx(context), margins.rightMargin, margins.bottomMargin);
        }

        return params;
    }

    /**
     * Returns the spacing used by both rows in pixels.
     */
    private static int gapPx(Context context) {
        return dimenPx(context, SPACING_DIMEN, 8);
    }

    /**
     * Returns a named dimension in pixels, or converts the fallback from dp.
     */
    private static int dimenPx(Context context, String name, int fallbackDp) {
        int id = resId(context, name, "dimen");
        if (id != 0) {
            try {
                return context.getResources().getDimensionPixelSize(id);
            } catch (Exception e) {
                // Fall through to the dp fallback below.
            }
        }

        return (int) TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, fallbackDp, context.getResources().getDisplayMetrics());
    }

    /**
     * Uses the theme's onBackgroundColor for the icon, as the other rows do.
     * Falls back to the settings header's color.
     */
    private static int iconColor(Context context, View metricButton) {
        int attrId = resId(context, ON_BACKGROUND_ATTR, "attr");
        if (attrId == 0) {
            return headerColor(context, metricButton);
        }

        return themeColor(context, attrId, headerColor(context, metricButton));
    }

    /**
     * Returns the settings header's color, or the theme's primary text color.
     */
    private static int headerColor(Context context, View metricButton) {
        TextView sample = headerSample(metricButton);
        if (sample == null) {
            return themeTextColor(context);
        }

        return sample.getTextColors().getDefaultColor();
    }

    /**
     * Returns the theme's primary text color.
     */
    private static int themeTextColor(Context context) {
        return themeColor(context, android.R.attr.textColorPrimary, 0xFFFFFFFF);
    }

    /**
     * Returns the theme attribute's color, or the fallback if resolution fails.
     */
    private static int themeColor(Context context, int attrId, int fallback) {
        TypedValue value = new TypedValue();
        if (!context.getTheme().resolveAttribute(attrId, value, true)) {
            return fallback;
        }

        try {
            if (value.type >= TypedValue.TYPE_FIRST_COLOR_INT && value.type <= TypedValue.TYPE_LAST_COLOR_INT) {
                return value.data;
            }

            return context.getColor(value.resourceId);
        } catch (Exception e) {
            return fallback;
        }
    }

    /**
     * Provides empty callbacks so subclasses only need to implement afterTextChanged.
     */
    private abstract static class KeySaver implements TextWatcher {
        @Override
        public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        }

        @Override
        public void onTextChanged(CharSequence s, int start, int before, int count) {
        }
    }
}
