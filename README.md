# PulseWave

> **Public Android pre-release:** [Download PulseWave Preview v0.2.0](https://github.com/jerry090900/Pulse_Wave/releases/tag/v0.2.0-preview).
>
> This release uses the `com.jerry.sharpwave.preview` app identity and permanent update-signing key. It is separate from the original `com.jerry.sharpwave` app. **Do not uninstall your existing signed Preview when updating.**
>
> The release's current source code and development features are on [`preview/updateable`](https://github.com/jerry090900/Pulse_Wave/tree/preview/updateable), while this `main` branch retains the earlier baseline. Future preview releases will receive new version tags and APK assets.
>
> **Status:** Experimental visualizer; on-phone visual matching and compatibility testing remain in progress. See the release notes and known limitations before installing.

### Preview screenshot

<img src="https://raw.githubusercontent.com/jerry090900/Pulse_Wave/preview/updateable/docs/screenshots/pulsewave-v0.2.0-emulator-home.png" width="320" alt="PulseWave Preview 0.2.0 home screen captured in a clean Android emulator">

*Actual 0.2.0 emulator screenshot; real-music visual effects and device overlays still need phone testing.*

See [new Preview features and development instructions](https://github.com/jerry090900/Pulse_Wave/blob/preview/updateable/README.md).


PulseWave is a small open-source Android audio visualizer overlay built around the requested design:

- **sharp triangular spikes** (no rounded ends)
- **full-width top overlay**
- **tiny adjustable gaps**
- reacts to music using Android's `Visualizer` output-mix API
- **Selected Apps**: the effect appears while a chosen app was recently in the foreground and audio is active
- default palette: **white `#FFFFFF` + grey `#9E9E9E` + deep red `#9F000E`**
- adjustable spike width, gap, maximum height and sensitivity
- no ads and no network permission

## Permissions

PulseWave asks for three user-facing permissions:

1. **Display over other apps** — draws the transparent visualizer overlay.
2. **Record audio** — required by Android's `Visualizer` API even though PulseWave uses the low-quality output mix for visualization and does not save audio.
3. **Usage access** — used to detect when a selected app was recently in the foreground. PulseWave no longer requests notification access, which avoids the Play Protect notification-listener warning.

PulseWave cannot identify exactly which app is producing sound without notification access. It keeps the overlay active while a selected app is foreground (or was the recent selected source within a short grace period) and audio/visualizer activity is present.

It also runs a low-priority foreground service while enabled so Android does not kill it between songs.

## Android support

- minSdk: 29 (Android 10)
- target/compileSdk: 36 (Android 16)
- Java 17
- Android Gradle Plugin 9.4.1
- Gradle 9.6.0

## Build

The included GitHub Actions workflow builds a debug APK automatically after a push.

Local build, with Android SDK 36 and Gradle 9.6 installed:

```bash
gradle :app:assembleDebug
```

APK output:

```text
app/build/outputs/apk/debug/app-debug.apk
```

## Experimental lock-screen mode (screen on only)

PulseWave has an explicit **Experimental lock screen** setting. When it is enabled and a selected media app is active, the service:

- starts a transparent, non-focusable `showWhenLocked` activity host above the screen-on keyguard using the public activity API;
- adds `FLAG_SHOW_WHEN_LOCKED` to the overlay window as a best-effort fallback for OEMs that honour it;
- falls back to the normal overlay and reports the limitation in the foreground notification if the OEM or display state prevents the host from appearing.

It does **not** request `WAKE_LOCK`, does not call `setTurnScreenOn(true)`, and does not keep the display awake. True always-on-display rendering while the panel is off is not possible with public third-party APIs, so PulseWave neither claims nor fakes it. Results still need runtime validation on the target Nothing Phone because OEM keyguard and background-activity-launch policies vary.

## Important Android limitation

A normal third-party overlay cannot replace Android's actual status icons. PulseWave draws at the top edge using `TYPE_APPLICATION_OVERLAY`. Whether it visually sits behind/around the status-bar area can vary slightly by ROM and display-cutout policy.

Also, some devices or ROMs may restrict global audio-session `Visualizer(0)`. If that happens, a future fallback can use Android's Audio Playback Capture / MediaProjection flow, which requires an explicit system capture confirmation.

## License

MIT
