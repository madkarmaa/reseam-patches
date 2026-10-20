const isStableRelease = process.env.GITHUB_REF === "refs/heads/main";

const plugins = [
    "@semantic-release/commit-analyzer",
    "@semantic-release/release-notes-generator",
    ["@semantic-release/npm", {npmPublish: false}],
    "./.github/scripts/release.cjs",
    ...(isStableRelease ? [
        [
            "@semantic-release/git",
            {
                assets: ["package.json", "README.md"],
                message: "chore(release): ${nextRelease.version} [skip ci]\n\n${nextRelease.notes}",
            },
        ],
    ] : []),
];

module.exports = {
    branches: ["main", {name: "dev", prerelease: true}],
    plugins,
};
