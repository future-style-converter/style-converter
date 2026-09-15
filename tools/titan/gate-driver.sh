#!/usr/bin/env bash
# tools/titan/gate-driver.sh — the device gate as CODE (wave 50).
#
# Why this file exists: every previous wave rebuilt the gate driver in its
# session scratchpad from the prose recipe in docs/BACKLOG.md ("Operational
# recipes → Gate script"), and every scratchpad was wiped between sessions —
# the wave-49 "reprovision and retry once" guard was never committed
# (BACKLOG obligation #5), and the retro's own gate driver was lost the same
# way (obligation #1's Current: block). This is the recipe, tracked, with the
# quiet-host preconditions the 17-attempt lesson of 2026-09-05→07 taught
# ("Gate on a quiet host" recipe): the gate itself adds ~10 load and ~4 GB;
# on a 16 GB Mac with browsers open it swaps and then everything times out.
#
# Usage:
#   tools/titan/gate-driver.sh <run-id> [--sections a,b,c] [--max-tests 48]
#       [--android 1] [--ios 1] [--section-timeout-min 50] [--resume]
#       [--skip-corpus] [--skip-fixture-net] [--force] [--plan]
#
#   <run-id>            the run dir name under tools/titan/runs/ (e.g.
#                       wave50-final). The scorer diffs it against the
#                       previous snapshot's run dir by name.
#   --sections a,b,c    subset (default: the 30 corpus sections, in the order
#                       corpus-v6.15 was captured).
#   --max-tests N       depth per section (default 48 = the corpus sampling).
#   --android N/--ios N devices to provision (default 1/1 — the quiet-host
#                       rule: the SECOND concurrent -read-only emulator wedged
#                       in 4 of 5 attempts on this host).
#   --section-timeout-min N   per-section watchdog (default 50). On expiry
#                       the section's process GROUP is killed, the Android
#                       fleet is reprovisioned, and the section is retried
#                       ONCE; two consecutive failed sections abort the gate
#                       (a wedged device fails every later section too).
#   --resume            skip sections already marked OK in this run dir.
#   --skip-corpus       run only the fixture net.
#   --skip-fixture-net  run only the 30 corpus sections.
#   --force             skip the quiet-host precondition check (NOT the disk
#                       check — section-runner enforces that one itself).
#   --plan              print the resolved plan and exit 0 (used by the test).
#
# Environment:
#   GATE_MAX_LOAD       1-min load ceiling for the quiet-host check (6).
#   GATE_MIN_FREE_MB    free+inactive memory floor in MB (2048).
#   ANDROID_SERIAL      honoured if set; otherwise taken from the provisioned
#                       marker when exactly one emulator was provisioned.
#
# Outputs (under tools/titan/runs/<run-id>/gate-driver/):
#   driver.log                 the driver's own timeline
#   <sec>.attempt<N>.log       section-runner output per attempt
#   <sec>.status               OK | FAILED(rc=N) | SHORT(...) | TIMEOUT
#   fixture-net.log / .rc      `BASELINE=1 ./test-all.sh --gate-set` output + rc
#   summary.txt                the per-section table printed at the end
#
# Exit codes:
#   0  every section OK and the fixture net exited 0
#   1  at least one section failed/short, or the fixture net exited non-zero
#      (its rc is in fixture-net.rc — exit 5 = DELETE the named stale ledger
#      line; exit 1 = a baseline regression to PNG-review; see BACKLOG
#      obligation #2 for the whole contract)
#   2  precondition refused (quiet host, disk, missing corpus) or bad usage
set -uo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TITAN_DIR="$PROJECT_ROOT/tools/titan"
# The 30 corpus sections, in corpus-v6.15's capture order — the scorer diffs
# by section name, so order only affects when a wedge would surface.
DEFAULT_SECTIONS="CSS2 css-anchor-position css-backgrounds css-break css-cascade css-color css-contain css-counter-styles css-display css-flexbox css-gaps css-grid css-images css-lists css-masking css-multicol css-overflow css-position css-pseudo css-sizing css-tables css-text css-text-decor css-transforms css-ui css-values css-view-transitions css-writing-modes filter-effects selectors"

RUN_ID=""; SECTIONS="$DEFAULT_SECTIONS"; MAX_TESTS=48; N_ANDROID=1; N_IOS=1
TIMEOUT_MIN=50; RESUME=0; SKIP_CORPUS=0; SKIP_FIXTURE_NET=0; FORCE=0; PLAN=0
GATE_MAX_LOAD="${GATE_MAX_LOAD:-6}"
GATE_MIN_FREE_MB="${GATE_MIN_FREE_MB:-2048}"

