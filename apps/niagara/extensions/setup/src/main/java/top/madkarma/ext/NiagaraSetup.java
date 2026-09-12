package top.madkarma.ext;

import android.content.Context;
import android.content.SharedPreferences;

@SuppressWarnings("unused")
public final class NiagaraSetup {
    private static final String PREFS = "bitpit.launcher_preferences";
    private static final String KEY_BLOCK = "bitpit.launcher.key.ONBOARDING_BLOCK";
    private static final String KEY_TERMS_VERSION = "bitpit.launcher.key.TERMS_ACCEPTED_VERSION";
    private static final String KEY_ANALYTICS_SELECTION = "bitpit.launcher.key.ONBOARDING_ANALYTICS_SELECTION";
    private static final String KEY_MARKETING_ANALYTICS_ENABLED = "bitpit.launcher.key.MARKETING_ANALYTICS_ENABLED";
    private static final String KEY_ANALYTICS = "bitpit.launcher.key.ANALYTICS";

    private static final String BLOCK_SELECT_FAVORITES = "select_favorites";
    private static final int TERMS_VERSION = 2;
    // Mirrors tapping Refuse.
    private static final String ANALYTICS_REFUSAL = "{\"allowMarketingAnalytics\":false,\"allowFunctionalAnalytics\":false}";

    private NiagaraSetup() {
    }

    private static SharedPreferences prefs(Context context) {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /**
     * Jumps the onboarding flow straight to the favorites setup step.
     */
    public static void skipToFavoritesSetup(Context context) {
        prefs(context).edit().putString(KEY_BLOCK, BLOCK_SELECT_FAVORITES).apply();
    }

    /**
     * Marks the terms as accepted (mirrors the post-consent state).
     */
    public static void acceptTerms(Context context) {
        prefs(context).edit().putInt(KEY_TERMS_VERSION, TERMS_VERSION).apply();
    }

    /**
     * Answers the analytics privacy prompt with refusal (mirrors Ablehnen / Refuse).
     */
    public static void refuseAnalytics(Context context) {
        prefs(context).edit().putString(KEY_ANALYTICS_SELECTION, ANALYTICS_REFUSAL).apply();
    }

    /**
     * Forces the analytics feature flags off.
     */
    public static void disableAnalyticsFlags(Context context) {
        prefs(context).edit()
            .putBoolean(KEY_MARKETING_ANALYTICS_ENABLED, false)
            .putBoolean(KEY_ANALYTICS, false)
            .apply();
    }
}
