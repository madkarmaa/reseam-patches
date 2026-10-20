// SPDX-FileCopyrightText: 2026 MadKarma <me@madkarma.top>
// SPDX-License-Identifier: AGPL-3.0-or-later

const {execFileSync} = require("node:child_process");
const {writeFileSync} = require("node:fs");

exports.prepare = (_, {branch, nextRelease, cwd, env}) => {
    const options = {cwd, env, stdio: "inherit"};
    execFileSync("just", ["index", nextRelease.gitTag], options);
    writeFileSync(`${cwd}/build/reseam/release-notes.md`, nextRelease.notes);
    if (branch.name === "main") {
        execFileSync("uv", ["run",
            ".github/scripts/update_readme_patches.py",
            "--patches-json", "build/reseam/release/patches.json",
        ], options);
    }
};

exports.publish = (_, {nextRelease, cwd, env}) => {
    execFileSync("just", ["release", nextRelease.gitTag], {cwd, env, stdio: "inherit"});
    return {
        name: "GitHub release",
        url: `${env.RESEAM_HOMEPAGE}/releases/tag/${nextRelease.gitTag}`,
    };
};
