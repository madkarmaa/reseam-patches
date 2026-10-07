// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.adguard.flm;

import com.adguard.flm.protobuf.StoredFilterMetadata;

import java.util.List;

public interface FlmAdapter {
    Long enableFilterLists(List<Integer> ids, boolean enabled);

    List<StoredFilterMetadata> getStoredFiltersMetadata();
}
