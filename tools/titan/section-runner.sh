#!/usr/bin/env bash
#
# tools/titan/section-runner.sh — Phase 2 per-section orchestrator.
#
# What is this?
# -------------
# Phase 1 (run-titan.sh) runs the SMOKE_TESTS list (100 tests across 8
# sections) sequentially in a single process. Phase 2 fans bucket-A out
# across ~30 spec sections, one Claude agent per section, in parallel.
# Naive parallelism (N copies of run-titan.sh) races on a long list of
# shared resources spelled out in TITAN_ARCHITECTURE.md §10 Q10:
#
#   - tools/visual/report/manifest.json    (compare-screenshots overwrites)
#   - vite port 3000                   (only one bind per port)
#   - /tmp/style-converter-testall.lock (mkdir-atomic; second runner aborts)
#   - apps/web-harness/public/ir-components.json (vite serves this; one writer)
#   - apps/web-harness/screenshots/    (capture-screenshots wipes & rewrites)
#   - tools/wpt/refs/<sha>/...       (browser-ref cache)
#   - fixtures/wpt/_smoke-combined.json (build-combined-fixture writes)
#
# This script fixes each by routing every per-section artifact through a
# per-section work dir under tools/titan/runs/<runId>/sections/<section>/.
# The browser-ref cache is left at its global path because it's already
# section-keyed (section/stem).png — two sections never write the same file.
#
# Usage:
#   tools/titan/section-runner.sh <section> [--max-tests N] [--run-id ID]
#                                   [--web-only|--all-platforms] [--foreground]
#
# Required positional arg:
#   <section>    Name of the spec section, matching wpt-buckets.json's
#                bySpecSection keys (e.g. "css-flexbox", "css-grid",
#                "css-color"). Determines which bucket-A tests we run.
#
# Optional flags:
#   --max-tests N      Cap the number of tests run (for smoke validation).
#                      Default: every bucket-A test in the section.
#   --run-id ID        Reuse an existing run dir (for the SWARM dispatcher
#                      that pre-creates one and hands the same ID to every
#                      agent). Default: <iso-timestamp>-<section>.
#   --web-only         Capture web only (default; matches Phase 2 scope per
#                      TITAN spec §6.2 — iOS/Android Phase 2 work is its
#                      own follow-up).
#   --all-platforms    Capture iOS + Android too. Requires the platform
#                      validation work that's outside Phase 2 scope.
#   --foreground       Override BACKGROUND_MODE=1 — opens GUIs (debug only).
#
# Outputs (under tools/titan/runs/<runId>/sections/<section>/):
#   manifest.json              — per-section v4 TITAN manifest
#   tests.list                 — the resolved test list (cap-applied)
#   combined.json              — per-section combined fixture (gradle input)
#   web/                       — per-section vite root (rsync'd from apps/web-harness)
#   web/public/ir-components.json — IR served to vite
#   screenshots/               — per-section web captures
#   report/                    — per-section HTML + diff PNGs
#   extract.log / capture.log / compare.log — per-stage logs
#
# Exit codes:
#   0 — full success
#   1 — at least one stage failed (extract/capture/compare), OR — under
#       --all-platforms — a native column came back SHORT or ABSENT
#       (NATIVE_SHORT, retro R8b/A9#1: the manifest IS written first, for
#       diagnosis; the exit code is what the gate driver keys on)
#   2 — infra error (missing corpus, missing tools, lock contention, disk
#       preflight below TITAN_MIN_FREE_GB, or the low-disk watchdog — see
#       $WORK_DIR/DISK_ABORT)

set -euo pipefail

# Track wall-clock so we can stamp the manifest's wpt.duration.captureMs with
# something more useful than null. inject-wpt-block reads this from the
# capture log via a `total elapsed: <N>s` regex, so we synthesize that line
# at the end (Step 5 closeout) using SECTION_START.
SECTION_START=$(date +%s)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS_DIR="$PROJECT_ROOT/tools"
TITAN_DIR="$TOOLS_DIR/titan"
WPT_DIR="$TOOLS_DIR/wpt"
RUNS_DIR="$TITAN_DIR/runs"
BUCKETS_FILE="$TITAN_DIR/wpt-buckets.json"

# ── Defaults ─────────────────────────────────────────────────────────────────

# Background mode is the spec-mandated default — TITAN runs are unattended
# and hours long; opening a GUI mid-run steals focus from the user's other
# work. Caller can flip this with --foreground.
export BACKGROUND_MODE="${BACKGROUND_MODE:-1}"

# Default platform scope per task §1: --web-only is Phase 2 scope. iOS /
# Android parallel dispatch is its own follow-up because each platform's
# capture pipeline pins shared singletons (CoreSimulator, the emulator
# AVD).
PLATFORM_SCOPE="web-only"

# WPT git SHA — read from tools/titan/WPT_REF unless overridden.
WPT_REF="${WPT_REF:-$(awk '!/^#/ && NF { print $1; exit }' "$TITAN_DIR/WPT_REF")}"

MAX_TESTS=""
RUN_ID_OVERRIDE=""
SECTION=""

# ── Args ─────────────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
  case "$1" in
    --max-tests)      MAX_TESTS="$2"; shift 2 ;;
    --run-id)         RUN_ID_OVERRIDE="$2"; shift 2 ;;
    --web-only)       PLATFORM_SCOPE="web-only"; shift ;;
    --all-platforms)  PLATFORM_SCOPE="all"; shift ;;
    --foreground)     BACKGROUND_MODE=0; shift ;;
    --background)     BACKGROUND_MODE=1; shift ;;
    --help|-h)
      sed -n '1,80p' "$0"
      exit 0
      ;;
    -*)
      echo "[section-runner] unknown flag: $1" >&2
      exit 2
      ;;
    *)
      if [[ -z "$SECTION" ]]; then
        SECTION="$1"
      else
        echo "[section-runner] extra positional arg: $1 (section already set to $SECTION)" >&2
        exit 2
      fi
      shift
      ;;
  esac
done

if [[ -z "$SECTION" ]]; then
  echo "[section-runner] usage: section-runner.sh <section> [--max-tests N] [--run-id ID]" >&2
  exit 2
fi

# Section name sanity — matches the wpt-buckets.json key set ([A-Za-z][A-Za-z0-9_-]*).
# Reject path traversal here so the per-section dir is safe to mkdir/rm.
if ! [[ "$SECTION" =~ ^[A-Za-z][A-Za-z0-9_-]+$ ]]; then
  echo "[section-runner] illegal section name: $SECTION" >&2
  exit 2
fi

# ── Colors ───────────────────────────────────────────────────────────────────
R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; N='\033[0m'
TAG="[section/$SECTION]"
log()  { echo -e "${G}${TAG}${N} $*"; }
warn() { echo -e "${Y}${TAG}${N} $*" >&2; }
err()  { echo -e "${R}${TAG}${N} $*" >&2; }
step() { echo -e "\n${B}━━━ ${TAG} $* ━━━${N}"; }

