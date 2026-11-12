#!/usr/bin/env bash
# Bangun APK rilis dan terbitkan sebagai GitHub Release (sumber update OTA di aplikasi).
#
#   GITHUB_TOKEN=... tools/release.sh "Catatan rilis"      # atau: tools/release.sh -F catatan.md
#
# Naikkan versionCode/versionName di app/build.gradle.kts dulu. Tag = v<versionName>.
# Butuh keystore.properties (kunci rilis); APK dengan kunci lain tidak bisa memperbarui aplikasi terpasang.
# Token: fine-grained dengan izin Contents read/write pada repo ini.
set -euo pipefail
cd "$(dirname "$0")/.."

REPO="gitgitmiko/xb-market-mobile"
: "${GITHUB_TOKEN:?GITHUB_TOKEN belum diisi}"
[ -f keystore.properties ] || { echo "keystore.properties tidak ada" >&2; exit 1; }

if [ "${1:-}" = "-F" ]; then NOTES=$(cat "$2"); else NOTES="${1:-}"; fi
VERSION=$(sed -n 's/.*versionName = "\(.*\)".*/\1/p' app/build.gradle.kts)
TAG="v$VERSION"

./gradlew -q :app:assembleRelease
APK=app/build/outputs/apk/release/app-release.apk
[ -f "$APK" ] || { echo "APK rilis tidak ditemukan (signing belum dikonfigurasi?)" >&2; exit 1; }

api() { curl -fsS -H "Authorization: Bearer $GITHUB_TOKEN" -H "Accept: application/vnd.github+json" "$@"; }

BODY=$(python3 -c 'import json,sys; print(json.dumps({"tag_name": sys.argv[1], "name": "XB Market " + sys.argv[2], "body": sys.argv[3], "target_commitish": sys.argv[4]}))' \
  "$TAG" "$VERSION" "$NOTES" "$(git rev-parse HEAD)")
RELEASE=$(api -X POST "https://api.github.com/repos/$REPO/releases" -d "$BODY")
UPLOAD=$(printf '%s' "$RELEASE" | python3 -c 'import json,sys; print(json.load(sys.stdin)["upload_url"].split("{")[0])')

api -X POST -H "Content-Type: application/vnd.android.package-archive" \
  --data-binary @"$APK" "$UPLOAD?name=xb-market-$VERSION.apk" >/dev/null
echo "Terbit: https://github.com/$REPO/releases/tag/$TAG"
