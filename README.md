# SwimWeek

Weekly swimming distance from **Google Health Connect**, shown on an AMOLED-optimized home-screen widget (Galaxy S24 Ultra). Data from Galaxy Watch 7 is expected via **Samsung Health → Health Connect**.

## Status

| PR | Scope | Status |
| --- | --- | --- |
| 1 | Project scaffold, Hilt, AMOLED Compose shell | Done |
| 2 | Domain models & week utilities | Done |
| 3 | Health Connect permissions | Done |
| 4 | Swim distance aggregation | Done |
| 5 | Companion UI + Samsung bridge onboarding | Done |
| 6 | Background sync (Changes + WorkManager) | Done |
| 7 | Glance AMOLED widget | Done |
| 8 | Weekly distance target + progress ring (home + widget) | Done |
| 9 | Testing harness & privacy gates | Planned |
| 10 | Play listing polish | Planned |
| 11 | Wear OS complication (`:wear`) + Data Layer sync | In review |

Full design: see design document produced with the planning pass (`grok-design-doc-*.md`).

## Requirements

- JDK 17+
- Android SDK 35 + build-tools
- Android Studio Ladybug+ or command-line Gradle

## Build

```bash
export ANDROID_HOME=/path/to/Android/Sdk
./gradlew :app:assembleDebug
```

Debug APK: `app/build/outputs/apk/debug/app-debug.apk`

## Wear OS module (`:wear`)

Galaxy Watch 7 (Wear OS 5 / API 34; minSdk 30) complication showing this week's swim distance.

- **Data flow:** phone `SwimDistanceRepository` → `WearSyncPublisher` (Hilt) writes the weekly total, target, unit and session count to the Wearable Data Layer at `/swimweek/weekly` after every summary refresh (same path that updates the Glance widget). The watch's `WeeklyDataListenerService` receives the change and calls `ComplicationDataSourceUpdateRequester.requestUpdateAll()`; `WeeklySwimComplicationService` reads the latest DataItem.
- **Complication types:** `SHORT_TEXT` (e.g. `1.2k` + unit), `RANGED_VALUE` (progress to the weekly target; full ring when no target), `MONOCHROMATIC_IMAGE`, `SMALL_IMAGE`. Tap opens a minimal Wear Compose Material 3 screen.
- **Privacy:** no `INTERNET` in `:wear`; only derived weekly totals leave the phone (no sessions or raw Health Connect records).
- `applicationId` matches the phone app (`com.swimweek.app`, `.debug` suffix for debug builds) — required for Data Layer pairing. Install phone and watch builds of the same variant, signed with the same key.
- Uses `androidx.wear.compose:compose-material3` 1.6.x: 1.7.x requires AGP 9.1 + compileSdk 37.

### Build and install over Wi-Fi

```bash
./gradlew :app:assembleDebug :wear:assembleDebug
```

1. On the watch: Settings → About watch → Software → tap *Software version* 5× → Developer options → enable **ADB debugging** and **Wireless debugging** (same Wi-Fi as your computer).
2. Wireless debugging → *Pair new device*: `adb pair <watch-ip>:<pair-port> <code>`
3. `adb connect <watch-ip>:<port>` (port shown on the Wireless debugging screen)
4. `./gradlew :wear:installDebug` (with only the watch connected) or `adb -s <watch-ip>:<port> install -r wear/build/outputs/apk/debug/wear-debug.apk`
5. Install the matching phone build (`./gradlew :app:installDebug` / `adb -s <phone> install -r app/build/outputs/apk/debug/app-debug.apk`) and open SwimWeek on the phone once (or tap *Refresh*) to publish the first total.

### Add the complication

Long-press the watch face → **Customize** (or Edit in the Galaxy Wearable app → Watch faces → Customize) → pick a complication slot → **SwimWeek / Weekly swim**. Ranged (ring) slots show progress to your weekly target; small round slots show the short distance.

### aarch64 / ARM hosts

Google’s Linux `aapt2` is x86_64-only. On ARM64 (e.g. Termux/proot), install [box64](https://github.com/ptitSeb/box64) and use the override in `gradle.properties`:

```properties
android.aapt2FromMavenOverride=/root/tools/aapt2
```

where `/root/tools/aapt2` is a shell wrapper that runs the real binary under `box64`.

## Privacy defaults (v1)

- No `INTERNET` permission
- `allowBackup=false` + DataStore excluded from backup/extraction
- Health Connect read-only (exercise + distance) — lands in later PRs

## License

Proprietary / TBD.
