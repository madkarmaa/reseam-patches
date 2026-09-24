package top.madkarma.universal.extensions;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.text.style.TypefaceSpan;
import android.text.style.UnderlineSpan;
import android.util.TypedValue;
import android.view.*;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * A generic dialog in the Reseam manager style (dark surface, mint accent):
 * overtext row with an icon, title, rich description, an optional old-to-new
 * version visualization, and inline Material text buttons. Built with
 * framework Views only, so it can be injected into any host app.
 */
@SuppressWarnings("unused")
public final class ReseamDialog extends Dialog {
    // Manager ReseamDarkColors: surface, mutedElevated, foreground, mutedForeground, primary, primaryDarker.
    static final int SURFACE = 0xFF1A1A1A;
    static final int CHIP = 0xFF242424;
    static final int FOREGROUND = 0xFFEDEDED;
    static final int MUTED_FOREGROUND = 0xFFA3A3A3;
    static final int PRIMARY = 0xFFB6F0CF;
    static final int PRIMARY_DARKER = 0xFF6BC58E;

    private static final int OVERTEXT_ICON_DP = 20;
    private static final int OVERTEXT_GAP_DP = 8;
    private static final int TEXT_GAP_DP = 6;
    private static final int APP_BLOCK_GAP_DP = 16;
    private static final int SECTION_GAP_DP = 16;
    private static final int BOTTOM_PADDING_DP = 8;
    private static final int CARD_RADIUS_DP = 16;
    private static final int CHIP_RADIUS_DP = 8;
    private static final int PADDING_DP = 24;
    private static final int APP_ICON_DP = 64;
    private static final int APP_TEXT_GAP_DP = 16;

    private ReseamDialog(Context context) {
        super(context);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
    }

    private static Paint fill(int color) {
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(color);
        return paint;
    }

    // Minimal parser for the subset used above: absolute M(ove), L(ine), C(urve), H(orizontal), V(ertical), Z(close).
    private static Path parsePath(String data) {
        Path path = new Path();
        int[] at = new int[1];
        float x = 0;
        float y = 0;
        char command = 0;
        while (at[0] < data.length()) {
            char c = data.charAt(at[0]);
            if (c == ' ' || c == ',') {
                at[0]++;
                continue;
            }
            if (Character.isLetter(c)) {
                command = c;
                at[0]++;
                if (command == 'Z') path.close();
                continue;
            }
            if (command == 'M') {
                x = readNumber(data, at);
                y = readNumber(data, at);
                path.moveTo(x, y);
            } else if (command == 'L') {
                x = readNumber(data, at);
                y = readNumber(data, at);
                path.lineTo(x, y);
            } else if (command == 'C') {
                float x1 = readNumber(data, at);
                float y1 = readNumber(data, at);
                float x2 = readNumber(data, at);
                float y2 = readNumber(data, at);
                x = readNumber(data, at);
                y = readNumber(data, at);
                path.cubicTo(x1, y1, x2, y2, x, y);
            } else if (command == 'H') {
                x = readNumber(data, at);
                path.lineTo(x, y);
            } else if (command == 'V') {
                y = readNumber(data, at);
                path.lineTo(x, y);
            } else {
                at[0]++;
            }
        }
        return path;
    }

    private static float readNumber(String data, int[] at) {
        int n = data.length();
        while (at[0] < n && (data.charAt(at[0]) == ' ' || data.charAt(at[0]) == ',')) at[0]++;
        int start = at[0];
        while (at[0] < n && !Character.isLetter(data.charAt(at[0])) && data.charAt(at[0]) != ' ' && data.charAt(at[0]) != ',')
            at[0]++;
        return Float.parseFloat(data.substring(start, at[0]));
    }

    /**
     * Mixed plain, accented-underlined, and monospace segments for one description line.
     */
    public static final class RichText {
        private final SpannableStringBuilder builder = new SpannableStringBuilder();

        public RichText text(String text) {
            builder.append(text);
            return this;
        }

