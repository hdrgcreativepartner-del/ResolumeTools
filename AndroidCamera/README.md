# HDRG Cam — Android

Native Camera2 video and timelapse application, Android 8.0 (API 26) or newer. Current candidate: **0.6.3-rc4**, version code 9. Release candidates use a test certificate and are not commercial releases.

## RC4 repairs

- Explicit hardware acceleration for the TextureView preview. The manual packager writes `uses-sdk` before `application`, avoiding target-dependent defaults being evaluated too early.
- Preview readiness is determined by delivered frames. A missing or stale preview disables recording, displays recovery guidance and retries once with automatic camera controls.
- Header, viewfinder and capture controls occupy separate layout regions. Grid bounds follow the fitted preview, system-bar insets are applied once and controls remain reachable in landscape.
- Centered camera icons and gallery thumbnail, compact tools, consistent dark sheets and the supplied HDRG launcher logo.
- R8 removes unused Kotlin runtime code. Both CI debug and manual delivery builds apply shrinking.

## Functionality

Live preview, supported video resolutions, optional microphone, timelapse, front/rear camera switch, pause/resume, manual controls where supported, gallery playback and MediaStore export. Recording continues through a visible foreground-service notification with a Stop action; OEM background restrictions still apply. The app does not stream or upload recordings.

## Build and verification

GitHub workflow `hdrg-camera-android.yml` builds the app and instrumentation APK, then runs a camera-equipped Android emulator. Its smoke test requires real TextureView frame callbacks, hardware acceleration, recording, saving, resumed preview and camera switching. Screenshots and logcat are retained as workflow artifacts. Check the run result; the existence of an APK does not establish that tests passed.

Fallback build: Kotlin 2.0.21, JDK 17, Android SDK platform/build-tools 35. Set `ANDROID_SDK_ROOT`, `KOTLIN_HOME`, optionally `ANDROID_APPLICATION_ID`, then run `bash build-sdk.sh`. Sign the resulting unsigned APK using the existing candidate certificate to retain update compatibility. Do not commit signing keys.

Commercial release still requires a private production signing identity and physical-device checks, including the reported device, permissions, background behavior, timelapse, thermal endurance and install/update timing. Emulator results cannot establish OEM compatibility or installation speed.
