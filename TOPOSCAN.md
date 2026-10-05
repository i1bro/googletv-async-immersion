# Aerial Views - Toposcan Prototype

Native Android experiment based on [Aerial Views](https://github.com/theothernt/AerialViews), upstream commit `59a1e6667e40c33eea66725cf0f4836cb1bac9c2`. The fork is published at [i1bro/googletv-async-immersion](https://github.com/i1bro/googletv-async-immersion). The upstream GPL-3.0 license remains in LICENSE. This is an independent visual study, not Sakamoto/Takatani's original software or footage.

## Try It

Build with JDK 21 and an Android SDK configured through ANDROID_HOME:

```sh
./gradlew :app:assembleImmersionDebug
adb install -r app/build/outputs/apk/immersion/debug/app-immersion-debug.apk
```

The published release app is named **Async Immersion**, package `com.i1bro.googletv.asyncimmersion`. The debug build is **Async Immersion (Debug)**, package `com.i1bro.googletv.asyncimmersion.debug`. Both install alongside the regular Play Store app and the earlier beta prototype (`com.neilturner.aerialviews.debug`), without replacing their settings. No TV has been connected or configured by this project yet.

### Release Versus Debug

Both variants have the same visual effect and HDR hardware requirements. Release disables debugging and LeakCanary, and uses R8 code optimization and resource shrinking. Debug retains diagnostic information and LeakCanary and is larger. Both `immersion` variants exclude Firebase SDKs and performance instrumentation, using upstream's existing no-op adapter. They still make network requests to selected media providers and any optional integrations the user enables.

Release is signed with a dedicated fork key, not the upstream or Android debug key. To build it, create an ignored `signing/immersion.properties` with `storeFile` (relative to the repository root or absolute), `storePassword`, `keyAlias` and `keyPassword`, then run `./gradlew :app:assembleImmersionRelease`. Keep the keystore and credentials private and backed up: future updates must use the same signing key. Do not commit them or attach them to GitHub releases. Release and debug use separate app IDs and do not share settings.

The initial `0.1.0-alpha.1` GitHub release is marked pre-release because real-projector HDR, performance and standby verification is still pending. This status is independent of the APK's optimized `release` build type.

Open **Settings > Toposcan**. Available controls:

- Enable/disable Toposcan. Disabling it restores the original media player.
- HDR effect (default on) and HDR status, including the last playback result or rejection reason. An existing installation keeps its toggle setting.
- HDR output: Auto (default), HDR10, or HLG (experimental). Auto prefers supported PQ output on Android 13+, otherwise tries supported HLG output on Android 12+. Explicit choices never silently select a different HDR output.
- Playback status: last render size, GPU, video format, frame callbacks, latched frames, draw count and scan phase. Stored locally without media URLs.
- Band height: 1-12 pixels referenced to a 1080-line screen, default 3.
- Scan duration: 8-96 seconds, default 32.
- Trailing freeze delay: 0-16 seconds, default 4.
- Frozen still duration: 0-30 seconds, default 5.
- Colour field duration: 1-20 seconds, default 8.
- Alternate, left-to-right, or right-to-left direction.
- Render long-edge limit: 1280, 1920 or 3840 (default). This limits GPU output, not source decoding resolution. An existing install keeps its previous selection.
- Preview (2 minutes): a normal activity that closes automatically, without selecting a system screensaver.

Settings are persisted and read when the screensaver next starts. Video speed, source selection, clock/weather/date/metadata overlays, brightness, remote controls and other settings remain in their existing Aerial Views menus. Select **3840** here, then select an actual **4K HDR10** source (BT.2020/PQ, not Dolby Vision). Source quality and render size are separate settings: a 4K surface cannot restore detail missing from a 1080p source. The source defaults remain upstream defaults. Weather needs no configuration for this test.

First use the app's **Test screensaver settings** command. Only after playback works should you make it the default screensaver.

### Black Picture With A Visible Clock

First compare with **Toposcan mode** disabled. If normal playback works, the issue is in the effect's graphics/decoder-surface path, not necessarily the source or Android version. Turning off **HDR effect** does not change source quality or convert an HDR file to SDR.

Starting in `0.1.0-alpha.2`, effect startup waits for an actual latched texture frame and video metadata, independent of the decoder's first-frame notification. If frame-available callbacks stop arriving, the renderer checks for new texture timestamps and can switch to polling; duplicate timestamps still do not advance the scan. This addresses notification/startup failures, not every cause of black output.

After a 10-20 second preview, return to **Toposcan > Playback status** and record its text. Zero frame callbacks/latched frames indicates that frames have not reached the effect; positive frame and draw counts with a black picture needs further source-texture/compositor investigation. The status also preserves graphics initialization errors. PQ output still requires Android 13+; alpha.3 adds a capability-gated HLG output alternative for Android 12+. SDR is supported on Android 12. Emulator playback alone does not verify Valerion's GPU driver.

### 4K Graphics Test (alpha.4)

Open **Settings > Toposcan > 4K graphics test**. No ADB, internet access or change to the default screensaver is needed. At each of six stages, select the button matching the picture: red/green/blue/white bars, black, or distorted. Send a photo of the final report, also stored under **4K test result**. Back/Home exits, and the activity closes after two minutes. The timeout is on the UI thread, not an independent watchdog.

The test uses actual 1920x1080 and 3840x2160 SDR buffers independently of the Android UI resolution. It never changes the playback width/HDR preferences or overwrites Playback status. Unsupported GPU dimensions are reported as skipped, not silently downscaled. Each resolution runs:

- DIRECT: scissored colour clears to the EGL window, without the effect shader.
- IMAGE: a generated pattern through the production photo copy, frozen-history and previous-image buffers, and the production scene shader.
- EXTERNAL: the same pattern submitted through a full-resolution Surface/SurfaceTexture and the production external-texture shader, then the same effect buffers. This is synthetic RGB input, not a hardware-decoded HEVC frame.

One-shot `glReadPixels` probes compare four known colours at three heights in each relevant buffer and the EGL window before swap. There is no readback in ordinary playback. `Live` or `Frozen` failure points to the corresponding graphics stage; successful intermediate probes with a failing `Window` narrow it to the final draw. All probes passing but a visually black image points towards surface presentation/composition, which GPU readback alone cannot verify. A passing synthetic test does **not** clear the hardware video decoder, YUV sampling, concurrent decoder memory pressure, HDR, or sustained performance. It is a diagnostic, not a 4K compatibility fix.

## Valerion Plus: Quality And Safety

Valerion's [Plus/Plus 2 product page](https://www.valerion.com/product/valerion-streammaster-plus2-plus-4k-rgb-triple-laser-projector) lists 4K UHD, Google TV, MT9618 and 4 GB RAM. The current page calls these models StreamMaster; some other Valerion pages use VisionMaster. Marketing specifications do not establish that a custom GPU effect will run smoothly on a particular firmware.

The effect supports **up to 3840x2160 SDR and HDR10 processing**, not just 4K decoding followed by a 1080p intermediate. It sizes its SurfaceView from the TV's *active physical display mode*, even if the Android UI is 1080p, and respects GPU texture/renderbuffer/viewport limits. It does not select a new display mode. If firmware reports an active 1080p mode, output remains 1080p. The allocation size is logged as `Toposcan buffer: ...`; verify it on the real projector. Three 4K RGBA8 SDR textures use about 95 MiB; four 4K RGBA16F HDR textures use about 253 MiB. This excludes decoded frames, photos, surface buffers and the rest of the app. Full-resolution photos use a bounded software decode/upload; videos are not re-encoded or assigned a new bitrate.

HDR10 now has a separate path: decoder YUV -> Media3's BT.2020/PQ-to-linear shader -> FP16 live/history/composite buffers -> Media3's linear-to-PQ shader -> RGB10_A2 EGL window surface tagged BT.2020/PQ. Colour-field blends use linear light. The effect shader uses high-precision samplers and does not clamp highlights to SDR white. Source static mastering/CLL metadata is forwarded when the optional EGL metadata extensions are present; missing values are cleared between clips. No HDR-to-SDR tone mapping is requested by this path.

**Compatibility is not established merely by the projector's HDR logo.** PQ output requires Android 13 / API 33 or newer, reported display HDR10 support and `EGL_EXT_gl_colorspace_bt2020_pq`. The experimental HLG output instead requires Android 12 / API 31 or newer, reported display HLG support and `EGL_EXT_gl_colorspace_bt2020_hlg`. Both require GLES 3, a 10-bit EGL config, `GL_EXT_YUV_target` and renderable RGBA16F textures. The EGL surface must confirm the requested colour-space tag. Missing capabilities or renderer errors restore native playback, without pretending an SDR surface is HDR. The PQ version restriction follows [Media3 GlUtil](https://github.com/androidx/media/blob/1.11.1/libraries/common/src/main/java/androidx/media3/common/util/GlUtil.java). Actual HDR presentation, colour accuracy and sustained performance still require verification on the Valerion firmware; the emulator does not establish those.

### Experimental Live HLG Output (alpha.3)

For Android 12, enable **HDR effect**, select **HDR output > Auto** (or **HLG (experimental)**), and preview a known HDR10 / BT.2020 PQ clip. Dolby Vision and HLG *input* clips still use the native player; HLG here describes the effect's output. Settings and source quality are independent. A disabled HDR toggle from alpha.1/alpha.2 remains disabled after updating.

The live path is HDR10 decoder YUV -> Media3 PQ decoding -> FP16 effect -> HLG output shader -> RGB10_A2 EGL surface tagged BT.2020/HLG. No pre-rendering, encoder, network service, CPU frame readback or additional full-size intermediate texture is introduced. Rendering remains up to 3840x2160, and overlays stay separate.

HLG conversion uses a 1000-nit reference display and the BT.2100 inverse OOTF (gamma 1.2) followed by the HLG OETF. A smooth, hue-preserving highlight shoulder above 500 nits maps brighter PQ values into HLG headroom rather than clipping everything above 1000 nits; very saturated out-of-gamut colours are compressed towards equal-luminance grey. This is HDR-to-HDR adaptation, not bit-exact HDR10 passthrough or SDR tone mapping. Brightness/highlight rendering can differ from native PQ playback. PQ mastering metadata is not attached to the HLG surface. The HLG transfer and EGL tagging are specified in [Khronos EXT_gl_colorspace_bt2020](https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_gl_colorspace_bt2020_linear.txt).

The settings show the available output path and missing prerequisite; **Playback status** records the actual selected path, buffer size and frame counters. Android 12 is no longer rejected solely because PQ output is unavailable. If the driver lacks HLG EGL output or raw HDR sampling, this release cannot make it available. It does not modify firmware or force a system display mode.

The HDR status preference reports display/EGL prerequisites; a successful playback result reports actual buffer size, RGB10_A2 output and FP16 processing. Failure displays a reason and restores native video for that session, without permanently disabling the effect. There is no silent conversion of HDR files into SDR effects. HLG input, Dolby Vision, unsupported BT.2020 transfers, or unavailable HDR hardware use that native fallback. HDR10+ can use its PQ base picture; dynamic HDR10+ metadata is not preserved. Unlabelled HDR files cannot be identified reliably.

Switching between SDR and HDR recreates the surface and starts a new colour field, so the previous image is not carried across that format boundary and a brief black interval can occur. Consecutive HDR10 clips retain the freeze/band transitions. Photos still use the SDR path; wide-gamut/Ultra HDR photo output is not implemented. Refresh-rate switching is suppressed while the Toposcan preference is enabled. Native Android clock/date overlays remain separate from the effect.

The colour adapter is isolated in `HdrFramePipeline` and uses Media3 1.11.1's library-group `DefaultShaderProgram` API. The dependency is pinned; rerun pixel tests and review [the upstream implementation](https://github.com/androidx/media/blob/1.11.1/libraries/effect/src/main/java/androidx/media3/effect/DefaultShaderProgram.java) before upgrading Media3. Static metadata uses [SMPTE2086 EGL attributes](https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_surface_SMPTE2086_metadata.txt) and [CTA861.3 EGL attributes](https://registry.khronos.org/EGL/extensions/EXT/EGL_EXT_surface_CTA861_3_metadata.txt).

There is no advance test that guarantees a prototype will never hang, crash, trigger a driver defect or cause additional heat. This is an ordinary, uninstallable Android app, not firmware or a root modification. No projector, bootloader, laser, fan or factory-calibration interfaces were added. The built APK has no WRITE_SECURE_SETTINGS, REBOOT or device-administrator permission. Existing upstream media/network permissions remain, as do optional overlay and notification integrations; do not grant those optional integrations for the first preview. Published `immersion` builds exclude Firebase SDKs. The older `beta` prototype still includes them with debug collection defaults disabled; it is not the published release variant. Debug builds are not production-hardened releases.

Recommended staged check:

1. Keep the original Ambient Mode selected. Do not run the `settings put` command below yet. Record the current component and power timeout values, optionally using the read-only preflight script.
2. Install the separate **Async Immersion** release package. Do not use `adb install -g`, root, bootloader unlock, firmware flashing or permission-grant commands. Streaming does not require granting access to all local media; a USB/local library does require the appropriate media access.
3. First use **Toposcan > Preview (2 minutes)** with a local 4K SDR clip. Then repeat with a known HDR10 clip and inspect HDR status. Check image detail, colours, band movement, clock, Home/Back exit, and return to normal apps. Compare the projector's signal information and the image with ordinary playback of the same HDR10 file. The timeout runs on the app's UI thread; it is not an independent hardware watchdog.
4. If stable, run a supervised 10-15 minute normal preview with unobstructed ventilation and unchanged manufacturer picture/laser settings. Stop if there are warnings, unexpected shutdowns, unusual heat/noise or persistent unresponsiveness. A few dropped frames indicate a performance problem, not proof of hardware damage.
5. Only then select the new screensaver. Check remote wake, standby/power-off, and the configured sleep timeout. Revert the component before uninstalling if you have selected this screensaver. Do not leave the projector running unattended until these checks pass. A screensaver still uses the light source; it does not replace standby.

The two-minute preview itself uses the upstream KEEP_SCREEN_ON window flag while visible, then removes it when stopped. Neither that preview nor the script changes the system's sleep timeout. The app's blackout mode clears the effect layer to black, but does not switch off the projector's laser. The projector's actual standby behaviour still needs testing.

Read-only inspection (after pairing ADB yourself):

```sh
adb devices -l
sh scripts/toposcan-preflight.sh YOUR_DEVICE_SERIAL
```

This only reads model/firmware, display modes, thermal service status (where available), existing screensaver/sleep settings and this app's package information. A normal thermal report is not a hardware safety certificate. The output may contain identifiers; redact those before sharing.

Optional ADB preview and exit, without changing the default screensaver:

```sh
adb -s YOUR_DEVICE_SERIAL shell am start -n com.i1bro.googletv.asyncimmersion/com.neilturner.aerialviews.ui.screensaver.TestActivity --ei toposcan_preview_timeout_seconds 120
adb -s YOUR_DEVICE_SERIAL shell am force-stop com.i1bro.googletv.asyncimmersion
```

For rollback, restore the screensaver component as explained below, then uninstall **Async Immersion** in Settings > Apps, or `adb -s YOUR_DEVICE_SERIAL uninstall com.i1bro.googletv.asyncimmersion`. This deletes only this fork app and its data; it does not uninstall Google Ambient Mode or the separate Play Store Aerial Views app. For debug, append `.debug` to the application ID in these commands. If the device becomes unresponsive, use its normal manufacturer-supported restart procedure, not repeated abrupt power cuts.

## Set As The TV Screensaver

Pair/connect ADB using the device's developer settings. Wireless-debugging pairing ports and connection ports can differ. Record the current component before changing it:

```sh
adb shell settings get secure screensaver_components
adb shell settings put secure screensaver_components com.i1bro.googletv.asyncimmersion/com.neilturner.aerialviews.ui.screensaver.DreamActivity
adb shell settings get secure screensaver_components
```

The full service class above is intentional: the fork application ID differs from the Kotlin package name. For the debug APK, append `.debug` only to the application ID before the slash. To revert, restore the exact previous component value. If the previous setting was absent (`null`), delete the override with `adb shell settings delete secure screensaver_components` instead of storing the string `null`.

This does not uninstall or disable Google Ambient Mode. It also does not change power-management or sleep timeouts. Those and manufacturer-specific auto-launch permissions must be checked on the actual TV. See the [upstream installation instructions](https://github.com/theothernt/AerialViews#how-to-set-aerial-views-as-the-default-screensaver).

## Media Sources

The original source menus/providers are unchanged. Built-in JSON catalogues contain URLs and descriptions, not the videos themselves:

| Catalogue | Media delivery |
| --- | --- |
| Apple aerials | Apple CDN, including sylvan.apple.com; multiple SDR/HDR and codec variants |
| Amazon Ambient | Amazon Fire TV CDN, aebcs-cdn-prod-na.services.firetv.amazon.dev |
| Jetson Creative community videos | GitHub release assets in glouel/AerialCommunity |
| Robin Fourcade | GitHub release assets in RobinFrcd/AerialShots |

The bundled catalogues are video collections. Photos and personal videos can come from device/USB storage, SMB/Samba, WebDAV, Immich, Nextcloud Memories, or custom feeds. Custom feeds accept CSV lists of image/video URLs and the supported JSON feed format. This does not connect to Google Ambient Mode's private photo catalogue or inherit Google Photos albums.

For the installation-like appearance, stationary-camera landscapes work better than moving-camera aerials. The browser prototype's Pexels lake/coast/stream clips can be transferred to the TV or a local share and selected through these existing providers. No originals from the exhibition are included. Media rights are separate from the app's GPL license; catalogue inclusion does not grant redistribution rights.

There is no new full-video download cache in this prototype. Upstream playlist caching caches the playlist, not all footage. Local/USB media is the reliable offline option; remote video still depends on streaming and buffering.

## Rendering And Limits

Media3/ExoPlayer decodes video to a SurfaceTexture. OpenGL ES copies the incoming frame into a GPU texture, records only newly crossed columns into a persistent history texture, and draws frozen image, live video and horizontal bands. Finished history carries into the next colour-field transition. Android overlay views remain outside this effect.

Each consumed frame advances the reveal/freeze by one nominal frame interval. Missing frames and long scheduling delays are never recovered by a wide catch-up step. Unknown frame rates fall back to 30 fps. At playback rates other than 1x, scan duration follows the delivered frame cadence. Photo animation uses bounded wall-clock steps. Pause freezes the media layer while the native clock remains independent.

Prototype differences from normal Aerial Views:

- Toposcan owns per-item duration. Normal photo/video duration limits and their progress indicators do not represent its full scan cycle. Short videos loop until the freeze finishes.
- Full-screen centre crop is used. Photo blur/background and portrait video rotation options are not applied to the processed image. Animated photo formats are treated as a still.
- HDR10 processing is capability-gated as described above, with PQ or experimental HLG output. HLG input, Dolby Vision and incompatible HDR devices use the native-player fallback. Tunnelling is disabled while the mode is enabled.
- Weather UI remains present, but a local build needs its own OpenWeather key in the ignored `secrets.properties` (`openWeatherDebug=...`). No private upstream key is supplied, and live weather has not been verified.
- SDR graphics initialization/rendering failure turns off Toposcan and restores the standard player. HDR graphics failure reports its reason and restores native playback for that session.
- Native Google Ambient Mode widgets, AI art and its Google-account integrations are not part of this app.

## Verification

```sh
./gradlew :app:formatKotlin
./gradlew :app:testBetaDebugUnitTest :app:lintKotlin :app:assembleBetaDebug
./gradlew :app:connectedBetaDebugAndroidTest
./gradlew :app:testImmersionDebugUnitTest :app:assembleImmersionDebug :app:assembleImmersionRelease
./gradlew :app:connectedImmersionDebugAndroidTest
```

The added JVM tests cover stalled/duplicate frames, pause, loop timestamps, zero freeze delay, direction changes, trailing completion, render-size caps, HDR colour policy, HDR capability gates and static-metadata units. Instrumented tests run the real ScreenController/ExoPlayer/GLSurfaceView with a generated H.264 fixture and a local test feed, check frozen versus live pixel changes, pause stability, black pixels during effect blackout, clock presence, video-photo-video transitions, one-pixel photo detail in a 3840x2160 buffer, native-surface fallback and timed preview exit without changing the default screensaver. Fixtures are test-only and are not bundled in the app APK.

`VideoFrameDeliveryTest` deliberately removes SurfaceTexture frame callbacks and does not register a decoder first-frame listener. It checks that polling still starts playback, advances the reveal and produces nonblack pixels. JVM startup-gate tests cover both metadata/frame arrival orders and per-clip reset.

`GraphicsDiagnosticActivityTest` exercises all six diagnostic stages, verifies their submitted surface pixels with PixelCopy, checks both real buffer sizes and unchanged playback preferences/status, and verifies that early exit leaves an unconfirmed result. JVM tests reject black, cropped and incorrectly ordered colour samples.

HDR output tests cover Auto/forced selection, Android 12 HLG prerequisites, rejection of mismatched EGL tags, PQ round-trip precision, PQ-to-HLG 10-bit gradients against an independent double-precision reference, distinct 1000/4000/10000-nit highlights and saturated-colour gamut limits. Offscreen tests verify processing, not physical HDR presentation. The physical HDR playback test remains capability-gated.

`HdrRenderingTest` runs the production effect shader and Media3 colour shaders in an offscreen GLES 3 context. It checks PQ -> FP16 -> effect -> 10-bit PQ round-trip accuracy, more than 900 distinct code values, retained highlights up to 10,000 nits in signal space, and linear-light field blending. It does not assert that the emulator's screen emits HDR.

`HdrDevicePlaybackTest` uses a generated 3840x2160, 24 fps, HEVC Main10, BT.2020/PQ fixture and the actual HDR EGL window / decoder / effect path. It is explicitly skipped when display/OS prerequisites fail, including on the SDR emulator. Run it on the target device; a passing offscreen test is not a substitute.

Verified on an Android 15 ARM64 emulator in landscape. This is not a substitute for testing the TV's hardware decoder, GPU, remote focus and idle/standby lifecycle. Installation and performance on the user's Google TV remain unverified.

To regenerate the synthetic test fixture with FFmpeg:

```sh
ffmpeg -f lavfi -i "nullsrc=s=640x360:r=25,geq=r='40+X/W*180':g='30+Y/H*180':b='mod(N*7,255)'" -t 20 -c:v libx264 -preset fast -crf 18 -pix_fmt yuv420p -movflags +faststart app/src/androidTest/assets/toposcan-test.mp4

ffmpeg -f lavfi -i testsrc2=size=3840x2160:rate=24 -t 4 -vf format=yuv420p10le -c:v libx265 -preset ultrafast -crf 28 -x265-params 'pools=1:log-level=error:hdr10=1:repeat-headers=1:master-display=G(13250,34500)B(7500,3000)R(34000,16000)WP(15635,16450)L(10000000,50):max-cll=1000,400' -color_primaries bt2020 -color_trc smpte2084 -colorspace bt2020nc -tag:v hvc1 -movflags +faststart app/src/androidTest/assets/toposcan-hdr10-test.mp4
```
