// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.vn.extensions;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;

import com.frontrow.common.model.SplashAdvertisement;

@SuppressWarnings("unused")
public final class Pro {
    private Pro() {
    }

    public static void initializePreferences(Context context) {
        Prefs.putBoolean(context, "devices", "KEY_LAST_SHOW_PRO_FEATURE", false);
        Prefs.putBoolean(context, "debug", "ENABLE_SHOW_STORE", false);
        Prefs.putBoolean(context, "debug", "SHOW_AD_ALWAYS", false);
    }

    public static void normalizeStartupConfiguration(Object response) {
        // R8 shares the startup Consumer with its Throwable error path.
        if (!(response instanceof SplashAdvertisement configuration)) return;

        configuration.ShowStore = Boolean.FALSE;
        configuration.ADJSON = "[]";
        configuration.GoogleADEnabled = 0;
    }

    public static final class SkippedPersonalizationActivity extends Activity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            finish();
        }
    }

    public static final class SkippedSubscriptionActivity extends Activity {
        @Override
        protected void onCreate(Bundle state) {
            super.onCreate(state);
            setResult(RESULT_OK);
            finish();
        }
    }
}
