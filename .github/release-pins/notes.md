# PulseWave Preview v0.2.0

The first public **pre-release** of PulseWave, an open-source Android music visualizer overlay.

### What's included
- Top-edge, downward-growing audio equalizer bars; classic red/white style remains available
- PulseWave Studio with adjustable colors, bar shapes, spacing, height, sensitivity and Wave effects
- Optional **Muviz-style Color Layers**: cyan/white/pink regions within each bar, with music-reactive boundaries (experimental)
- Visualizer can remain visible while PulseWave itself is open; adjustable in-app switch
- **Off** action in the ongoing service notification
- **Reset visualizer settings** action that keeps saved Studio designs and selected apps
- Optional Beat Sync Pro / adaptive visual response (off by default)

### Install and update
1. Download `PulseWave-Preview-v0.2.0.apk` below (APK, not the GitHub source archive).
2. Install it on Android 10 or newer and grant requested display-overlay, audio visualization, and selected-app usage access.
3. If you're already using the permanently signed Preview 0.1.6–0.1.9, install this **over it**. Do not uninstall; the package and signing identity are preserved.
4. The Preview's package is `com.jerry.sharpwave.preview`. It is **separate** from the original `com.jerry.sharpwave` app and cannot update that original installation.

### Known limitations
- This is **not yet a stable 1.0 release**. The three-color effect is still being compared with a user-supplied Muviz reference, and visual similarity is not guaranteed.
- The app uses Android's output-mix Visualizer API; music capture, Bluetooth response, lock-screen overlays and status bar behavior vary by device.
- Studio playback is simulated; phone verification is still required for real audio synchronization and Android notification Off behavior.
- The real 0.2.0 home screen is captured on a clean Android 15 emulator below. This is a UI screenshot, **not proof of live music visualization**, and actual Nothing Phone overlays may differ.

### Screenshot (clean Android emulator)

![PulseWave Preview v0.2.0 home screen](https://github.com/jerry090900/Pulse_Wave/releases/download/v0.2.0-preview/pulsewave-v0.2.0-emulator-home.png)
- No association with or endorsement by the Muviz app.

### Verification
- Build GitHub Actions [#42611824671](https://github.com/jerry090900/Pulse_Wave/actions/runs/42611824671): tests and signing checks passed.
- Android package: `com.jerry.sharpwave.preview`; versionCode: `10052`; versionName: `0.2.0-preview`
- APK SHA-256: `c60b9cec81dd19b08ab3e55ff16d392bc271ad0b3c43c06454d482dafe21662b`
- Signing certificate SHA-256: `2e17f55689ce2bbeff2326d8b12d4e68e4bec3b7b6e2e5e5ce0ae724aba3c6a6`

MIT licensed. Please open an issue for bugs or feature requests and include the app version and phone model, not private signing data.