# ── Per-section lock ─────────────────────────────────────────────────────────
#
# Mirrors test-all.sh's mkdir-atomic lock pattern (lines 30-65 of test-all.sh)
# but keyed PER SECTION. Two agents both running `section-runner.sh
# css-flexbox` would still race on the per-section work dir + cached vite
# port; this lock catches that. Different sections (`css-flexbox` and
# `css-color`) hold different lock dirs and proceed in parallel.
LOCK="/tmp/titan-section-${SECTION}.lock"
if ! mkdir "$LOCK" 2>/dev/null; then
  other_pid=$(cat "$LOCK/pid" 2>/dev/null || echo "")
  # Stale-lock self-heal (mirrors test-all.sh): `kill -0` probes process
  # existence without signalling. If the recorded pid is numeric and
  # provably dead, the previous run crashed and the lock is stale; reclaim
  # it instead of demanding a manual `rm -rf`. A live pid — or a missing/
  # garbled pid file, which could be a runner that just mkdir'd and hasn't
  # written its pid yet — still aborts.
  if [[ "$other_pid" =~ ^[0-9]+$ ]] && ! kill -0 "$other_pid" 2>/dev/null; then
    warn "stale lock: pid $other_pid is dead — reclaiming $LOCK"
    rm -rf "$LOCK"
    # Re-acquire atomically — a concurrent invocation may have raced us
    # to the same reclaim; whoever wins mkdir runs, the loser aborts.
    if ! mkdir "$LOCK" 2>/dev/null; then
      err "another section-runner grabbed the lock during reclaim — aborting"
      exit 2
    fi
  else
    err "another section-runner holds $LOCK (pid=${other_pid:-?}). rm -rf $LOCK if stale."
    exit 2
  fi
fi
echo "$$" > "$LOCK/pid"
trap 'rm -rf "$LOCK" 2>/dev/null || true; rm -rf "${VITE_TMPDIR:-/dev/null}" 2>/dev/null || true' EXIT
# Fatal signals exit with the conventional 128+signo code, which fires the
# EXIT trap above. Trapping the cleanup command on the signals directly —
# the old scheme — had two bugs (mirrors test-all.sh): bash RESUMES the
# script after a trapped signal (a Ctrl-C'd run kept running with the lock
# already released), and cleanup then ran a second time at EOF.
trap 'exit 130' INT
trap 'exit 143' TERM
trap 'exit 129' HUP

# ── Pre-flight ───────────────────────────────────────────────────────────────

step "Pre-flight"
[[ -d "$WPT_DIR/css" ]]      || { err "$WPT_DIR/css missing — run tools/titan/fetch-wpt.sh"; exit 2; }
[[ -f "$BUCKETS_FILE" ]]     || { err "$BUCKETS_FILE missing — run tools/titan/bucket-wpt.mjs"; exit 2; }
command -v node    >/dev/null || { err "node not found"; exit 2; }
command -v rsync   >/dev/null || { err "rsync not found"; exit 2; }
command -v lsof    >/dev/null || warn "lsof not found — skipping port-busy preflight check"

# ── Disk preflight (retro R8b / A10#12 — BACKLOG obligation #6 made code) ────
#
# The first wave-49 gate attempt lost its whole Android column when the HOST
# hit 94% full: the emulator destabilised into an adb "Broken pipe" during
# :app:installDebug. Until now the "≥10G or abort" preflight and the "<8G"
# mid-run abort lived only in BACKLOG prose (the gate script was re-derived
# from the template every wave), so any wave could drop them silently. This
# is the code. `df -Pk` is POSIX (macOS and Linux agree on it; `df -g` is
# BSD-only) and column 4 is Available in 1K blocks → integer GiB. Both floors
# are env-tunable for a small machine, never switch-off-able.
TITAN_MIN_FREE_GB="${TITAN_MIN_FREE_GB:-10}"    # preflight floor  (BACKLOG: "≥10G or abort")
TITAN_ABORT_FREE_GB="${TITAN_ABORT_FREE_GB:-8}" # mid-run watchdog (BACKLOG: "<8G is a gate abort")
_free_gb() { df -Pk "$PROJECT_ROOT" 2>/dev/null | awk 'NR==2 { printf "%d", $4 / 1048576 }'; }
FREE_GB="$(_free_gb)"
if [[ -z "$FREE_GB" ]]; then
  warn "df unreadable for $PROJECT_ROOT — disk preflight skipped"
elif (( FREE_GB < TITAN_MIN_FREE_GB )); then
  # Refuse BEFORE creating the run dir: a section that starts on a nearly
  # full disk ends as the wave-49 incident did. The recipe that freed space
  # (BACKLOG obligation #6) is named so the operator need not re-derive it.
  err "disk preflight: ${FREE_GB}G free < ${TITAN_MIN_FREE_GB}G — refusing to start. Prune tools/titan/runs/*, delete Shutdown sims + DerivedData, then 'tmutil thinlocalsnapshots / 21474836480 4' (APFS parks freed space in local snapshots)."
  exit 2
fi
log "disk: ${FREE_GB:-?}G free (floor ${TITAN_MIN_FREE_GB}G; watchdog aborts below ${TITAN_ABORT_FREE_GB}G)"

# ── Deterministic vite port from section name ────────────────────────────────
#
# Hash the section name into the 3100..3299 range. Reproducible per-section
# port lets us correlate logs across runs and avoids the race of "ask the
# kernel for an ephemeral port" between bind() and `vite --port`.
# cksum is portable (BSD + GNU + macOS) — no python/node dep needed for the
# initial port assignment. % 200 keeps us inside the reserved TITAN range.
PORT_OFFSET=$(echo -n "$SECTION" | cksum | awk '{print $1 % 200}')
PORT=$((3100 + PORT_OFFSET))
log "vite port: $PORT (deterministic from section name)"

# Probe whether something is squatting on the port — if so, fall back to the
# next free port within the reserved 3100..3299 range. This covers the rare
# case where the deterministic hash collides with another process.
if command -v lsof >/dev/null && lsof -ti:"$PORT" >/dev/null 2>&1; then
  warn "port $PORT busy — scanning for free port in 3100..3299"
  for try in $(seq 3100 3299); do
    if ! lsof -ti:"$try" >/dev/null 2>&1; then
      PORT=$try
      log "fell back to port $PORT"
      break
    fi
  done
fi
export WEB_PORT="$PORT"

# ── Run dir + work dir ───────────────────────────────────────────────────────

if [[ -n "$RUN_ID_OVERRIDE" ]]; then
  RUN_ID="$RUN_ID_OVERRIDE"
else
  # Per-agent timestamp + section so two `section-runner.sh css-color`
  # invocations launched at the same second still get unique paths.
  RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)-${SECTION}"
fi
RUN_DIR="$RUNS_DIR/$RUN_ID"
WORK_DIR="$RUN_DIR/sections/$SECTION"
mkdir -p "$WORK_DIR"
log "run id: $RUN_ID"
log "work dir: $WORK_DIR"

