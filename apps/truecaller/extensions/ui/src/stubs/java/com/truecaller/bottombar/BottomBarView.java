// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.truecaller.bottombar;

import android.content.Context;
import android.view.Menu;
import android.widget.FrameLayout;

public final class BottomBarView extends FrameLayout {
    public BottomBarView(Context context) {
        super(context);
        throw new UnsupportedOperationException("Stub");
    }

    public Menu getMenu() {
        throw new UnsupportedOperationException("Stub");
    }

    public enum BottomBarMenuItemId {
        MESSAGES,
        BLOCKING,
        PREMIUM,
        FAMILY_PROTECTION_CONFIG;

        public int getId() {
            throw new UnsupportedOperationException("Stub");
        }
    }
}