        public RichText highlight(String text, int color) {
            int start = builder.length();
            builder.append(text);
            builder.setSpan(new ForegroundColorSpan(color), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            builder.setSpan(new UnderlineSpan(), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return this;
        }

        public RichText mono(String text) {
            int start = builder.length();
            builder.append(text);
            builder.setSpan(new TypefaceSpan("monospace"), start, builder.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            return this;
        }

        public CharSequence build() {
            return builder;
        }
    }

    /**
     * The Reseam logo mark (manager LogoKt, primary over primaryDarker), drawn without resources.
     * Currently unused by the update dialog; kept for later use.
     */
    public static final class LogoDrawable extends Drawable {
        private static final String PRIMARY_PATH = "M13 16.5C13 14.8 14.3 13.5 16 13.5H23C25 13.5 26.5 15 26.5 17V20C26.5 22 25 23.5 23 23.5H16C14.3 23.5 13 22.2 13 20.5V16.5Z";
        private static final String SECONDARY_PATH = "M6 8.5C6 6.8 7.3 5.5 9 5.5H16.5C19 5.5 20.5 7.5 20.5 10C20.5 13 18.5 14 16.5 14H12C10.3 14 9 15.3 9 17V23.5C9 25.2 7.7 26.5 6 26.5V8.5Z";

        private final PathDrawable secondary = new PathDrawable(SECONDARY_PATH, PRIMARY_DARKER, 32f);
        private final PathDrawable primary = new PathDrawable(PRIMARY_PATH, PRIMARY, 32f);

        @Override
        public void draw(Canvas canvas) {
            secondary.setBounds(getBounds());
            primary.setBounds(getBounds());
            secondary.draw(canvas);
            primary.draw(canvas);
        }

        @Override
        public void setAlpha(int alpha) {
            secondary.setAlpha(alpha);
            primary.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            secondary.setColorFilter(colorFilter);
            primary.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    /**
     * Material Icons arrow_forward, drawn without resources.
     */
    public static final class ArrowDrawable extends PathDrawable {
        private static final String ARROW_PATH = "M12 4L10.59 5.41L16.17 11H4V13H16.17L10.59 18.59L12 20L20 12Z";

        public ArrowDrawable() {
            this(MUTED_FOREGROUND);
        }

        public ArrowDrawable(int color) {
            super(ARROW_PATH, color, 24f);
        }
    }

    private static class PathDrawable extends Drawable {
        private final Path path;
        private final Paint paint;
        private final float viewport;

        PathDrawable(String data, int color, float viewport) {
            path = parsePath(data);
            paint = fill(color);
            this.viewport = viewport;
        }

        @Override
        public void draw(Canvas canvas) {
            android.graphics.Rect bounds = getBounds();
            if (bounds.isEmpty()) return;
            canvas.save();
            canvas.translate(bounds.left, bounds.top);
            float scale = Math.min(bounds.width(), bounds.height()) / viewport;
            canvas.scale(scale, scale);
            canvas.drawPath(path, paint);
            canvas.restore();
        }

        @Override
        public void setAlpha(int alpha) {
            paint.setAlpha(alpha);
        }

        @Override
        public void setColorFilter(ColorFilter colorFilter) {
            paint.setColorFilter(colorFilter);
        }

        @Override
        public int getOpacity() {
            return android.graphics.PixelFormat.TRANSLUCENT;
        }
    }

    public static final class Builder {
        private final Context context;
        private int themeColor = PRIMARY;
        private Drawable overtextIcon;
        private String overtext;
        private String title;
        private CharSequence description;
        private Drawable appIcon;
        private String appTitle;
        private String appSubtitle;
        private String oldVersion;
        private String newVersion;
        private String negativeText;
        private DialogInterface.OnClickListener negativeListener;
        private String positiveText;
        private DialogInterface.OnClickListener positiveListener;

        public Builder(Context context) {
            this.context = context;
        }

        public Builder themeColor(int themeColor) {
            this.themeColor = themeColor;
            return this;
        }

        public Builder overtext(Drawable icon, String overtext) {
            this.overtextIcon = icon;
            this.overtext = overtext;
            return this;
        }

        public Builder overtext(String overtext) {
            this.overtext = overtext;
            return this;
        }

        public Builder title(String title) {
            this.title = title;
            return this;
        }

        public Builder description(CharSequence description) {
            this.description = description;
            return this;
        }

        public Builder appIdentity(Drawable icon, String title, String subtitle) {
            this.appIcon = icon;
            this.appTitle = title;
            this.appSubtitle = subtitle;
            return this;
        }

        public Builder versions(String oldVersion, String newVersion) {
            this.oldVersion = oldVersion;
            this.newVersion = newVersion;
            return this;
        }

        public Builder negativeButton(String text, DialogInterface.OnClickListener listener) {
            this.negativeText = text;
            this.negativeListener = listener;
            return this;
        }

        public Builder positiveButton(String text, DialogInterface.OnClickListener listener) {
            this.positiveText = text;
            this.positiveListener = listener;
            return this;
        }

        public ReseamDialog show() {
            ReseamDialog dialog = new ReseamDialog(context);
            dialog.setContentView(content(dialog));
            dialog.setCancelable(true);
            dialog.setCanceledOnTouchOutside(true);
            dialog.show();
            Window window = dialog.getWindow();
            if (window == null) return dialog;
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            return dialog;
        }

        private LinearLayout content(ReseamDialog dialog) {
            Context context = dialog.getContext();
            LinearLayout root = vertical(context);
            root.setPadding(dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP), dp(BOTTOM_PADDING_DP));
            root.setBackgroundDrawable(rounded(SURFACE, CARD_RADIUS_DP));

            LinearLayout textSection = textSection(context);
            if (textSection.getChildCount() > 0) addSection(root, textSection);
            if (oldVersion != null && newVersion != null) addSection(root, versionsRow(context));
            if (negativeText != null || positiveText != null) addSection(root, actionsRow(dialog));

            LinearLayout frame = new LinearLayout(context);
            frame.setOrientation(LinearLayout.VERTICAL);
            frame.setPadding(dp(PADDING_DP), 0, dp(PADDING_DP), 0);
            frame.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
            return frame;
        }


        private LinearLayout textSection(Context context) {
            LinearLayout section = vertical(context);
            if (overtext != null) addTextItem(section, overtextRow(context));
            if (title != null) addTextItem(section, titleView(context));
            if (appTitle != null) addAppRow(section, appRow(context));
            if (description != null) addTextItem(section, descriptionView(context));
            return section;
        }

        private void addTextItem(LinearLayout section, View view) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (section.getChildCount() > 0) params.topMargin = dp(TEXT_GAP_DP);
            section.addView(view, params);
        }

        private void addAppRow(LinearLayout section, View view) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            params.topMargin = dp(APP_BLOCK_GAP_DP);
            section.addView(view, params);
        }

        private void addSection(LinearLayout root, View view) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (root.getChildCount() > 0) params.topMargin = dp(SECTION_GAP_DP);
            root.addView(view, params);
        }

        private LinearLayout overtextRow(Context context) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (overtextIcon != null) {
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(overtextIcon);
                row.addView(icon, new LinearLayout.LayoutParams(dp(OVERTEXT_ICON_DP), dp(OVERTEXT_ICON_DP)));
            }

            TextView label = new TextView(context);
            label.setText(overtext);
            label.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            label.setTextColor(0xA3FFFFFF);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (overtextIcon != null) params.leftMargin = dp(OVERTEXT_GAP_DP);
            row.addView(label, params);
            return row;
        }