# ── Low-disk watchdog (retro R8b / A10#12) ───────────────────────────────────
#
# A background poller: every 30s it re-reads free space and, below
# TITAN_ABORT_FREE_GB, writes $WORK_DIR/DISK_ABORT (the number + UTC time, for
# the gate driver to read) and signals this shell with USR1. The handler kills
# the native feeders (they would otherwise keep writing to a full disk while
# holding their pool slots), then exits 2 — the infra-error code — which fires
# the EXIT trap (lock, slots, vite). bash defers a trap while a FOREGROUND
# command runs (gradle, compare, inject), so there the abort lands when that
# command returns; during the feeders' `wait` it lands immediately (bash
# `wait` returns on a trapped signal). The poller stops on its own once this
# shell is gone (`kill -0 $$` fails) and every EXIT trap below also stops it.
DISK_ABORT_MARKER="$WORK_DIR/DISK_ABORT"
rm -f "$DISK_ABORT_MARKER"   # a stale marker from a re-used --run-id must not read as this run's abort
_on_disk_abort() {
  err "DISK WATCHDOG: $(cat "$DISK_ABORT_MARKER" 2>/dev/null || echo 'free space') is below ${TITAN_ABORT_FREE_GB}G — aborting section (marker: $DISK_ABORT_MARKER)"
  kill -TERM ${FEED_ANDROID_PID:-} ${FEED_IOS_PID:-} 2>/dev/null || true   # unquoted on purpose: empty pids vanish
  exit 2
}
trap '_on_disk_abort' USR1
(
  # Subshell: traps reset to defaults here, so its exit never runs the parent's
  # EXIT cleanup (the same isolation the backgrounded vite relies on).
  while kill -0 "$$" 2>/dev/null; do
    free="$(_free_gb)"
    if [[ -n "$free" ]] && (( free < TITAN_ABORT_FREE_GB )); then
      echo "free=${free}G at $(date -u +%Y-%m-%dT%H:%M:%SZ)" > "$DISK_ABORT_MARKER"
      kill -USR1 "$$" 2>/dev/null
      exit 0
    fi
    sleep 30
  done
) &
DISK_WD_PID=$!
_stop_disk_watchdog() { kill "${DISK_WD_PID:-}" 2>/dev/null || true; }
# Re-install the base EXIT trap with the watchdog stop in front (the later
# trap rewrites below each carry it too).
trap '_stop_disk_watchdog; rm -rf "$LOCK" 2>/dev/null || true; rm -rf "${VITE_TMPDIR:-/dev/null}" 2>/dev/null || true' EXIT

# ── Resolve test list (bucket-A entries in the section) ─────────────────────

step "Resolving test list"
TESTS_LIST="$WORK_DIR/tests.list"
node -e "
const m = require('$BUCKETS_FILE');
const section = process.argv[1];
const max = process.argv[2] ? Number(process.argv[2]) : Infinity;
// Filter bucket-A by spec section. Section path layout is css/<section>/...;
// nested subdirs (css/<section>/sub/foo.html) belong to <section>.
let arr = (m.buckets?.A || []).filter(t => t.split('/')[1] === section);
if (Number.isFinite(max)) arr = arr.slice(0, max);
process.stdout.write(arr.join('\n') + (arr.length ? '\n' : ''));
" "$SECTION" "${MAX_TESTS:-}" > "$TESTS_LIST"

N=$(awk 'NF' "$TESTS_LIST" | wc -l | tr -d '[:space:]')
if [[ "$N" == "0" ]]; then
  err "no bucket-A tests for section '$SECTION' (typo? not in wpt-buckets.json bySpecSection?)"
  exit 2
fi
log "tests: $N (cap: ${MAX_TESTS:-none})"

# Read SMOKE-style array for the extract step (bash 3.2 compatible).
TESTS=()
while IFS= read -r _line; do
  [[ -n "$_line" ]] && TESTS+=("$_line")
done < "$TESTS_LIST"

# ── Step 1: extract per-test fixtures ────────────────────────────────────────
#
# extract-fixture.mjs writes to fixtures/wpt/<section>/<stem>.json — that's
# the SAME path other section-runners write to. SAFE: each section-runner
# only touches its own section's subdir (e.g. css-flexbox writes to
# fixtures/wpt/css-flexbox/, css-grid to fixtures/wpt/css-grid/), so no
# collision between agents working different sections. The intra-section
# write happens once per file from a single process.

step "Step 1: extract fixtures (n=$N)"
EXTRACT_LOG="$WORK_DIR/extract.log"
EXTRACT_RC=0
# RC-A5a (wave 19): POST_LOAD_EXTRACT=1 is set HERE, not inherited from the
# caller's environment. extract-fixture.mjs only augments wall-tagged tests
# (requires-script-mutation / requires-script-driven-scroll — post-load's
# isWallTagged gate) when this env is present; without it a fresh section run
# silently re-extracted those tests STATIC-ONLY, clobbering any earlier
# post-load stamp and leaving e.g. dynamic-align-self-001 rendering its
# pre-mutation state (postLoadExtracted:false). Engagement must not depend
# on ambient shell state. All existing stability/scroll/structure bails and
# the top-layer decline inside post-load-extract.mjs stay intact.
# wave-30 fix-T3: BIDI_BAKE=1 is pinned HERE for exactly the same reason and
# with exactly the same failure mode. extract-fixture's bidi bake is opt-in
# (`--bidi-bake` / BIDI_BAKE=1, see its main()), and the bake is what gives an
# RTL test its measured visual order. Left to the ambient shell it engaged
# only when the operator happened to export it: wave29-final's
# selectors/dir-selector-change-003 and -004 carry baked bidi geometry that a
# fresh `section-runner.sh selectors` reproduced WITHOUT, silently downgrading
# those two tests to logical order with no marker to explain the drift.
# Engagement must not depend on ambient shell state — same contract as
# POST_LOAD_EXTRACT above. The two are independent and compose (state first,
# then bidi geometry measured on the same settled page).
POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node "$TITAN_DIR/extract-fixture.mjs" "${TESTS[@]}" >"$EXTRACT_LOG" 2>&1 || EXTRACT_RC=$?
EXTRACTED_OK=$(grep -c '^extracted ' "$EXTRACT_LOG" || true)
EXTRACTED_FAIL=$(grep -c '^FAIL ' "$EXTRACT_LOG" || true)
log "extracted=$EXTRACTED_OK failed=$EXTRACTED_FAIL"
if [[ "$EXTRACT_RC" -ne 0 ]]; then
  warn "extract step exited non-zero ($EXTRACT_RC) — see $EXTRACT_LOG"
fi

# ── Step 2: browser-ref capture (cached) ─────────────────────────────────────
#
# Cache lives at tools/wpt/refs/<sha>/white-black-ink-font-lh/<section>/<stem>.png —
# the `white-black-ink-font-lh` segment is capture-browser-ref.mjs's CANVAS_REV
# (the corpus-v4.1 contract: white canvas + spec-BLACK injected default
# ink + the harness Inter font stack + the deterministic REF_LINE_HEIGHT
# line-height pin on the ref; the line-height-less black-ink scratch refs
# sit at refs/<sha>/white-black-ink-font/, the v4.0 white-ink refs at
# refs/<sha>/white/ and pre-v4 dark
# refs at the un-segmented path — none mixes into v4.1 diffs). Section-keyed
# so two sections never overwrite each other; intra-section writes are
# serial within this process. capture-browser-ref.mjs already tolerates
# `existsSync(dest)` and short-circuits to `cached` — multi-agent re-runs
# are safe.

step "Step 2: browser-ref capture"
REF_LOG="$WORK_DIR/browser-ref.log"
node "$TITAN_DIR/capture-browser-ref.mjs" "${TESTS[@]}" >"$REF_LOG" 2>&1 || warn "browser-ref had failures — see $REF_LOG"
REFS_RENDERED=$(grep -c 'rendered ' "$REF_LOG" || true)
REFS_CACHED=$(grep -c 'cache '    "$REF_LOG" || true)
log "browser-refs: rendered=$REFS_RENDERED cached=$REFS_CACHED"

