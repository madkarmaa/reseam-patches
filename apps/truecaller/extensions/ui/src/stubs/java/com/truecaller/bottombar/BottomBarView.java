// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.truecaller.bottombar;

import android.view.Menu;

public final class BottomBarView {
    public Menu getMenu() {
        throw new UnsupportedOperationException("Stub");
    }

    public enum BottomBarMenuItemId {
        MESSAGES, BLOCKING, PREMIUM, FAMILY_PROTECTION_CONFIG;

        public int getId() {
            throw new UnsupportedOperationException("Stub");
        }
    }
}
