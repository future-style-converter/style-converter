#!/usr/bin/env bash
#
# test-all.sh — run the cross-platform screenshot comparison pipeline.
#
# 1. Convert CSS → IR using the main Gradle project.
# 2. Sync IR into iOS / Android / web bundles.
# 3. (optional) Build + launch iOS on a headless simulator, capture chromeless PNGs.
# 4. (optional) Launch a headless Android emulator (if none running) + build,
#    capture chromeless PNGs, tear it down.
# 5. (optional) Start vite in capture mode, run headless Puppeteer, capture PNGs.
#
# Focus-stealing rule: NONE of the steps above are allowed to open a GUI
# window on the host Mac (Simulator.app, the emulator window, a Chrome
# window, etc.). TITAN-class runs can last hours; the laptop must remain
# usable for unrelated work throughout. See the per-step comments for how
# each platform achieves headlessness.
# 6. Run testing/compare-screenshots.mjs to generate the HTML report.
#
# Platform steps are auto-skipped (with a warning) when the required tools
# are missing. The comparison report always runs and includes whatever was
# captured.
#
# Usage:
#     ./test-all.sh [input.json]         # default: examples/visual-test.json
#
# Environment overrides:
#     SKIP_IOS=1 SKIP_ANDROID=1 SKIP_WEB=1    skip a platform
#     UPDATE_BASELINE=1                        copy captures → testing/baseline/
#     BASELINE=1                               compare vs baseline, fail on regression
#     SIM_DEVICE="iPhone 17 Pro"               force a specific iOS simulator (by name)
#     SIM_UDID="0BB986A6-1EAD-..."              force a specific simulator by UDID
#                                                (used by Tier 12 OS-matrix runner
#                                                to disambiguate same-name devices
#                                                across different iOS runtimes)
#     WEB_PORT=3000                            vite dev-server port
#     EMULATOR_AVD="Medium_Phone_API_36.1"     pick a specific Android AVD; otherwise
#                                                we boot the first one `emulator
#                                                -list-avds` returns
#     EMULATOR_KEEP=1                          leave the emulator running after the
#                                                run instead of tearing it down
#                                                (only meaningful when WE booted it)
#     BACKGROUND_MODE=1                        TITAN-mode: suppress every GUI / audio
#                                                hook in this script (no `open` of
#                                                the report, no notifications). The
#                                                emulator/simulator/chrome are
#                                                ALWAYS headless regardless of this
#                                                flag — BACKGROUND_MODE only mutes
#                                                the host-side niceties.
#
set -euo pipefail

# ── Single-run lock ──────────────────────────────────────────────────────────
# Two test-all.sh invocations in parallel will race on several shared paths:
#   out/tmpOutput.json, testing/report/, the Android/iOS IR asset bundles,
#   the iOS xcodebuild DerivedData + build.db, the Vite dev server port,
#   and the Android installDebug APK staging area.
# macOS ships without flock(1), so we roll a `mkdir`-based advisory lock.
# `mkdir` is atomic on the local filesystem, so the first caller wins and
# everyone else aborts cleanly with a readable message. Unlike file locks,
# this also prevents re-entry after a crash (stale lock dir) — which is a
# feature, not a bug: manual cleanup is trivial (`rm -rf "$LOCK"`) and the
# alternative (letting a half-finished run corrupt shared artefacts) is
# worse.
# Skip the lock when TESTALL_SKIP_LOCK=1 (used by sub-scripts that already
# hold it, e.g. our own retry loops).
LOCK="/tmp/style-converter-testall.lock"
if [[ -z "${TESTALL_SKIP_LOCK:-}" ]]; then
  if ! mkdir "$LOCK" 2>/dev/null; then
    other_pid=$(cat "$LOCK/pid" 2>/dev/null || echo "?")
    echo "[test-all] another run is in progress (pid=$other_pid, lock=$LOCK)" >&2
    echo "[test-all] remove $LOCK if you know no other run is active" >&2
    exit 2
  fi
  echo "$$" > "$LOCK/pid"
  # Release the lock no matter how we exit. The vite section below installs
  # its own EXIT trap that previously clobbered this one; that's now fixed
  # by having the vite trap chain back through _testall_on_exit.
  # Also chains the headless-emulator teardown — defined further down, but
  # bash resolves function calls at runtime so the forward reference is
  # fine. The teardown is a no-op unless WE booted the emulator (it checks
  # EMULATOR_STARTED_BY_US itself).
  _testall_on_exit() {
      if declare -F _teardown_headless_emulator >/dev/null 2>&1; then
          _teardown_headless_emulator
      fi
      rm -rf "$LOCK" 2>/dev/null || true
  }
  trap _testall_on_exit EXIT INT TERM HUP
fi

# ── Configuration ────────────────────────────────────────────────────────────
PROJECT_ROOT="$(cd "$(dirname "$0")" && pwd)"
INPUT_JSON="${1:-examples/visual-test.json}"

