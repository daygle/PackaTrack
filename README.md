# 📦 PackaTrack

[![Android CI](https://github.com/daygle/PackaTrack/actions/workflows/android-ci.yml/badge.svg)](https://github.com/daygle/PackaTrack/actions/workflows/android-ci.yml)
[![License: MIT](https://img.shields.io/badge/License-MIT-yellow.svg)](https://opensource.org/licenses/MIT)
[![Kotlin](https://img.shields.io/badge/kotlin-2.4.10-blue.svg?logo=kotlin)](http://kotlinlang.org)
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

- **Language**: Kotlin 2.4.10
- **UI**: Jetpack Compose (Material 3)
- **Architecture**: MVI / Clean Architecture (Modularized)
- **Database**: Room (with SQLCipher encryption)
- **Networking**: OkHttp 5.5.0
- **Background Work**: WorkManager
- **DI**: Hilt / Manual DI (Modularized via `AppContainer`)

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
```

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

PackaTrack is split into two main modules to ensure high-quality, testable logic:

### 🧩 `core` Module (Pure Kotlin)
The heart of the application. It contains the tracking engine, parsers, and change detection logic.
- **Parsers**: Normalizes raw carrier data (Cainiao, AU Post, iMile, Aramex).
- **Detectors**: Logic for renumbering detection using weight fingerprints and consolidation detection.
- **Change Log**: Business logic for generating shipment events.

### 📱 `app` Module (Android)
The UI and infrastructure layer.
- **Compose UI**: Modern, reactive screens for home, details, and settings.
- **Repository**: Orchestrates data flow between network fetchers and the local Room database.
- **Backup**: Encrypted export/import system for your tracking history.
- **Security**: Keystore integration and database encryption management.

---

## 🔒 Security & Privacy

We value your privacy. PackaTrack does not use any intermediate servers to store your data. Your tracking history is fetched directly from the carriers and stored locally in a secure, encrypted database.

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
