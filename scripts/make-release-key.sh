#!/usr/bin/env bash
# Creates the release signing key for OM Desk. Run it yourself, once, in a terminal:
# it asks for a password and writes it only to ~/.omdesk/ (mode 600), never to the repo.
# KEEP A BACKUP of ~/.omdesk/ somewhere safe: without this key you can never ship an
# update that installs over the published version.
set -euo pipefail

dir="${OMDESK_DIR:-$HOME/.omdesk}"
store="$dir/release.jks"
props="$dir/keystore.properties"
alias="omdesk"

if [[ -e "$store" || -e "$props" ]]; then
  echo "A key already exists in $dir: refusing to overwrite it." >&2
  exit 1
fi
command -v keytool >/dev/null || { echo "keytool not found (install a JDK, see ~/.profile)" >&2; exit 1; }

read -r -p "Name shown in the certificate (e.g. your handle): " cn
read -r -s -p "Key password (min 8 chars): " pw; echo
read -r -s -p "Repeat it: " pw2; echo
[[ "$pw" == "$pw2" ]] || { echo "Passwords differ." >&2; exit 1; }
(( ${#pw} >= 8 )) || { echo "Too short." >&2; exit 1; }

umask 077
mkdir -p "$dir"
keytool -genkeypair -v -keystore "$store" -storetype PKCS12 -alias "$alias" \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -dname "CN=${cn}, O=OM Desk" -storepass "$pw" -keypass "$pw" >/dev/null
cat > "$props" <<PROPS
storeFile=$store
storePassword=$pw
keyAlias=$alias
keyPassword=$pw
PROPS
chmod 600 "$store" "$props"

echo "Key created in $dir"
echo "Certificate SHA-256 (publish it so people can check the APK signer):"
keytool -list -v -keystore "$store" -storepass "$pw" -alias "$alias" | grep -E "SHA256:" | sed 's/^\s*//'
echo "Now back up $dir (e.g. an encrypted USB stick or a password manager)."
