# MadKarma Reseam patches

My patch bundle for Android apps I use, built with [Reseam](https://reseam.app).

## Setup

- JDK 17.
- Android SDK, with `ANDROID_HOME` pointing at it.
- The `reseam` CLI on `PATH`, or at the path in `RESEAM_BIN`. Use the release
  matching the plugin version in `settings.gradle.kts`.
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

## Get updates in Reseam Manager

Add this index URL in Reseam Manager:

```text
https://github.com/madkarmaa/reseam-patches/releases/latest/download/patches.json
```

## Releases

The release workflow generates [GitHub build provenance attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
for `madkarma-patches.reseam`, including stable releases,
prereleases, and manual builds.

After downloading assets from a release, verify them with the GitHub CLI:

```shell
gh attestation verify madkarma-patches.reseam --repo madkarmaa/reseam-patches \
  --signer-workflow madkarmaa/reseam-patches/.github/workflows/release.yml

gh attestation verify patches.json --repo madkarmaa/reseam-patches \
  --signer-workflow madkarmaa/reseam-patches/.github/workflows/release.yml
```
