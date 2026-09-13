package bin.mt.signature;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Parcel;
import android.os.Parcelable;
import android.util.Base64;
import android.util.Log;

import org.lsposed.hiddenapibypass.HiddenApiBypass;

import java.io.*;
import java.lang.reflect.Field;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Spoofs the app's own signature at runtime (ApkSignatureKillerEx).
 *
 * <ul>
 *   <li>{@link #killSignature} fakes {@link PackageInfo} signatures, for apps
 *   that merely ask the package manager for the signature.</li>
 *   <li>{@link #killApkPath} additionally redirects raw reads of the installed
 *   APK file to the pristine copy embedded as {@value #ORIGIN_ASSET_PATH}, for
 *   apps that verify the APK file itself.</li>
 * </ul>
 */
@SuppressWarnings("unused")
public class SignatureKiller {
    private static final String TAG = "SignatureKiller";
    private static final String LIBRARY_NAME = "SignatureKiller";
    private static final String ORIGIN_ASSET_PATH = "assets/SignatureKiller/origin.apk";
    private static final String ORIGIN_FILE_NAME = "origin.apk";
    private static final String SELF_MAPS_PATH = "/proc/self/maps";
    private static final int COPY_BUFFER_SIZE = 102400;

    private SignatureKiller() {
    }

    /**
     * Fakes PackageManager signatures.
     */
    public static void killSignature(String packageName, String base64Sig) {
        Log.i(TAG, "killSignature: " + packageName + " (sig " + base64Sig.length() + " chars)");
        killPM(packageName, base64Sig);
    }

    /**
     * Redirects reads of the installed APK to the embedded pristine copy.
     * Takes the application context to resolve its data directory.
     */
    public static void killApkPath(Context context, String packageName) {
        Log.i(TAG, "killApkPath: " + packageName + " on " + Build.SUPPORTED_ABIS[0]);
        killOpen(context, packageName);
    }

    /**
     * Logs the signature the app is currently installed with (read here,
     * before any spoofing) next to the spoofed one, and whether they match.
     * Must run before {@link #killSignature}.
     */
    public static void checkSignatures(Context context, String packageName, String base64Sig) {
        String real;
        try {
            Signature[] current = currentSignatures(context, packageName);
            real = current != null && current.length > 0 ? shortSig(current[0].toByteArray()) : "none";
        } catch (PackageManager.NameNotFoundException | RuntimeException e) {
            real = "unavailable (" + e + ")";
        }

        String spoofed = shortSig(Base64.decode(base64Sig, Base64.DEFAULT));
        Log.i(TAG, "checkSignatures: " + packageName + " real=" + real + " spoofed=" + spoofed + (real.equals(spoofed) ? " SAME" : " DIFFERENT"));
    }

    // GET_SIGNATURES is the only API on pre-P devices.
    @SuppressWarnings("deprecation")
    private static Signature[] currentSignatures(Context context, String packageName) throws PackageManager.NameNotFoundException {
        PackageManager pm = context.getPackageManager();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            PackageInfo info = pm.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES);
            return info.signingInfo != null ? info.signingInfo.getApkContentsSigners() : null;
        } else {
            return getLegacySignatures(pm.getPackageInfo(packageName, PackageManager.GET_SIGNATURES));
        }
    }

    private static String shortSig(byte[] cert) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(cert);

            StringBuilder hex = new StringBuilder();
            for (byte b : digest) {
                hex.append(Character.forDigit((b >> 4) & 0xF, 16));
                hex.append(Character.forDigit(b & 0xF, 16));
            }

            return hex.substring(0, 16);
        } catch (NoSuchAlgorithmException e) {
            return "unavailable";
        }
    }

    private static void killPM(String packageName, String signatureData) {
        Signature fakeSignature = new Signature(Base64.decode(signatureData, Base64.DEFAULT));
        Parcelable.Creator<PackageInfo> creator = getCreator(packageName, fakeSignature);

        try {
            findField(PackageInfo.class, "CREATOR").set(null, creator);
        } catch (ReflectiveOperationException | RuntimeException e) {
            throw new RuntimeException(e);
        }
        Log.i(TAG, "killPM: PackageInfo creator spoof installed");

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            HiddenApiBypass.addHiddenApiExemptions("Landroid/os/Parcel;", "Landroid/content/pm", "Landroid/app");
        }

        clearQuietly("package info cache", () -> {
            Object cache = findField(PackageManager.class, "sPackageInfoCache").get(null);
            cache.getClass().getMethod("clear").invoke(cache);
        });

        clearQuietly("parcel creator cache", () -> {
            Map<?, ?> mCreators = (Map<?, ?>) findField(Parcel.class, "mCreators").get(null);
            mCreators.clear();
        });

        clearQuietly("parcel paired-creator cache", () -> {
            Map<?, ?> sPairedCreators = (Map<?, ?>) findField(Parcel.class, "sPairedCreators").get(null);
            sPairedCreators.clear();
        });
    }

    private static Parcelable.Creator<PackageInfo> getCreator(String packageName, Signature fakeSignature) {
        Parcelable.Creator<PackageInfo> originalCreator = PackageInfo.CREATOR;

        return new Parcelable.Creator<>() {
            @Override
            public PackageInfo createFromParcel(Parcel source) {
                PackageInfo packageInfo = originalCreator.createFromParcel(source);

                if (packageInfo.packageName.equals(packageName)) {
                    replaceFirstSignature(getLegacySignatures(packageInfo), fakeSignature);

                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        if (packageInfo.signingInfo != null) {
                            Signature[] signaturesArray = packageInfo.signingInfo.getApkContentsSigners();
                            replaceFirstSignature(signaturesArray, fakeSignature);
                        }
                    }
                }

                return packageInfo;
            }

            @Override
            public PackageInfo[] newArray(int size) {
                return originalCreator.newArray(size);
            }
        };
    }

    /**
     * Reads PackageInfo.signatures reflectively instead of touching the
     * deprecated field, so no deprecation suppression is needed. The field
     * still exists (and is still populated for compatibility) on every API
     * level, which is also what keeps this working on pre-P devices that
     * have no signingInfo.
     */
    private static Signature[] getLegacySignatures(PackageInfo packageInfo) {
        try {
            return (Signature[]) findField(PackageInfo.class, "signatures").get(packageInfo);
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Reading legacy signatures failed", e);
            return null;
        }
    }

    private static void replaceFirstSignature(Signature[] signatures, Signature fakeSignature) {
        if (signatures != null && signatures.length > 0) {
            signatures[0] = fakeSignature;
        }
    }

    private static void clearQuietly(String what, QuietAction action) {
        try {
            action.run();
        } catch (ReflectiveOperationException | RuntimeException e) {
            Log.w(TAG, "Clearing " + what + " failed", e);
        }
    }

    private static Field findField(Class<?> start, String fieldName) throws NoSuchFieldException {
        NoSuchFieldException missing = null;

        for (Class<?> c = start; c != null && c != Object.class; c = c.getSuperclass()) {
            try {
                Field field = c.getDeclaredField(fieldName);
                field.setAccessible(true);
                return field;
            } catch (NoSuchFieldException e) {
                if (missing == null) {
                    missing = e;
                }
            }
        }

        throw missing != null ? missing : new NoSuchFieldException(fieldName);
    }

    private static void killOpen(Context context, String packageName) {
        try {
            System.loadLibrary(LIBRARY_NAME);
        } catch (UnsatisfiedLinkError | RuntimeException e) {
            Log.e(TAG, "Load SignatureKiller library failed", e);
            return;
        }
        Log.i(TAG, "killOpen: native library loaded");

        String apkPath = getApkPath(packageName);
        if (apkPath == null) {
            Log.e(TAG, "Get apk path failed");
            return;
        }
        Log.i(TAG, "killOpen: installed apk at " + apkPath);

        File apkFile = new File(apkPath);
        File repFile;

        try {
            repFile = extractOriginApk(apkFile, context);
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        if (repFile == null) {
            return;
        }
        Log.i(TAG, "killOpen: hooking " + apkFile.getAbsolutePath() + " -> " + repFile.getAbsolutePath() + " (" + repFile.length() + " bytes)");

        hookApkPath(apkFile.getAbsolutePath(), repFile.getAbsolutePath());
    }

    /**
     * Copies the pristine APK embedded in this APK to the app's data
     * directory. Returns the copy, or null when the embedded entry is absent.
     * Uses the authoritative data directory (API 24+; reseam targets 26+)
     * instead of guessing it from storage paths.
     */
    private static File extractOriginApk(File apkFile, Context context) throws IOException {
        File repFile = new File(context.getDataDir(), ORIGIN_FILE_NAME);

        try (ZipFile zipFile = new ZipFile(apkFile)) {
            ZipEntry entry = zipFile.getEntry(ORIGIN_ASSET_PATH);

            if (entry == null) {
                Log.e(TAG, "Entry not found: " + ORIGIN_ASSET_PATH);
                return null;
            }

            if (!repFile.exists() || repFile.length() != entry.getSize()) {
                try (InputStream in = zipFile.getInputStream(entry); OutputStream out = new FileOutputStream(repFile)) {
                    copyStream(in, out);
                }
            }
        }

        return repFile;
    }

    private static void copyStream(InputStream in, OutputStream out) throws IOException {
        byte[] buffer = new byte[COPY_BUFFER_SIZE];
        int read;

        while ((read = in.read(buffer)) != -1) {
            out.write(buffer, 0, read);
        }
    }

    private static String getApkPath(String packageName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(SELF_MAPS_PATH))) {
            String line;

            while ((line = reader.readLine()) != null) {
                String[] columns = line.split("\\s+");
                String path = columns[columns.length - 1];

                if (isApkPath(packageName, path)) {
                    return path;
                }
            }

            return null;
        } catch (IOException | RuntimeException e) {
            throw new RuntimeException(e);
        }
    }

    private static boolean isApkPath(String packageName, String path) {
        if (!path.startsWith("/") || !path.endsWith(".apk")) {
            return false;
        }

        String[] segments = path.substring(1).split("/", 6);
        return switch (segments.length) {
            case 4, 5 ->
                isInstalledBaseApk(packageName, segments) || isAsecPackageApk(packageName, segments);
            case 3 -> isLegacyAppApk(packageName, segments);
            case 6 -> isAdoptedStorageApk(packageName, segments);
            default -> false;
        };
    }

    /**
     * /data/app/&lt;dir&gt;/base.apk, where the dir may carry split suffixes.
     */
    private static boolean isInstalledBaseApk(String packageName, String[] segments) {
        return segments[0].equals("data") && segments[1].equals("app") && segments[segments.length - 1].equals("base.apk") && segments[segments.length - 2].startsWith(packageName);
    }

    /**
     * /mnt/asec/&lt;dir&gt;/pkg.apk (forward-locked apps on ancient releases).
     */
    private static boolean isAsecPackageApk(String packageName, String[] segments) {
        return segments[0].equals("mnt") && segments[1].equals("asec") && segments[segments.length - 1].equals("pkg.apk") && segments[segments.length - 2].startsWith(packageName);
    }

    /**
     * /data/app/&lt;package&gt;.apk (pre-split install layout).
     */
    private static boolean isLegacyAppApk(String packageName, String[] segments) {
        return segments[0].equals("data") && segments[1].equals("app") && segments[2].startsWith(packageName);
    }

    /**
     * /mnt/expand/&lt;uuid&gt;/app/&lt;dir&gt;/base.apk (adopted storage).
     */
    private static boolean isAdoptedStorageApk(String packageName, String[] segments) {
        return segments[0].equals("mnt") && segments[1].equals("expand") && segments[3].equals("app") && segments[5].equals("base.apk") && segments[4].endsWith(packageName);
    }

    private static native void hookApkPath(String apkPath, String repPath);

    /**
     * A reflective cache clear that must never break startup.
     */
    private interface QuietAction {
        void run() throws ReflectiveOperationException;
    }
}
