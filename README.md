# PackaTrack

Track AliExpress parcels that travel through **Cainiao UBI Smart Parcel**, **Australia Post** and **iMile** — including the tricky bits: parcels that get a **new tracking number** mid-journey, or packages that are **combined/consolidated** into one shipment.

Native Android app, Kotlin + Jetpack Compose, Room, WorkManager, OkHttp.

## Quick start

```bash
# Build the debug APK
./gradlew :app:assembleDebug
# -> app/build/outputs/apk/debug/app-debug.apk

# Run the pure-logic unit tests
./gradlew :core:testDebugUnitTest
```

Open `app-debug.apk` on a device/emulator (API 24+). The app starts in **demo mode** so it works with zero setup.

## Using it

1. Tap **Add parcel** and paste an AliExpress tracking number. PackaTrack auto-detects the carrier from the number format (override in the dialog if needed). You can also save the AliExpress order link and the declared weight.
2. **Refresh** pulls each carrier's latest scans. Background sync runs automatically (default every 6 h, changeable in Settings).
3. Tap a parcel for its **timeline** — every courier's scans merged newest-first with status dots and a carrier tag, plus a *"What PackaTrack noticed"* section.
4. Status changes worth knowing (delivered, renumbered, combined) trigger a notification.

### One parcel, many couriers

A single AliExpress parcel often travels under several tracking numbers — a Cainiao number for the China leg, an Australia Post number for the final mile, sometimes an iMile number too. PackaTrack treats a **parcel** as a container of **courier legs**:

- Open a parcel and tap **Add courier** to attach another provider's tracking number. Each leg is polled independently and its scans merge into one timeline.
- Remove a courier with the **✕** on its card.
- Already tracking the two halves separately? Open one, choose **⋮ → Combine with another parcel**, and pick the other — its couriers, scans and history fold into this parcel. PackaTrack also still auto-detects renumbering and weight-based combination on its own.

### Many orders, one parcel

The reverse also happens: Cainiao often **consolidates several of your orders under one tracking number**. That's physically one parcel now, so PackaTrack keeps it as one parcel and lets you list the **orders** inside it:

- A parcel has an **Orders** section — tap **Add order** to record each AliExpress order (a name, and optionally its order link). The parcel is named after its orders unless you set a custom name.
- When you **Combine** two parcels, their orders are merged too, so the consolidated parcel lists everything it now carries.

(A tracking number is unique to one parcel — you record the shared shipment once and list its orders, rather than duplicating the number across parcels.)

## Carrier support

| Carrier | Source | Credentials |
| --- | --- | --- |
| Cainiao UBI Smart Parcel | Cainiao public global detail endpoint (`global.cainiao.com/global/detail.json`) | none |
| Australia Post | Official digital API v2 `track/events` | free `AUTH-KEY` from developers.auspost.com.au → paste in Settings |
| iMile | Customer-facing track endpoint (best effort) | none |

The public endpoints are undocumented and occasionally change; PackaTrack treats an unreachable carrier gracefully (no crash, parcel stays visible, "Open on carrier website" always works).

## Demo mode (offline)

Demo mode is ON by default. Add these fictional numbers to see every feature without keys:

- `DEMO600087654321` — Cainiao-style journey that is **renumbered** to `AU600087654321` when Australia Post takes over, then out-for-delivery → delivered.
- `DEMO111222333` — a small parcel that gets **consolidated** into another shipment.
- `CNDEMOCOMBO9X` — the combined parcel; its weight (480 g) matches the sum of the two parcels above, which is how PackaTrack recognises it.

Each manual refresh advances the story one step.

## How change detection works (`:core` module)

Pure Kotlin, fully unit-tested (26 tests), no Android dependencies:

- **Carrier detection** — tracking-number format rules for AU Post (UPU `XX000000000AU`, domestic, 7-prefix consignments), Cainiao (`CN…`), iMile (`IM…`).
- **Parsers** — one per carrier, tolerant of shape drift, normalising raw codes (`SIGNED_SUCCESS` → `DELIVERED`, …) and weights (kg → g).
- **Renumbering detection** — when a refresh comes back under a different number, PackaTrack checks *number suffix fingerprints* (≥ 8 trailing chars) plus weight/dimension closeness to confirm it is the same physical parcel, then records a `RENUMBERED` change and keeps the old number in the parcel's alias history.
- **Combination detection** — several previously tracked numbers converge onto one parcel whose weight ≈ sum of the parts (within tolerance) or whose events mention consolidation → `COMBINED` change on every involved parcel.
- **Change log** — every detection is persisted and surfaced in the UI and notifications; re-fetches are deduplicated.

## Project layout

```
core/   Pure tracking engine: models, parsers, detectors, change logic, demo data
app/    Android app: Room database, OkHttp fetchers, WorkManager sync, Compose UI
```

- `core/src/main/java/com/packatrack/core/parse/` — Cainiao / Australia Post / iMile parsers
- `core/src/main/java/com/packatrack/core/changelog/ChangeLogService.kt` — renumber/combine detection
- `app/src/main/java/com/packatrack/app/data/TrackingRepository.kt` — sync + detection orchestration
- `app/src/main/java/com/packatrack/app/data/fetch/` — live + demo fetchers

## Toolchain

Gradle 9.7.1 wrapper, AGP 9.3.2 (built-in Kotlin), Kotlin 2.3.10, Compose BOM 2026.08.00, Room 2.8.4 (KSP), WorkManager 2.11.2, OkHttp 5.5.0, Coroutines 1.11.0. compileSdk 37 / minSdk 24 / targetSdk 36.

## Data model

- **Parcel** (`shipments`) — the physical shipment: an optional custom name and declared weight.
- **Courier leg** (`tracking_legs`) — one carrier + tracking number following a parcel; a parcel has one or many, added and removed freely.
- **Order** (`orders`) — an AliExpress order carried in a parcel (name + optional link); a parcel has one or many, so a Cainiao-consolidated parcel lists every order inside it.
- **Event** (`events`) — a scan, tied to its leg and parcel, merged into the parcel's timeline.

The database is versioned; upgrading from an earlier schema rebuilds locally (demo data regenerates on the next refresh).
