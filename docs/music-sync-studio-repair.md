# Music response and Studio persistence repair

This change targets `preview/updateable`; it does not implement album-art sync,
projectM integration, new palettes or gallery pruning.

## Music response

Reference: 0.1.7 commit `d803aff6cb45e6303f72c2310567e37340f9f6d7`.
The output-mix capture setup (including scaling flags, size and maximum capture
rate), waveform/FFT callbacks, frequency mapping, attack/release smoothing and
baseline response settings are unchanged from that commit. The smoothed levels
now feed the renderer directly again. Beat Sync Pro, adaptive gain and response
profile processing and their controls have been removed, including the processor
class. Old preference entries are inert; an explicit visual reset can remove them.
Recent Studio rendering modes, notification Off and overlay visibility controls
remain available. This is response-path parity, not a claim of phone visual parity.

## Settings ownership

Home width, gap, height and sensitivity controls edit those fields on the applied
Studio preset and persist them along with the corresponding global preferences.
They preserve the preset ID, name, type, custom colors and other appearance fields;
other saved presets are not edited. The palette control explicitly replaces only
colors using DHH Signature and retains the active style. Bass gain stays global.
Reapplying a saved style therefore uses the edited geometry rather than stale
values. Save and Apply retain their existing behavior in the Studio editor.

The existing v1 JSON collection and active IDs remain compatible. The old
one-time Wave-to-classic migration is retired without clearing the active preset.
An active Wave already cleared by an older build cannot be inferred safely; the
saved Wave is retained and can be explicitly reapplied. Classic bars and confirmed
visual reset remain explicit ways to deselect the active design.

## Validation and remaining phone QA

Run from the repository with SDK 36, build tools 36.0.0 and Gradle 9.6:

```sh
gradle :app:testDebugUnitTest :app:assembleDebug
```

In this cloud workspace the configured launcher is
`/workspace/.pulsewave-tools/gradle`. Host tests include real JSON serialization
and an in-memory SharedPreferences implementation for cold-context persistence;
they do not simulate Android UI/lifecycle behavior.

Before release on the Nothing Phone:

- Compare actual music playback with 0.1.7: pause/resume, quiet/loud sections,
  output device changes, app switching and screen lock/unlock.
- Save and apply a custom Wave/Bars/Spikes design; return home, edit height,
  width and gap, then reopen Studio and restart the process. Verify the same
  design, colors and edited geometry remain selected.
- Verify explicit palette selection, Classic bars and confirmed reset scopes.

No permanent signing key, signing configuration, version or release asset is
changed. Local debug assembly is build validation only; no APK is published.
