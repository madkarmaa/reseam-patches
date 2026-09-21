package top.madkarma.liftoff.extensions;

import java.text.SimpleDateFormat;
import java.util.*;

/**
 * Fabricates the JS-facing "pro" entitlement entries that RevenueCat's
 * backend never sends for an account that never purchased. Keys and shapes
 * mirror {@code EntitlementInfoMapperKt} output, so key lookups, emptiness
 * checks, and field reads all see Pro.
 */
@SuppressWarnings("unused")
public final class ProEntitlements {
    private ProEntitlements() {
    }

    /**
     * Points both the {@code active} and {@code all} maps of an
     * {@code EntitlementInfosMapperKt.map} result at the fabricated entries.
     */
    public static void proActiveEntriesMap(Map<String, Object> result) {
        Map<String, Object> entries = proActiveEntries();
        result.put("active", entries);
        result.put("all", entries);
    }

    /**
     * Entries to merge into the mapped {@code active} object.
     */
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

        Map<String, Object> active = new HashMap<>();

        active.put("pro", pro);
        active.put("premium", pro);

        return active;
    }

    private static String iso8601(long millis) {
        SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US);
        format.setTimeZone(TimeZone.getTimeZone("UTC"));
        return format.format(new Date(millis));
    }
}
