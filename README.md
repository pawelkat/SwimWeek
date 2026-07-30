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
| 8 | Testing harness & privacy gates | Planned |
| 9 | Play listing polish | Planned |

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
