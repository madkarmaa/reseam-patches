const isStableRelease = process.env.GITHUB_REF === "refs/heads/main";

const plugins = [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    ["@semantic-release/npm", {npmPublish: false}],
    [
        "@semantic-release/exec",
        {
            prepareCmd: [
                'export RESEAM_BUNDLE_URL="$RESEAM_HOMEPAGE/releases/download/v${nextRelease.version}/madkarma-patches.reseam"',
                './gradlew stageRelease -PreleaseTag=v${nextRelease.version} --no-daemon',
                isStableRelease
                    ? 'python3 .github/scripts/update_readme_patches.py --patches-json build/reseam/release/patches.json'
                    : null,
            ].filter(Boolean).join(" && "),
        },
    ],
    ...(isStableRelease ? [
        [
            "@semantic-release/git",
            {
                assets: ["package.json", "README.md"],
                message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
            },
        ],
    ] : []),
    [
        "@semantic-release/github",
        {
            assets: ["build/reseam/release/*"],
            successComment: false,
            failComment: false,
        },
    ],
];

module.exports = {
    branches: ["main", {name: "dev", prerelease: true}],
    plugins,
};
