#!/usr/bin/env bash
# One-time setup for the personal updateable PulseWave Preview signing key.
# Run interactively on your own phone/Termux. Do not share the keystore/password.
set -euo pipefail
umask 077

REPO="jerry090900/Pulse_Wave"
ALIAS="pulsewave-preview"
KEY_DIR="\${XDG_DATA_HOME:-$HOME/.local/share}/pulsewave-signing"
KEYSTORE="$KEY_DIR/preview-updateable.p12"

for executable in gh keytool base64 awk tr grep; do
  if ! command -v "$executable" >/dev/null 2>&1; then
    printf 'Missing %s. In Termux, install gh, openjdk-17 and coreutils first.\n' "$executable" >&2
    exit 1
  fi
done

if ! gh auth status >/dev/null 2>&1; then
  echo "GitHub CLI is not authenticated. Run: gh auth login" >&2
  exit 1
fi

mkdir -p "$KEY_DIR"
chmod 700 "$KEY_DIR"

if [ ! -f "$KEYSTORE" ]; then
  # Replacing an existing server-side key would permanently break APK updates.
  if gh secret list --repo "$REPO" --json name --jq '.[].name' \
       | grep -qx 'PW_PREVIEW_KEYSTORE_BASE64'; then
    echo "STOP: GitHub already has a Preview signing key but your local keystore is missing." >&2
    echo "Recover the backup instead of generating another signing identity." >&2
    exit 1
  fi

  echo "Creating your ONE permanent PulseWave Preview signing key."
  echo "Choose and safely save the password. It will not appear on the screen."
  keytool -genkeypair -keystore "$KEYSTORE" -storetype PKCS12 \
    -alias "$ALIAS" -keyalg RSA -keysize 3072 -validity 10000 \
    -dname "CN=PulseWave Preview, O=Private Android Testing, C=IN"
fi
chmod 600 "$KEYSTORE"

printf 'Enter the keystore password (hidden): '
IFS= read -r -s PASSWORD
printf '\n'
if [ -z "$PASSWORD" ]; then
  echo "A nonempty password is required." >&2
  exit 1
fi
trap 'unset PASSWORD' EXIT

# Read the public certificate fingerprint without putting the password in argv.
LISTING="$(printf '%s\n' "$PASSWORD" | keytool -list -v \
  -keystore "$KEYSTORE" -storetype PKCS12 -alias "$ALIAS" 2>/dev/null)" || {
  echo "Could not validate the keystore/password. No GitHub secret was changed." >&2
  exit 1
}
FINGERPRINT="$(printf '%s\n' "$LISTING" | awk '/SHA256: / { gsub(":", "", $2); print tolower($2); exit }')"
if [[ ! "$FINGERPRINT" =~ ^[0-9a-f]{64}$ ]]; then
  echo "Could not read a valid SHA-256 fingerprint. No GitHub secret was changed." >&2
  exit 1
fi

echo "Uploading protected signing material to GitHub Actions secrets..."
# GitHub CLI reads secret values from stdin; none are printed or placed in argv.
base64 < "$KEYSTORE" | tr -d '\n' \
  | gh secret set PW_PREVIEW_KEYSTORE_BASE64 --repo "$REPO"
printf '%s' "$PASSWORD" \
  | gh secret set PW_PREVIEW_STORE_PASSWORD --repo "$REPO"
printf '%s' "$ALIAS" \
  | gh secret set PW_PREVIEW_KEY_ALIAS --repo "$REPO"
printf '%s' "$PASSWORD" \
  | gh secret set PW_PREVIEW_KEY_PASSWORD --repo "$REPO"
printf '%s' "$FINGERPRINT" \
  | gh variable set PW_PREVIEW_CERT_SHA256 --repo "$REPO"

echo
echo "Preview signing configured. Public certificate SHA-256: $FINGERPRINT"
echo "PRIVATE KEY BACKUP REQUIRED: $KEYSTORE"
echo "Back up this .p12 file and its password privately, outside the repository."
echo "Never upload the .p12 file to a GitHub commit, chat, or public file share."
echo "Now rerun the signed Preview workflow for branch preview/updateable on GitHub."
