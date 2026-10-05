// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.picsart.ultra;

import android.util.Log;

import com.picsart.payment.api.subscription.tiers.domain.TierType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;

@SuppressWarnings("unused")
public final class Ultra {
    private static boolean logged;

    private Ultra() {
    }

    public static boolean isUserSubscribed() {
        return true;
    }

    public static int accessLevel() {
        // Custom tool gates compare the level against server-configured minimums.
        return Integer.MAX_VALUE;
    }

    public static TierType tier() {
        return TierType.ULTRA;
    }

    public static synchronized List<String> addPremiumPermissions(List<String> original) {
        try {
            LinkedHashSet<String> permissions = new LinkedHashSet<>();

            if (original != null) permissions.addAll(original);

            permissions.add("premium_tools_standard");
            permissions.add("premium_tools_ai");

            if (!logged) {
                Log.i("PicsartUltra", "Local Ultra entitlement applied");
                logged = true;
            }

            return new ArrayList<>(permissions);
        } catch (RuntimeException exception) {
            Log.w("PicsartUltra", "Could not normalize permissions", exception);
            return original;
        }
    }
}
