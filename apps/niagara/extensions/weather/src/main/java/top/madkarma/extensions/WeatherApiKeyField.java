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
 * Adds the WeatherAPI key field to the weather settings sheet, right
 * below the temperature-units button. The sheet's content callback is
 * shared with other dialogs, so anything that isn't the weather sheet
 * (no temperature-units button) is left alone.
 */
@SuppressWarnings("unused")
public final class WeatherApiKeyField {
    private static final String MARKER = "madkarmaWeatherApiKey";

    private static final String METRIC_BUTTON = "use_metric_button";
    private static final String HEADER_TEXT = "header_text_view";
    private static final String LOCK_ICON = "ic_ip_act_lock";
    private static final String ON_BACKGROUND_ATTR = "onBackgroundColor";

    private static final String TEXT_START_DIMEN = "material52";
    private static final String ICON_SIZE_DIMEN = "small_icon_size";
    private static final String SPACING_DIMEN = "material8";

    private static final String LABEL_TEXT = "WeatherAPI.com key";
    private static final String HINT_TEXT = "a1b2c3d4";

    private WeatherApiKeyField() {
    }

    /**
     * Injects the key row into the inflated sheet content, once.
     * Content is the {@code weather_dialog_content} root on the weather
     * sheet and something else on the sibling dialogs.
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
     * Resource id by name, or 0 when the app has no such entry.
     */
    @SuppressWarnings("DiscouragedApi")
    private static int resId(Context context, String name, String type) {
        return context.getResources().getIdentifier(name, type, context.getPackageName());
    }

    /**
     * View by resource name, or null when it is missing.
     */
    private static View findByName(View root, String name) {
        int id = resId(root.getContext(), name, "id");
        if (id == 0) {
            return null;
        }

        return root.findViewById(id);
    }

    /**
     * Header text of the temperature-units button, or null when the app
     * layout no longer has one.
     */
    private static TextView headerSample(View metricButton) {
        View header = findByName(metricButton, HEADER_TEXT);
        if (!(header instanceof TextView sample)) {
            return null;
        }

        return sample;
    }

    /**
     * Lock icon plus label and key input, mirroring the tile rows above.
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
        row.addView(body);
        return row;
    }

    /**
     * Lock glyph sized and tinted like the tile icons, or null when the
     * drawable is gone.
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
     * Vertical label-plus-input stack, starting where the tile texts start.
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
     * Row label in the tile header's size and color.
     */
    @SuppressWarnings("SetTextI18n")
    private static TextView buildLabel(Context context, View metricButton) {
        TextView label = new TextView(context);
        label.setText(LABEL_TEXT);

        TextView sample = headerSample(metricButton);
        if (sample != null) {
            label.setTextSize(TypedValue.COMPLEX_UNIT_PX, sample.getTextSize());
            label.setTextColor(sample.getTextColors());
        }

        return label;
    }

    /**
     * Monospace key input, saving to settings on every edit. The text is
     * set before the watcher is added, so prefilling never writes.
     */
    @SuppressWarnings("SetTextI18n")
    private static EditText buildInput(Context context, WeatherApi api) {
        EditText input = new EditText(context);
        input.setInputType(InputType.TYPE_CLASS_TEXT);
        input.setSingleLine(true);
        input.setSelectAllOnFocus(true);
        input.setTypeface(Typeface.MONOSPACE);
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
     * Same width as the temperature-units button, with a small top gap.
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
     * Small spacing step both rows use.
     */
    private static int gapPx(Context context) {
        return dimenPx(context, SPACING_DIMEN, 8);
    }

    /**
     * Dimen by name, with a dp fallback so a missing entry can't break the row.
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
     * Siblings tint their glyphs with the theme's background-on color,
     * else the tile header's color.
     */
    private static int iconColor(Context context, View metricButton) {
        int attrId = resId(context, ON_BACKGROUND_ATTR, "attr");
        if (attrId == 0) {
            return headerColor(context, metricButton);
        }

        return themeColor(context, attrId, headerColor(context, metricButton));
    }

    /**
     * Tile header's color, or the theme's own text color.
     */
    private static int headerColor(Context context, View metricButton) {
        TextView sample = headerSample(metricButton);
        if (sample == null) {
            return themeTextColor(context);
        }

        return sample.getTextColors().getDefaultColor();
    }

    /**
     * Framework primary text color, readable in either theme.
     */
    private static int themeTextColor(Context context) {
        return themeColor(context, android.R.attr.textColorPrimary, 0xFFFFFFFF);
    }

    /**
     * Theme color for an attr, or the fallback when it can't resolve.
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
     * Text watcher with only the save hook left to implement.
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