OUTPUT_DIR="$PROJECT_ROOT/out"
IOS_DIR="$PROJECT_ROOT/testing/iOS"
ANDROID_DIR="$PROJECT_ROOT/testing/Android"
WEB_DIR="$PROJECT_ROOT/testing/web"
TESTING_DIR="$PROJECT_ROOT/testing"

IOS_BUNDLE="com.styleconverter.test"
ANDROID_PACKAGE="com.styleconverter.test"
ANDROID_ACTIVITY="$ANDROID_PACKAGE.MainActivity"
WEB_PORT="${WEB_PORT:-3000}"

# ── Background-mode flag ─────────────────────────────────────────────────────
# When the orchestrator (TITAN-PREP-B's output) drives long unattended
# capture runs, every GUI/audio side effect of this script must be muted —
# otherwise opening the report, ringing the terminal bell, or playing a
# completion chime steals focus or makes noise on the user's Mac. Setting
# BACKGROUND_MODE=1 implies NO_OPEN=1 (so we don't `open` the HTML report)
# AND exports IS_BACKGROUND=1 for any child tool to consume.
# This flag is a NICETY layer: even with it unset, the emulator/simulator/
# chrome are still launched headlessly per the focus-stealing rule above.
if [[ "${BACKGROUND_MODE:-0}" == "1" ]]; then
    export IS_BACKGROUND=1
    : "${NO_OPEN:=1}"
    export NO_OPEN
fi

# ── Colors ───────────────────────────────────────────────────────────────────
R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; N='\033[0m'
log()   { echo -e "${G}[test-all]${N} $1"; }
warn()  { echo -e "${Y}[test-all]${N} $1"; }
err()   { echo -e "${R}[test-all]${N} $1"; }

# Per-stage timing. `step "Name"` records the current timestamp and prints a
# divider; `step_end` prints "took Ns" for the most recent `step`. Makes it
# obvious which stage is slow when runs drift (e.g. from 40s to 100s).
SCRIPT_START=$(date +%s)
_STAGE_START=0
_STAGE_NAME=""
step() {
    if [[ -n "$_STAGE_NAME" ]]; then
        step_end
    fi
    _STAGE_START=$(date +%s)
    _STAGE_NAME="$1"
    echo -e "\n${B}━━━ $1 ━━━${N}"
}
step_end() {
    if [[ -n "$_STAGE_NAME" ]]; then
        local dur=$(( $(date +%s) - _STAGE_START ))
        echo -e "${G}[test-all]${N} ↳ ${_STAGE_NAME} took ${dur}s"
        _STAGE_NAME=""
    fi
}

# Count files matching a glob, safely under `set -euo pipefail`.
#
# Implementation: split the input into directory + pattern, hand both to
# `find -maxdepth 1 -name`. We avoid `ls $1` because the glob would expand
# at the shell command-line BEFORE invoking ls — and on the Tier 10 perf
# bench that means 10000 PNG paths × ~80 chars = ~800 KB of arguments,
# which exceeds macOS ARG_MAX (~256 KB) and silently fails. The `2>/dev/null`
# wrapper then swallows the error and we'd report 0 captures despite the
# directory having 10000 valid files. (This was the round-46 perf-bench
# follow-up bug: web reported "captured 0" while the comparison report
# went on to find 10000 iOS-web SSIM pairs from the same dir.)
#
# `find` handles arbitrarily large file counts internally without
# building a giant argv. Strip ALL whitespace (incl newlines) from the
# result so caller's `$((COUNT))` arithmetic works.
count_glob() {
    # Usage: count_glob "/path/*.png"
    local dir pattern n
    dir="${1%/*}"
    pattern="${1##*/}"
    n=$(find "$dir" -maxdepth 1 -name "$pattern" -type f 2>/dev/null | wc -l | tr -d '[:space:]' || true)
    echo "${n:-0}"
}

# Track which platforms actually captured so the final message is honest.
# Plain vars (not an associative array) because macOS ships bash 3.2 which
# doesn't support `declare -A` and we can't assume users have `brew install bash`.
CAPTURED_IOS=""
CAPTURED_ANDROID=""
CAPTURED_WEB=""

# ── Probe-fixture exclusion guard (B-EXT spec Section 7 step 4) ──────────────
# Probe fixtures under examples/_metric_probes/ are deliberately captured at
# 4× resolution by testing/probe-text-metrics.sh and never enter the
# 1×-pinned 327-pair regression baseline. If someone hand-feeds one to
# test-all.sh they'd otherwise pollute baseline/ on the next BASELINE=1
# run, so refuse the input early with a pointer to the right driver.
if [[ "$INPUT_JSON" == *"_metric_probes/"* ]]; then
  echo "[test-all] $INPUT_JSON is a probe fixture (B8/B9/B10); use testing/probe-text-metrics.sh instead." >&2
  exit 2
fi

