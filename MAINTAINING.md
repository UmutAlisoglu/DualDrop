# Maintaining DualDrop

Notes for building, releasing and updating DualDrop. For using the app, see the [README](README.md).

## Where things are

| What | Where |
|---|---|
| Source code | this repository (`main` branch) |
| Upstream (Osmosis) | git remote `upstream` = https://github.com/KonradIT/Osmosis |
| DualDrop's own code | `app/src/main/java/de/druomi/osmolink/`, `res/values*/osmolink_strings.xml`, `res/drawable/ol_*` |
| Changes to Osmosis files | small, marked with `DualDrop:` comments (search for it) |
| Release signing key | `osmolink-release.jks` (kept outside the repo, never committed) |
| Key passwords | `keystore.properties` in the repo root (gitignored, never committed) |
| Built APK | `app/build/outputs/apk/release/app-release.apk` |

**Back up the signing key and `keystore.properties`.** Without them no one can build an update that installs over the existing app.

## Build

Requirements: JDK 17, Android SDK with platform 36 and build-tools 35/36, adb.

```bash
export JAVA_HOME="/path/to/jdk-17"
./gradlew assembleRelease
adb install -r app/build/outputs/apk/release/app-release.apk
```

Without `keystore.properties`, `assembleRelease` produces an unsigned APK; use `assembleDebug` for local testing.

## Release a new version

1. In `app/build.gradle`, raise `versionCode` by 1 and set `versionName`.
2. Add an entry to [CHANGELOG.md](CHANGELOG.md).
3. Build with `./gradlew assembleRelease` and test the APK on a phone.
4. Commit, tag (`git tag v0.x.y`) and push.
5. Create a GitHub release for the tag and attach the APK as `DualDrop-0.x.y.apk`. Paste the changelog entry and the install line from the previous release.

## Merge updates from Osmosis

```bash
git fetch upstream
git merge upstream/main
```

Conflicts usually only appear in `MainActivity.kt`, `activity_main.xml`, `build.gradle` and `AndroidManifest.xml`.

## How "keep home Wi-Fi" works

- Command: `cmd wifi force-overlay-config-value bool config_wifiMultiStaLocalOnlyConcurrencyEnabled enabled true` (off: `... enabled false`). Lasts until the next restart.
- The app pairs once with the phone's own Wireless debugging (library libadb-android), grants itself `WRITE_SECURE_SETTINGS`, and afterwards switches Wireless debugging on and off by itself.
- After every restart: `BootReceiver` → `EnsureWorker` (WorkManager, waits for Wi-Fi) → set the flag. Measured 15 to 45 seconds after unlocking.
- Compatibility check: `cmd wifi query-interface 2000 com.android.shell STA -new` must report "can be created without destroying".
- `WifiManager.isStaConcurrencyForLocalOnlyConnectionsSupported` is cached on Samsung phones, so the app remembers the boot in which the flag was confirmed.

## Licences and distribution

Not legal advice; a summary of what was checked before publishing.

- **Osmosis** is MIT licensed. The MIT notice (`LICENSE.txt`) must ship with the app and the source.
- **libadb-android** is dual-licensed Apache-2.0 / GPL-3.0; DualDrop uses it under Apache-2.0. **spake2-java** (pulled in by it) is LGPL-3.0, which is fine for an open-source app as long as the licence and a source link are provided. All third-party licences are listed in [THIRD_PARTY_NOTICES.md](THIRD_PARTY_NOTICES.md).
- **Trademarks:** "DJI" and "Osmo" are DJI trademarks. The app name must not contain them (the app was renamed from OsmoLink for that reason). Naming the supported cameras descriptively is fine, without DJI logos and with the "not affiliated with DJI" notice in the README and app.
- **Reverse engineering:** the camera protocol was documented by Osmosis from captures of its own camera traffic. No DJI code or firmware is included. In the EU, Directive 2009/24/EC Art. 6 allows reverse engineering for interoperability.
- **Distribution:** GitHub releases and F-Droid are possible. For Google Play, the use of `WRITE_SECURE_SETTINGS` and the `specialUse` foreground service has to be justified. Google's planned developer verification for sideloaded apps (2026 onwards) may also affect distribution.
- **Risk:** if Google restricts on-device ADB in a future Android version, the "keep home Wi-Fi" setup may stop working. The camera part would not be affected.
