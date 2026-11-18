# 🚀 Releasing PackaTrack

Everything needed to sign, release and publish PackaTrack: signing credentials, the tag-driven
GitHub Release workflow, and the first Google Play upload.

---

## 🔑 Release signing

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
> existing app when both are signed with the same key. If the key is lost, users must uninstall and
> reinstall. Keep Play App Signing enabled as well: that is what lets a lost *upload* key be reset.

---

## 🏷 Releases

Pushing a `vMAJOR.MINOR.PATCH` tag (e.g. `v1.0.0`, or `v1.1.0-beta.1` for a pre-release) runs lint
and the unit tests, then publishes a signed **App Bundle** as `PackaTrack-<version>-release.aab`
to GitHub Releases. The `versionCode` is derived from the tag (`v1.0.0` → `1000000`) so it always
increases; the workflow fails rather than publishing an unsigned bundle or reusing a version.

The `.aab` embeds the R8 deobfuscation mapping, so Google Play picks that up automatically on
upload; a copy of `mapping.txt` is kept as a workflow artifact for retracing stack traces locally.

### 🧩 Native debug symbols

The bundle does carry native code, but none of it is ours: `libsqlcipher.so` from the SQLCipher
AAR (`net.zetetic:sqlcipher-android`, shipped for `arm64-v8a`, `armeabi-v7a`, `x86` and `x86_64`)
is the only `.so` in it. That library arrives **already stripped** — no `.symtab` and no
`.debug_info` section in any ABI — so the Android Gradle Plugin has nothing to strip and the bundle
gets no `BUNDLE-METADATA/com.android.tools.build.debugsymbols/` entry. Installing the NDK or
setting `ndk.debugSymbolLevel` would not change that: there are no symbols in the input to package.

Play therefore reports *"This AppBundle contains native code, and you haven't uploaded debug symbol
files"* on upload. It is informational — the bundle is accepted and can be rolled out. All those
symbols would buy is symbolized native stack frames if a crash ever happened inside
`libsqlcipher.so`; Kotlin/Java crashes are unaffected, because the R8 `mapping.txt` is embedded in
the bundle.

The release workflow only warns when the bundle contains a `.so` that is **not** in its
`THIRD_PARTY_SOS` allowlist (currently just `libsqlcipher.so`), so the notice on a normal release
means "no first-party native code, nothing to package" instead of looking like a defect. If
first-party native code is ever added, either package its symbols with
`android.buildTypes.release.ndk.debugSymbolLevel = "SYMBOL_TABLE"` in `app/build.gradle.kts`, or —
when the new library is third-party and pre-stripped as well — add its file name to that allowlist.

---

## 📤 Publishing to Google Play (first upload)

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
