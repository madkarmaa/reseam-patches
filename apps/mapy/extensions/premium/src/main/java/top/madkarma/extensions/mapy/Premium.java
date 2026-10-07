// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package top.madkarma.extensions.mapy;

import android.util.Log;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;

@SuppressWarnings("unused")
public final class Premium {
    private Premium() {
    }

    public static void unlockFeatures(Object features, Object offlineMaps, Object featureAccess) {
        try {
            for (Field field : features.getClass().getDeclaredFields()) {
                if (Modifier.isStatic(field.getModifiers())) continue;

                Class<?> type = field.getType();
                Object value;

                if (type == boolean.class) value = Boolean.TRUE;
                else if (type.isInstance(offlineMaps)) value = offlineMaps;
                else if (type.isInstance(featureAccess)) value = featureAccess;
                else continue;

                field.setAccessible(true);
                field.set(features, value);
            }
        } catch (ReflectiveOperationException | RuntimeException exception) {
            Log.w("ReseamMapy", "Could not normalize Premium features", exception);
        }
    }

    public static Object enabled() {
        return Boolean.TRUE;
    }

    public static String premiumTitle(String text, String attribution) {
        if ("Mapy.com Premium".equals(text) || "Mapy.com פרימיום".equals(text)) return attribution;
        return text;
    }
}
