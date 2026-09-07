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
# usage: provision-devices.sh [--android N] [--ios N] [--avd NAME] [--restart-fleet]
#
# retro R8b (A9#6) — two idempotence repairs:
#   * iOS: Shutdown `titan-pool-*` simulators from a previous run are BOOTED
#     before any new one is created; new ones get a unique `titan-pool-<n>-
#     <utc>` name. Creating fresh sims on every run (each a fresh CoreSimulator
#     data dir) is the mechanism behind BACKLOG #6's 81-sim / 38 GB disk
#     exhaustion that killed the first wave-49 Android column.
#   * Android: the writable-emulator restart kills only emulators THIS script
#     launched (pids in $POOL_ROOT/emulator-pids); killing every qemu on the
#     host — other sessions' emulators included — now needs --restart-fleet.
#
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
REPO_ROOT="$(cd "$SCRIPT_DIR/../.." && pwd)"
POOL_ROOT="/tmp/titan-device-pool"
BUNDLE_ID="com.styleconverter.test"
IOS_DIR="$REPO_ROOT/apps/ios-harness"
APK="$REPO_ROOT/apps/android-harness/app/build/outputs/apk/debug/app-debug.apk"
APP="$IOS_DIR/build/Build/Products/Debug-iphonesimulator/StyleConverterTest.app"

