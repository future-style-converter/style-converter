#!/usr/bin/env bash
#
# tools/titan/provision-devices.sh — boot + prepare the TITAN native device
# pool for parallel section-runners.
#
# What it does (idempotent — safe to re-run):
#   1. Builds the Android harness APK once (:app:assembleDebug) and the iOS
#      harness .app once (the exact xcodebuild invocation feed-ios.mjs uses).
#   2. Ensures N Android emulators are booted. Extra instances launch the
#      SAME AVD with -read-only (the standard parallel-farm trick): identical
#      AVD config → identical GPU/rendering config → pixel parity across the
#      pool. Headless (-no-window) so nothing steals focus.
#   3. Ensures N iOS simulators are booted. Extra devices are CREATED from
#      the currently-booted iPhone's deviceType+runtime (simctl clone needs a
#      shutdown source; create-from-same-type gives the same rendering).
#      Booted via `simctl boot` (headless unless Simulator.app is open).
#   4. Installs the prebuilt app on EVERY pool device (adb install -r /
#      simctl install) — so section-runner's concurrent feeders can pass
#      --skip-install / --no-build and never trigger two simultaneous
#      gradle/xcodebuild runs in one checkout (build-dir collision).
#   5. Writes the pool markers section-runner.sh Step 5b consults:
#         /tmp/titan-device-pool/provisioned-android   (one adb serial/line)
#         /tmp/titan-device-pool/provisioned-ios       (one sim UDID/line)
#
# Sizing: defaults are --android 2 --ios 3, chosen for a 16 GB machine — each
# emulator instance wants ~2-3 GB while simulators are lightweight processes;
# more Android instances than RAM allows makes the whole pool SLOWER (swap).
#
# usage: provision-devices.sh [--android N] [--ios N] [--avd NAME]
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POOL_ROOT="/tmp/titan-device-pool"
BUNDLE_ID="com.styleconverter.test"
IOS_DIR="$REPO_ROOT/apps/ios-harness"
APK="$REPO_ROOT/apps/android-harness/app/build/outputs/apk/debug/app-debug.apk"
APP="$IOS_DIR/build/Build/Products/Debug-iphonesimulator/StyleConverterTest.app"

WANT_ANDROID=2
WANT_IOS=3
AVD_NAME=""
while [[ $# -gt 0 ]]; do
  case "$1" in
    --android) WANT_ANDROID="$2"; shift 2 ;;
    --ios)     WANT_IOS="$2"; shift 2 ;;
    --avd)     AVD_NAME="$2"; shift 2 ;;
    -h|--help) grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown arg: $1" >&2; exit 2 ;;
  esac
done

log() { printf '[provision] %s\n' "$*"; }

# macOS ships no `timeout`, so _bounded is the hard watchdog every per-device
# call MUST go through. Two real incidents forced this: `simctl bootstatus -b`
# hung a run for 7 hours, then `simctl terminate` against a zombie simulator
# hung the FIXED run for 38 minutes — on a sick device ANY CoreSimulator/adb
# call can block forever. Runs the command in the background, kills it after
# $1 seconds, and returns its (or the kill's) status; stdout passes through,
# so `VAR=$(_bounded 60 cmd…)` works.
_bounded() {
  local secs="$1"; shift
  "$@" & local pid=$!
  ( sleep "$secs"; kill -KILL "$pid" 2>/dev/null ) & local wd=$!
  local rc=0; wait "$pid" 2>/dev/null || rc=$?
  kill -KILL "$wd" 2>/dev/null; wait "$wd" 2>/dev/null || true
  return $rc
}

# ── Tool resolution (mirror the feeders) ────────────────────────────────────
ADB="$(command -v adb || true)"
[[ -z "$ADB" && -x "${ANDROID_HOME:-}/platform-tools/adb" ]] && ADB="$ANDROID_HOME/platform-tools/adb"
[[ -z "$ADB" && -x "$HOME/Library/Android/sdk/platform-tools/adb" ]] && ADB="$HOME/Library/Android/sdk/platform-tools/adb"
EMU="$(command -v emulator || true)"
[[ -z "$EMU" && -x "${ANDROID_HOME:-}/emulator/emulator" ]] && EMU="$ANDROID_HOME/emulator/emulator"
[[ -z "$EMU" && -x "$HOME/Library/Android/sdk/emulator/emulator" ]] && EMU="$HOME/Library/Android/sdk/emulator/emulator"
[[ -z "$ADB" ]] && { echo "adb not found (set ANDROID_HOME)" >&2; exit 2; }
[[ -z "${JAVA_HOME:-}" ]] && export JAVA_HOME="$(/usr/libexec/java_home -v 21 2>/dev/null || true)"

mkdir -p "$POOL_ROOT"

# ── 1. Build both apps ONCE ─────────────────────────────────────────────────
log "building Android APK (:app:assembleDebug)…"
( cd "$REPO_ROOT/apps/android-harness" && ./gradlew :app:assembleDebug --quiet )
[[ -f "$APK" ]] || { echo "APK missing after build: $APK" >&2; exit 2; }

