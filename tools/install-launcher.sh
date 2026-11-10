#!/usr/bin/env bash
# Portable install: keep stock Home, accessibility settings, and system apps untouched.
set -euo pipefail
cd "$(dirname "$0")/.."
: "${TV_HOST:?set TV_HOST to your TV or Google TV dongle address}"
PY=tools/venv/bin/python
[ -x "$PY" ] || PY=tools/venv/Scripts/python.exe
APK="${1:-launcher/app/build/outputs/apk/release/app-release.apk}"
[ -f "$APK" ] || { echo 'Build first: launcher/gradlew -p launcher :app:assembleRelease' >&2; exit 1; }
T() { "$PY" -I tools/tv.py "$1"; }
BACKUP="backup/launcher-$(date +%Y%m%d-%H%M%S)"
mkdir -p "$BACKUP"
T 'cmd package resolve-activity --brief -a android.intent.action.MAIN -c android.intent.category.HOME' > "$BACKUP/home.txt"
T 'settings get secure enabled_accessibility_services; settings get secure accessibility_enabled' > "$BACKUP/accessibility.txt"
T 'pm list packages -d' > "$BACKUP/disabled-packages.txt"
"$PY" -I mytv/b64push-tcl.py "$APK" /data/local/tmp/tv-vibe-launcher.apk
RESULT="$(T 'pm install -r /data/local/tmp/tv-vibe-launcher.apk')"
printf '%s\n' "$RESULT"
printf '%s\n' "$RESULT" | tr -d '\r' | grep -qx Success || exit 1
T 'rm -f /data/local/tmp/tv-vibe-launcher.apk'
T 'appops set com.gathin.tvhome GET_USAGE_STATS allow'
T 'am start -W -n com.gathin.tvhome/.MainActivity'
echo "Installed. Existing Home and accessibility settings preserved. Backup: $BACKUP"
echo 'See SETUP.md to select Home and configure your model-specific remote shortcut.'
