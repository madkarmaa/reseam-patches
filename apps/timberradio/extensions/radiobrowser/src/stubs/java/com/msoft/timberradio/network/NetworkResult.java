// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.msoft.timberradio.network;

public abstract class NetworkResult {
    public static final class Success extends NetworkResult {
        public Success(Object data) {
            throw new RuntimeException("Stub");
        }

        public final Object getData() {
            throw new RuntimeException("Stub");
        }
    }
}
