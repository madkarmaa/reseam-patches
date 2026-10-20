# SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
# SPDX-FileCopyrightText: 2026 Cossale <hello@auna.li>
# SPDX-License-Identifier: AGPL-3.0-or-later

export GRADLE_OPTS := "-Xmx1g"
export RESEAM_BIN := justfile_directory() / "build/engine/reseam"
export RESEAM_BUNDLE_KEY := justfile_directory() / "build/bundle-signing.key"

gradle := "./gradlew --no-daemon -Dorg.gradle.jvmargs=-Xmx1536m"
engine := `sed -n 's/.*id("app.reseam.workspace") version "\(.*\)".*/\1/p' settings.gradle.kts`
bundle := "build/reseam/madkarma-patches.reseam"

cli:
    test -x "$RESEAM_BIN" || curl -fsSL --create-dirs -o "$RESEAM_BIN" https://cdn.reseam.app/engine/v{{ engine }}/reseam-linux-x64
    chmod +x "$RESEAM_BIN"

check: cli
    test -f "$RESEAM_BUNDLE_KEY" || "$RESEAM_BIN" bundle keygen --out "$RESEAM_BUNDLE_KEY"
    {{ gradle }} bundle

bundle tag: cli
    test -n "$BUNDLE_SIGNING_KEY_B64"
    printf '%s' "$BUNDLE_SIGNING_KEY_B64" | base64 -d > "$RESEAM_BUNDLE_KEY"
    {{ gradle }} bundle "-PreleaseTag={{ tag }}"

index tag="v0.0.0-latest-check": (bundle tag)
    "$RESEAM_BIN" release patches {{ bundle }} --tag {{ tag }} --to build/reseam/release \
        --public-url "$RESEAM_HOMEPAGE/releases/download" --homepage "$RESEAM_HOMEPAGE" --notes /dev/null

release tag: (bundle tag)
    "$RESEAM_BIN" release patches {{ bundle }} --tag {{ tag }} --homepage "$RESEAM_HOMEPAGE" \
        --notes build/reseam/release-notes.md
