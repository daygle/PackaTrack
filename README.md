# 📦 PackaTrack

[![Android CI](https://github.com/daygle/PackaTrack/actions/workflows/android-ci.yml/badge.svg)](https://github.com/daygle/PackaTrack/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.21-blue.svg?logo=kotlin)](http://kotlinlang.org)
[![Platform](https://img.shields.io/badge/platform-Android-green.svg?logo=android)](https://www.android.com)

**PackaTrack** is a modern, native Android application designed to handle the complexities of international package tracking. It specializes in tracking shipments from **AliExpress**, **Cainiao**, **Australia Post**, and **iMile**, with a focus on solving the "renumbering" and "consolidation" problems that often leave users confused.

---

## ✨ Key Features

- 🔄 **Smart Renumbering Detection**: PackaTrack automatically notices when a parcel receives a new tracking number mid-journey (e.g., transitioning from China to local delivery).
- 📦 **Consolidation Support**: Manage multiple AliExpress orders consolidated into a single physical shipment.
- 🏗️ **Multi-Courier Timelines**: Attach multiple tracking numbers to a single parcel and see a unified, merged timeline of all scans.
- 🔔 **Intelligent Notifications**: Get alerted for delivered parcels, renumbering events, and combined shipments.
- 🎨 **Visual Transit Tracking**: At-a-glance color coding (Green, Yellow, Orange, Red) based on parcel age, with fully customizable day thresholds.
- 🛡️ **Privacy-First & Secure**:
    - **No Servers**: Your tracking data stays on your device.
    - **Encrypted Database**: At-rest encryption using **SQLCipher** (AES-256).
    - **Hardware Security**: Encryption keys are stored in the **Android Keystore System**.
- 🌓 **Modern UI**: Full Material 3 support with Light and Dark modes.

---

## 🛠 Tech Stack

- **Language**: Kotlin 2.4.21
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (ViewModels per screen), modularized by layer and feature
- **Database**: Room (with SQLCipher encryption)
- **Networking**: OkHttp 5.5.1
- **Background Work**: WorkManager
- **DI**: Hilt

---

## 🚀 Getting Started

### Installation
1.  Clone the repository.
2.  Open in Android Studio (Ladybug or newer).
3.  Add your **Australia Post API Key** in the app settings if you need live local tracking.

### Build & Test
```bash
# Build the debug APK
./gradlew :app:assembleDebug

# Run unit tests (Core logic)
./gradlew :core:testDebugUnitTest

# Build the signed release App Bundle (see Release signing)
./gradlew bundleRelease
```

### Release signing
Release builds are signed from a keystore that must **never** be committed. The four signing
values are read first from environment variables, then (for local builds) from an optional
`keystore.properties` file in the project root, which is gitignored:

```properties
RELEASE_KEYSTORE_FILE=keystore/packatrack-release.keystore   # absolute path, or relative to the project root
RELEASE_KEYSTORE_PASSWORD=...
RELEASE_KEY_ALIAS=packatrack
RELEASE_KEY_PASSWORD=...
```

`bundleRelease` then writes `app/build/outputs/bundle/release/app-release.aab`. Without a
configured keystore the build still succeeds, but the bundle is unsigned and Google Play will
not accept it.

To let CI build signed release bundles, add these repository secrets under
*Settings → Secrets and variables → Actions*:

| Secret | Value |
| --- | --- |
| `RELEASE_KEYSTORE_B64` | the keystore file, base64-encoded: `base64 -w0 keystore/packatrack-release.keystore` |
| `RELEASE_KEYSTORE_PASSWORD` | keystore password |
| `RELEASE_KEY_ALIAS` | key alias |
| `RELEASE_KEY_PASSWORD` | key password |

> [!WARNING]
> Back up the keystore and its passwords somewhere safe. Android only installs an update over an
existing app when both are signed with the same key. If the key is lost, users must uninstall and
reinstall. Keep Play App Signing enabled as well: that is what lets a lost *upload* key be reset.

### Releases
Pushing a `vMAJOR.MINOR.PATCH` tag (e.g. `v1.0.0`, or `v1.1.0-beta.1` for a pre-release) runs lint
and the unit tests, then publishes a signed **App Bundle** as `PackaTrack-<version>-release.aab`
to GitHub Releases. The `versionCode` is derived from the tag (`v1.0.0` → `1000000`) so it always
increases; the workflow fails rather than publishing an unsigned bundle or reusing a version.

The `.aab` embeds the R8 deobfuscation mapping, so Google Play picks that up automatically on
upload; a copy of `mapping.txt` is kept as a workflow artifact for retracing stack traces locally.

### Publishing to Google Play (first upload)
The listing copy and graphics live in this repo, so only the screenshots, the privacy policy URL
and the declarations still have to be filled in by hand:

| Play Console field | File |
| --- | --- |
| App icon (512 × 512, 32-bit PNG) | [store-listing/app-icon-512.png](store-listing/app-icon-512.png) — vector source: [icon.svg](store-listing/icon.svg) |
| Feature graphic (1024 × 500) | [store-listing/feature-graphic-1024x500.png](store-listing/feature-graphic-1024x500.png) — HTML source: [feature.html](store-listing/feature.html) |
| Short description (≤ 80 characters) | [store-listing/short.txt](store-listing/short.txt) |
| Full description (≤ 4000 characters) | [store-listing/full.txt](store-listing/full.txt) |

In Play Console order:

1. **Create the app**: name `PackaTrack`, default language English, type *App*, *Free*.
2. **Package name** `com.packatrack` (the `applicationId`; the Kotlin namespace stays
   `com.packatrack.app`). This is permanent and cannot be changed after the first upload, and
   Google Sign-In (Gmail import) is bound to it, so do not rename the `applicationId` later.
3. **Play App Signing**: opt in on the first upload. Play then holds the app signing key and the
   keystore configured above becomes the *upload* key. Never upload with a debug keystore.
4. **Upload a bundle**: push a `v*` tag and take `PackaTrack-<version>-release.aab` from the release
   (or its workflow artifact), then upload it to an *Internal testing* track first. Every later
   upload needs a higher `versionCode`, which the tag-derived scheme gives you for free.
5. **Store listing**: copy the descriptions and graphics above. Play also requires **at least two
   phone screenshots**, which are not in this repo: capture them from a device or emulator at
   1080 × 1920 or taller, with the status bar clean.
6. **Privacy policy**: a public URL is mandatory. PackaTrack stores everything on-device and has no
   servers, so `SECURITY.md` is a good basis. It must cover the notification permission, the
   optional Gmail read access and the fact that nothing leaves the device except the carrier
   requests themselves.
7. **Data safety**: declare that no user data is collected or shared. Tracking history, the
   Australia Post API key and Gmail mail are processed on the device only, and there are no
   analytics, ads or crash-reporting SDKs in the dependency graph.
8. **Content rating questionnaire**: no ads, no user-generated content, no purchases, no data
   sharing, so the result is the lowest rating band (Everyone / PEGI 3).
9. **Target audience**: not designed for children, so exclude the Families policies.
10. **Gmail restricted scope**: Gmail Smart Import uses `gmail.readonly`, which Google only grants
    to verified apps. Before relying on that feature in a public release, complete OAuth
    verification in the Google Cloud project, add the release key's SHA-1 to the Android OAuth
    client, and answer the Data safety and permissions declarations consistently. If that is not
    done yet, ship the first release without it — Play review can otherwise reject the app for
    using a restricted scope.
11. **Rollout**: promote from internal testing to production once the bundle installs and runs.
    The first production release always goes through Play review, which can take a few days.

---

## 📡 Carrier Support

| Carrier | Source | Credentials Required |
| :--- | :--- | :--- |
| **Cainiao UBI** | Public Global Detail API | None |
| **Australia Post** | Official Digital API v2 | `AUTH-KEY` (Free from developers.auspost.com.au) |
| **iMile** | Customer Track Endpoint | None |
| **Aramex** | Public Shipment Endpoint | None |
| **Morning Global** | Web-based Tracker | None |

---

## 🏗 Project Architecture

PackaTrack is split into layered modules so the tracking logic stays testable on the plain JVM:

### 🧩 `core` (tracking engine)
- **Parsers**: Normalize raw carrier data (Cainiao, AU Post, iMile, Aramex).
- **Detectors**: Carrier detection from number formats, renumbering detection (number
  fingerprints / dimensions) and consolidation detection.
- **Change Log**: Business logic for generating shipment events, plus the Room entities.

### 💾 `data` (infrastructure)
- **Repository**: Orchestrates data flow between the carrier fetchers and the local Room database.
- **Security**: SQLCipher database encryption with a Keystore-wrapped key; Keystore-encrypted secrets.
- **Backup**: Passphrase-encrypted export/import of your tracking history.
- **Background work**: Periodic sync, Gmail Smart Import and notifications.

### 🖼 `feature:*` (UI)
- `feature:home`, `feature:detail`, `feature:settings`: Compose screens with their ViewModels.
- `feature:common`: Shared theme, components, strings and display helpers.

### 📱 `app`
The application shell: Hilt setup, navigation, biometric lock and worker scheduling.

---

## 🔒 Security & Privacy

We value your privacy. PackaTrack does not use any intermediate servers to store your data. Your tracking history is fetched directly from the carriers and stored locally in a secure, encrypted database.

### 🔐 App Lock

App Lock is a UI gate, not encryption. While it is on, PackaTrack asks for a biometric or your
device PIN, pattern or password before showing your parcels, sets `FLAG_SECURE` so screenshots
and the app-switcher thumbnail are blanked, and hides parcel details from notifications while
the device is locked.

It deliberately fails open: if a device has no biometrics and no screen lock at all, the app
opens instead of trapping you outside your own tracking history. It also does not encrypt the
database on its own, so someone holding your unlocked device - or with root or a device backup -
can still read your data. The cryptographic boundary in PackaTrack is the passphrase on portable
backups.

For more information, please see our [SECURITY.md](SECURITY.md) and [LICENSE](LICENSE).

---

## 🤝 Contributing

Contributions are welcome! Please feel free to submit a Pull Request.

1.  Fork the Project.
2.  Create your Feature Branch (`git checkout -b feature/AmazingFeature`).
3.  Commit your Changes (`git commit -m 'Add some AmazingFeature'`).
4.  Push to the Branch (`git push origin feature/AmazingFeature`).
5.  Open a Pull Request.

---

Developed with ❤️ for the global shopping community.