while (( $# )); do
  case "$1" in
    --sections) SECTIONS="${2//,/ }"; shift 2 ;;
    --max-tests) MAX_TESTS="$2"; shift 2 ;;
    --android) N_ANDROID="$2"; shift 2 ;;
    --ios) N_IOS="$2"; shift 2 ;;
    --section-timeout-min) TIMEOUT_MIN="$2"; shift 2 ;;
    --resume) RESUME=1; shift ;;
    --skip-corpus) SKIP_CORPUS=1; shift ;;
    --skip-fixture-net) SKIP_FIXTURE_NET=1; shift ;;
    --force) FORCE=1; shift ;;
    --plan) PLAN=1; shift ;;
    -h|--help) sed -n '2,60p' "$0"; exit 0 ;;
    -*) echo "gate-driver: unknown flag $1" >&2; exit 2 ;;
    *) if [[ -z "$RUN_ID" ]]; then RUN_ID="$1"; shift; else echo "gate-driver: unexpected arg $1" >&2; exit 2; fi ;;
  esac
done
[[ -z "$RUN_ID" ]] && { echo "gate-driver: <run-id> is required" >&2; exit 2; }

RUN_DIR="$TITAN_DIR/runs/$RUN_ID"
DRV_DIR="$RUN_DIR/gate-driver"
LOG="$DRV_DIR/driver.log"
log() { local line; line="[gate-driver $(date -u +%H:%M:%S)] $*"; echo "$line"; [[ -d "$DRV_DIR" ]] && echo "$line" >> "$LOG"; }

# ── --plan: the resolved plan, for eyes and for the test ────────────────────
if (( PLAN )); then
  echo "run-id: $RUN_ID"
  echo "run-dir: $RUN_DIR"
  echo "sections ($(wc -w <<<"$SECTIONS" | tr -d ' ')): $SECTIONS"
  echo "max-tests: $MAX_TESTS  devices: android=$N_ANDROID ios=$N_IOS  section-timeout-min: $TIMEOUT_MIN"
  echo "corpus: $(( SKIP_CORPUS ? 0 : 1 ))  fixture-net: $(( SKIP_FIXTURE_NET ? 0 : 1 ))  resume: $RESUME  force: $FORCE"
  echo "quiet-host: load1 < $GATE_MAX_LOAD and free+inactive > ${GATE_MIN_FREE_MB} MB"
  exit 0
fi

# ── Preconditions ───────────────────────────────────────────────────────────
# Corpus prerequisites: the bucket index is derived (gitignored) and the
# mirror is ~1.5 GB sparse — neither is something the driver should fetch
# silently in the middle of a gate.
[[ -f "$TITAN_DIR/wpt-buckets.json" ]] || { echo "gate-driver: tools/titan/wpt-buckets.json missing — run node tools/titan/bucket-wpt.mjs first" >&2; exit 2; }
[[ -d "$PROJECT_ROOT/tools/wpt/css" ]] || { echo "gate-driver: tools/wpt/ corpus missing — run tools/titan/fetch-wpt.sh first" >&2; exit 2; }

# Quiet-host check — the 17-attempt lesson. Measured AFTER our own processes
# are stopped (below), because a leftover emulator or a Chrome-for-Testing
# fleet from lane work is exactly the load this check exists to see through.
host_load1() { sysctl -n vm.loadavg | awk '{print $2}'; }
host_free_mb() { vm_stat | awk '/Pages free/{f=$3} /Pages inactive/{i=$3} END{gsub(/\./,"",f); gsub(/\./,"",i); printf "%d", (f+i)*16384/1048576}'; }
stop_our_processes() {
  # Only OUR processes: the emulator fleet provision-devices launches, the
  # puppeteer browsers lane work leaves behind, and the Gradle daemons of both
  # build roots. The user's browser is theirs — the check below reports it as
  # load/memory and refuses instead of killing it.
  pkill -f 'qemu-system' 2>/dev/null || true
  pkill -f 'Chrome for Testing' 2>/dev/null || true
  export JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21 2>/dev/null || true)}"
  (cd "$PROJECT_ROOT" && ./gradlew --stop -q >/dev/null 2>&1) || true
  (cd "$PROJECT_ROOT/apps/android-harness" && ./gradlew --stop -q >/dev/null 2>&1) || true
}

mkdir -p "$DRV_DIR"
log "run-id=$RUN_ID sections=$(wc -w <<<"$SECTIONS" | tr -d ' ') max-tests=$MAX_TESTS android=$N_ANDROID ios=$N_IOS timeout=${TIMEOUT_MIN}min resume=$RESUME"
stop_our_processes
sleep 5
LOAD1="$(host_load1)"; FREE_MB="$(host_free_mb)"
log "host after stopping our processes: load1=$LOAD1 free+inactive=${FREE_MB}MB (ceiling load $GATE_MAX_LOAD, floor ${GATE_MIN_FREE_MB}MB)"
if (( ! FORCE )); then
  if awk -v l="$LOAD1" -v m="$GATE_MAX_LOAD" 'BEGIN{exit !(l>=m)}'; then
    log "REFUSED: 1-min load $LOAD1 >= $GATE_MAX_LOAD — not a quiet host (close browsers / other sessions' emulators; --force to override)"; exit 2
  fi
  if (( FREE_MB < GATE_MIN_FREE_MB )); then
    log "REFUSED: free+inactive ${FREE_MB}MB < ${GATE_MIN_FREE_MB}MB — the gate would swap (--force to override)"; exit 2
  fi
