// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.vn.extensions;

import android.content.Context;
import android.util.Log;

@SuppressWarnings("unused")
public final class Prefs {
    private static final String TAG = "Prefs";

    private Prefs() {
    }

    public static void putBoolean(Context context, String store, String key, boolean value) {
        try {
            context.getSharedPreferences(store, Context.MODE_PRIVATE).edit().putBoolean(key, value).apply();
        } catch (Exception exception) {
            Log.e(TAG, "Could not write " + store + "." + key, exception);
        }
    }
}
