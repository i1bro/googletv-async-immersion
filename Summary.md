# Aerial Views — Project Summary

## Overview
Android screensaver app for TV / Google TV / phones (Nvidia Shield, Fire TV, Google TV Streamer, Android phones).

## Current Status
- **Branch**: master (clean)
- **Last commit**: "Add experimental tiled SDR presentation for 4K output"

## Tech Stack
- **Language**: Kotlin 2.4+
- **Build**: Android Gradle Plugin 9+, JDK 21
- **Core libs**: Coroutines, Flow, Room 3 (KSP), Coil 3, ExoPlayer/Media3, Ktor, Kotpref
- **Services**: Firebase (Crashlytics, Perf, Analytics)

## Modules
| Module | Purpose |
|--------|---------|
| `:app` | Main application |
| `:projectivyapi` | API for Projectivy launcher |
| `:baselineprofile` | Startup profile |
| `:microbenchmark` | Macrobenchmarks |

## Flavors
- `github`, `beta` (default), `googleplay`, `googleplaybeta`, `amazon`, `fdroid`, `immersion`
- Use **beta** for local development

## Quick Build Commands
```bash
./gradlew :app:testBetaDebugUnitTest    # Unit tests
./gradlew :app:lintKotlin               # Lint check
./gradlew :app:formatKotlin             # Auto-format
./gradlew :app:assembleBetaDebug        # Debug APK
./gradlew :app:installBetaDebug         # Install to device
```

## Code Style
- Kotlin official style (enforced by kotlinter)
- Run `:app:formatKotlin` before finishing
- `:app:lintKotlin` must pass

## Testing
- Framework: JUnit 5 (Jupiter), MockK, kotlinx-coroutines-test
- Unit tests: `app/src/test`
- Instrumentation tests: `app/src/androidTest`
