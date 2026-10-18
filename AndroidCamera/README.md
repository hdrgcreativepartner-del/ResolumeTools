# HDRG Camera — Android (prototype)
Android native camera app: video recording, timelapse capture rate, background recording via visible foreground-service notification.

**Status:** Experimental source. Do not label as production APK until build succeeds and runtime device tests pass.

## Privacy & platform restrictions
- Camera recording only starts after the user taps START in the foreground and grants CAMERA permission.
- Camera foreground service always shows a persistent recording notification with a STOP action.
- No stealth, auto-start, invisible notification, remote streaming or upload.
- Background camera access may be restricted by Android/OEM battery policy. Force-stop stops recording.
- Media saved under Android app-specific Movies folder; later version should add an explicit export-to-gallery workflow.
- Current prototype records video **without audio** to minimize permissions.
- Timelapse uses MediaRecorder captureRate and depends on camera/encoder device support; results may vary.
- No live viewfinder yet: this is an initial engineering prototype, **not a polished camera app**.

## Features next
Camera preview with CameraX/PreviewView, pause/resume recording, quality presets, stabilization, audio toggle, timelapse interval testing, storage notifications, automatic file recovery and MediaStore export.

## Build
On GitHub Actions, run **Build HDRG Camera APK** and download its debug APK artifact after successful completion. APK is debug-signed for testing, not Play Store production.