# ── Step 3: build per-section combined fixture ──────────────────────────────
#
# Per-section path keeps multiple agents from racing on
# fixtures/wpt/_smoke-combined.json. inject-wpt-block.mjs is taught to look
# at --combined so it can read this section's keyMap.

step "Step 3: build combined fixture"
COMBINED_FIXTURE="$PROJECT_ROOT/fixtures/wpt/_section-${SECTION}.json"
node "$TITAN_DIR/build-combined-fixture.mjs" --out "$COMBINED_FIXTURE" --tests "$TESTS_LIST"
COMP_COUNT=$(grep -o '"properties"' "$COMBINED_FIXTURE" | wc -l | tr -d '[:space:]')
log "combined: $COMP_COUNT components → $COMBINED_FIXTURE"

# ── Step 4: gradle convert (per-section out file) ────────────────────────────
#
# gradle's `--args=-o out` defaults to PROJECT_ROOT/out which is shared
# across all section-runners. Override to write into the work dir so
# concurrent agents don't clobber each other's IR mid-flight.

step "Step 4: convert IR (gradle)"
GRADLE_OUT_DIR="$WORK_DIR/out"
mkdir -p "$GRADLE_OUT_DIR"
REL_INPUT="fixtures/wpt/_section-${SECTION}.json"
GRADLE_LOG="$WORK_DIR/gradle-convert.log"
(
  cd "$PROJECT_ROOT"
  # Match test-all.sh's fallback: try gradle daemon first, fall back to the
  # prebuilt JVM classes if the daemon is unreachable (port exhaustion).
  if ! ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i $REL_INPUT -o $GRADLE_OUT_DIR" --quiet >"$GRADLE_LOG" 2>&1; then
    if grep -q "Could not connect to the Gradle daemon\|BindException" "$GRADLE_LOG" 2>/dev/null && \
       [[ -d "$PROJECT_ROOT/converter/build/classes/kotlin/main" ]]; then
      warn "gradle daemon unreachable — using prebuilt classes"
      CP="$PROJECT_ROOT/converter/build/classes/kotlin/main"
      CP="$CP:$(find "$HOME/.gradle/caches/modules-2" -name '*.jar' 2>/dev/null | tr '\n' ':')"
      java -cp "$CP" app.MainKt convert --from css --to ir -i "$REL_INPUT" -o "$GRADLE_OUT_DIR"
    else
      tail -20 "$GRADLE_LOG" >&2
      err "gradle convert failed — see $GRADLE_LOG"
      exit 1
    fi
  fi
)
[[ -f "$GRADLE_OUT_DIR/tmpOutput.json" ]] || { err "gradle did not produce tmpOutput.json"; exit 1; }
log "IR: $(grep -o '"id"' "$GRADLE_OUT_DIR/tmpOutput.json" | wc -l | tr -d '[:space:]') components"

# ── Step 5: per-section vite root + capture ─────────────────────────────────
#
# We need apps/web-harness/public/ir-components.json to point at THIS section's
# IR while a sibling agent's vite is serving a different section's IR. We
# can't rewrite that path (App.tsx hard-codes /ir-components.json), so we
# materialize a per-section copy of apps/web-harness/ via rsync (excluding the
# heavy node_modules + dist + screenshots), symlink node_modules so we
# don't pay the npm install cost, and run vite from there. ~50ms cost per
# section-runner; eliminates the public/ir-components.json race.

step "Step 5: spawn isolated web stack"
WEB_ROOT="$WORK_DIR/web"
mkdir -p "$WEB_ROOT"
# rsync excludes the heavy / per-run-derived dirs. -a preserves symlinks
# and timestamps; --delete keeps the per-section root in sync if we re-run.
rsync -a --delete \
  --exclude=node_modules --exclude=dist --exclude=screenshots \
  "$PROJECT_ROOT/apps/web-harness/" "$WEB_ROOT/"
# Symlink node_modules — the per-section root resolves imports against the
# real cache without paying for ~200MB of npm copy.
[[ -e "$WEB_ROOT/node_modules" ]] || ln -s "$PROJECT_ROOT/node_modules" "$WEB_ROOT/node_modules"
mkdir -p "$WEB_ROOT/public"
cp "$GRADLE_OUT_DIR/tmpOutput.json" "$WEB_ROOT/public/ir-components.json"

# Per-section screenshot dir — capture-screenshots.mjs writes here, then
# compare-screenshots.mjs reads via the WEB_SCREENSHOTS_DIR env we added.
WEB_SHOTS_DIR="$WORK_DIR/screenshots"
rm -rf "$WEB_SHOTS_DIR"
mkdir -p "$WEB_SHOTS_DIR"

# Spin up vite in its own process group so we can kill the whole tree on
# exit (mirrors test-all.sh's vite handling at lines 671-700).
#
# ── wave-35 lane B2: the @font-face ASSET HOP for the section pipeline ──────
#
# vite.config.ts's /wpt-font/ route resolves the IR's corpus-relative
# `fontFaces[].src` against a corpus root it derives as
# `<config dir>/../../tools/wpt` — correct for the canonical apps/web-harness/,
# WRONG for this rsync'd copy, which sits at
# runs/<id>/sections/<section>/web/ and would resolve to the nonexistent
# runs/<id>/sections/tools/wpt. Every font request would 404 and every face
# would silently fall back — the exact "screenshot records the wrong typeface
# with no error anywhere" failure useFontFaces.ts's font-display:block guard
# exists to prevent.
#
# The route already honours WPT_DIR for exactly this reason (same env var
# extract-fixture.mjs reads), so the hop is one exported variable rather than
# a copy step: the corpus stays the single source of truth, and no stale copy
# under a per-section public/ can shadow a corpus update. (Contrast the
# NATIVE hop, which must copy — a device cannot read the host's disk; see the
# --wpt-dir flag threaded to the two feeders in Step 5b.)
export WPT_DIR
set +m
( cd "$WEB_ROOT" && exec npx vite --port "$PORT" >"$WORK_DIR/vite.log" 2>&1 ) &
VITE_PID=$!
_cleanup_vite() {
  if [[ -n "${VITE_PID:-}" ]]; then
    kill -TERM "-$VITE_PID" 2>/dev/null || kill -TERM "$VITE_PID" 2>/dev/null || true
    wait "$VITE_PID" 2>/dev/null || true
  fi
  lsof -ti:"$PORT" 2>/dev/null | xargs kill -9 2>/dev/null || true
}
# Chain into the lock-release trap installed at top of script. EXIT-only:
# the INT/TERM/HUP traps installed up top keep routing signals through
# `exit`, which fires this handler exactly once.
trap '_stop_disk_watchdog; _cleanup_vite; rm -rf "$LOCK" 2>/dev/null || true' EXIT

log "waiting for vite @ :$PORT…"
VITE_READY=0
for i in $(seq 1 30); do
  if curl -s "http://localhost:$PORT" >/dev/null 2>&1; then
    VITE_READY=1
    break
  fi
  if ! kill -0 "$VITE_PID" 2>/dev/null; then
    break
  fi
  sleep 1
done

if [[ "$VITE_READY" -eq 0 ]]; then
  err "vite failed to start on port $PORT — see $WORK_DIR/vite.log"
  tail -20 "$WORK_DIR/vite.log" >&2 || true
  exit 1