# ── Step 1: Convert CSS → IR ─────────────────────────────────────────────────
step "Converting $INPUT_JSON → IR"
[[ -f "$PROJECT_ROOT/$INPUT_JSON" ]] || { err "Input not found: $INPUT_JSON"; exit 1; }
cd "$PROJECT_ROOT"
# When gradle's daemon-client handshake is flaky (macOS ephemeral port
# exhaustion from unrelated background HTTPS traffic — we've seen this
# in Phase 12 audit runs with 16k+ TIME_WAIT sockets), fall back to
# driving the already-built JVM classes directly. Requires a prior
# `./gradlew :converter:build` or `./gradlew :converter:classes` to populate
# converter/build/classes/; we try gradle first and only fall through on
# connection failure.
if ! ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i $INPUT_JSON -o out" --quiet 2>/tmp/gradle-convert.log; then
  if grep -q "Could not connect to the Gradle daemon\|BindException" /tmp/gradle-convert.log 2>/dev/null && \
     [[ -d "$PROJECT_ROOT/converter/build/classes/kotlin/main" ]]; then
    warn "gradle daemon unreachable (loopback port exhaustion?) — using prebuilt classes"
    CP="$PROJECT_ROOT/converter/build/classes/kotlin/main"
    CP="$CP:$(find "$HOME/.gradle/caches/modules-2" -name '*.jar' 2>/dev/null | tr '\n' ':')"
    java -cp "$CP" app.MainKt convert --from css --to ir -i "$INPUT_JSON" -o out
  else
    cat /tmp/gradle-convert.log >&2
    exit 1
  fi
fi
log "out/tmpOutput.json written"

# `grep -c` exits 1 on zero matches which, combined with `|| echo "?"`,
# would produce a two-line "0\n?" value. Count IDs in the IR safely by
# running grep inside a pipe to `wc -l` and guarding the pipeline so it
# always emits a numeric string, even for empty / malformed input.
COMPONENT_COUNT=$(grep -o '"id"' "$OUTPUT_DIR/tmpOutput.json" 2>/dev/null | wc -l | tr -d ' ' || echo 0)
log "$COMPONENT_COUNT components"

# ── Step 2: Sync IR into every bundle ────────────────────────────────────────
step "Syncing IR to each platform"
mkdir -p "$IOS_DIR/StyleConverterTest/Resources"
mkdir -p "$ANDROID_DIR/app/src/main/assets"
mkdir -p "$WEB_DIR/public"
cp "$OUTPUT_DIR/tmpOutput.json" "$IOS_DIR/StyleConverterTest/Resources/tmpOutput.json"
cp "$OUTPUT_DIR/tmpOutput.json" "$ANDROID_DIR/app/src/main/assets/tmpOutput.json"
cp "$OUTPUT_DIR/tmpOutput.json" "$WEB_DIR/public/ir-components.json"
log "IR synced"

# Install the testing/ helper deps (pngjs, sharp, pixelmatch, ssim.js) now —
# the iOS post-capture step uses normalize-pngs.mjs which pulls in pngjs.
# Doing it here (rather than at step 6) means a single `npm install` for
# the whole run and avoids racy on-demand installs under time pressure.
if [[ ! -d "$TESTING_DIR/node_modules" ]]; then
    log "installing testing deps…"
    ( cd "$TESTING_DIR" && npm install --silent )
fi

# ── Step 3: iOS capture ──────────────────────────────────────────────────────
if [[ "${SKIP_IOS:-0}" == "1" ]]; then
    warn "SKIP_IOS=1 → skipping iOS capture"
elif ! command -v xcodebuild &>/dev/null; then
    warn "xcodebuild not available → skipping iOS"
elif ! /usr/bin/xcode-select -p 2>/dev/null | grep -q Xcode.app; then
    warn "xcode-select still points at CommandLineTools; run: sudo xcode-select -s /Applications/Xcode.app/Contents/Developer"
    warn "skipping iOS"