log "building iOS app (xcodebuild, same invocation as feed-ios)…"
xcodebuild \
  -project "$IOS_DIR/StyleConverterTest.xcodeproj" \
  -target StyleConverterTest -configuration Debug \
  -sdk iphonesimulator -arch arm64 \
  "CONFIGURATION_BUILD_DIR=$IOS_DIR/build/Build/Products/Debug-iphonesimulator" \
  CODE_SIGNING_ALLOWED=NO build >/dev/null
[[ -d "$APP" ]] || { echo ".app missing after build: $APP" >&2; exit 2; }

# ── 2. Android fleet ─────────────────────────────────────────────────────────
#
# ALL pool emulators must run -read-only: the emulator refuses to share one
# AVD otherwise ("Another emulator instance is running. Please close it or
# run all emulators with -read-only flag") — a WRITABLE primary silently
# blocks every additional instance. So when we need more instances than are
# running and the running one is writable, we restart the whole Android
# fleet read-only. Consequence of -read-only (temporary disk overlay):
# installs do NOT persist across emulator restarts — re-run this script
# after any emulator restart.
_booted_serials() { "$ADB" devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'; }
CUR=$(_booted_serials | wc -l | tr -d ' ')
if (( CUR < WANT_ANDROID )) && (( CUR > 0 )); then
  QPID=$(pgrep -f 'qemu-system' | head -1 || true)
  if [[ -n "$QPID" ]] && ! ps -o command= -p "$QPID" | grep -q -- '-read-only'; then
    log "running emulator is WRITABLE (blocks extra instances) — restarting Android fleet read-only…"
    pkill -f 'qemu-system' || true
    sleep 5
    CUR=0
  fi
fi
if (( CUR < WANT_ANDROID )); then
  [[ -z "$EMU" ]] && { echo "emulator binary not found but $((WANT_ANDROID-CUR)) more instance(s) needed" >&2; exit 2; }
  [[ -z "$AVD_NAME" ]] && AVD_NAME="$("$EMU" -list-avds | head -1)"
  [[ -z "$AVD_NAME" ]] && { echo "no AVD available (create one in Android Studio or pass --avd)" >&2; exit 2; }
  for (( i=CUR; i<WANT_ANDROID; i++ )); do
    # Per-instance log (NOT /dev/null — a silent launch failure cost a full
    # provisioning run once). -gpu swiftshader_indirect pins deterministic
    # CPU rendering, matching how the primary harness emulator runs, so
    # every pool instance rasterizes identically (pixel parity).
    ELOG="$POOL_ROOT/emulator-launch-$i.log"
    log "launching emulator instance $((i+1))/$WANT_ANDROID of AVD '$AVD_NAME' (read-only, headless) → $ELOG"
    nohup "$EMU" -avd "$AVD_NAME" -read-only -no-window -no-audio -no-boot-anim \
      -no-snapshot -gpu swiftshader_indirect >"$ELOG" 2>&1 &
    disown || true
  done
  # Bounded wait for the fleet to boot (cold boots under -read-only: ~60-90s
  # each). On timeout we WARN and continue with whatever booted — an
  # asymmetric pool still works; section-runner just has fewer Android slots.
  log "waiting up to 240s for $WANT_ANDROID Android instance(s) to boot…"
  READY=0
  for (( t=0; t<240; t+=5 )); do
    READY=0
    for s in $(_booted_serials); do
      [[ "$("$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null | tr -d '\r')" == "1" ]] && READY=$((READY+1))
    done
    (( READY >= WANT_ANDROID )) && break
    sleep 5
  done
  if (( READY < WANT_ANDROID )); then
    log "WARNING: only $READY/$WANT_ANDROID Android instance(s) booted — continuing with a smaller pool. Launch log tails:"
    for f in "$POOL_ROOT"/emulator-launch-*.log; do
      [[ -f "$f" ]] && { echo "--- $f:"; tail -5 "$f"; }
    done
  fi
fi

: > "$POOL_ROOT/provisioned-android.tmp"
for s in $(_booted_serials); do
  # sys.boot_completed=1 fires BEFORE the package service is usable on a
  # cold -read-only boot — an immediate install dies with "Broken pipe /
  # Failure calling service package". Poll pm readiness (bounded), then
  # install with retries; a device that never comes up is skipped with a
  # warning (smaller pool beats a dead provisioning run).
  PM_OK=0
  for (( t=0; t<120; t+=5 )); do
    if _bounded 10 "$ADB" -s "$s" shell pm path android >/dev/null 2>&1; then PM_OK=1; break; fi
    sleep 5
  done
  if (( ! PM_OK )); then log "WARNING: $s package service never came up — skipping"; continue; fi
  INSTALLED=0
  for attempt in 1 2 3; do
    if _bounded 120 "$ADB" -s "$s" install -r "$APK" >/dev/null 2>&1; then INSTALLED=1; break; fi
    log "install attempt $attempt on $s failed — retrying in 10s…"
    sleep 10
  done
  if (( INSTALLED )); then
    # wave-45: `pm clear` after every (re)install. On API-36.1 emulator
    # images a reinstall over an existing package leaves the app's external
    # files dir in a broken state until its data is cleared (the wave-38
    # "bare adb install wedge" family; re-proven at the wave-45 gate, where
    # the first seven sections' feeders hit the wave-42 loud-fail — the app
    # could not create its inbox — and produced zero Android captures).
    # Clearing data right after install gives every gate a factory-state
    # app; the feeder's app-first dir creation then works from fixture one.
    _bounded 60 "$ADB" -s "$s" shell pm clear com.styleconverter.test >/dev/null 2>&1 \
      || log "WARNING: pm clear failed on $s — external-dir wedge possible"
    log "installed APK on $s (+ pm clear)"
    echo "$s" >> "$POOL_ROOT/provisioned-android.tmp"
  else
    log "WARNING: install failed 3x on $s — device excluded from pool"
  fi
done
mv "$POOL_ROOT/provisioned-android.tmp" "$POOL_ROOT/provisioned-android"

# ── 3. iOS fleet ─────────────────────────────────────────────────────────────
_booted_udids() { xcrun simctl list devices booted | grep -oE '\([0-9A-F-]{36}\)' | tr -d '()'; }
CUR=$(_booted_udids | wc -l | tr -d ' ')
if (( CUR < WANT_IOS )); then
  # Create new sims of the SAME device type + runtime as the booted iPhone so
  # the whole pool renders identically. simctl clone needs a Shutdown source;
  # create-from-type sidesteps that.
  BASE_UDID="$(_booted_udids | head -1)"
  [[ -z "$BASE_UDID" ]] && { echo "no booted simulator to derive device type from — boot one first" >&2; exit 2; }
  PAIR=$(xcrun simctl list devices --json | node -e '
    const j = JSON.parse(require("fs").readFileSync(0, "utf8"));
    const base = process.argv[1];
    for (const [runtime, devs] of Object.entries(j.devices)) {
      const d = devs.find((x) => x.udid === base);
      if (d) { console.log(`${d.deviceTypeIdentifier}\t${runtime}`); process.exit(0); }
    }
    process.exit(1);' "$BASE_UDID")
  DEVTYPE="${PAIR%%$'\t'*}" ; RUNTIME="${PAIR##*$'\t'}"
  for (( i=CUR; i<WANT_IOS; i++ )); do
    log "creating + booting extra simulator $((i+1))/$WANT_IOS (titan-pool-$i, $DEVTYPE)…"
    U=$(_bounded 60 xcrun simctl create "titan-pool-$i" "$DEVTYPE" "$RUNTIME") || { log "WARNING: simctl create timed out — continuing with fewer sims"; continue; }
    _bounded 60 xcrun simctl boot "$U" || log "WARNING: simctl boot of $U timed out — it may still come up"
  done
  # Bounded boot wait. NEVER use `simctl bootstatus -b` here: on some
  # freshly-created sims it blocks FOREVER (the boot-complete event it waits
  # for never fires even though the device reaches Booted) — it hung one
  # provisioning run for 7 hours. Poll the device list instead, capped at
  # 120s; a sim listed as Booted is ready enough for `simctl install`,
  # which fails loudly on a genuinely broken device.
  log "waiting up to 120s for $WANT_IOS simulator(s) to reach Booted…"
  for (( t=0; t<120; t+=5 )); do
    (( $(_booted_udids | wc -l | tr -d ' ') >= WANT_IOS )) && break
    sleep 5
  done
fi

: > "$POOL_ROOT/provisioned-ios.tmp"
for u in $(_booted_udids); do
  log "installing app on $u…"
  # Every call bounded: a zombie sim wedges ANY simctl verb indefinitely
  # (terminate hung 38 min once). A device that times out is skipped with a
  # warning and left out of the pool — smaller pool beats a hung run.
  _bounded 30 xcrun simctl terminate "$u" "$BUNDLE_ID" >/dev/null 2>&1 || true
  _bounded 60 xcrun simctl uninstall "$u" "$BUNDLE_ID" >/dev/null 2>&1 || true
  if _bounded 120 xcrun simctl install "$u" "$APP" >/dev/null 2>&1; then
    log "installed app on $u"
    echo "$u" >> "$POOL_ROOT/provisioned-ios.tmp"
  else
    log "WARNING: install on $u failed/timed out — device excluded from pool"
  fi
done
mv "$POOL_ROOT/provisioned-ios.tmp" "$POOL_ROOT/provisioned-ios"

# ── 4. Summary ───────────────────────────────────────────────────────────────
log "pool ready:"
log "  android ($(wc -l < "$POOL_ROOT/provisioned-android" | tr -d ' ')): $(tr '\n' ' ' < "$POOL_ROOT/provisioned-android")"
log "  ios     ($(wc -l < "$POOL_ROOT/provisioned-ios" | tr -d ' ')): $(tr '\n' ' ' < "$POOL_ROOT/provisioned-ios")"
log "section-runner.sh will now acquire free slots per section and pass --skip-install/--no-build."