fi
log "vite ready on $PORT"

# Capture web screenshots into the per-section dir.
#
# WPT_MODE=1 tells capture-screenshots.mjs to append `&wpt=1` to the
# capture URL, which in turn tells ComponentRenderer.PlaceholderContent
# to suppress the visible placeholder name. Without this, every WPT
# capture carries a "wpt <section> <stem> <idx>" text overlay (the
# component name with underscores → spaces) that contaminates browser-ref
# pixel diffs and drives false `structural-divergence` classifications
# across the entire WPT corpus. See the pilot investigation at
# tools/titan/investigations/pilot-001/
# css-backgrounds__background-color-animation-in-body.json for the
# original symptom that motivated this flag. Legacy flows (test-all.sh)
# don't pass WPT_MODE, so the placeholder text continues to render the
# way the visual-test baselines expect.
CAPTURE_LOG="$WORK_DIR/capture.log"
# WPT_COMPOSED=1 renders each test's components COMPOSED on ONE
# ref-matching canvas (one <safe(testKey)>.png per test) instead of the
# per-component + inflated-stitch method — the honest per-page comparison
# (matching run-titan.sh --smoke). inject prefers the composed PNG.
#
# Run the CANONICAL in-place driver (apps/web-harness/), NOT the rsync'd copy
# in $WEB_ROOT: the driver only talks to vite over --url (HTTP) and writes to
# the absolute --out, so its cwd is irrelevant — but its relative imports
# (`./capture-url.mjs`, `../../tools/titan/safe-name.mjs`) only resolve from
# the original apps/web-harness/ depth. The copy sits two dirs deeper
# ($WORK_DIR/web/), where `../../tools/titan/` points at the nonexistent
# sections/tools/titan/ and the whole capture crashes at import time. The
# $WEB_ROOT copy is only needed for the vite SERVER (it serves the per-section
# public/ir-components.json), never for the capture driver.
( cd "$PROJECT_ROOT" && WPT_MODE=1 WPT_COMPOSED=1 node apps/web-harness/capture-screenshots.mjs --url "http://localhost:$PORT" --out "$WEB_SHOTS_DIR" ) \
  >"$CAPTURE_LOG" 2>&1 || warn "capture-screenshots exited non-zero — see $CAPTURE_LOG"
WEB_COUNT=$(find "$WEB_SHOTS_DIR" -maxdepth 1 -name '*.png' -type f 2>/dev/null | wc -l | tr -d '[:space:]')
log "captured $WEB_COUNT web screenshots"
# Synthesize the `total elapsed: <N>s` line that inject-wpt-block.mjs scrapes
# from the capture log to populate wpt.duration.captureMs. Mirrors the
# format test-all.sh emits at its own summary step.
echo "total elapsed: $(( $(date +%s) - SECTION_START ))s" >> "$CAPTURE_LOG"

# Tear vite down before we move on so the next stage isn't holding the port.
_cleanup_vite
unset VITE_PID
# Restore the base lock-release trap (vite cleanup already ran). EXIT-only —
# the signal traps from the top of the script stay installed.
trap '_stop_disk_watchdog; rm -rf "$LOCK" 2>/dev/null || true' EXIT
set -m

