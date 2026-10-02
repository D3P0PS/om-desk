#!/usr/bin/env bash
# Signed release APK + checksum into dist/. Needs the key from make-release-key.sh.
set -euo pipefail
cd "$(dirname "$0")/.."

props="${OMDESK_KEYSTORE_PROPS:-$HOME/.omdesk/keystore.properties}"
[[ -f "$props" ]] || { echo "No release key ($props). Run scripts/make-release-key.sh first." >&2; exit 1; }

version=$(node -p "require('./package.json').version")
npm test
node build.mjs
npx cap sync android
(cd android && ./gradlew --no-daemon -q clean assembleRelease)

apk=android/app/build/outputs/apk/release/app-release.apk
[[ -f "$apk" ]] || { echo "release APK not produced (is the signing config loaded?)" >&2; exit 1; }

sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner | sort -V | tail -1)
"$apksigner" verify --print-certs "$apk" > /tmp/omdesk-verify.txt
grep -q "Signer #1 certificate DN" /tmp/omdesk-verify.txt || { echo "APK is not signed" >&2; exit 1; }

mkdir -p dist
out="dist/OM-Desk-${version}.apk"
cp "$apk" "$out"
(cd dist && sha256sum "OM-Desk-${version}.apk" > "OM-Desk-${version}.apk.sha256")
echo "Built $out"
cat "dist/OM-Desk-${version}.apk.sha256"
grep -E "certificate (DN|SHA-256)" /tmp/omdesk-verify.txt
rm -f /tmp/omdesk-verify.txt
