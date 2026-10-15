// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

plugins {
    id("app.reseam.patches")
}

dependencies {
    compileOnly(project(":apps:universal:patch"))
}
