// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.ss.android.deviceregister;

@SuppressWarnings("unused")
public interface OnResetListener {
    void onDidUpdate(String deviceId, String installId);

    void onTimeout();
}
