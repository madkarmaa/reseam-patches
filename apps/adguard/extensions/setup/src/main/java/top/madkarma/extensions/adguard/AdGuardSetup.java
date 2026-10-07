// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.extensions.adguard;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import com.adguard.flm.FlmAdapter;
import com.adguard.flm.protobuf.StoredFilterMetadata;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@SuppressWarnings("unused")
public final class AdGuardSetup {
    private static final String TAG = "ReseamAdGuard";
    private static final String SETUP_DONE = "reseam_setup_completed";
    private final Context context;
    private final FlmAdapter adapter;
    private boolean filterSetupFailed;

    private AdGuardSetup(Context context, FlmAdapter adapter) {
        this.context = context;
        this.adapter = adapter;
    }

    public static AdGuardSetup create(Context context, FlmAdapter adapter) {
        return new AdGuardSetup(context, adapter);
    }

    private SharedPreferences prefs() {
        return context.getSharedPreferences("base", Context.MODE_PRIVATE);
    }

    public boolean needsSetup() {
        return !prefs().getBoolean(SETUP_DONE, false);
    }

    public Set<Integer> approvedAnnoyanceFilters(int groupCode) {
        Set<Integer> approved = new HashSet<>();

        List<StoredFilterMetadata> filters = adapter.getStoredFiltersMetadata();
        if (filters == null) return approved;

        for (StoredFilterMetadata filter : filters)
            if (filter.getGroup_id() == groupCode) approved.add(filter.getId());

        return approved;
    }

    private void setBooleanPreference(String key, boolean value) {
        try {
            prefs().edit().putBoolean(key, value).apply();
        } catch (Exception exception) {
            Log.e(TAG, "Could not write setup preference " + key, exception);
        }
    }

    public void acceptTerms() {
        setBooleanPreference("privacy_policy", true);
    }

    public void disableAutomaticCrashReporting() {
        setBooleanPreference("automatic_crash_reporting", false);
    }

    public void disableUsageTelemetry() {
        setBooleanPreference("technical_and_interaction_data", false);
    }

    public void markFirstOnboardingShown() {
        setBooleanPreference("onboarding_first_shown", true);
    }

    public void markSecondOnboardingShown() {
        setBooleanPreference("onboarding_second_shown", true);
    }

    public void setNotificationPromptEnabled(boolean enabled) {
        setBooleanPreference("notifications_dialog_should_be_shown", enabled);
    }

    public void setAnnoyanceBlockingEnabled(boolean enabled) {
        setBooleanPreference("annoyances_enabled", enabled);
    }

    public void setBrowsingSecurityEnabled(boolean enabled) {
        setBooleanPreference("browsing_security_enabled", enabled);
    }

    public void setPrivacyProtectionEnabled(boolean enabled) {
        setBooleanPreference("stealth_mode_enabled", enabled);
    }

    private void setFilterEnabled(int filterId, boolean enabled) {
        if (filterSetupFailed) return;

        try {
            if (adapter.enableFilterLists(Collections.singletonList(filterId), enabled) == null)
                throw new IllegalStateException("Filter database did not accept setup");
        } catch (Exception exception) {
            filterSetupFailed = true;
            Log.e(TAG, "Could not configure filter " + filterId, exception);
        }
    }

    public void setSearchAdsBlockingEnabled(boolean enabled) {
        // The exceptions filter allows search ads, so blocking disables it.
        setFilterEnabled(10, !enabled);
    }

    public void setSocialMediaFilterEnabled(boolean enabled) {
        setFilterEnabled(4, enabled);
    }

    public void setCookieNoticesFilterEnabled(boolean enabled) {
        setFilterEnabled(18, enabled);
    }

    public void setPopupsFilterEnabled(boolean enabled) {
        setFilterEnabled(19, enabled);
    }

    public void setMobileAppBannersFilterEnabled(boolean enabled) {
        setFilterEnabled(20, enabled);
    }

    public void setOtherAnnoyancesFilterEnabled(boolean enabled) {
        setFilterEnabled(21, enabled);
    }

    public void setWidgetsFilterEnabled(boolean enabled) {
        setFilterEnabled(22, enabled);
    }

    public void markSetupCompleted() {
        if (filterSetupFailed) return;

        try {
            prefs().edit().putBoolean(SETUP_DONE, true).apply();
            Log.i(TAG, "Setup completed with selected protection options");
        } catch (Exception exception) {
            Log.e(TAG, "Could not complete setup", exception);
        }
    }
}