# ── Step 5b: native composed capture (--all-platforms, device-pooled) ───────
#
# Native devices are a POOL, not a singleton: parallel section-runners capture
# web concurrently (isolated vite ports), and each grabs one free Android
# device + one free iOS simulator from whatever is currently booted. With a
# single emulator + single simulator this degrades to the old serialized
# behavior; with a provisioned fleet (tools/titan/provision-devices.sh) N
# sections feed natives concurrently. Slots are mkdir-atomic per-device locks
# with stale-heal (same pattern as the per-section lock). Android and iOS are
# INDEPENDENT hardware, so the two feeders run CONCURRENTLY within a section.
# The per-section combined IR is split into per-test docs the inbox feeders
# stream in composed mode → one <safe(testKey)>.png per test in the
# per-section native dirs, which inject reads via diffComposedVsRef. For
# --web-only these dirs are created but left EMPTY, so Step 6/7 (which always
# read them) behave exactly as the old EMPTY_DIR path.
IOS_SHOTS_DIR="$WORK_DIR/ios-screenshots"
ANDROID_SHOTS_DIR="$WORK_DIR/android-screenshots"
rm -rf "$IOS_SHOTS_DIR" "$ANDROID_SHOTS_DIR"
mkdir -p "$IOS_SHOTS_DIR" "$ANDROID_SHOTS_DIR"
# retro R8b (A9#1): NATIVE_SHORT is the section's "a native column is short
# or absent" flag. Every native delivery failure below sets it — a slot never
# acquired, a missing per-test dir, a feeder exiting non-zero, a capture
# count that does not match the per-test count, inject's whole-column-zero
# exit — and the Summary turns it into exit 1 AFTER the manifest is written.
# Before this flag each of those paths was a `warn` into a per-section log,
# the runner exited 0, and the missing cells simply shrank that platform's
# denominator (a missing capture is neither pass nor fail). --web-only never
# sets it: the empty native dirs there are declared, not short.
NATIVE_SHORT=0
if [[ "$PLATFORM_SCOPE" == "all" ]]; then
  step "Step 5b: native composed capture (device-pooled)"
  PERTEST_DIR="$WORK_DIR/per-test-ir"
  # Split THIS section's combined IR (from Step 4) into per-test docs.
  node "$TITAN_DIR/split-combined-ir.mjs" --in "$GRADLE_OUT_DIR/tmpOutput.json" --out "$PERTEST_DIR" \
    >>"$CAPTURE_LOG" 2>&1 || { warn "split-combined-ir failed — natives will be absent"; NATIVE_SHORT=1; }

  POOL_ROOT="/tmp/titan-device-pool"
  # adb lives outside PATH in most shells; mirror feed-android's resolution.
  _adb_bin() {
    command -v adb 2>/dev/null && return 0
    local c
    for c in "${ANDROID_HOME:-}/platform-tools/adb" "$HOME/Library/Android/sdk/platform-tools/adb"; do
      [[ -x "$c" ]] && { echo "$c"; return 0; }
    done
    return 1
  }
  # Booted-device discovery — the pool is "whatever is booted right now", so
  # provisioning more devices needs zero config here.
  _android_candidates() {
    local adb; adb=$(_adb_bin) || return 0
    # Retro gate 2026-09-06: this `adb devices` was UNBOUNDED and hung for 49 min (twice) when the
    # adb server wedged mid-gate, stalling Step 5b before either feeder launched — the 50-min
    # watchdog then killed the whole section. Bound it (perl alarm), and on a timeout restart the
    # server DETACHED (a server spawned from a pipeline inherits its fds) and retry once.
    local out=""
    for _attempt in 1 2; do
      if out=$(perl -e 'alarm 20; exec @ARGV' "$adb" devices </dev/null 2>/dev/null); then break; fi
      warn "adb devices timed out (attempt $_attempt) — restarting the adb server and retrying"
      perl -e 'alarm 10; exec @ARGV' "$adb" kill-server </dev/null >/dev/null 2>&1; pkill -9 -x adb 2>/dev/null; sleep 1
      nohup "$adb" start-server >/dev/null 2>&1 </dev/null; sleep 2; out=""
    done
    printf '%s\n' "$out" | awk '$2=="device" && $1 ~ /^emulator-/ {print $1}'
  }
  _ios_candidates() {
    xcrun simctl list devices booted 2>/dev/null | grep -oE '\([0-9A-F-]{36}\)' | tr -d '()'
  }
  # Acquire ANY free slot among the candidates: try each device's mkdir-atomic
  # lock (with stale-heal), looping until one frees up or the 2h cap. Echoes
  # the acquired device id on stdout.
  _pool_acquire() {
    local platform="$1"; shift
    [[ $# -eq 0 ]] && return 1          # no booted devices → caller skips platform
    mkdir -p "$POOL_ROOT/$platform"
    local waited=0 dev slot pid
    while :; do
      for dev in "$@"; do
        slot="$POOL_ROOT/$platform/$dev.lock"
        if mkdir "$slot" 2>/dev/null; then echo "$$" > "$slot/pid"; printf '%s\n' "$dev"; return 0; fi
        pid=$(cat "$slot/pid" 2>/dev/null || echo "")
        if [[ "$pid" =~ ^[0-9]+$ ]] && ! kill -0 "$pid" 2>/dev/null; then
          rm -rf "$slot"                 # stale holder died — reclaim and retry
          if mkdir "$slot" 2>/dev/null; then echo "$$" > "$slot/pid"; printf '%s\n' "$dev"; return 0; fi
        fi
      done
      sleep 5; waited=$((waited+5))
      [[ $waited -ge 7200 ]] && return 1
    done
  }
  _pool_release() { [[ -n "${2:-}" ]] && rm -rf "$POOL_ROOT/$1/$2.lock" 2>/dev/null || true; }
  ANDROID_DEV="" ; IOS_DEV=""

  if [[ -d "$PERTEST_DIR" ]]; then
    # Acquire one slot per platform (independently — a busy emulator pool must
    # not delay the iOS feed, and vice versa). Empty candidate list or a 2h
    # wait → that platform is skipped with a warning, the other still runs.
    # retro R8b (A9#1): a slot that was never acquired is a SHORT column, not
    # a footnote — flag it so the section exits 1 once the manifest is out.
    ANDROID_DEV=$(_pool_acquire android $(_android_candidates)) \
      || { warn "no free Android device (pool empty or 2h wait) — Android column absent for $SECTION"; NATIVE_SHORT=1; }
    IOS_DEV=$(_pool_acquire ios $(_ios_candidates)) \
      || { warn "no free iOS simulator (pool empty or 2h wait) — iOS column absent for $SECTION"; NATIVE_SHORT=1; }
    trap '_stop_disk_watchdog; _cleanup_vite 2>/dev/null; _pool_release android "$ANDROID_DEV"; _pool_release ios "$IOS_DEV"; rm -rf "$LOCK" 2>/dev/null || true' EXIT

    # Prebuilt markers (written by provision-devices.sh): the app is already
    # installed on every pool device, so feeders skip their own gradle install /
    # xcodebuild — this is what makes CONCURRENT feeds safe (two simultaneous
    # gradle installs or xcodebuilds in one repo checkout can collide).
    # Plain strings, NOT arrays: macOS ships bash 3.2, where expanding an
    # EMPTY array with "${arr[@]}" under `set -u` is an "unbound variable"
    # error that kills the backgrounded feeder before it starts. The flags are
    # space-free, so unquoted word-split expansion is safe here.
    ANDROID_FLAGS="" ; IOS_FLAGS=""
    grep -qxF "$ANDROID_DEV" "$POOL_ROOT/provisioned-android" 2>/dev/null && ANDROID_FLAGS="--skip-install"
    grep -qxF "$IOS_DEV" "$POOL_ROOT/provisioned-ios" 2>/dev/null && IOS_FLAGS="--no-build"

    # Both feeders take --timeout-per-fixture in SECONDS (unified in the
    # honesty quick-wins); --composed = one <safe(testKey)>.png per test.
    # Separate log files (appended to CAPTURE_LOG after) so the two feeders'
    # concurrent output can't interleave mid-line.
    #
    # wave-35 lane B2 — `--wpt-dir` is the NATIVE half of the @font-face asset
    # hop. The web half is a static route over the host's corpus (see the
    # WPT_DIR export at Step 5); a device has no such reach, so each feeder
    # reads the per-test doc's `fontFaces[].src`, resolves it under this root
    # and COPIES the file into the app's own sandbox (adb push /
    # simctl container copy) beside the IR it is about to feed. Same
    # fixture-copy channel, one directory over.
    FEED_ANDROID_PID="" ; FEED_IOS_PID=""
    if [[ -n "$ANDROID_DEV" ]]; then
      log "android slot: $ANDROID_DEV — feeding (composed)"
      node "$TITAN_DIR/feed-android.mjs" --fixtures "$PERTEST_DIR" --composed \
        --udid "$ANDROID_DEV" $ANDROID_FLAGS --wpt-dir "$WPT_DIR" \
        --out "$ANDROID_SHOTS_DIR" --timeout-per-fixture 180 >"$WORK_DIR/feed-android.log" 2>&1 &
      FEED_ANDROID_PID=$!
    fi
    if [[ -n "$IOS_DEV" ]]; then
      log "ios slot: $IOS_DEV — feeding (composed)"
      node "$TITAN_DIR/feed-ios.mjs" --fixtures "$PERTEST_DIR" --composed \
        --udid "$IOS_DEV" $IOS_FLAGS --wpt-dir "$WPT_DIR" \
        --out "$IOS_SHOTS_DIR" --timeout-per-fixture 180 >"$WORK_DIR/feed-ios.log" 2>&1 &
      FEED_IOS_PID=$!
    fi
    # retro R8b (A9#1): a feeder's non-zero exit (its own okCount != total)
    # is a SHORT column — flagged, not just warned about.
    [[ -n "$FEED_ANDROID_PID" ]] && { wait "$FEED_ANDROID_PID" || { warn "feed-android exited non-zero — Android column partial"; NATIVE_SHORT=1; }; }
    [[ -n "$FEED_IOS_PID" ]]     && { wait "$FEED_IOS_PID"     || { warn "feed-ios exited non-zero — iOS column partial"; NATIVE_SHORT=1; }; }
    cat "$WORK_DIR/feed-android.log" "$WORK_DIR/feed-ios.log" >>"$CAPTURE_LOG" 2>/dev/null || true

    # Release slots the instant feeding ends so the next section can start
    # (web/compare work below continues without any device held).
    _pool_release android "$ANDROID_DEV" ; _pool_release ios "$IOS_DEV"
    ANDROID_DEV="" ; IOS_DEV=""
    trap '_stop_disk_watchdog; rm -rf "$LOCK" 2>/dev/null || true' EXIT   # drop pool slots from the trap
    # retro R8b (A9#1): CAPTURE-COUNT PARITY. A missing native capture is
    # neither pass nor fail — inject silently shrinks that platform's
    # denominator, and assertPlatformColumns only ever sees a WHOLE column at
    # zero, never a partial one. So the runner compares each native dir's PNG
    # count with the number of per-test docs it fed (composed mode: exactly
    # one PNG per doc, feed-*.mjs composedPngName). Only a feeder that RAN is
    # held to it — a platform whose slot was never acquired already flagged.
    N_PERTEST=$(find "$PERTEST_DIR" -maxdepth 1 -name '*.json' -type f | wc -l | tr -d ' ')
    N_IOS=$(find "$IOS_SHOTS_DIR" -maxdepth 1 -name '*.png' -type f | wc -l | tr -d ' ')
    N_ANDROID=$(find "$ANDROID_SHOTS_DIR" -maxdepth 1 -name '*.png' -type f | wc -l | tr -d ' ')
    log "native composed: iOS $N_IOS, Android $N_ANDROID (per-test docs fed: $N_PERTEST)"
    if [[ -n "$FEED_IOS_PID" && "$N_IOS" != "$N_PERTEST" ]]; then
      warn "iOS column SHORT: $N_IOS captures for $N_PERTEST per-test docs"; NATIVE_SHORT=1
    fi
    if [[ -n "$FEED_ANDROID_PID" && "$N_ANDROID" != "$N_PERTEST" ]]; then
      warn "Android column SHORT: $N_ANDROID captures for $N_PERTEST per-test docs"; NATIVE_SHORT=1
    fi
  else
    # retro R8b (A9#1): no per-test dir means the split above wrote nothing —
    # both native columns will be absent from a run that asked for them.
    warn "per-test IR dir $PERTEST_DIR missing — natives absent"; NATIVE_SHORT=1
  fi
fi

# ── Step 6: compare (web composed; natives from Step 5b or empty) ────────────
#
# compare-screenshots.mjs does pairwise diffs. With web-only scope, we point
# IOS_SCREENSHOTS_DIR + ANDROID_SCREENSHOTS_DIR at empty dirs so the
# unmatched-platform branch fires (rows[].pairs.iOS-* are null) without
# attempting to read the real apps/ios-harness/screenshots/ which a sibling
# section may be wiping. REPORT_DIR + MANIFEST_OUT scope every output file.

step "Step 6: compare + manifest"
EMPTY_DIR="$WORK_DIR/_empty"
mkdir -p "$EMPTY_DIR"
COMPARE_LOG="$WORK_DIR/compare.log"
REPORT_DIR="$WORK_DIR/report"
MANIFEST_OUT="$WORK_DIR/manifest.json"
mkdir -p "$REPORT_DIR"
(
  cd "$TOOLS_DIR/visual"
  IOS_SCREENSHOTS_DIR="$IOS_SHOTS_DIR" \
  ANDROID_SCREENSHOTS_DIR="$ANDROID_SHOTS_DIR" \
  WEB_SCREENSHOTS_DIR="$WEB_SHOTS_DIR" \
  REPORT_DIR="$REPORT_DIR" \
  MANIFEST_OUT="$MANIFEST_OUT" \
  node compare-screenshots.mjs --input "$REL_INPUT" --no-cross-platform-gate
) >"$COMPARE_LOG" 2>&1 || COMPARE_EXIT=$?
COMPARE_EXIT="${COMPARE_EXIT:-0}"

# The cross-platform gate is EXPLICITLY OFF here, and the explicit flag
# matters more than the behaviour.
#
# TITAN scores the WPT corpus against frozen browser references; its
# runtime-vs-runtime pairs are informational, hundreds of them legitimately
# diverge (the runtimes sit well under 100% WPT conformance), and
# tools/visual/cross-platform-expectations.json covers the fixture corpus,
# not WPT. Gating here would be noise.
#
# Without the flag the gate would still RUN and still exit 4, and the `||`
# below would quietly downgrade that to a warning — so the gate would look
# like it covered TITAN runs while being inert. Opting out in the open is
# the honest version of the same outcome.
#
# Exit 3 is NOT downgraded: it means a capture is not untagged-sRGB, so the
# pixels were decoded in the wrong colour space and every number this run
# produces — including the WPT conformance scores — is untrustworthy. That
# is worse for a scoring harness than for a gate, so the section ABORTS with
# exit 2, this file's existing convention for "cannot produce valid output"
# (see the preflight exits above). Continuing would write a manifest full of
# numbers computed from the wrong colour space, which is the degenerate-pass
# shape: a result that looks like a measurement and is not one.
if [[ "$COMPARE_EXIT" -eq 3 ]]; then
    err "compare-screenshots exited 3 — a capture is not untagged-sRGB, so this section's numbers would be invalid (see $COMPARE_LOG)"
    exit 2
elif [[ "$COMPARE_EXIT" -ne 0 ]]; then
    warn "compare-screenshots exited $COMPARE_EXIT — see $COMPARE_LOG"
fi

if [[ ! -f "$MANIFEST_OUT" ]]; then
  err "compare-screenshots did not write $MANIFEST_OUT"
  tail -30 "$COMPARE_LOG" >&2 || true
  exit 1
fi
log "manifest v3 → $MANIFEST_OUT"

# ── Step 6.5: write skeleton wpt block (recovery beacon) ────────────────────
#
# Race-bug recovery: if the script (or its parent agent) is killed AFTER Step 6
# writes the v3 manifest but BEFORE Step 7 finishes injecting the wpt block,
# the resulting manifest has NO wpt: field. aggregate-sections.mjs then treats
# the section as zero (totalTests=0, empty results) — silently invisible to
# roll-ups. To make this state recoverable we stamp a minimal wpt skeleton
# here, BEFORE running the (slower) inject step. The skeleton carries
# `incomplete: true` so a later run / verify pass can detect "this manifest
# needs inject re-run" by looking at the marker rather than guessing from
# missing fields.
#
# The skeleton is overwritten in-place by Step 7's inject-wpt-block.mjs (which
# replaces manifest.wpt entirely); the marker disappears on success. If a
# verify pass runs and sees `incomplete: true`, it knows to re-inject.
log "writing skeleton wpt block (recovery beacon)"
node -e "
const fs = require('fs');
const path = '$MANIFEST_OUT';
const m = JSON.parse(fs.readFileSync(path, 'utf8'));
const tests = fs.readFileSync('$TESTS_LIST', 'utf8')
  .split('\n').map(s => s.trim()).filter(s => s && !s.startsWith('#'));
m.manifestVersion = 4;
// Only write a skeleton if no real wpt block exists yet — re-runs of
// section-runner against a manifest with a real wpt block (re-inject case)
// must not clobber it.
if (!m.wpt || m.wpt.incomplete) {
  m.wpt = {
    ref: '$WPT_REF',
    runId: '$RUN_ID',
    totalTests: tests.length,
    buckets: { A: 0, B: 0, C: 0 },
    duration: null,
    results: {},
    skipped: {},
    incomplete: true,  // cleared by inject-wpt-block.mjs on success
    incompleteReason: 'skeleton — Step 7 (inject-wpt-block) did not run yet',
  };
  // Atomic write — same pattern as inject-wpt-block.mjs.
  const tmp = path + '.skel.tmp.' + process.pid;
  fs.writeFileSync(tmp, JSON.stringify(m, null, 2) + '\n');
  fs.renameSync(tmp, path);
}
" || warn "skeleton beacon write failed (non-fatal — Step 7 will still run)"

# ── Step 7: inject WPT v4 block ──────────────────────────────────────────────

step "Step 7: inject wpt: block (manifest v4)"
# --web-dir/--ios-dir/--android-dir all point at THIS section's isolated
# capture dirs (never the GLOBAL apps/*-harness/screenshots, which a sibling
# section may be filling). Under --all-platforms these hold the composed
# per-test PNGs from Step 5/5b, so inject's diffComposedVsRef scores every
# platform honestly; under --web-only the native dirs are empty (Step 5b
# creates but doesn't feed them) so only web-ref is produced — same result
# as the old EMPTY_DIR path, without the hardcoded dead-end.
#
# retro R8b (A9#1): DECLARE the platform scope to inject's column-presence
# assertion (assertPlatformColumns) instead of letting it warn into a log:
#   --web-only      → SKIP_IOS=1 SKIP_ANDROID=1 — the empty native dirs are
#                     intentional, so the assertion stays silent;
#   --all-platforms → TITAN_REQUIRE_ALL_COLUMNS=1 — a whole column at zero,
#                     or (retro R13's per-test parity, `missingCells`) a
#                     PARTIAL column — a native cell absent or error-shaped
#                     where the web-ref scored — is FATAL in inject (exit 3;
#                     the manifest is written first), mapped to NATIVE_SHORT
#                     by _inject_rc_check.
# Scoped to the inject commands through `env` — compare-screenshots.mjs in
# Step 6 also reads SKIP_* and must never see them. A plain string, not an
# array: macOS bash 3.2 + `set -u` reject an empty "${arr[@]}", and every
# value here is space-free so word-splitting is safe.
if [[ "$PLATFORM_SCOPE" == "all" ]]; then
  INJECT_ENV="TITAN_REQUIRE_ALL_COLUMNS=1"
else
  INJECT_ENV="SKIP_IOS=1 SKIP_ANDROID=1"
fi
_inject_rc_check() {
  # inject's exit 3 = "a platform column produced ZERO scored diffs while its
  # siblings did" OR (retro R13, assertPlatformColumns' per-test parity) "a
  # native column has cells absent/error-shaped where the web-ref scored" —
  # both reachable only under TITAN_REQUIRE_ALL_COLUMNS=1. The
  # manifest is written BEFORE inject exits, so the section carries on and
  # fails LOUDLY at the Summary via NATIVE_SHORT instead of dying under
  # `set -e` with nothing left to diagnose. Other non-zero codes (1 usage,
  # 2 IO) are left to Step 7.5's wpt-block check + one recovery re-run.
  case "$1" in
    0) ;;
    3) warn "inject: platform column ABSENT or PARTIAL under --all-platforms — manifest written for diagnosis; section will exit 1"; NATIVE_SHORT=1 ;;
    *) warn "inject exited $1 — Step 7.5 verifies the wpt block and retries once" ;;
  esac
}
INJECT_RC=0
env $INJECT_ENV node "$TITAN_DIR/inject-wpt-block.mjs" \
  --manifest "$MANIFEST_OUT" \
  --tests "$TESTS_LIST" \
  --wpt-ref "$WPT_REF" \
  --run-id "$RUN_ID" \
  --refs-root "$WPT_DIR/refs/$WPT_REF/white-black-ink-font-lh" \
  --capture-log "$CAPTURE_LOG" \
  --combined "$COMBINED_FIXTURE" \
  --web-dir "$WEB_SHOTS_DIR" \
  --ios-dir "$IOS_SHOTS_DIR" \
  --android-dir "$ANDROID_SHOTS_DIR" || INJECT_RC=$?
