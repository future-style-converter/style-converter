#!/usr/bin/env bash
# tools/titan/results/wave55-gate/installed-build-hash.sh <label>
#
# The standing constraint (docs/BACKLOG.md, "A device A/B that claims to EXCLUDE
# a code change records the installed base.apk sha1 against the APK it built"):
# a run without this record is a score, not evidence. Run it WHILE the run's
# devices are up (the gate driver tears the emulator down when it ends), and
# before anything rebuilds the harness apps.
#
# Android: sha1 of the installed base.apk, computed ON the device (no pull —
#          one shell command, so a capture in progress is not disturbed), vs
#          the sha1 of the APK provision-devices.sh built.
# iOS:     a digest over every file of the installed .app container vs the
#          same digest over the built .app (simctl install copies the bundle).
# Plan-skeptic round 2: (process-holes S1) "+ uncommitted" is read with `git status --porcelain --untracked-files=all` over the
# code paths (runtimes, apps, converter, tools/titan outside results, root Package.swift; *.md excluded) — an UNTRACKED source
# the build compiles was invisible to `git diff --quiet`; (S9) every block prints `asset sha1 ios=… android=…`: BOTH bundles
# embed the last test-all IR (the apk's assets/tmpOutput.json, the .app's tmpOutput.json — test-all.sh syncs both on every
# fixture), so a digest / sha1 compares across launches only with the same asset line.
# Appends one block to build-hashes.txt next to this script (wave 55 copy of the
# wave-52 tool — a per-wave copy, because the wave-53 opening-gate blocks first
# landed in wave52-gate/build-hashes.txt by running the wave-52 one); exit 1 on a
# mismatch or a missing side.
set -uo pipefail
LABEL="${1:?usage: installed-build-hash.sh <label>}"
HERE="$(cd "$(dirname "$0")" && pwd)"
ROOT="$(cd "$HERE/../../../.." && pwd)"
OUT="$HERE/build-hashes.txt"
APK="$ROOT/apps/android-harness/app/build/outputs/apk/debug/app-debug.apk"
APP="$ROOT/apps/ios-harness/build/Build/Products/Debug-iphonesimulator/StyleConverterTest.app"
PKG=com.styleconverter.test
ADB="${ANDROID_HOME:-$HOME/Library/Android/sdk}/platform-tools/adb"
# The pool markers provision-devices.sh writes: one serial / one UDID per line.
SERIAL="${ANDROID_SERIAL:-$(head -1 /tmp/titan-device-pool/provisioned-android 2>/dev/null)}"
UDID="$(head -1 /tmp/titan-device-pool/provisioned-ios 2>/dev/null)"
# A bundle's digest: sha1 of the sorted "sha1  relative-path" list of its files.
bundle_digest() { ( cd "$1" && find . -type f -print0 | sort -z | xargs -0 shasum -a 1 | shasum -a 1 | cut -d' ' -f1 ); }

rc=0
{
  # Documentation under the build trees (*.md) never reaches a build, so it is not "uncommitted changes" here.
  DIRTY="$(git -C "$ROOT" --no-optional-locks status --porcelain --untracked-files=all -- runtimes apps converter tools/titan Package.swift ':(exclude)tools/titan/results' ':(exclude)*.md')"
  echo "== $LABEL — $(date -u +%Y-%m-%dT%H:%M:%SZ) — HEAD $(git -C "$ROOT" rev-parse --short HEAD)$( [[ -z "$DIRTY" ]] || echo ' + uncommitted code changes (tracked or untracked)')"
  # Which uncommitted source files the build contains (an exclude arm lists its patch here), untracked ones included.
  [[ -z "$DIRTY" ]] || sed 's/^/   /' <<<"$DIRTY"
  built_apk="$(shasum -a 1 "$APK" 2>/dev/null | cut -d' ' -f1)"
  on_device="$("$ADB" -s "$SERIAL" shell "sha1sum \$(pm path $PKG | head -1 | sed 's/^package://')" 2>/dev/null | cut -d' ' -f1 | tr -d '\r')"
  echo "android  serial=${SERIAL:-none}  built app-debug.apk sha1=${built_apk:-MISSING}  installed base.apk sha1=${on_device:-MISSING}"
  if [[ -n "$built_apk" && "$built_apk" == "$on_device" ]]; then echo "android  MATCH"; else echo "android  MISMATCH"; rc=1; fi
  container="$(xcrun simctl get_app_container "$UDID" "$PKG" app 2>/dev/null)"
  built_app="$( [[ -d "$APP" ]] && bundle_digest "$APP" )"
  on_sim="$( [[ -d "$container" ]] && bundle_digest "$container" )"
  echo "ios      udid=${UDID:-none}  built .app digest=${built_app:-MISSING}  installed .app digest=${on_sim:-MISSING}"
  if [[ -n "$built_app" && "$built_app" == "$on_sim" ]]; then echo "ios      MATCH"; else echo "ios      MISMATCH"; rc=1; fi
  asset_ios="$( [[ -f "$APP/tmpOutput.json" ]] && shasum -a 1 "$APP/tmpOutput.json" | cut -d' ' -f1 )"
  asset_and="$( [[ -f "$APK" ]] && unzip -l "$APK" assets/tmpOutput.json >/dev/null 2>&1 && unzip -p "$APK" assets/tmpOutput.json | shasum -a 1 | cut -d' ' -f1 )"
  echo "asset    sha1 ios=${asset_ios:-MISSING} android=${asset_and:-MISSING}  (the last test-all IR both bundles embed: compare digests only with the same asset line)"
  echo
} | tee -a "$OUT"
exit $rc
