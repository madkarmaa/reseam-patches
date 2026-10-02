// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.liftoff.extensions;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Supplies lifetime Pro entries in RevenueCat's React Native mapper format.
 */
@SuppressWarnings("unused")
public final class ProEntitlements {
    private ProEntitlements() {
    }

    /**
     * Points both the {@code active} and {@code all} maps of an
     * {@code EntitlementInfosMapperKt.map} result at the fabricated entries.
     */
    public static void applyProEntitlements(Map<String, Object> result) {
        Map<String, Object> entries = proActiveEntries();
        result.put("active", entries);
        result.put("all", entries);
    }

    private static Map<String, Object> proActiveEntries() {
        long nowMillis = System.currentTimeMillis();
        String nowIso = iso8601(nowMillis);

        Map<String, Object> pro = new HashMap<>();

        pro.put("identifier", "pro");
        pro.put("isActive", Boolean.TRUE);
        pro.put("willRenew", Boolean.FALSE);
        pro.put("periodType", "NORMAL");
        pro.put("latestPurchaseDateMillis", nowMillis);
        pro.put("latestPurchaseDate", nowIso);
        pro.put("originalPurchaseDateMillis", nowMillis);
        pro.put("originalPurchaseDate", nowIso);
        pro.put("expirationDateMillis", null);
        pro.put("expirationDate", null);
        pro.put("store", "PLAY_STORE");
        pro.put("productIdentifier", "pro");
        pro.put("productPlanIdentifier", null);
        pro.put("isSandbox", Boolean.FALSE);
        pro.put("unsubscribeDetectedAt", null);
        pro.put("unsubscribeDetectedAtMillis", null);
        pro.put("billingIssueDetectedAt", null);
        pro.put("billingIssueDetectedAtMillis", null);
        pro.put("ownershipType", "PURCHASED");
        pro.put("verification", "VERIFIED");

        Map<String, Object> entries = new HashMap<>();

        entries.put("pro", pro);
        entries.put("premium", pro);

        return entries;
    }

    private static String iso8601(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }
}
