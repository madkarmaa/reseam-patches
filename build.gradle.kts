// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

// Ship the license texts and notices inside the signed bundle.
tasks.named<Sync>("stageBundle") {
    from(layout.projectDirectory.file("LICENSE")) { into("resources/legal") }
    from(layout.projectDirectory.file("NOTICE")) { into("resources/legal") }
    from(layout.projectDirectory.dir("LICENSES")) { into("resources/legal/LICENSES") }
}