WANT_ANDROID="${WANT_ANDROID:-2}"   # retro 2026-09-06: overridable — on this host the SECOND concurrent -read-only instance of the AVD wedged in 4 of 5 gate attempts (adbd never answered / app vanished); gates run WANT_ANDROID=1
WANT_IOS="${WANT_IOS:-3}"
AVD_NAME=""
RESTART_FLEET=0   # retro R8b (A9#6): opt-in to killing emulators this script did not launch
while [[ $# -gt 0 ]]; do
  case "$1" in
    --android) WANT_ANDROID="$2"; shift 2 ;;
    --ios)     WANT_IOS="$2"; shift 2 ;;
    --avd)     AVD_NAME="$2"; shift 2 ;;
    --restart-fleet) RESTART_FLEET=1; shift ;;
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
# Retro 2026-09-06: the adb SERVER wedges on this host (an `adb devices` hung 48 min inside this
# script, 49 min inside section-runner). Every device listing is bounded and, on a timeout,
# restarts the server DETACHED (a server spawned from a pipeline inherits its fds) and retries once.
_adb_devices() {
  local out=""
  for _a in 1 2; do
    if out=$(perl -e 'alarm 20; exec @ARGV' "$ADB" devices </dev/null 2>/dev/null); then printf '%s\n' "$out"; return 0; fi
    perl -e 'alarm 10; exec @ARGV' "$ADB" kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1
    nohup "$ADB" start-server >/dev/null 2>&1 </dev/null; sleep 2
  done
  printf '%s\n' "$out"
}
_booted_serials() { _adb_devices | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'; }
# retro R8b (A9#6): the emulators THIS script launched, by pid. The old
# `pkill -f qemu-system` killed EVERY emulator on the host — including
# instances owned by other sessions (cross-session emulator contention is a
# recorded project hazard). Launch pids are appended to $EMU_PIDS_FILE at
# launch time; the restart branch below kills only those unless the operator
# passed --restart-fleet. The emulator launcher may exec into qemu (same pid)
# or fork it (child pid), so both the recorded pid and its children count.
EMU_PIDS_FILE="$POOL_ROOT/emulator-pids"
_our_live_emulator_pids() {
  [[ -f "$EMU_PIDS_FILE" ]] || return 0
  local p
  while read -r p; do
    [[ "$p" =~ ^[0-9]+$ ]] || continue            # ignore garbage lines
    kill -0 "$p" 2>/dev/null && echo "$p"         # still alive → ours
    pgrep -P "$p" 2>/dev/null || true             # its qemu child, if forked
  done < "$EMU_PIDS_FILE"
}
CUR=$(_booted_serials | wc -l | tr -d ' ')
if (( CUR < WANT_ANDROID )) && (( CUR > 0 )); then
  # Every qemu whose command line lacks -read-only is a WRITABLE instance and
  # blocks additional -read-only launches of the same AVD.
  WRITABLE=""
  for q in $(pgrep -f 'qemu-system' || true); do
    ps -o command= -p "$q" 2>/dev/null | grep -q -- '-read-only' || WRITABLE="$WRITABLE $q"
  done
  if [[ -n "$WRITABLE" ]]; then
    OURS=" $(_our_live_emulator_pids | tr '\n' ' ')"
    FOREIGN=""
    for q in $WRITABLE; do [[ "$OURS" == *" $q "* ]] || FOREIGN="$FOREIGN $q"; done
    if (( RESTART_FLEET )); then
      # The operator asked for it explicitly — this is the only path that may
      # take down emulators this script never started.
      log "writable emulator(s) [$WRITABLE ] block extra instances — --restart-fleet: restarting the WHOLE Android fleet read-only…"
      pkill -f 'qemu-system' || true   # --restart-fleet ONLY: kills other sessions' emulators too
      sleep 5
      CUR=0
    elif [[ -z "$FOREIGN" ]]; then
      # Every writable instance is one we launched: restart just those.
      log "writable emulator(s) [$WRITABLE ] were launched by this script — restarting them read-only…"
      kill $WRITABLE 2>/dev/null || true   # unquoted on purpose: a space-separated pid list
      sleep 5
      CUR=$(_booted_serials | wc -l | tr -d ' ')
    else
      # A foreign writable emulator: never kill it silently. Continue with the
      # smaller pool (section-runner just gets fewer Android slots) and say
      # exactly which flag would change that and what it costs.
      log "WARNING: writable emulator(s) [$FOREIGN ] NOT launched by this script block extra -read-only instances — continuing with the $CUR booted. Pass --restart-fleet to kill them (that also kills other sessions' emulators)."
      WANT_ANDROID=$CUR
    fi
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
    echo $! >> "$EMU_PIDS_FILE"   # retro R8b (A9#6): remembered so a later restart kills only OUR instances
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
      # retro 2026-09-06: BOUNDED — an instance whose adbd never answers made this poll hang 30+ min
      # (attempt 5 of the retro gate); the outer 240s budget only bounds the loop, not one call.
      [[ "$(_bounded 15 "$ADB" -s "$s" shell getprop sys.boot_completed 2>/dev/null </dev/null | tr -d '\r')" == "1" ]] && READY=$((READY+1))
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
    # Retro gate 2026-09-06: `adb install` exits 0 even when it prints `Failure [...]`
    # (seen on an API-36.1 read-only instance whose external dir was wedged): the
    # pool was declared ready with NO app on emulator-5554 and every section fed it
    # to a 0/48 Android column. Success is the package being PRESENT, not adb's rc.
    if _bounded 120 "$ADB" -s "$s" install -r "$APK" >/dev/null 2>&1 \
       && [ -n "$(_bounded 30 "$ADB" -s "$s" shell pm path com.styleconverter.test 2>/dev/null </dev/null)" ]; then INSTALLED=1; break; fi
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
# retro R8b (A9#6): REUSE before CREATE. Every run that found fewer than
# WANT_IOS booted sims used to `simctl create` new devices — each a fresh
# CoreSimulator data dir — even when Shutdown titan-pool-* sims from the
# previous run were sitting right there (this host carries titan-pool-1 and
# titan-pool-2 TWICE each). That is the mechanism behind BACKLOG #6's "81 sims
# → 5, CoreSimulator 38G → 11G" disk exhaustion. Boot the existing pool sims
# first — bounded, a wedged sim must not hang the run — then create only the
# remaining shortfall. `unavailable` sims (runtime gone) are skipped: they
# cannot boot and `simctl delete unavailable` is the operator's job.
_shutdown_pool_udids() {
  xcrun simctl list devices 2>/dev/null | grep -E 'titan-pool-[0-9]+' | grep '(Shutdown)' \
    | grep -v unavailable | grep -oE '[0-9A-F-]{36}'
}
CUR=$(_booted_udids | wc -l | tr -d ' ')
if (( CUR < WANT_IOS )); then
  for u in $(_shutdown_pool_udids || true); do
    (( $(_booted_udids | wc -l | tr -d ' ') >= WANT_IOS )) && break
    log "reusing Shutdown pool simulator $u (simctl boot)…"
    _bounded 60 xcrun simctl boot "$u" || log "WARNING: simctl boot of pool sim $u timed out/failed — trying the next"
  done
  CUR=$(_booted_udids | wc -l | tr -d ' ')   # re-count: the create loop below fills only what is still missing
fi
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
    # retro R8b (A9#6): a UNIQUE name (index + UTC stamp). A re-run must never
    # mint a second device under an existing pool name — the reuse loop above
    # finds every past pool sim by the `titan-pool-<n>` prefix regardless.
    NAME="titan-pool-$i-$(date -u +%Y%m%dT%H%M%SZ)"
    log "creating + booting extra simulator $((i+1))/$WANT_IOS ($NAME, $DEVTYPE)…"
    U=$(_bounded 60 xcrun simctl create "$NAME" "$DEVTYPE" "$RUNTIME") || { log "WARNING: simctl create timed out — continuing with fewer sims"; continue; }
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
