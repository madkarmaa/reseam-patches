package top.madkarma.universal.extensions;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public final class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String DEFAULT_PATCHES_JSON_URL = "https://github.com/madkarmaa/reseam-patches/releases/latest/download/patches.json";

    private static final String PREFS = "reseam_update_checker";
    private static final String KEY_NEVER = "never_show_again";
    private static final String KEY_SNOOZE_UNTIL = "snoozed_until";
    private static final long SNOOZE_MILLIS = TimeUnit.HOURS.toMillis(6);

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BODY_BYTES = 1 << 20;

    private UpdateChecker() {
    }

    public static void check(Context context, String patchesJsonUrl, String installedVersion, String packageName) {
        try {
            Context appCtx = context.getApplicationContext();
            if (!(appCtx instanceof Application app)) {
                Log.w(TAG, "expected application context, got " + appCtx.getClass().getName());
                return;
            }

            String normalized = normalizeVersion(installedVersion);
            if (normalized == null || normalized.isEmpty()) {
                Log.w(TAG, "no installed version, skipping check");
                return;
            }

            if (packageName == null || packageName.isEmpty()) {
                Log.w(TAG, "no package name, skipping check");
                return;
            }

            if (isSuppressed(app)) return;

            String url = patchesJsonUrl == null || patchesJsonUrl.isEmpty() ? DEFAULT_PATCHES_JSON_URL : patchesJsonUrl;
            // The fetch fires on the first activity resume, so background starts never hit the network.
            app.registerActivityLifecycleCallbacks(new Tracker(app, url, normalized, packageName));
        } catch (Throwable t) {
            Log.w(TAG, "check failed", t);
        }
    }

    static String normalizeVersion(String raw) {
        if (raw == null) return null;
        return raw.split(" - ")[0].trim().split("\\s+")[0];
    }

    private static SharedPreferences prefs(Application app) {
        return app.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    private static boolean isSuppressed(Application app) {
        SharedPreferences prefs = prefs(app);
        return prefs.getBoolean(KEY_NEVER, false) || prefs.getLong(KEY_SNOOZE_UNTIL, 0) > System.currentTimeMillis();
    }

    private static final class Tracker implements Application.ActivityLifecycleCallbacks {
        private final Application app;
        private final String patchesJsonUrl;
        private final String installedVersion;
        private final String packageName;

        private final AtomicBoolean popupShown = new AtomicBoolean(false);
        private final AtomicBoolean checkStarted = new AtomicBoolean(false);

        private volatile Activity foregroundActivity;
        private volatile String pendingVersion;

        Tracker(Application app, String patchesJsonUrl, String installedVersion, String packageName) {
            this.app = app;
            this.patchesJsonUrl = patchesJsonUrl;
            this.installedVersion = installedVersion;
            this.packageName = packageName;
        }

        private void startUpdateCheckOnce() {
            if (!checkStarted.compareAndSet(false, true)) return;

            Thread thread = new Thread(this::checkForUpdates, TAG);
            thread.setDaemon(true);
            thread.start();
        }

        private void checkForUpdates() {
            try {
                if (!isUpdateAvailable()) return;

                pendingVersion = installedVersion;
                showPopupIfReady();
            } catch (Throwable t) {
                Log.w(TAG, "update check failed", t);
            }
        }

        private boolean isUpdateAvailable() {
            try {
                byte[] body = downloadBody();
                if (body == null) return false;

                JSONObject root = new JSONObject(new String(body, StandardCharsets.UTF_8));
                JSONArray patches = latestPatches(root);
                if (patches == null) return false;

                Set<String> supported = declaredVersions(patches);
                if (supported == null) {
                    Log.i(TAG, "no patches entry for " + packageName);
                    return false;
                }
                if (supported.isEmpty()) {
                    Log.i(TAG, "all versions supported for " + packageName);
                    return false;
                }
                if (supported.contains(installedVersion)) {
                    Log.i(TAG, "supported: " + packageName + " " + installedVersion);
                    return false;
                }

                return true;
            } catch (Throwable t) {
                Log.w(TAG, "patches.json fetch failed", t);
                return false;
            }
        }

        private byte[] downloadBody() {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(patchesJsonUrl).openConnection();
                conn.setConnectTimeout(TIMEOUT_MILLIS);
                conn.setReadTimeout(TIMEOUT_MILLIS);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "patches.json HTTP " + code);
                    return null;
                }

                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_BODY_BYTES) {
                            Log.w(TAG, "patches.json body too large");
                            return null;
                        }
                        out.write(buffer, 0, read);
                    }

                    return out.toByteArray();
                }
            } catch (Throwable t) {
                Log.w(TAG, "patches.json fetch failed", t);
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private JSONArray latestPatches(JSONObject root) {
            JSONArray releases = root.optJSONArray("releases");
            if (releases == null || releases.length() == 0) {
                Log.w(TAG, "patches.json has no releases");
                return null;
            }

            JSONArray patches = releases.optJSONObject(0).optJSONArray("patches");
            if (patches == null) {
                Log.w(TAG, "patches.json release has no patches");
            }
            return patches;
        }

        // Null when the package is not declared at all; empty means every version is supported.
        private Set<String> declaredVersions(JSONArray patches) {
            Set<String> supported = new HashSet<>();
            boolean declared = false;

            for (int i = 0; i < patches.length(); i++) {
                JSONObject compat = patches.optJSONObject(i).optJSONObject("compatibility");
                if (compat == null || !"packages".equals(compat.optString("kind", null))) continue;

                JSONArray packages = compat.optJSONArray("packages");
                if (packages == null) continue;

                for (int j = 0; j < packages.length(); j++) {
                    JSONObject entry = packages.optJSONObject(j);
                    if (entry == null || !packageName.equals(entry.optString("package", null)))
                        continue;

                    declared = true;
                    JSONArray versions = entry.optJSONArray("versions");
                    if (versions == null) continue;

                    for (int k = 0; k < versions.length(); k++) {
                        String version = versions.optString(k, null);
                        if (version == null || version.isEmpty()) continue;
                        supported.add(version);
                    }
                }
            }

            return declared ? supported : null;
        }

        private void showPopupIfReady() {
            Activity activity = foregroundActivity;

            if (activity == null || pendingVersion == null || !popupShown.compareAndSet(false, true))
                return;

            try {
                activity.runOnUiThread(() -> showUpdatePopup(activity));
            } catch (Throwable t) {
                Log.w(TAG, "popup post failed", t);
            }
        }

        private void showUpdatePopup(Activity activity) {
            try {
                if (activity.isFinishing()) return;

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN_MR1 && activity.isDestroyed())
                    return;

                String message = "There's a newer version of " + packageName + " available for patching (installed: " + pendingVersion + ").";

                // @formatter:off
                new AlertDialog.Builder(activity)
                        .setTitle("Update available")
                        .setMessage(message)
                        .setCancelable(true)
                        .setPositiveButton("OK", (dialog, which) -> dialog.dismiss())
                        .setNeutralButton("Remind me later", (dialog, which) -> {
                            prefs(app).edit().putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MILLIS).apply();
                            dialog.dismiss();
                        })
                        .setNegativeButton("Never show again", (dialog, which) -> {
                            prefs(app).edit().putBoolean(KEY_NEVER, true).apply();
                            dialog.dismiss();
                        })
                        .show();
                // @formatter:on

                Log.i(TAG, "showing update popup: newer version available for " + packageName + " (installed: " + pendingVersion + ")");
            } catch (Throwable t) {
                Log.w(TAG, "popup failed", t);
            }
        }

        @Override
        public void onActivityCreated(Activity activity, Bundle savedInstanceState) {
        }

        @Override
        public void onActivityStarted(Activity activity) {
        }

        @Override
        public void onActivityResumed(Activity activity) {
            foregroundActivity = activity;

            if (pendingVersion == null) {
                startUpdateCheckOnce();
                return;
            }

            showPopupIfReady();
        }

        @Override
        public void onActivityPaused(Activity activity) {
            if (foregroundActivity != activity) return;
            foregroundActivity = null;
        }

        @Override
        public void onActivityStopped(Activity activity) {
        }

        @Override
        public void onActivitySaveInstanceState(Activity activity, Bundle outState) {
        }

        @Override
        public void onActivityDestroyed(Activity activity) {
            if (foregroundActivity != activity) return;
            foregroundActivity = null;
        }
    }
}
