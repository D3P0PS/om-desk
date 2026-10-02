#!/usr/bin/env bash
# Uploads dist/OM-Desk-<version>.apk + .sha256 as a GitHub release of the public repo.
# Run after `npm run release`. Needs the GitHub CLI (gh) logged in with access to the repo.
set -euo pipefail
cd "$(dirname "$0")/.."
repo="${OMDESK_REPO:-D3P0PS/om-desk}"
version=$(node -p "require('./package.json').version")
apk="dist/OM-Desk-${version}.apk"
[[ -f "$apk" && -f "$apk.sha256" ]] || { echo "Run npm run release first ($apk missing)" >&2; exit 1; }
sdk="${ANDROID_HOME:-$HOME/Android/Sdk}"
apksigner=$(ls -d "$sdk"/build-tools/*/apksigner | sort -V | tail -1)
cert=$("$apksigner" verify --print-certs "$apk" | sed -n 's/^Signer #1 certificate SHA-256 digest: //p')
[[ -n "$cert" ]] || { echo "APK is not signed" >&2; exit 1; }
notes="OM Desk ${version}, unofficial and free Android app for OpenMarket.

**APK SHA-256:** \`$(cut -d' ' -f1 "$apk.sha256")\`
**Signing certificate SHA-256:** \`${cert}\`

Not affiliated with OpenMarket. See the README for install steps and known limits."
gh release create "v${version}" "$apk" "$apk.sha256" --repo "$repo" --title "OM Desk ${version}" --notes "$notes"
