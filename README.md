# MadKarma Reseam patches

My patch bundle for Android apps I use, built with [Reseam](https://reseam.app).

## Setup

- JDK 17.
- Android SDK, with `ANDROID_HOME` pointing at it.
- The `reseam` CLI on `PATH`, or at the path in `RESEAM_BIN`. Use the release
  matching the plugin version in `settings.gradle.kts`. Releases provide a
  Linux x64 binary; on other
  platforms, [build the CLI from source](https://reseam.app/docs/cli/install/).
- A signing key, created with `reseam bundle keygen --out ~/.reseam/bundle-signing.key`.
  The command prints the public key users will trust. Keep the private key
  secret and out of the repository.

## Build and try the bundle

```shell
./gradlew bundle
```

This writes `build/reseam/madkarma-patches.reseam`. List its patches and try
one on an APK:

```shell
reseam bundle list build/reseam/madkarma-patches.reseam --trust <public key>
reseam patch app.apk \
  --bundle build/reseam/madkarma-patches.reseam \
  --trust <public key> \
  --enable "<patch name>" \
  --output patched.apk
```

Only when changing the engine alongside these patches, set
`RESEAM_WORKSPACE=/path/to/reseam` to use that checkout's SDK, Gradle plugin,
and CLI instead of the published versions.

## Get updates in Reseam Manager

Add this index URL in Reseam Manager:

```text
https://github.com/madkarmaa/reseam-patches/releases/latest/download/patches.json
```

Manager shows the bundle signer's public key before trust is granted. Publish
that key somewhere users can verify independently.

## Releases

Commit messages are checked locally by Husky and Commitlint and must follow
Conventional Commits. Push conventional commits to `dev` for prereleases. To
publish a stable release, manually run the `release` workflow on `main`.

Set the repository secret `BUNDLE_SIGNING_KEY_B64` to the base64-encoded bundle
signing key (for example, `base64 -w0 ~/.reseam/bundle-signing.key`). When
updating Reseam, keep the plugin version in `settings.gradle.kts` and
`ENGINE_VERSION` in `.github/workflows/*.yml` in sync.
