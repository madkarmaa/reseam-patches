// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.maxmpz.audioplayer;

@SuppressWarnings("unused")
public final class Sync {
    public static native void native_acquire(String tag);

    public static native void native_close(int count);

    public static native String native_lock(int index);

    public static native int native_release(Object value, int command);
}
