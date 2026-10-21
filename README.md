# MadKarma Reseam patches

My patch bundle for Android apps I use, built with [Reseam](https://reseam.app).

[![Add to Reseam Manager](https://img.shields.io/badge/Add_to_Reseam_Manager-51AE79?style=for-the-badge&logo=data%3Aimage%2Fsvg%2Bxml%3Bbase64%2CPHN2ZyB4bWxucz0iaHR0cDovL3d3dy53My5vcmcvMjAwMC9zdmciIHZpZXdCb3g9IjAgMCAyNCAyNCI%2BPHBhdGggZmlsbD0id2hpdGUiIGQ9Ik0xMC41IDNoM3Y3LjVIMjF2M2gtNy41VjIxaC0zdi03LjVIM3YtM2g3LjV6Ii8%2BPC9zdmc%2B)](https://reseam.app/add/?url=https://github.com/madkarmaa/reseam-patches)

## Patches

<!-- PATCHES:START -->

<details>
<summary><strong>bitpit.launcher</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Bug fixes | Fixes app bugs. | None |
| Disable analytics | Refuses and blocks analytics collection. | None |
| Skip intro | Skips the promo and terms screens and opens favorites setup. | None |
| Unlock Pro | Unlocks Pro-only features. | None |
| Weather widget fix | Replaces the Niagara weather service with WeatherAPI.com. Enter your API key in the weather settings. | None |

</details>

<details>
<summary><strong>com.accuweather.android</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Family Premium+ | Unlocks Premium-only features. | None |

</details>

<details>
<summary><strong>com.adguard.android</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Skip setup | Skips onboarding and applies the selected setup options on first startup. Configure advanced filters in the app settings. | None |
| Unlock Lifetime premium | Unlocks Premium-only features. | None |

</details>

<details>
<summary><strong>com.alltrails.alltrails</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Peak | Unlocks Peak-only features. | None |

</details>

<details>
<summary><strong>com.arn.scrobble</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Plus | Unlocks Plus-only features. | None |

</details>

<details>
<summary><strong>com.cloudflare.onedotonedotonedotone</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock WARP+ Unlimited | Unlocks the local WARP+ Unlimited account state. Does not unlock server-side WARP+. | None |

</details>

<details>
<summary><strong>com.frontrow.vlog</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features. | None |

</details>

<details>
<summary><strong>com.google.android.apps.photos</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| GmsCore support | Installs Google Photos alongside the original app. Uses a separate Reseam GmsCore installation for sign-in. | None |
| Unlimited backup storage | Reports a Pixel XL device identity to request Google's unlimited backup storage offer. | None |

</details>

<details>
<summary><strong>com.gymbros.app</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features, including shop items. | None |

</details>

<details>
<summary><strong>com.paypal.android.p2pmobile</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Disable startup checks | Prevents local integrity and emulator checks from crashing the app at startup. | None |

</details>

<details>
<summary><strong>com.picsart.studio</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Remove annoyances | Removes promotional popups from the app. | None |
| Unlock Ultra | Unlocks Ultra-only features. Server credit balances remain unchanged. | None |

</details>

<details>
<summary><strong>com.shahzaman.pricetracker</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Disable ads | Disables in-app ads and their consent prompt. | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3, 2.5.4 |
| Skip onboarding | Skips the setup screens and opens the home screen. | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3, 2.5.4 |
| Unlock Lifetime Premium | Unlocks Premium-only features. | 2.2.1, 2.4.0, 2.4.1, 2.5.0, 2.5.1, 2.5.2, 2.5.3, 2.5.4 |

</details>

<details>
<summary><strong>com.yazio.android</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Pro | Unlocks Pro-only features. | None |

</details>

<details>
<summary><strong>cz.seznam.mapy</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Premium | Unlocks Premium-only features. | None |

</details>

<details>
<summary><strong>ginlemon.flowerfree</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Unlock Lifetime premium | Unlocks Premium-only features. | None |

</details>

<details>
<summary><strong>org.kustom.widget</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Allow locked preset export | Allows exporting author-locked KWGT presets. | None |
| Unlock Pro | Unlocks Pro-only features. | None |

</details>

<details>
<summary><strong>pe.tumicro.android</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| GmsCore support | Signs in through separately installed Reseam GmsCore. | None |
| Unlock Lifetime Premium | Unlocks Premium-only features. | None |

</details>

<details>
<summary><strong>Universal</strong></summary>

| Name | Description | Version pins |
| --- | --- | --- |
| Disable Play Store updates | Hides the app from Google Play's pending updates. | None |
| Force extract native libs | Sets android:extractNativeLibs to true when it is false. | None |
| Hide packages | Hides the chosen packages from the app's PackageManager queries, as if they were not installed. | None |
| Signature Killer | Spoofs the original app signature and optionally redirects installed-file reads to the original app files. | None |

</details>

<!-- PATCHES:END -->

## Setup

- JDK 17.
- Android SDK, with `ANDROID_HOME` pointing at it.
- The `reseam` CLI on `PATH`, or at the path in `RESEAM_BIN`. Use the release
  matching the plugin version in `settings.gradle.kts`.
- A bundle signing key. Create it with
  `reseam bundle keygen --out ~/.reseam/bundle-signing.key`. The command prints
  the public key users will trust. Keep the private key out of the repository.
  Use `RESEAM_BUNDLE_KEY` to select a different key file.

## Build and try the bundle

```shell
./gradlew build bundle
```

This writes `build/reseam/madkarma-patches.reseam`. List its patches and try
one on an APK:

```shell
reseam bundle list build/reseam/madkarma-patches.reseam --trust <bundle-public-key>
reseam patch app.apk \
  --bundle build/reseam/madkarma-patches.reseam \
  --trust <bundle-public-key> \
  --preset none --enable "<patch-name>" \
  --key patched.pk8 --cert patched.der \
  --output patched.apk
```

Some patches depend on `removePairip` from the official Reseam bundle. Download
the latest bundle listed in [the official index](https://api.reseam.app/patches.json)
and add these arguments to the patch command:

```shell
--bundle /path/to/reseam-patches.reseam \
--trust 556c1b22f4e03398212ba10fe6a31db252df49f599b938d24a9f2b6baec41a1d
```

## Releases

The release workflow creates
[GitHub build provenance attestations](https://docs.github.com/en/actions/how-tos/secure-your-work/use-artifact-attestations/use-artifact-attestations)
for `madkarma-patches.reseam`.

After downloading a release bundle, verify its provenance with the GitHub CLI:

```shell
gh attestation verify madkarma-patches.reseam --repo madkarmaa/reseam-patches \
  --signer-workflow madkarmaa/reseam-patches/.github/workflows/release.yml
```

## License

Project-owned code is licensed under [AGPL-3.0-or-later](LICENSE). See
[NOTICE](NOTICE) for inherited Reseam section 7 terms and third-party notices;
vendored code retains the licenses recorded in [REUSE.toml](REUSE.toml).