fi

# ── Devices ─────────────────────────────────────────────────────────────────
# adb's server must be started DETACHED: a server that inherits the driver's
# pipeline fds keeps every later `| while read` open forever (the 5-hour
# hang of 2026-09-05). Never `adb devices | while read` anywhere in here.
start_adb_detached() {
  adb kill-server >/dev/null 2>&1 || true
  nohup adb start-server >/dev/null 2>&1 </dev/null &
  sleep 3
}
provision() {
  rm -f /tmp/titan-device-pool/provisioned-*
  start_adb_detached
  # provision-devices.sh builds both apps, boots the pool, installs with a
  # `pm path` check and `pm clear`s — bounded at 25 min so a wedge here is a
  # logged failure, not a silent night.
  WANT_ANDROID="$N_ANDROID" WANT_IOS="$N_IOS" \
    perl -e 'alarm 1500; exec @ARGV' -- "$TITAN_DIR/provision-devices.sh" --android "$N_ANDROID" --ios "$N_IOS" \
    >"$DRV_DIR/provision.$(date -u +%H%M%S).log" 2>&1
  local rc=$?
  # With exactly one emulator the serial pin is unambiguous; with more the
  # caller must set ANDROID_SERIAL (test-all refuses to guess — exit 2).
  if [[ -z "${ANDROID_SERIAL:-}" && -f /tmp/titan-device-pool/provisioned-android ]] \
     && [[ "$(wc -l < /tmp/titan-device-pool/provisioned-android | tr -d ' ')" == 1 ]]; then
    export ANDROID_SERIAL="$(head -1 /tmp/titan-device-pool/provisioned-android)"
  fi
  log "provision rc=$rc android=[$(tr '\n' ' ' < /tmp/titan-device-pool/provisioned-android 2>/dev/null)] ios=[$(tr '\n' ' ' < /tmp/titan-device-pool/provisioned-ios 2>/dev/null)] ANDROID_SERIAL=${ANDROID_SERIAL:-unset}"
  return $rc
}
reprovision_android() {
  # The watchdog path: kill the emulator fleet we launched and rebuild the
  # pool. Simulators are left alone — every wedge so far was Android-side.
  log "reprovision: killing emulators + adb, rebuilding the pool"
  pkill -f 'qemu-system' 2>/dev/null || true
  sleep 5
  unset ANDROID_SERIAL
  provision
}

# ── One section under a watchdog ────────────────────────────────────────────
# perl's setpgrp puts section-runner (and every feeder/emulator helper it
# spawns) in its own process group, so a timeout kills the whole family with
# one signal instead of orphaning a feeder that keeps the device busy.
run_section_once() { # <sec> <attempt> → rc (124 = watchdog)
  local sec="$1" attempt="$2"
  local slog="$DRV_DIR/$sec.attempt$attempt.log"
  perl -e 'setpgrp(0,0); exec @ARGV' -- "$TITAN_DIR/section-runner.sh" "$sec" --all-platforms --max-tests "$MAX_TESTS" --run-id "$RUN_ID" --foreground >"$slog" 2>&1 &
  local pid=$! t0=$SECONDS
  while kill -0 "$pid" 2>/dev/null; do
    if (( SECONDS - t0 > TIMEOUT_MIN * 60 )); then
      log "WATCHDOG: $sec attempt $attempt exceeded ${TIMEOUT_MIN} min — killing process group $pid"
      kill -TERM -- "-$pid" 2>/dev/null; sleep 10; kill -KILL -- "-$pid" 2>/dev/null
      wait "$pid" 2>/dev/null
      return 124
    fi
    sleep 30
  done
  wait "$pid"; return $?
}
# Capture COUNTS per column against tests.list — the retro's rule: verify
# counts, never process liveness. A composed capture is one PNG per test
# (component PNGs carry a __N suffix and are not counted).
column_report() { # <sec> → "web=N/T android=N/T ios=N/T" and rc 1 if any short
  local sec="$1" sdir="$RUN_DIR/sections/$1" short=0 out="" pair label col have want
  want="$(wc -l < "$sdir/tests.list" 2>/dev/null | tr -d ' ')"; want="${want:-0}"
  for pair in "web:screenshots" "android:android-screenshots" "ios:ios-screenshots"; do
    label="${pair%%:*}"; col="${pair#*:}"
    have="$(ls "$sdir/$col"/*.png 2>/dev/null | grep -vc '__[0-9][0-9]*\.png$')"
    out+="$label=$have/$want "
    (( have != want )) && short=1
  done
  echo "$out"
  return $short
}