else
    step "iOS capture"
    if ! command -v xcodegen &>/dev/null; then
        if command -v brew &>/dev/null; then
            log "installing xcodegen via brew…"
            brew install xcodegen >/dev/null
        else
            warn "xcodegen missing and no brew available → skipping iOS"
        fi
    fi
    if command -v xcodegen &>/dev/null; then
        (
            cd "$IOS_DIR"
            rm -rf StyleConverterTest.xcodeproj build
            xcodegen generate --quiet
        )

        # Pick a usable iPhone simulator.
        #
        # Selection priority:
        #   1. SIM_UDID env var (Tier 12 OS-matrix sets this to disambiguate
        #      between e.g. iPhone 17 Pro on iOS 26.0 vs iOS 26.2 — same
        #      device name, different runtimes).
        #   2. SIM_DEVICE env var (matches by name; picks first runtime found).
        #   3. Built-in preference list (newest iPhones first).
        SIM_INFO=$(
            xcrun simctl list devices available -j |
            /usr/bin/python3 -c "
import json, sys, os
data = json.load(sys.stdin)
force_udid = os.environ.get('SIM_UDID', '')
force_name = os.environ.get('SIM_DEVICE', '')
preferred = [force_name] if force_name else ['iPhone 17','iPhone 17 Pro','iPhone 16','iPhone 16 Pro','iPhone 15','iPhone 15 Pro']
iphones = []
for rt, devs in data['devices'].items():
    for d in devs:
        if d.get('isAvailable') and d['name'].startswith('iPhone'):
            iphones.append((d['name'], d['udid'], rt))
# UDID match wins outright — that's how Tier 12 forces a specific runtime.
if force_udid:
    for n, u, rt in iphones:
        if u == force_udid:
            print(f'{n}\t{u}\t{rt}'); sys.exit(0)
    sys.exit(1)
for pref in preferred:
    for n, u, rt in iphones:
        if n == pref:
            print(f'{n}\t{u}\t{rt}'); sys.exit(0)
if iphones:
    n, u, rt = iphones[0]; print(f'{n}\t{u}\t{rt}'); sys.exit(0)
sys.exit(1)
" 2>/dev/null || echo "")

        if [[ -z "$SIM_INFO" ]]; then
            warn "no iPhone simulator available → skipping iOS"
        else
            SIM_NAME=$(echo "$SIM_INFO" | cut -f1)
            SIM_UDID=$(echo "$SIM_INFO" | cut -f2)
            log "simulator: $SIM_NAME ($SIM_UDID)"

            # Boot the simulator headlessly. We DO NOT call `open -a Simulator`:
            # the GUI viewport would steal focus from whatever the user is doing
            # on their Mac (TITAN runs can take hours). `xcrun simctl boot`
            # spins up the SimulatorRenderHost daemon which is all that's needed
            # for `xcrun simctl io <UDID> screenshot` and the in-app capture
            # path used here — the visible window is purely a viewer.
            STATE=$(xcrun simctl list devices -j | /usr/bin/python3 -c "
import json, sys
data = json.load(sys.stdin)
u = '$SIM_UDID'
for _, devs in data['devices'].items():
    for d in devs:
        if d['udid'] == u:
            print(d.get('state','Unknown')); sys.exit(0)
print('Unknown')")
            [[ "$STATE" != "Booted" ]] && xcrun simctl boot "$SIM_UDID"
            # NOTE: `open -a Simulator` was intentionally REMOVED here —
            # see the comment block above. If you need to debug visually,
            # run `open -a Simulator` manually in a separate terminal.

            # Build via -target (avoids the Xcode 26.x scheme/platform bug)
            BUILD_PRODUCTS_DIR="$IOS_DIR/build/Build/Products/Debug-iphonesimulator"
            log "building…"
            xcodebuild \
                -project "$IOS_DIR/StyleConverterTest.xcodeproj" \
                -target StyleConverterTest \
                -configuration Debug \
                -sdk iphonesimulator \
                -arch arm64 \
                CONFIGURATION_BUILD_DIR="$BUILD_PRODUCTS_DIR" \
                CODE_SIGNING_ALLOWED=NO \
                build >/tmp/xcodebuild.log 2>&1 || {
                    err "xcodebuild failed — see /tmp/xcodebuild.log"
                    tail -20 /tmp/xcodebuild.log
                    exit 1
                }
            APP_PATH="$BUILD_PRODUCTS_DIR/StyleConverterTest.app"
            log "installed + launching"
            xcrun simctl terminate "$SIM_UDID" "$IOS_BUNDLE" 2>/dev/null || true
            # Wipe the old app so we start from a clean Documents directory —
            # guarantees we only pull screenshots from THIS run.
            xcrun simctl uninstall "$SIM_UDID" "$IOS_BUNDLE" 2>/dev/null || true
            xcrun simctl install "$SIM_UDID" "$APP_PATH"
            xcrun simctl launch "$SIM_UDID" "$IOS_BUNDLE" >/dev/null

            # Poll the simulator's Documents/test_screenshots directory until
            # all captures land (or progress stalls). Much more robust than a
            # fixed sleep — a fixed sleep has to be long enough for the worst
            # case and silently misses captures if the app is slow to launch.
            APP_CONTAINER=""
            log "waiting for per-component capture…"
            IOS_LAST_COUNT=-1
            IOS_STUCK_FOR=0
            IOS_STUCK_LIMIT=10   # 10 × 1 s = 10 s with no progress → give up
            for i in $(seq 1 60); do
                # `get_app_container` can race with install; tolerate nulls.
                if [[ -z "$APP_CONTAINER" ]]; then
                    APP_CONTAINER=$(xcrun simctl get_app_container "$SIM_UDID" "$IOS_BUNDLE" data 2>/dev/null || echo "")
                fi
                if [[ -n "$APP_CONTAINER" && -d "$APP_CONTAINER/Documents/test_screenshots" ]]; then
                    COUNT=$(count_glob "$APP_CONTAINER/Documents/test_screenshots/*.png")
                else
                    COUNT=0
                fi
                # Force base-10 to avoid bash treating leading-zero strings as octal
                # (auditor round 14 finding — caused intermittent NORESULTs).
                COUNT=$((10#${COUNT:-0}))
                echo "  $COUNT / $COMPONENT_COUNT captured…"
                if [[ "$COUNT" -ge "$COMPONENT_COUNT" ]]; then
                    break
                fi
                if [[ "$COUNT" == "$IOS_LAST_COUNT" ]]; then
                    IOS_STUCK_FOR=$(( IOS_STUCK_FOR + 1 ))
                    if [[ $IOS_STUCK_FOR -ge $IOS_STUCK_LIMIT ]]; then
                        warn "iOS count stuck at $COUNT for $(( IOS_STUCK_FOR ))s — app may have crashed"
                        break
                    fi
                else
                    IOS_STUCK_FOR=0
                    IOS_LAST_COUNT=$COUNT
                fi
                sleep 1
            done

            rm -rf "$IOS_DIR/screenshots"
            mkdir -p "$IOS_DIR/screenshots"
            if [[ -n "$APP_CONTAINER" && -d "$APP_CONTAINER/Documents/test_screenshots" ]]; then
                cp -R "$APP_CONTAINER/Documents/test_screenshots/"*.png "$IOS_DIR/screenshots/" 2>/dev/null || true
                COUNT=$(count_glob "$IOS_DIR/screenshots/*.png")
                log "pulled $COUNT iOS screenshots"

                # iOS's UIImage.pngData() embeds non-deterministic metadata
                # (timestamps etc.), so pixel-identical runs produce different
                # MD5 hashes. Strip the ancillary chunks so captures are byte-
                # reproducible — makes committed baselines usable in git.
                if [[ -d "$TESTING_DIR/node_modules" ]] && command -v node &>/dev/null; then
                    ( cd "$TESTING_DIR" && node normalize-pngs.mjs "$IOS_DIR/screenshots" ) || warn "PNG normalization failed (non-fatal)"
                fi

                CAPTURED_IOS=$COUNT
            else
                warn "no iOS screenshots found in simulator sandbox"
            fi
        fi
    fi
fi

# ── Step 4: Android capture ──────────────────────────────────────────────────
# Use `${X:-}` when probing env vars so `set -u` doesn't abort when a
# user's shell doesn't export ANDROID_HOME / ANDROID_SDK_ROOT.
ADB=""
for c in \
    "$HOME/Library/Android/sdk/platform-tools/adb" \
    "${ANDROID_HOME:-}/platform-tools/adb" \
    "${ANDROID_SDK_ROOT:-}/platform-tools/adb" \
    "$(command -v adb || true)"; do
    [[ -n "$c" && -x "$c" ]] && { ADB="$c"; break; }
done

# Pin the `emulator` binary the same way we pin `adb`. The Android SDK ships
# it under <sdk>/emulator/, which is rarely on PATH on a fresh install. When
# missing entirely we'll skip the auto-launch step but still try to use any
# device the user already has connected.
EMULATOR_BIN=""
for c in \
    "$HOME/Library/Android/sdk/emulator/emulator" \
    "${ANDROID_HOME:-}/emulator/emulator" \
    "${ANDROID_SDK_ROOT:-}/emulator/emulator" \
    "$(command -v emulator || true)"; do
    [[ -n "$c" && -x "$c" ]] && { EMULATOR_BIN="$c"; break; }
done

# Track whether THIS script booted the emulator. If we did, we tear it down
# at the end (unless EMULATOR_KEEP=1). If the user already had one running
# we leave it strictly alone — they may be using it for unrelated work.
EMULATOR_STARTED_BY_US=0
EMULATOR_PID=""

# Headlessly boot an Android emulator if none is connected. Called below only
# when the Android section is going to actually try to capture (gated on
# SKIP_ANDROID, ADB availability, etc.). Returns 0 on success, 1 on failure
# (caller falls through to "no device → skip").
maybe_start_headless_emulator() {
    # Bail if a device/emulator is already attached. `adb devices` lists one
    # line per device; we count those that ended in the literal "device"
    # status (ignoring "offline" / "unauthorized").
    local dev_count
    dev_count=$("$ADB" devices | grep -c "device$" || true)
    if [[ "$dev_count" -gt 0 ]]; then
        log "android: using already-connected device (count=$dev_count)"
        return 0
    fi

    # No device + no emulator binary = nothing we can do. Caller will skip.
    if [[ -z "$EMULATOR_BIN" ]]; then
        warn "no Android device connected and no emulator binary found → skipping Android"
        return 1
    fi

    # Pick an AVD. EMULATOR_AVD env wins, else the first one returned by
    # `emulator -list-avds`. We don't try to be smart about hardware
    # selection — the fixture canvas is 390×844 @ 1px=1dp, which any phone
    # AVD can render.
    local avd avds
    if [[ -n "${EMULATOR_AVD:-}" ]]; then
        avd="$EMULATOR_AVD"
    else
        avds=$("$EMULATOR_BIN" -list-avds 2>/dev/null | head -1 | tr -d '[:space:]')
        if [[ -z "$avds" ]]; then
            warn "no Android AVDs configured (run `avdmanager create avd`) → skipping Android"
            return 1
        fi
        avd="$avds"
    fi
    log "android: launching headless emulator @${avd}"

    # Launch headless. Flag rationale:
    #   -no-window         → no GUI viewport (this is THE focus-stealing fix)
    #   -no-audio          → don't grab the host audio device
    #   -no-boot-anim      → shaves a few seconds off the boot
    #   -no-snapshot       → start clean every run; avoids stale-state surprises
    #   -gpu swiftshader_indirect → software rendering, headless-friendly,
    #                              avoids needing a host GL context
    # nohup + & detaches from this shell so a TERM on test-all.sh doesn't
    # cascade and kill the AVD before we get to the explicit teardown.
    nohup "$EMULATOR_BIN" -avd "$avd" \
        -no-window -no-audio -no-boot-anim -no-snapshot \
        -gpu swiftshader_indirect \
        >/tmp/emulator-headless.log 2>&1 &
    EMULATOR_PID=$!
    EMULATOR_STARTED_BY_US=1
    log "android: emulator pid=$EMULATOR_PID, log=/tmp/emulator-headless.log"

    # Wait up to ~90 s for the device to attach AND finish booting. We do
    # this in two phases:
    #   (a) `adb wait-for-device` blocks until a device shows in `adb
    #       devices`, but it only requires the adb daemon to be up — the
    #       framework may still be starting.
    #   (b) Poll `getprop sys.boot_completed` until "1", which means
    #       SystemServer is up and we can install APKs.
    log "android: waiting for emulator to attach (up to 90s)…"
    # `adb wait-for-device` has no built-in timeout, so wrap it.
    local i
    for i in $(seq 1 90); do
        if "$ADB" devices | grep -q "device$"; then
            break
        fi
        # If the emulator process died (bad AVD, missing system image, etc.)
        # there's no point waiting the rest of the budget.
        if ! kill -0 "$EMULATOR_PID" 2>/dev/null; then
            err "android: emulator process died early — see /tmp/emulator-headless.log"
            tail -20 /tmp/emulator-headless.log >&2 || true
            EMULATOR_STARTED_BY_US=0
            EMULATOR_PID=""
            return 1
        fi
        sleep 1
    done
    if ! "$ADB" devices | grep -q "device$"; then
        err "android: emulator never attached within 90s — giving up"
        return 1
    fi

    log "android: emulator attached, waiting for boot_completed…"
    for i in $(seq 1 90); do
        # `getprop` returns "1\r\n" on Android — strip CR before comparing.
        local booted
        booted=$("$ADB" shell getprop sys.boot_completed 2>/dev/null | tr -d '[:space:]')
        if [[ "$booted" == "1" ]]; then
            log "android: boot_completed in ${i}s"
            return 0
        fi
        sleep 1
    done
    err "android: boot_completed never reached 1 within 90s — proceeding anyway"
    return 0
}

# Tear down the emulator we booted, IF we booted it AND the user didn't
# pin it open with EMULATOR_KEEP=1. Called from the EXIT trap chain.
_teardown_headless_emulator() {
    if [[ "$EMULATOR_STARTED_BY_US" != "1" ]]; then
        return 0
    fi
    if [[ "${EMULATOR_KEEP:-0}" == "1" ]]; then
        log "android: EMULATOR_KEEP=1 → leaving emulator running (pid=$EMULATOR_PID)"
        return 0
    fi
    log "android: killing headless emulator (pid=$EMULATOR_PID)"
    # `adb emu kill` is the clean shutdown (sends a stop command to the
    # qemu console). Falls back to SIGTERM on the PID for the rare case
    # where adb has already detached.
    "$ADB" emu kill 2>/dev/null || true
    if [[ -n "$EMULATOR_PID" ]]; then
        kill -TERM "$EMULATOR_PID" 2>/dev/null || true
        wait "$EMULATOR_PID" 2>/dev/null || true
    fi
    EMULATOR_STARTED_BY_US=0
    EMULATOR_PID=""
}

if [[ "${SKIP_ANDROID:-0}" == "1" ]]; then
    warn "SKIP_ANDROID=1 → skipping Android capture"
elif [[ -z "$ADB" ]]; then
    warn "adb not found → skipping Android"
elif ! maybe_start_headless_emulator; then
    : # warning already printed by the helper; fall through past the else
else
    DEV_COUNT=$("$ADB" devices | grep -c "device$" || true)
    if [[ "$DEV_COUNT" -eq 0 ]]; then
        warn "no Android device/emulator connected → skipping Android"
    else
        step "Android capture"

        # Match the shared 390×844 canvas at 1px = 1dp.
        "$ADB" shell wm size 390x844 >/dev/null
        "$ADB" shell wm density 160 >/dev/null
        log "emulator: 390×844 @ 160dpi"

        # Pick Java 21 for Gradle
        if /usr/libexec/java_home -v 21 &>/dev/null; then
            export JAVA_HOME=$(/usr/libexec/java_home -v 21)
        fi

        log "building + installing APK…"
        (
            cd "$ANDROID_DIR"
            JAVA_HOME="${JAVA_HOME:-}" ./gradlew installDebug --quiet
        )

        "$ADB" shell am force-stop "$ANDROID_PACKAGE" 2>/dev/null || true
        # Wipe device-side captures from any previous run BEFORE launching.
        # The app is supposed to clear on startup, but if it crashes before
        # clearing — or if a previous run had a larger fixture — we'd see
        # stale PNGs and the poll loop below (count >= COMPONENT_COUNT)
        # would exit instantly without waiting for THIS run's captures.
        SCREENSHOT_DIR_DEVICE="/sdcard/Android/data/$ANDROID_PACKAGE/files/test_screenshots"
        "$ADB" shell rm -rf "$SCREENSHOT_DIR_DEVICE" 2>/dev/null || true
        "$ADB" shell am start -n "$ANDROID_PACKAGE/$ANDROID_ACTIVITY" >/dev/null

        # Poll sdcard until all captures land, OR until the count has been
        # stuck for 20 s (→ the app likely crashed mid-capture). The plain
        # "wait 120 s then proceed silently" pattern used to hide crashes.
        log "waiting for per-component capture…"
        LAST_COUNT=-1
        STUCK_FOR=0
        STUCK_LIMIT=10   # 10 × 2 s = 20 s with no progress
        for i in $(seq 1 60); do
            # Same pipefail guard rationale as count_glob above — adb shell
            # ls of a missing/empty directory exits non-zero under pipefail.
            # Strip ALL whitespace (incl newlines) — `wc -l || echo 0` can
            # produce a multi-line "0\n0" that breaks arithmetic.
            COUNT=$("$ADB" shell ls "$SCREENSHOT_DIR_DEVICE" 2>/dev/null | wc -l | tr -d '[:space:]' || echo 0)
            # Force base-10 to avoid bash treating leading-zero strings ("00",
            # "08") as octal — caused NORESULT for InitialLetter/RubyPosition/
            # MaxLines in noop_fix batch (auditor round 14 finding).
            COUNT=$((10#${COUNT:-0}))
            echo "  $COUNT / $COMPONENT_COUNT captured…"
            if [[ "$COUNT" -ge "$COMPONENT_COUNT" ]]; then
                break
            fi
            if [[ "$COUNT" == "$LAST_COUNT" ]]; then
                STUCK_FOR=$(( STUCK_FOR + 1 ))
                if [[ $STUCK_FOR -ge $STUCK_LIMIT ]]; then
                    warn "Android count has been stuck at $COUNT for $(( STUCK_FOR * 2 ))s"
                    warn "app may have crashed — check: $ADB logcat | grep com.styleconverter.test"
                    break
                fi
            else
                STUCK_FOR=0
                LAST_COUNT=$COUNT
            fi
            sleep 2
        done

        rm -rf "$ANDROID_DIR/screenshots"
        mkdir -p "$ANDROID_DIR/screenshots"
        "$ADB" pull "$SCREENSHOT_DIR_DEVICE" "$ANDROID_DIR/screenshots/tmp" >/dev/null 2>&1 || true
        if [[ -d "$ANDROID_DIR/screenshots/tmp" ]]; then
            mv "$ANDROID_DIR/screenshots/tmp"/*.png "$ANDROID_DIR/screenshots/" 2>/dev/null || true
            rmdir "$ANDROID_DIR/screenshots/tmp" 2>/dev/null || true
        fi
        COUNT=$(count_glob "$ANDROID_DIR/screenshots/*.png")
        log "pulled $COUNT Android screenshots"
        CAPTURED_ANDROID=$COUNT
    fi
fi

# ── Step 5: Web capture ──────────────────────────────────────────────────────
if [[ "${SKIP_WEB:-0}" == "1" ]]; then
    warn "SKIP_WEB=1 → skipping web capture"
elif ! command -v node &>/dev/null; then
    warn "node not found → skipping web"
else
    step "Web capture"

    # Ensure web deps are installed
    if [[ ! -d "$WEB_DIR/node_modules/puppeteer" ]]; then
        log "installing web deps…"
        ( cd "$WEB_DIR" && npm install --silent )
    fi

    # Kill anything squatting on the port
    lsof -ti:"$WEB_PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true

    # Start vite. `set +m` silences bash's job-control "Terminated" message
    # when we kill the background process below.
    set +m
    # Launch in its own process group so we can kill the whole tree (npx ->
    # node -> vite -> esbuild helpers) in one `kill -TERM -$PID`. Without
    # this, `kill $VITE_PID` only kills the outer subshell and orphans vite.
    ( cd "$WEB_DIR" && exec npx vite --port "$WEB_PORT" >/tmp/vite.log 2>&1 ) &
    VITE_PID=$!
    # Defensive cleanup on EXIT, SIGINT, SIGTERM, SIGHUP so Ctrl-C or any
    # abnormal termination doesn't leave vite squatting on the port.
    # `lsof | xargs kill -9` is belt-and-suspenders in case the process tree
    # fragmented across PGIDs.
    _cleanup_vite() {
        if [[ -n "${VITE_PID:-}" ]]; then
            kill -TERM "-$VITE_PID" 2>/dev/null || kill -TERM "$VITE_PID" 2>/dev/null || true
            wait "$VITE_PID" 2>/dev/null || true
        fi
        lsof -ti:"$WEB_PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
    }
    # Chain: vite cleanup + lock release. Preserves the lock release that
    # was installed at script start so we don't leave a stale lock behind.
    _testall_cleanup_all() {
        _cleanup_vite
        if declare -F _testall_on_exit >/dev/null 2>&1; then
            _testall_on_exit
        fi
    }
    trap _testall_cleanup_all EXIT INT TERM HUP

    # Poll for vite readiness. Bail fast (a) if the process died — don't
    # wait the full 30s only to have Puppeteer report "connection refused",
    # and (b) with a clear message so the user knows what to look at.
    log "waiting for vite @ :$WEB_PORT…"
    VITE_READY=0
    for i in {1..30}; do
        if curl -s "http://localhost:$WEB_PORT" >/dev/null 2>&1; then
            VITE_READY=1
            break
        fi
        # Has the vite process already died? No point polling further.
        if ! kill -0 "$VITE_PID" 2>/dev/null; then
            break
        fi
        sleep 1
    done

    if [[ $VITE_READY -eq 0 ]]; then
        err "vite failed to start on port $WEB_PORT → skipping web capture"
        err "  vite log tail:"
        tail -20 /tmp/vite.log 2>/dev/null | sed 's/^/    /' >&2 || true
        _cleanup_vite
        # Restore just the lock-release trap (vite cleanup already ran).
        if declare -F _testall_on_exit >/dev/null 2>&1; then
            trap _testall_on_exit EXIT INT TERM HUP
        else
            trap - EXIT INT TERM HUP
        fi
        set -m
    else
        ( cd "$WEB_DIR" && node capture-screenshots.mjs --url "http://localhost:$WEB_PORT" )
        COUNT=$(count_glob "$WEB_DIR/screenshots/*.png")
        log "captured $COUNT web screenshots"
        CAPTURED_WEB=$COUNT

        _cleanup_vite
        # Restore just the lock-release trap (vite cleanup already ran).
        if declare -F _testall_on_exit >/dev/null 2>&1; then
            trap _testall_on_exit EXIT INT TERM HUP
        else
            trap - EXIT INT TERM HUP
        fi
        set -m
    fi
fi

# ── Step 6: Compare ──────────────────────────────────────────────────────────
step "Generating comparison report"
# Testing deps were installed at step 2 (so normalize-pngs.mjs can run during
# the iOS capture step). If someone deleted node_modules between runs, repair.
if [[ ! -d "$TESTING_DIR/node_modules" ]]; then
    log "installing testing deps…"
    ( cd "$TESTING_DIR" && npm install --silent )
fi

COMPARE_ARGS=()
if [[ "${UPDATE_BASELINE:-0}" == "1" ]]; then
    COMPARE_ARGS+=(--update-baseline)
elif [[ "${BASELINE:-0}" == "1" ]]; then
    COMPARE_ARGS+=(--baseline)
fi

# `${X[@]:-}` instead of `${X[@]}` so `set -u` doesn't fire when the array
# is empty (default mode — no baseline flags).
# `--input` threads the IR filename into the HTML report headline so you can
# tell at a glance which test case the report was generated from.
( cd "$TESTING_DIR" && node compare-screenshots.mjs --input "$INPUT_JSON" "${COMPARE_ARGS[@]:-}" )

# ── Summary ──────────────────────────────────────────────────────────────────
# Close out whatever stage was running, then print the summary without
# starting a new "stage" (so no step_end is printed for the summary).
step_end
echo -e "\n${B}━━━ Done ━━━${N}"
SCRIPT_TOTAL=$(( $(date +%s) - SCRIPT_START ))
echo
for platform_info in "iOS:$CAPTURED_IOS" "Android:$CAPTURED_ANDROID" "web:$CAPTURED_WEB"; do
    p="${platform_info%%:*}"
    n="${platform_info##*:}"
    if [[ -n "$n" && "$n" -gt 0 ]]; then
        echo -e "  ${G}✓${N} $p: $n screenshot(s)"
    else
        echo -e "  ${Y}–${N} $p: skipped"
    fi
done

echo
echo -e "  ${G}total elapsed: ${SCRIPT_TOTAL}s${N}"

REPORT_PATH="$TESTING_DIR/report/index.html"
# After --update-baseline there's no comparison report, so don't dangle a
# stale "Report: ..." line pointing at the previous run's output.
if [[ "${UPDATE_BASELINE:-0}" != "1" ]] && [[ -f "$REPORT_PATH" ]]; then
    echo
    echo -e "  Report: ${B}$REPORT_PATH${N}"
    if command -v open &>/dev/null && [[ "${NO_OPEN:-0}" != "1" ]]; then
        open "$REPORT_PATH"
    fi
fi
