// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-FileCopyrightText: 2026 AunAli K. <hello@auna.li>
// SPDX-License-Identifier: GPL-3.0-or-later

package top.madkarma.googlephotos.gms;

import android.Manifest;
import android.accounts.Account;
import android.accounts.OnAccountsUpdateListener;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.*;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import android.widget.Toast;

import java.lang.ref.WeakReference;
import java.util.Arrays;
import java.util.concurrent.Callable;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@SuppressWarnings("unused")
public final class GmsCoreSupport {
    private static final String TAG = "ReseamPhotos";
    private static final int ACCOUNT_PERMISSION_REQUEST = 0x5250;
    private static final String PREFERENCES = "reseam_photos_gms";
    private static final String MEDIA_PERMISSION_HANDLED = "media_permission_handled";
    private static final ExecutorService accountRefreshExecutor = Executors.newSingleThreadExecutor();
    private static final CopyOnWriteArrayList<WeakReference<OnAccountsUpdateListener>> accountsListeners = new CopyOnWriteArrayList<>();
    private static volatile Callable<?> accountAccessRequest;
    private static volatile boolean accountAccessRejected;
    private static volatile WeakReference<Activity> foregroundActivity = new WeakReference<>(null);
    private static WeakReference<Activity> permissionRequestActivity = new WeakReference<>(null);
    private static WeakReference<AlertDialog> activePrompt = new WeakReference<>(null);

    private GmsCoreSupport() {
    }

    public static void initialize(Callable<?> request) {
        accountAccessRequest = request;
    }

    public static ExecutorService getAccountRefreshExecutor() {
        return accountRefreshExecutor;
    }

    public static Context withPackageName(Context context, String packageName) {
        return new ContextWrapper(context) {
            @Override
            public String getPackageName() {
                return packageName;
            }
        };
    }

