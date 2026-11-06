# PulseWave

PulseWave is a small open-source Android audio visualizer overlay built around the requested design:

- **sharp triangular spikes** (no rounded ends)
- **full-width top overlay**
- **tiny adjustable gaps**
- reacts to music using Android's `Visualizer` output-mix API
- **Selected Apps**: the effect appears while a chosen app was recently in the foreground and audio is active
- default palette: **white `#FFFFFF` + grey `#9E9E9E` + deep red `#9F000E`**
- adjustable spike width, gap, maximum height and sensitivity
- no ads and no network permission

## PulseWave Studio — initial editor milestone

Open **PulseWave Studio** on the home screen to browse original style presets, tap **Edit**, see a generated motion preview, and **Save** or **Apply**. Styles are stored locally in private app preferences.

- **Audio styles**: joined white/red bars, thin bars, spikes and waves. Saving and applying an audio style updates the existing output-mix visualizer overlay when it is enabled. Existing selected-app detection and service permissions are unchanged.
- **Photo clock**: choose an image through Android's **Open Document** picker; the app retains only a user-granted read URI and renders the photo with an adjustable opacity and clock in its in-app preview. Photos are not uploaded or copied to a hidden server.
- **Fingerprint halo**: adjustable size, position, colors and animation in an in-app preview. It is **not** a real biometric sensor animation and cannot detect or replace the system fingerprint unlock screen.
- **Preset editing**: color selections, width/gap/height/sensitivity, local Save, Duplicate and Apply. Legacy visualizer controls remain available and override a selected Studio audio preset when manually changed.

**Limitations of this milestone:** The Studio uses *generated demo motion*, not live captured audio, inside the editor. Applying photo and fingerprint presets selects only their Studio preview; it does **not** replace OEM AOD or system biometric behavior. No background AOD service, Accessibility access, notification listener, or extra privileged permissions were added. Different devices may limit overlay and audio capture. Android 10–17 compatibility has not been validated on hardware.

Planned next milestones: draggable canvas elements, richer photo masks/layouts, preset import/export, opt-in media notification sessions, charging effects, sensor-driven rules, plus real-device power and lock-screen QA.

## Screen-centered visualizer / audio-response test fix

This candidate adds a **Position: Middle** default (with Top and Bottom choices), symmetric **bass-in-the-center** mapping, a consistent bottom baseline for the equalizer, **Solid-color bars**, and a tunable **Bass response** slider. The legacy two-tone style remains available by turning solid colors off.

The **Follow playback loudness** toggle defaults to Android's `SCALING_MODE_AS_PLAYED` to preserve changes in volume. If Bluetooth or a player produces weak/empty output-mix data, switch it off to restore normalized capture. **Check live audio signal** reports whether Android has delivered any recent FFT peaks; it does not identify the source app or prove musical timing is correct.

The experimental screen-on lock-screen host retains a previously selected music source briefly when switching through the home screen before locking, as long as media remains active. It still cannot replace Android's secure lock screen, display-off AOD, or system fingerprint unlock animations; stock Android may suppress the host entirely due to background-activity launch and keyguard policy.

The candidate is a **draft test build**. Verify audio/visual sync, position, selected app continuity, screen-on lock test, and update signing on the target Nothing Phone before merging. Existing installation signing may not match GitHub Actions debug signing.

## One built-in theme and optional music-session notification access

PulseWave now ships **one** legacy built-in theme: **DHH Signature** (white, deep red, gray). The nine other legacy named themes are removed from the built-in palette menu and renderer. Stored legacy palette indices safely fall back to DHH Signature. User-created PulseWave Studio preset designs are kept.

On the home page, **Access → Music notification access (optional)** opens Android's notification access settings. PulseWave also provides a separate **Use playing media sessions from selected apps** switch, initially **off**. Both explicit opt-in and OS-level permission are required.

When enabled, PulseWave uses `MediaSessionManager.getActiveSessions()` through its authorized `NotificationListenerService` solely to identify the *package name* of **selected** apps with a media session in PLAYING state. This allows music-source tracking when switching apps or locking the screen; it **does not provide waveform/FFT data**, so the Android output-mix Visualizer remains responsible for animating the bars.

The Android notification-access permission is broad and technically allows access to all notifications. This implementation does **not** process or store notification titles, text, messages, contacts, album artwork, or session metadata. No remote upload is implemented. Users can disable the switch or revoke notification access in Android settings. The app continues to support its previous non-notification fallback. Android or OEM policy may restrict the feature on sideloaded apps.

**Important:** Successful unit tests/CI compilation are not the same as proving real music synchronization, notification-access behavior, Bluetooth audio capture, or lock-screen visibility on the Nothing Phone. Keep this branch separate from main until device QA.

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

### Termux-specific AAPT2

The shared `gradle.properties` intentionally does **not** reference a Termux filesystem path, because GitHub Actions uses an Ubuntu runner and must use its own AAPT2.

If building directly inside Termux requires its local ARM64 `aapt2`, first verify that `$PREFIX/bin/aapt2` exists. Then supply that override **only for the local build**:

```bash
gradle -Pandroid.aapt2FromMavenOverride="$PREFIX/bin/aapt2" :app:assembleDebug
```

This Termux command is not used by the GitHub Actions workflow. Its success depends on the actual local Android SDK and AAPT2 installation.

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
