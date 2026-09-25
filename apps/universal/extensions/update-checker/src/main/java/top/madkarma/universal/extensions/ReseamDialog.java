package top.madkarma.universal.extensions;

import android.app.Dialog;
import android.content.Context;
import android.content.DialogInterface;
import android.graphics.Color;
import android.graphics.Outline;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
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
    // Manager ReseamDarkColors: surface, mutedElevated, foreground, mutedForeground, primary.
    static final int SURFACE = 0xFF1A1A1A;
    static final int CHIP = 0xFF242424;
    static final int FOREGROUND = 0xFFEDEDED;
    static final int MUTED_FOREGROUND = 0xFFA3A3A3;
    static final int PRIMARY = 0xFFB6F0CF;

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

    private final Context context;
    private final Builder options;

    public ReseamDialog(Context context) {
        this(context, new Builder(context));
    }

    private ReseamDialog(Context context, Builder options) {
        super(context);
        this.context = getContext();
        this.options = options;
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(content());
        setCancelable(true);
        setCanceledOnTouchOutside(true);
    }

    @Override
    public void show() {
        super.show();
        Window window = getWindow();
        if (window == null) return;
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    /**
     * Loads the native vector resources installed by the update-checker patch.
     */
    public Drawable logo() {
        return drawable("reseam_logo");
    }

    public Drawable arrow() {
        return arrow(MUTED_FOREGROUND);
    }

    public Drawable arrow(int color) {
        Drawable arrow = drawable("reseam_arrow");
        arrow.setTint(color);
        return arrow;
    }

    private Drawable drawable(String name) {
        int resourceId = context.getResources().getIdentifier(name, "drawable", context.getPackageName());
        return context.getDrawable(resourceId).mutate();
    }

    private LinearLayout content() {
        LinearLayout root = vertical();
        root.setPadding(dp(PADDING_DP), dp(PADDING_DP), dp(PADDING_DP), dp(BOTTOM_PADDING_DP));
        root.setBackgroundDrawable(rounded(SURFACE, CARD_RADIUS_DP));

        LinearLayout textSection = textSection();
        if (textSection.getChildCount() > 0) addSpaced(root, textSection, SECTION_GAP_DP);
        if (options.oldVersion != null && options.newVersion != null)
            addSpaced(root, versionsRow(), SECTION_GAP_DP);
        if (options.negativeText != null || options.positiveText != null)
            addSpaced(root, actionsRow(), SECTION_GAP_DP);

        LinearLayout frame = vertical();
        frame.setPadding(dp(PADDING_DP), 0, dp(PADDING_DP), 0);
        frame.addView(root, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        return frame;
    }

    private LinearLayout textSection() {
        LinearLayout section = vertical();
        if (options.overtext != null) addSpaced(section, overtextRow(), TEXT_GAP_DP);
        if (options.title != null) addSpaced(section, titleView(options.title), TEXT_GAP_DP);
        if (options.appTitle != null) addWithTopMargin(section, appRow(), APP_BLOCK_GAP_DP);
        if (options.description != null) addSpaced(section, descriptionView(), TEXT_GAP_DP);
        return section;
    }

    private void addSpaced(LinearLayout parent, View view, int gapDp) {
        addWithTopMargin(parent, view, parent.getChildCount() == 0 ? 0 : gapDp);
    }

    private void addWithTopMargin(LinearLayout parent, View view, int marginDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(marginDp);
        parent.addView(view, params);
    }

    private LinearLayout overtextRow() {
        LinearLayout row = horizontal();

        if (options.overtextIcon != null) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(options.overtextIcon);
            row.addView(icon, new LinearLayout.LayoutParams(dp(OVERTEXT_ICON_DP), dp(OVERTEXT_ICON_DP)));
        }

        TextView label = textView(options.overtext, 14, 0xA3FFFFFF);
        label.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        LinearLayout.LayoutParams params = wrapped();
        if (options.overtextIcon != null) params.leftMargin = dp(OVERTEXT_GAP_DP);
        row.addView(label, params);
        return row;
    }

    private TextView titleView(String text) {
        TextView title = textView(text, 18, Color.WHITE);
        title.setTypeface(Typeface.create("sans-serif-medium", Typeface.BOLD));
        return title;
    }

    private TextView descriptionView() {
        TextView description = textView(options.description, 14, FOREGROUND);
        description.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return description;
    }

    private LinearLayout appRow() {
        LinearLayout row = horizontal();

        if (options.appIcon != null) {
            ImageView icon = new ImageView(context);
            icon.setImageDrawable(options.appIcon);
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

        LinearLayout texts = vertical();
        texts.addView(titleView(options.appTitle));
        if (options.appSubtitle != null)
            texts.addView(textView(options.appSubtitle, 14, MUTED_FOREGROUND));

        LinearLayout.LayoutParams params = wrapped();
        if (options.appIcon != null) params.leftMargin = dp(APP_TEXT_GAP_DP);
        row.addView(texts, params);
        return row;
    }

    private LinearLayout versionsRow() {
        LinearLayout row = horizontal();
        row.addView(versionChip(options.oldVersion, Typeface.DEFAULT), wrapped());
        row.addView(arrowView());
        row.addView(versionChip(options.newVersion, Typeface.create("sans-serif-medium", Typeface.BOLD)), weighted());
        return row;
    }

    private LinearLayout.LayoutParams wrapped() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private LinearLayout.LayoutParams weighted() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    private TextView versionChip(String version, Typeface typeface) {
        TextView chip = textView(version, 18, Color.WHITE);
        chip.setTypeface(typeface);
        chip.setGravity(Gravity.CENTER);
        chip.setPadding(dp(16), dp(14), dp(16), dp(14));
        chip.setBackgroundDrawable(rounded(CHIP, CHIP_RADIUS_DP));
        return chip;
    }

    private ImageView arrowView() {
        ImageView arrow = new ImageView(context);
        arrow.setImageDrawable(arrow());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(24), dp(24));
        params.leftMargin = dp(8);
        params.rightMargin = dp(8);
        arrow.setLayoutParams(params);
        return arrow;
    }

    private LinearLayout actionsRow() {
        LinearLayout row = horizontal();
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        if (options.negativeText != null)
            row.addView(actionButton(options.negativeText, options.negativeListener, false));
        if (options.positiveText != null)
            row.addView(actionButton(options.positiveText, options.positiveListener, options.negativeText != null));
        return row;
    }

    private Button actionButton(String text, DialogInterface.OnClickListener listener, boolean spaced) {
        Button button = new Button(context, null, android.R.attr.borderlessButtonStyle);
        button.setText(text);
        button.setAllCaps(true);
        button.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        button.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        button.setTextColor(options.themeColor);
        button.setMinWidth(0);
        button.setMinimumWidth(0);
        button.setPadding(dp(12), dp(8), dp(12), dp(8));
        TypedValue ripple = new TypedValue();
        context.getTheme().resolveAttribute(android.R.attr.selectableItemBackgroundBorderless, ripple, true);
        if (ripple.resourceId != 0) button.setBackgroundResource(ripple.resourceId);
        LinearLayout.LayoutParams params = wrapped();
        if (spaced) params.leftMargin = dp(8);
        button.setLayoutParams(params);
        button.setOnClickListener(ignored -> {
            if (listener != null) listener.onClick(this, 0);
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

    private TextView textView(CharSequence text, int sizeSp, int color) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sizeSp);
        view.setTextColor(color);
        return view;
    }

    private LinearLayout horizontal() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private LinearLayout vertical() {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
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
            ReseamDialog dialog = new ReseamDialog(context, this);
            dialog.show();
            return dialog;
        }
    }
}