    public static void check(Activity activity, String packageName, String vendor) {
        foregroundActivity = new WeakReference<>(activity);
        try {
            if (!isInstalled(activity, packageName)) {
                Log.w(TAG, "GmsCore is not installed: " + packageName);
                prompt(activity, "GmsCore is not installed", "Google Photos needs GmsCore (" + packageName + ") to sign in. Install it, " + "open it once and grant its permissions.", "Get GmsCore", () -> open(activity, new Intent(Intent.ACTION_VIEW, Uri.parse("https://github.com/revanced/gmscore/releases/latest"))));
                return;
            }

            if (accountAccessRejected && !hasExtendedAccess(activity, vendor)) {
                if (!isMediaPermissionHandled(activity)) return;

                Log.i(TAG, "GmsCore account access required");
                requestAccountPermission(activity, vendor);
                return;
            }

            PowerManager power = (PowerManager) activity.getSystemService(Context.POWER_SERVICE);
            boolean automotive = activity.getPackageManager().hasSystemFeature(PackageManager.FEATURE_AUTOMOTIVE);

            if (!automotive && power != null && !power.isIgnoringBatteryOptimizations(packageName)) {
                Log.w(TAG, "GmsCore is battery optimized: " + packageName);

                prompt(activity, "GmsCore is battery optimized", "Turn battery optimization off for GmsCore so Google Photos can stay signed in " + "and back up in the background.", "Open settings", () -> {
                    Intent intent = new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS);

                    if (intent.resolveActivity(activity.getPackageManager()) == null)
                        intent = new Intent(Settings.ACTION_SETTINGS);

                    open(activity, intent);
                });

                return;
            }

            Uri provider = Uri.parse("content://" + vendor + ".android.gsf.gservices/prefix");

            try (ContentProviderClient client = activity.getContentResolver().acquireContentProviderClient(provider)) {
                if (client != null) {
                    Log.i(TAG, "GmsCore is available: " + packageName);
                    return;
                }
            }

            Log.w(TAG, "GmsCore provider is unavailable: " + provider);

            prompt(activity, "GmsCore is unavailable", "Open GmsCore and check its permissions and background activity settings.", "Open GmsCore", () -> {
                Intent intent = activity.getPackageManager().getLaunchIntentForPackage(packageName);
                if (intent != null) open(activity, intent);
            });
        } catch (RuntimeException exception) {
            Log.w(TAG, "Cannot check GmsCore availability", exception);
        }
    }

    private static boolean hasExtendedAccess(Context context, String vendor) {
        return context.checkSelfPermission(vendor + ".org.microg.gms.EXTENDED_ACCESS") == PackageManager.PERMISSION_GRANTED;
    }

    public static Account[] readAccounts(Context context, String vendor) throws RemoteException {
        try {
            Account[] accounts = queryAccounts(context, vendor);
            accountAccessRejected = false;
            return accounts;
        } catch (RemoteException exception) {
            if (!isAccountPermissionRejection(exception)) throw exception;

            accountAccessRejected = true;
            Log.i(TAG, "GmsCore rejected account access; extended access is required");

            Activity activity = foregroundActivity.get();
            if (activity != null)
                activity.runOnUiThread(() -> check(activity, vendor + ".android.gms", vendor));

            return new Account[0];
        }
    }

    private static boolean isAccountPermissionRejection(Exception exception) {
        // Photos can wrap the provider's SecurityException in a RemoteException.
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();

            if (message == null) continue;

            if (message.contains("missing google package permission for ACCOUNT") || message.contains("missing google package permission or GET_ACCOUNTS") || message.contains("missing EXTENDED_ACCESS permission"))
                return true;
        }
        return false;
    }

    @SuppressWarnings("deprecation")
    private static Account[] queryAccounts(Context context, String vendor) throws RemoteException {
        String authority = vendor + ".android.gms.auth.accounts";
        try (ContentProviderClient client = context.getContentResolver().acquireUnstableContentProviderClient(authority)) {
            if (client == null)
                throw new RemoteException("The " + authority + " provider is not available.");

            Bundle extras = new Bundle();
            extras.putString("callingActivity", context instanceof Activity ? ((Activity) context).getComponentName().getClassName() : "");

            Bundle response = client.call("get_accounts", vendor, extras);
            if (response == null)
                throw new RemoteException("Null result from the accounts provider");

            Parcelable[] accounts = Build.VERSION.SDK_INT >= 33 ? response.getParcelableArray("accounts", Account.class) : response.getParcelableArray("accounts");
            if (accounts == null)
                throw new RemoteException("The accounts provider returned no accounts array");

            return Arrays.copyOf(accounts, accounts.length, Account[].class);
        } catch (RuntimeException exception) {
            // Preserve Photos' account-query exception contract, including provider permission errors.
            throw new RemoteException("Accounts ContentProvider failed: " + exception.getMessage());
        }
    }

    public static void rememberAccountsListener(OnAccountsUpdateListener listener) {
        for (WeakReference<OnAccountsUpdateListener> reference : accountsListeners) {
            OnAccountsUpdateListener existing = reference.get();

            if (existing == listener) return;
            if (existing == null) accountsListeners.remove(reference);
        }

        accountsListeners.add(new WeakReference<>(listener));
    }

    private static void refreshAccounts(Activity activity, String vendor) {
        accountRefreshExecutor.execute(() -> {
            try {
                Context context = activity.getApplicationContext();

                // The app's own request makes the account visible to this installed package.
                accountAccessRequest.call();

                Account[] accounts = readAccounts(context, vendor);

                activity.runOnUiThread(() -> {
                    if (activity.isFinishing() || activity.isDestroyed()) return;

                    for (WeakReference<OnAccountsUpdateListener> reference : accountsListeners) {
                        OnAccountsUpdateListener listener = reference.get();

                        if (listener == null) {
                            accountsListeners.remove(reference);
                            continue;
                        }

                        listener.onAccountsUpdated(accounts);
                    }

                    Log.i(TAG, "Refreshed GmsCore accounts after permission grant: " + accounts.length);

                    // A recreated activity restores Photos' earlier signed-out onboarding state.
                    Intent launch = activity.getPackageManager().getLaunchIntentForPackage(activity.getPackageName());
                    if (launch == null) {
                        activity.recreate();
                        return;
                    }

                    launch.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
                    activity.startActivity(launch);
                });
            } catch (Exception exception) {
                Log.w(TAG, "Cannot refresh GmsCore accounts", exception);
            }
        });
    }

    private static void requestAccountPermission(Activity activity, String vendor) {
        if (activity.isFinishing() || activity.isDestroyed() || permissionRequestActivity.get() == activity)
            return;

        Log.i(TAG, "Requesting GmsCore account access");
        permissionRequestActivity = new WeakReference<>(activity);
        activity.requestPermissions(new String[]{vendor + ".org.microg.gms.EXTENDED_ACCESS"}, ACCOUNT_PERMISSION_REQUEST);
    }

    /**
     * Handles only our account request; Photos handles all other permission results normally.
     */
    public static boolean onAccountPermissionResult(Activity activity, int requestCode, String[] permissions, int[] results, String vendor) {
        if (requestCode != ACCOUNT_PERMISSION_REQUEST || permissions.length != 1 || !(vendor + ".org.microg.gms.EXTENDED_ACCESS").equals(permissions[0])) {
            for (String permission : permissions) {
                if (!isMediaPermission(permission)) continue;

                activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putBoolean(MEDIA_PERMISSION_HANDLED, true).apply();

                // Let Photos finish its callback before opening another permission request.
                activity.getWindow().getDecorView().post(() -> check(activity, vendor + ".android.gms", vendor));

                break;
            }

            return false;
        }

        permissionRequestActivity = new WeakReference<>(activity);

        if (results.length != 1 || results[0] != PackageManager.PERMISSION_GRANTED) {
            Log.w(TAG, "GmsCore account access denied");
            return true;
        }

        Log.i(TAG, "GmsCore account access granted");
        refreshAccounts(activity, vendor);
        return true;
    }

    private static boolean isMediaPermissionHandled(Context context) {
        if (context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getBoolean(MEDIA_PERMISSION_HANDLED, false))
            return true;

        if (Build.VERSION.SDK_INT < 33)
            return context.checkSelfPermission(Manifest.permission.READ_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;

        return context.checkSelfPermission(Manifest.permission.READ_MEDIA_IMAGES) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_MEDIA_VIDEO) == PackageManager.PERMISSION_GRANTED || context.checkSelfPermission(Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED) == PackageManager.PERMISSION_GRANTED;
    }

    private static boolean isMediaPermission(String permission) {
        return Manifest.permission.READ_EXTERNAL_STORAGE.equals(permission) || Manifest.permission.READ_MEDIA_IMAGES.equals(permission) || Manifest.permission.READ_MEDIA_VIDEO.equals(permission) || Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED.equals(permission);
    }

    private static boolean isInstalled(Context context, String packageName) {
        try {
            context.getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException exception) {
            return false;
        }
    }

    private static void prompt(Activity activity, String title, String message, String action, Runnable onAction) {
        if (activity.isFinishing() || activity.isDestroyed()) return;

        AlertDialog previous = activePrompt.get();
        if (previous != null && previous.isShowing()) return;

        AlertDialog dialog = new AlertDialog.Builder(activity).setTitle(title).setMessage(message).setCancelable(false).setPositiveButton(action, (prompt, which) -> onAction.run()).setNegativeButton("Ignore", (prompt, which) -> prompt.dismiss()).create();

        activePrompt = new WeakReference<>(dialog);
        dialog.show();
    }

    private static void open(Activity activity, Intent intent) {
        try {
            activity.startActivity(intent);
        } catch (ActivityNotFoundException exception) {
            Toast.makeText(activity, "No app is available to open this screen", Toast.LENGTH_LONG).show();
        }
    }

}
