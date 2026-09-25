package top.madkarma.universal.extensions;

import android.app.Activity;
import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.Iterator;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

@SuppressWarnings("unused")
public final class UpdateChecker {
    private static final String TAG = "UpdateChecker";
    private static final String DEFAULT_REVISIONS_URL = "https://github.com/madkarmaa/reseam-patches/releases/latest/download/revisions.json";
    private static final String APPLIED_ASSET = "reseam/applied.json";

    private static final String PREFS = "reseam_update_checker";
    private static final String KEY_NEVER = "never_show_again";
    private static final String KEY_SNOOZE_UNTIL = "snoozed_until";
    private static final long SNOOZE_MILLIS = TimeUnit.HOURS.toMillis(6);

    private static final int TIMEOUT_MILLIS = 10_000;
    private static final int MAX_BODY_BYTES = 1 << 20;

    private UpdateChecker() {
    }

    public static void check(Context context, String revisionsUrl, String packageName) {
        try {
            Context appCtx = context.getApplicationContext();
            if (!(appCtx instanceof Application app)) {
                Log.w(TAG, "expected application context, got " + appCtx.getClass().getName());
                return;
            }

            if (packageName == null || packageName.isEmpty()) {
                Log.w(TAG, "no package name, skipping check");
                return;
            }

            if (isSuppressed(app)) return;

            String url = revisionsUrl == null || revisionsUrl.isEmpty() ? DEFAULT_REVISIONS_URL : revisionsUrl;
            // The fetch fires on the first activity resume, so background starts never hit the network.
            app.registerActivityLifecycleCallbacks(new Tracker(app, url, packageName));
        } catch (Throwable t) {
            Log.w(TAG, "check failed", t);
        }
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
        private final String revisionsUrl;
        private final String packageName;

        private final AtomicBoolean popupShown = new AtomicBoolean(false);
        private final AtomicBoolean checkStarted = new AtomicBoolean(false);

        private volatile Activity foregroundActivity;
        private volatile boolean updatePending;

        Tracker(Application app, String revisionsUrl, String packageName) {
            this.app = app;
            this.revisionsUrl = revisionsUrl;
            this.packageName = packageName;
        }

        static boolean updateAvailable(JSONObject baked, JSONObject catalog) {
            Iterator<String> ids = baked.keys();
            while (ids.hasNext()) {
                String id = ids.next();
                String bakedRevision = baked.optString(id, null);
                String catalogRevision = catalog.optString(id, null);
                if (catalogRevision == null || !catalogRevision.equals(bakedRevision)) {
                    Log.i(TAG, "update available: " + id + (catalogRevision == null ? " (missing from catalog)" : ""));
                    return true;
                }
            }

            Log.i(TAG, "applied patches are up to date");
            return false;
        }

        private void startUpdateCheckOnce() {
            if (!checkStarted.compareAndSet(false, true)) return;

            Thread thread = new Thread(this::checkForUpdates, TAG);
            thread.setDaemon(true);
            thread.start();
        }

        private void checkForUpdates() {
            try {
                if (!catalogHasUpdate()) return;

                updatePending = true;
                showPopupIfReady();
            } catch (Throwable t) {
                Log.w(TAG, "update check failed", t);
            }
        }

        private boolean catalogHasUpdate() {
            try {
                JSONObject baked = new JSONObject(readAsset(APPLIED_ASSET));
                byte[] body = downloadBody();
                if (body == null) return false;

                JSONObject catalog = new JSONObject(new String(body, StandardCharsets.UTF_8)).optJSONObject("patches");
                if (catalog == null) {
                    Log.w(TAG, "revisions.json has no patches");
                    return false;
                }

                return updateAvailable(baked, catalog);
            } catch (Throwable t) {
                Log.w(TAG, "revisions check failed", t);
                return false;
            }
        }

        private String readAsset(String path) throws java.io.IOException {
            try (InputStream in = app.getAssets().open(path); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                byte[] buffer = new byte[8192];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
                return out.toString(StandardCharsets.UTF_8);
            }
        }

        private byte[] downloadBody() {
            HttpURLConnection conn = null;
            try {
                conn = (HttpURLConnection) new URL(revisionsUrl).openConnection();
                conn.setConnectTimeout(TIMEOUT_MILLIS);
                conn.setReadTimeout(TIMEOUT_MILLIS);
                conn.setRequestProperty("User-Agent", "Mozilla/5.0");
                conn.setInstanceFollowRedirects(true);

                int code = conn.getResponseCode();
                if (code != HttpURLConnection.HTTP_OK) {
                    Log.w(TAG, "revisions.json HTTP " + code);
                    return null;
                }

                try (InputStream in = conn.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
                    byte[] buffer = new byte[8192];
                    int total = 0;
                    int read;

                    while ((read = in.read(buffer)) != -1) {
                        total += read;
                        if (total > MAX_BODY_BYTES) {
                            Log.w(TAG, "revisions.json body too large");
                            return null;
                        }
                        out.write(buffer, 0, read);
                    }

                    return out.toByteArray();
                }
            } catch (Throwable t) {
                Log.w(TAG, "revisions.json fetch failed", t);
                return null;
            } finally {
                if (conn != null) {
                    conn.disconnect();
                }
            }
        }

        private void showPopupIfReady() {
            Activity activity = foregroundActivity;

            if (activity == null || !updatePending || !popupShown.compareAndSet(false, true))
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

                new ReseamDialog.Builder(activity).overtext("Reseam").title("New patchable app version").appIdentity(appIcon(), appLabel(), packageName).negativeButton("NEVER SHOW AGAIN", (dialog, which) -> {
                    prefs(app).edit().putBoolean(KEY_NEVER, true).apply();
                    dialog.dismiss();
                }).positiveButton("OK", (dialog, which) -> {
                    prefs(app).edit().putLong(KEY_SNOOZE_UNTIL, System.currentTimeMillis() + SNOOZE_MILLIS).apply();
                    dialog.dismiss();
                }).show();

                Log.i(TAG, "showing update popup for " + packageName);
            } catch (Throwable t) {
                Log.w(TAG, "popup failed", t);
            }
        }

        private String appLabel() {
            try {
                android.content.pm.PackageManager manager = app.getPackageManager();
                android.content.pm.ApplicationInfo info = manager.getApplicationInfo(packageName, 0);
                return String.valueOf(manager.getApplicationLabel(info));
            } catch (Throwable t) {
                return packageName;
            }
        }

        private android.graphics.drawable.Drawable appIcon() {
            try {
                return app.getPackageManager().getApplicationIcon(packageName);
            } catch (Throwable t) {
                return null;
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

            if (!updatePending) {
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
