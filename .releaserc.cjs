const plugins = [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    ["@semantic-release/npm", {npmPublish: false}],
    [
        "@semantic-release/exec",
        {
            prepareCmd:
                'export RESEAM_BUNDLE_URL="$RESEAM_HOMEPAGE/releases/download/v${nextRelease.version}/madkarma-patches.reseam" && ./gradlew stageRelease -PreleaseTag=v${nextRelease.version} --no-daemon',
        },
    ],
    [
        "@semantic-release/github",
        {
            assets: ["build/reseam/release/*"],
            successComment: false,
            failComment: false,
        },
    ],
];

if (process.env.GITHUB_REF === "refs/heads/main")
    plugins.splice(4, 0, [
        "@semantic-release/git",
        {
            assets: ["package.json"],
            message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
        },
    ]);

module.exports = {
    branches: ["main", {name: "dev", prerelease: true}],
    plugins,
};