        private TextView titleView(Context context) {
            TextView title = new TextView(context);
            title.setText(this.title);
            title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            title.setTextColor(Color.WHITE);
            return title;
        }

        private TextView descriptionView(Context context) {
            TextView description = new TextView(context);
            description.setText(this.description);
            description.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            description.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            description.setTextColor(FOREGROUND);
            return description;
        }

        private LinearLayout appRow(Context context) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);

            if (appIcon != null) {
                ImageView icon = new ImageView(context);
                icon.setImageDrawable(appIcon);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    icon.setClipToOutline(true);
                    icon.setOutlineProvider(new ViewOutlineProvider() {
                        @Override
                        public void getOutline(View view, Outline outline) {
                            outline.setOval(0, 0, view.getWidth(), view.getHeight());
                        }
                    });
                }
                row.addView(icon, new LinearLayout.LayoutParams(dp(APP_ICON_DP), dp(APP_ICON_DP)));
            }

            LinearLayout texts = vertical(context);
            TextView name = new TextView(context);
            name.setText(appTitle);
            name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            name.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
            name.setTextColor(Color.WHITE);
            texts.addView(name);

            if (appSubtitle != null) {
                TextView subtitle = new TextView(context);
                subtitle.setText(appSubtitle);
                subtitle.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
                subtitle.setTextColor(MUTED_FOREGROUND);
                texts.addView(subtitle);
            }

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (appIcon != null) params.leftMargin = dp(APP_TEXT_GAP_DP);
            row.addView(texts, params);
            return row;
        }

        private LinearLayout versionsRow(Context context) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.addView(versionChip(context, oldVersion, Typeface.DEFAULT), wrapped());
            row.addView(arrowView(context));
            row.addView(versionChip(context, newVersion, Typeface.create("sans-serif-medium", Typeface.BOLD)), weighted());
            return row;
        }

        private LinearLayout.LayoutParams wrapped() {
            return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }

        private LinearLayout.LayoutParams weighted() {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
            return params;
        }

        private TextView versionChip(Context context, String version, Typeface typeface) {
            TextView chip = new TextView(context);
            chip.setText(version);
            chip.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
            chip.setTypeface(typeface);
            chip.setTextColor(Color.WHITE);
            chip.setGravity(Gravity.CENTER);
            chip.setPadding(dp(16), dp(14), dp(16), dp(14));
            chip.setBackgroundDrawable(rounded(CHIP, CHIP_RADIUS_DP));
            return chip;
        }

        private ImageView arrowView(Context context) {
            ImageView arrow = new ImageView(context);
            arrow.setImageDrawable(new ArrowDrawable());
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
            params.leftMargin = dp(8);
            params.rightMargin = dp(8);
            arrow.setLayoutParams(params);
            return arrow;
        }

        private LinearLayout actionsRow(ReseamDialog dialog) {
            LinearLayout row = new LinearLayout(dialog.getContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
            if (negativeText != null)
                row.addView(actionButton(dialog, negativeText, negativeListener, false));
            if (positiveText != null)
                row.addView(actionButton(dialog, positiveText, positiveListener, negativeText != null));
            return row;
        }

        private Button actionButton(ReseamDialog dialog, String text, DialogInterface.OnClickListener listener, boolean spaced) {
            Button button = new Button(dialog.getContext(), null, android.R.attr.borderlessButtonStyle);
            button.setText(text);
            button.setAllCaps(true);
            button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
            button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
            button.setTextColor(themeColor);
            button.setMinWidth(0);
            button.setMinimumWidth(0);
            button.setPadding(dp(12), dp(8), dp(12), dp(8));
            TypedValue ripple = new TypedValue();
            dialog.getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
            if (ripple.resourceId != 0) button.setBackgroundResource(ripple.resourceId);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            if (spaced) params.leftMargin = dp(8);
            button.setLayoutParams(params);
            button.setOnClickListener(ignored -> {
                if (listener != null) listener.onClick(dialog, 0);
            });
            return button;
        }

        private int dp(int dp) {
            float density = context.getResources().getDisplayMetrics().density;
            return Math.round(dp * density);
        }

        private GradientDrawable rounded(int color, int radiusDp) {
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(radiusDp * context.getResources().getDisplayMetrics().density);
            return drawable;
        }

        private LinearLayout vertical(Context context) {
            LinearLayout layout = new LinearLayout(context);
            layout.setOrientation(LinearLayout.VERTICAL);
            return layout;
        }
    }
}
