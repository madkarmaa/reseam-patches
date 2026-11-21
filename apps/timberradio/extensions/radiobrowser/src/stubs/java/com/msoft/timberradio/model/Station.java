// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

package com.msoft.timberradio.model;

import java.util.List;

public final class Station {
    public Station(
            int id,
            String stationCode,
            String name,
            String slogan,
            String frequency,
            String band,
            String url,
            String twitterId,
            String logo,
            String location,
            String countryCode,
            String description,
            String email,
            String phone,
            String mailingAddress,
            String language,
            String languageId,
            String genreId,
            String genreName,
            String regionId,
            String regionName,
            String tz,
            String tzOffset,
            String contentClassification,
            String city,
            List listen,
            long insertedAt) {
        throw new RuntimeException("Stub");
    }

    public final String getName() {
        throw new RuntimeException("Stub");
    }

    public final List getListen() {
        throw new RuntimeException("Stub");
    }
}
