package top.madkarma.droplert.extensions;

import android.content.Context;
import android.util.Log;

import androidx.datastore.preferences.PreferencesProto;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Overrides DataStore preferences before the app reads them. Runs from the
 * application entry point, before any app code opens the DataStore files.
 */
@SuppressWarnings("unused")
public final class Prefs {
    private static final String TAG = "Prefs";
    private static final String[] STORES = {"user_preferences"};

    private Prefs() {
    }

    public static void putBoolean(Context context, String key, boolean value) {
        File dir = new File(context.getFilesDir(), "datastore");

        if (!dir.isDirectory() && !dir.mkdirs()) {
            Log.w(TAG, "datastore dir unavailable");
            return;
        }

        PreferencesProto.Value entry = PreferencesProto.Value.newBuilder().setBoolean(value).build();

        for (String store : STORES) {
            mergeKey(new File(dir, store + ".preferences_pb"), key, entry);
        }
    }

    private static void mergeKey(File file, String key, PreferencesProto.Value value) {
        try {
            PreferencesProto.PreferenceMap.Builder prefs;

            if (file.isFile()) {
                prefs = PreferencesProto.PreferenceMap.parseFrom(readAll(file)).toBuilder();
            } else {
                prefs = PreferencesProto.PreferenceMap.newBuilder();
            }

            prefs.putPreferences(key, value);

            byte[] data = prefs.build().toByteArray();

            try (FileOutputStream stream = new FileOutputStream(file)) {
                stream.write(data);
                stream.getFD().sync();
            }
        } catch (IOException e) {
            Log.w(TAG, "merge into " + file.getName() + " failed, leaving file untouched", e);
        }
    }

    private static byte[] readAll(File file) throws IOException {
        long length = file.length();

        if (length > 1 << 20) {
            throw new IOException("preferences file implausibly large: " + length);
        }

        try (FileInputStream stream = new FileInputStream(file)) {
            byte[] data = new byte[(int) length];
            int filled = 0;

            while (filled < data.length) {
                int read = stream.read(data, filled, data.length - filled);

                if (read < 0) {
                    break;
                }

                filled += read;
            }

            if (filled != data.length) {
                throw new IOException("short read of " + file.getName());
            }

            return data;
        }
    }
}