# ── Corpus: 30 sections × depth ─────────────────────────────────────────────
GATE_RC=0
if (( ! SKIP_CORPUS )); then
  provision || log "WARNING: provision returned non-zero — continuing, the first section will show whether the pool is usable"
  consecutive_failures=0
  for sec in $SECTIONS; do
    status_file="$DRV_DIR/$sec.status"
    if (( RESUME )) && [[ -f "$status_file" ]] && grep -q '^OK' "$status_file"; then
      log "$sec: OK already (resume) — skipping"; continue
    fi
    sec_ok=0
    for attempt in 1 2; do
      log "$sec: attempt $attempt starting"
      run_section_once "$sec" "$attempt"; rc=$?
      cols="$(column_report "$sec")"; short=$?
      log "$sec: attempt $attempt rc=$rc columns: $cols"
      if (( rc == 0 && short == 0 )); then
        echo "OK attempt=$attempt $cols" > "$status_file"; sec_ok=1; break
      fi
      if (( attempt == 1 )); then
        # Kill + reprovision + retry once (the guard wave 49 never committed).
        reprovision_android || log "WARNING: reprovision returned non-zero"
      else
        if (( rc == 124 )); then echo "TIMEOUT $cols" > "$status_file"
        elif (( short )); then echo "SHORT rc=$rc $cols" > "$status_file"
        else echo "FAILED rc=$rc $cols" > "$status_file"; fi
      fi
    done
    if (( sec_ok )); then consecutive_failures=0
    else
      GATE_RC=1; consecutive_failures=$((consecutive_failures + 1))
      log "$sec: FAILED after 2 attempts ($(cat "$status_file"))"
      if (( consecutive_failures >= 2 )); then
        log "ABORT: two consecutive sections failed — the pool is wedged; fix the device, then re-run with --resume"
        break
      fi
    fi
  done
  # Aggregate + dashboard (README "The device gate" step 7); best effort.
  node "$TITAN_DIR/aggregate-sections.mjs" "$RUN_DIR" >"$DRV_DIR/aggregate.log" 2>&1 || log "WARNING: aggregate-sections exited non-zero (see gate-driver/aggregate.log)"
  node "$TITAN_DIR/render-titan.mjs" --run "$RUN_ID" >"$DRV_DIR/render.log" 2>&1 || log "note: render-titan exited non-zero (dashboard only)"
fi

# ── The fixture net: BASELINE=1 ./test-all.sh --gate-set ────────────────────
# One child run per line of tools/visual/gate-fixtures.txt (retro A12#3), so
# every fixture-scoped ledger line and every _expect.waive can go stale.
# Non-zero here is INFORMATION for the scorer, not an abort: exit 5 means
# DELETE the named stale ledger line, exit 1 a baseline regression to review.
if (( ! SKIP_FIXTURE_NET )); then
  if [[ ! -f /tmp/titan-device-pool/provisioned-android ]]; then
    provision || log "WARNING: provision for the fixture net returned non-zero"
  fi
  log "fixture net: BASELINE=1 ./test-all.sh --gate-set (ANDROID_SERIAL=${ANDROID_SERIAL:-unset})"
  ( cd "$PROJECT_ROOT" && BASELINE=1 perl -e 'alarm 5400; exec @ARGV' -- ./test-all.sh --gate-set ) >"$DRV_DIR/fixture-net.log" 2>&1
  FX_RC=$?
  echo "$FX_RC" > "$DRV_DIR/fixture-net.rc"
  log "fixture net rc=$FX_RC (5 = stale ledger line → DELETE it; 1 = baseline regression → PNG review; 7 = missing/short column → fix the capture)"
  (( FX_RC != 0 )) && GATE_RC=1
fi

# ── Teardown + summary ──────────────────────────────────────────────────────
pkill -f 'qemu-system' 2>/dev/null || true
{
  echo "gate-driver summary — run-id $RUN_ID — $(date -u +%Y-%m-%dT%H:%M:%SZ)"
  for sec in $SECTIONS; do printf '%-26s %s\n' "$sec" "$(cat "$DRV_DIR/$sec.status" 2>/dev/null || echo 'not run')"; done
  [[ -f "$DRV_DIR/fixture-net.rc" ]] && echo "fixture-net rc=$(cat "$DRV_DIR/fixture-net.rc")"
  echo "score: node tools/titan/score-gate.mjs <previous-run-id> $RUN_ID"
} | tee "$DRV_DIR/summary.txt"
exit $GATE_RC
