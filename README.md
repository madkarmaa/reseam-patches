# MadKarma Reseam patches

My patch bundle for Android apps I use, built with [Reseam](https://reseam.app).

## Patches

<!-- PATCHES:START -->

<details>
<summary><strong>bitpit.launcher</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Bug fixes | Fixes various app bugs. | Any version |
| Disable analytics | Refuses and blocks analytics collection. | Any version |
| Skip intro | Skips the promo and terms screens, landing directly on setup. | Any version |
| Unlock Pro | Unlocks Pro-only features. | Any version |
| Weather widget fix | Serves the weather widget from WeatherAPI.com instead of the Niagara backend. Enter your key in the weather settings sheet. | Any version |

</details>

<details>
<summary><strong>com.accuweather.android</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Family Premium+ | Unlocks Premium-only features. | Any version |

</details>

<details>
<summary><strong>com.adguard.android</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Skip setup | Configure the app before installing it. Does NOT include the advanced filters available in the app settings. | Any version |
| Unlock Lifetime premium | Unlocks Premium-only features. | Any version |

</details>

<details>
<summary><strong>com.alltrails.alltrails</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Peak | Unlocks Peak-only features. | Any version |

</details>

<details>
<summary><strong>com.arn.scrobble</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Plus | Unlocks Plus-only features. | Any version |

</details>

<details>
<summary><strong>com.frontrow.vlog</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features. | Any version |

</details>

<details>
<summary><strong>com.gymbros.app</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features, including shop items. | Any version |

</details>

<details>
<summary><strong>com.shahzaman.pricetracker</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Disable ads | Disables in-app ads | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3 |
| Skip onboarding | Skips the setup screens, landing directly on home. | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3 |
| Unlock Lifetime Premium | Unlocks Premium-only features. | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3 |

</details>

<details>
<summary><strong>com.yazio.android</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features. | Any version |

</details>

<details>
<summary><strong>cz.seznam.mapy</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Unlock Premium | Unlocks Premium-only features. | Any version |

</details>

<details>
<summary><strong>Universal</strong></summary>

| Name | Description | Supported versions |
| --- | --- | --- |
| Bypass signature checks | Spoofs the original app signature (port of ApkSignatureKillerEx). | Any version |
| Force extract native libs | Sets android:extractNativeLibs to true when it is false. | Any version |

</details>

<!-- PATCHES:END -->

## Get updates in Reseam Manager

Add this index URL in Reseam Manager:

```text
https://github.com/madkarmaa/reseam-patches/releases/latest/download/patches.json
```

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

## Releases

The release workflow
generates [GitHub build provenance attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
for `madkarma-patches.reseam`, including stable releases,
prereleases, and manual builds.

After downloading assets from a release, you can verify them with the GitHub CLI:

```shell
gh attestation verify madkarma-patches.reseam --repo madkarmaa/reseam-patches \
  --signer-workflow madkarmaa/reseam-patches/.github/workflows/release.yml

gh attestation verify patches.json --repo madkarmaa/reseam-patches \
  --signer-workflow madkarmaa/reseam-patches/.github/workflows/release.yml
```
