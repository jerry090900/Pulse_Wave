# Updateable PulseWave Preview — persistent signing

This is a **new installation lineage** for `com.jerry.sharpwave.preview`.
It does **not** recover or replace the signature of the original `com.jerry.sharpwave`.
Uninstall **only** the old PulseWave Preview **once** before installing this first
permanently signed Preview APK. Its existing Preview-only data will be lost.
Never uninstall the original PulseWave as part of this procedure.

## One-time setup (on your own Termux phone)

Prerequisites: GitHub CLI authorized for `jerry090900/Pulse_Wave`,
JDK keytool, base64, Bash. On Termux: `pkg install gh openjdk-17 coreutils`;
then `gh auth login` if necessary.

From the repository's `preview/updateable` branch:

```bash
bash scripts/setup-preview-signing.sh
```

The script creates **one** private PKCS12 keystore outside the repository at
`~/.local/share/pulsewave-signing/preview-updateable.p12`
(or below `$XDG_DATA_HOME`), using an interactive hidden password. When it
already exists, the script reuses it, never silently rotates it. If GitHub
already has a key but the local key is missing, setup stops and asks for a backup.

The script sets these **GitHub Actions secrets**:
- `PW_PREVIEW_KEYSTORE_BASE64` — private keystore bytes (base64 encoded)
- `PW_PREVIEW_STORE_PASSWORD`
- `PW_PREVIEW_KEY_ALIAS`
- `PW_PREVIEW_KEY_PASSWORD`

It also pins one **GitHub Actions repository variable**:
`PW_PREVIEW_CERT_SHA256`, the public expected signing-certificate SHA-256.

**Immediately back up the keystore and password privately to a secure offline
location.** Losing them may make future updates impossible. Never paste
passwords, keystore contents, base64 of the keystore, or secret values into chat,
commits, screenshots, issue comments or workflow logs. Do not use the old
ephemeral CI debug certificate as the new key.

## CI behavior

- Branch `preview/updateable` is the **only branch** that publishes an
  installable updateable Preview APK. Pushes there require the four secrets
  and matching pinned fingerprint; when anything is missing/changed, upload
  is blocked instead of shipping an incompatible build.
- PR tests still run without secret access. Their throwaway-signed test APK
  is **not uploaded** as an installable build.
- Workflow computes `versionCode = 10000 + GITHUB_RUN_NUMBER`. Each normal
  new run increases this value; reruns of the same run retain the same code.
- The Android package remains exactly `com.jerry.sharpwave.preview`;
  versionName starts at `0.1.6-preview` and can be changed independently.
- User-visible artifact name: `PulseWave-Preview-UPDATEABLE-apk`.
- Before installing, verify the package, versionCode and certificate
  digest in the Actions logs (workflow checks them automatically).
- After setup, rerun the **Build Android APK** workflow on
  `preview/updateable`, or push a reviewed code change to that branch.

**Important:** Do not run untrusted PR code or change the signing workflow
without reviewing it. Repository secrets are protected but are made available
to workflows on the trusted signing branch. Keep its write access limited.

## Updates

For every future Preview version, use the `preview/updateable` signing
pipeline with its unchanged key. Android will update in place when all three
conditions hold:
1. Identical application ID: `com.jerry.sharpwave.preview`.
2. Identical signing certificate (or supported signing lineage).
3. Same or newer acceptable versionCode, preferably strictly increasing.

Changing the keystore or changing back to `com.jerry.sharpwave` will break
in-place Preview updates. A different release identity needs its own plan.

Music sync, capture callbacks, FFT and Wave geometry are outside the scope of
these signing changes. On-device installation and update tests still need to
be performed on the Nothing Phone after the first signed artifact exists.