_inject_rc_check "$INJECT_RC"
log "manifest v4 → $MANIFEST_OUT"

# ── Step 7.5: verify wpt block was actually written ─────────────────────────
#
# Belt-and-suspenders: even though Step 7 just succeeded (set -e would have
# tripped on a non-zero exit), inject-wpt-block.mjs is a long-running node
# script that loads PNGs and computes SSIM. If it was racy or wrote a partial
# manifest somehow, surface it here BEFORE we hand control back to the
# dispatcher. A second inject attempt costs ~10s and is idempotent (it just
# overwrites manifest.wpt) — much cheaper than re-running the whole section.
WPT_OK=$(node -e "
const m = require('$MANIFEST_OUT');
process.stdout.write(
  (m.wpt && !m.wpt.incomplete && typeof m.wpt.totalTests === 'number') ? '1' : '0'
);
" 2>/dev/null || echo "0")
if [[ "$WPT_OK" != "1" ]]; then
  warn "manifest missing wpt block after Step 7 — re-running inject (recovery)"
  # retro R8b (A9#1): the recovery run carries the SAME scope declaration
  # and the same exit-3 → NATIVE_SHORT mapping as Step 7 — a column absent
  # on the retry is exactly as short as one absent on the first pass.
  INJECT_RC=0
  env $INJECT_ENV node "$TITAN_DIR/inject-wpt-block.mjs" \
    --manifest "$MANIFEST_OUT" \
    --tests "$TESTS_LIST" \
    --wpt-ref "$WPT_REF" \
    --run-id "$RUN_ID" \
    --refs-root "$WPT_DIR/refs/$WPT_REF/white-black-ink-font-lh" \
    --capture-log "$CAPTURE_LOG" \
    --combined "$COMBINED_FIXTURE" \
    --web-dir "$WEB_SHOTS_DIR" \
    --ios-dir "$IOS_SHOTS_DIR" \
    --android-dir "$ANDROID_SHOTS_DIR" || INJECT_RC=$?
  _inject_rc_check "$INJECT_RC"
  WPT_OK=$(node -e "
const m = require('$MANIFEST_OUT');
process.stdout.write(
  (m.wpt && !m.wpt.incomplete && typeof m.wpt.totalTests === 'number') ? '1' : '0'
);
" 2>/dev/null || echo "0")
  if [[ "$WPT_OK" != "1" ]]; then
    err "manifest STILL missing wpt block after recovery inject — manual investigation needed"
    exit 1
  fi
  log "recovery inject succeeded — manifest now has wpt block"
fi

# ── Summary ──────────────────────────────────────────────────────────────────

step "Summary"
node -e "
const m = require('$MANIFEST_OUT');
console.log('section         :', '$SECTION');
console.log('manifestVersion :', m.manifestVersion);
console.log('totalTests      :', m.wpt?.totalTests);
console.log('buckets         :', JSON.stringify(m.wpt?.buckets));
const labels = {};
for (const r of Object.values(m.wpt?.results ?? {})) {
  const l = r.divergence ?? 'unknown';
  labels[l] = (labels[l] ?? 0) + 1;
}
const order = ['identical','sub-pixel-noise','color-drift','edge-shift','structural-divergence','mixed','unknown','no-data'];
const seen = Object.keys(labels).filter(k => !order.includes(k));
const summary = [...order, ...seen].filter(k => labels[k]).map(k => k+': '+labels[k]).join(' · ');
console.log('classifier      :', summary);
"

# retro R8b (A9#1): the NATIVE_SHORT gate — AFTER the manifest is written and
# verified (Step 7.5), so every cell that did land is there to diagnose, and
# BEFORE the exit code the gate driver keys on. Under --all-platforms a
# section whose native column is short or absent is a DELIVERY FAILURE, not a
# result: a missing capture is neither pass nor fail, it silently shrinks that
# platform's denominator. wave49-final happened to land 0 missing cells; the
# next run that does not now fails loudly instead of averaging over the hole.
if [[ "${NATIVE_SHORT:-0}" == 1 ]]; then
  err "native column short/absent under --all-platforms — manifest written at $MANIFEST_OUT for diagnosis; exit 1. Refeed the missing cells (BACKLOG 'Single-fixture recovery', with --wpt-dir) and re-run inject."
  exit 1
fi

log "done."
exit 0
