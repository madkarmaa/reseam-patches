// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

@file:Suppress("unused")

package top.madkarma.extensions.adguard

import android.content.Context
import android.util.Log
import app.reseam.runtime.Export
import com.adguard.flm.FlmAdapter

class AdGuardSetup
private constructor(private val context: Context, private val adapter: FlmAdapter) {
    private var filterSetupFailed = false

    private fun prefs() = context.getSharedPreferences("base", Context.MODE_PRIVATE)

    @Export
    fun needsSetup(): Boolean = !prefs().getBoolean(SETUP_DONE, false)

    @Export
    fun approvedAnnoyanceFilters(groupCode: Int): Set<Int> {
        val approved = HashSet<Int>()
        val filters = adapter.storedFiltersMetadata ?: return approved
        for (filter in filters) if (filter.group_id == groupCode) approved.add(filter.id)
        return approved
    }

    @SuppressWarnings("UseKtx")
    private fun setBooleanPreference(key: String, value: Boolean) {
        try {
            prefs().edit().putBoolean(key, value).apply()
        } catch (exception: Exception) {
            Log.e(TAG, "Could not write setup preference $key", exception)
        }
    }

    @Export
    fun acceptTerms() = setBooleanPreference("privacy_policy", true)

    @Export
    fun disableAutomaticCrashReporting() = setBooleanPreference("automatic_crash_reporting", false)

    @Export
    fun disableUsageTelemetry() = setBooleanPreference("technical_and_interaction_data", false)

    @Export
    fun markFirstOnboardingShown() = setBooleanPreference("onboarding_first_shown", true)

    @Export
    fun markSecondOnboardingShown() = setBooleanPreference("onboarding_second_shown", true)

    @Export
    fun setNotificationPromptEnabled(enabled: Boolean) =
        setBooleanPreference("notifications_dialog_should_be_shown", enabled)

    @Export
    fun setAnnoyanceBlockingEnabled(enabled: Boolean) =
        setBooleanPreference("annoyances_enabled", enabled)

    @Export
    fun setBrowsingSecurityEnabled(enabled: Boolean) =
        setBooleanPreference("browsing_security_enabled", enabled)

    @Export
    fun setPrivacyProtectionEnabled(enabled: Boolean) =
        setBooleanPreference("stealth_mode_enabled", enabled)

    private fun setFilterEnabled(filterId: Int, enabled: Boolean) {
        if (filterSetupFailed) return
        try {
            if (adapter.enableFilterLists(
                    listOf(filterId),
                    enabled,
                ) == null
            ) throw IllegalStateException("Filter database did not accept setup")
        } catch (exception: Exception) {
            filterSetupFailed = true
            Log.e(TAG, "Could not configure filter $filterId", exception)
        }
    }

    @Export
    fun setSearchAdsBlockingEnabled(enabled: Boolean) = setFilterEnabled(10, !enabled)

    @Export
    fun setSocialMediaFilterEnabled(enabled: Boolean) = setFilterEnabled(4, enabled)

    @Export
    fun setCookieNoticesFilterEnabled(enabled: Boolean) = setFilterEnabled(18, enabled)

    @Export
    fun setPopupsFilterEnabled(enabled: Boolean) = setFilterEnabled(19, enabled)

    @Export
    fun setMobileAppBannersFilterEnabled(enabled: Boolean) = setFilterEnabled(20, enabled)

    @Export
    fun setOtherAnnoyancesFilterEnabled(enabled: Boolean) = setFilterEnabled(21, enabled)

    @Export
    fun setWidgetsFilterEnabled(enabled: Boolean) = setFilterEnabled(22, enabled)

    @SuppressWarnings("UseKtx")
    @Export
    fun markSetupCompleted() {
        if (filterSetupFailed) return
        try {
            prefs().edit().putBoolean(SETUP_DONE, true).apply()
            Log.i(TAG, "Setup completed with selected protection options")
        } catch (exception: Exception) {
            Log.e(TAG, "Could not complete setup", exception)
        }
    }

    companion object {
        private const val TAG = "ReseamAdGuard"
        private const val SETUP_DONE = "reseam_setup_completed"

        @Export
        @JvmStatic
        fun create(context: Context, adapter: FlmAdapter): AdGuardSetup =
            AdGuardSetup(context, adapter)
    }
}
