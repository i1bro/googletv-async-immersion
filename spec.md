# Spec: "Running locally" section in README.md (FEA-1)

## Goal

Someone who has just cloned this fork can build the app, install it on a device and find the result, using only README.md. Today the build steps exist only in [TOPOSCAN.md](TOPOSCAN.md) ("Try It") and [AGENTS.md](AGENTS.md). README.md has no build instructions, and its upstream section links to store downloads and is not about building.

## In scope

- One new `## Running locally` section in `README.md`, under 40 lines including the heading, code fences and blank lines.
- Placement: in the fork-specific part at the top, after the bullet list and before `## Upstream Aerial Views`. A reader then finds it before the upstream content, which says it is "not this fork".
- Content:
  1. **Prerequisites:** JDK 21; an Android SDK found through `ANDROID_HOME` or `sdk.dir` in an ignored `local.properties`; `adb` (Android platform-tools); an Android TV / Google TV device or emulator with USB or network debugging enabled. Link to the existing "Enable Developer Mode" instructions further down the README instead of copying them.
  2. **Build:** `./gradlew :app:assembleImmersionDebug`. Use the Gradle wrapper, not a system Gradle.
  3. **Install / run:** `adb install -r app/build/outputs/apk/immersion/debug/app-immersion-debug.apk`. Then open **Async Immersion (Debug)** from the device's launcher, or use Settings > Toposcan > Preview. Link to the existing screensaver-setup instructions for making it the system screensaver.
  4. **Where output appears:**
     - On disk, the APK is under `app/build/outputs/apk/immersion/debug/`.
     - On the device, the app is listed as **Async Immersion (Debug)**, package `com.i1bro.googletv.asyncimmersion.debug`. It installs alongside upstream Aerial Views and the release build.
     - Logs are available through `adb logcat`.
  5. **Optional:** one line on the unit tests (`./gradlew :app:testImmersionDebugUnitTest`). One line saying that a signed release build needs the ignored `signing/immersion.properties` described in TOPOSCAN.md.

## Out of scope

- Any code, Gradle, CI or manifest change.
- Rewriting or moving TOPOSCAN.md "Try It", or the upstream README sections. They may link to or from the new section, but their content stays as it is.
- Instructions for other flavors (`beta`, `github`, `googleplay`, `amazon`, `fdroid`), for Android Studio, or for instrumentation tests and benchmarks.
- Release signing setup in detail, and creating keystores.
- Setting up OpenWeather keys (`secrets.properties`). The build works without it.

## Acceptance criteria

1. `README.md` contains exactly one heading `## Running locally`. It sits between the fork's intro bullet list and `## Upstream Aerial Views`.
2. The section, from its heading up to the next `##` heading, is at most 40 lines (`awk` / `sed` line count).
3. It names JDK 21, the Android SDK (`ANDROID_HOME` or `local.properties`), `adb`, and a device with debugging enabled.
4. It contains the literal command `./gradlew :app:assembleImmersionDebug`. On a machine that meets the prerequisites, the command completes successfully from a clean checkout, without `secrets.properties` or signing files.
5. It contains the literal path `app/build/outputs/apk/immersion/debug/app-immersion-debug.apk`. Criterion 4 produces a file at that path.
6. It contains an `adb install -r <that path>` command, and it gives the on-device app name `Async Immersion (Debug)` and package `com.i1bro.googletv.asyncimmersion.debug`. Both match `app/src/immersionDebug/res/values/brand.xml` and the `immersion` flavor `applicationId` + `.debug` suffix.
7. Every Gradle task named in the section exists (`./gradlew :app:tasks --all` lists it). Every relative link and in-page anchor in the section resolves.
8. The diff touches only `README.md`, plus removing this `spec.md` if the pipeline expects that. No other lines of `README.md` change except, optionally, a link to the new section.

## Open questions (with proposed defaults)

1. **Which variant should the section document?** Default: `immersionDebug`. It is the fork's distributed flavor, it is what TOPOSCAN.md uses, and it needs no signing files. AGENTS.md says `beta` is for agent/local verification. Mention it at most in one line, or leave it out.
2. **What does "run" mean for a screensaver app?** Default: install with `adb`, then open the app from the launcher, or use the Toposcan Preview. Link to the existing README section for setting it as the system screensaver instead of copying the ADB `settings put secure …` commands. Note that those commands currently use the upstream package name.
3. **Should the section copy TOPOSCAN.md "Try It" or link to it?** Default: the README section is self-contained for build, install and output. It links to TOPOSCAN.md for release signing and HDR setup. TOPOSCAN.md stays unchanged.
4. **Include the test command?** Default: yes, one line (`./gradlew :app:testImmersionDebugUnitTest`), if it fits within 40 lines. Otherwise drop it.
5. **Include Windows (`gradlew.bat`) instructions?** Default: no. Use Unix-style commands only, and optionally add a short note that `gradlew.bat` is the Windows equivalent.
6. **Should `spec.md` stay in the repo after implementation?** Default: it stays on this branch for the later pipeline steps. The implementing step or the PR reviewer decides whether to remove it before merge.
