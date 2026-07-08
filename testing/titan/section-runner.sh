#!/usr/bin/env bash
#
# testing/titan/section-runner.sh — Phase 2 per-section orchestrator.
#
# What is this?
# -------------
# Phase 1 (run-titan.sh) runs the SMOKE_TESTS list (100 tests across 8
# sections) sequentially in a single process. Phase 2 fans bucket-A out
# across ~30 spec sections, one Claude agent per section, in parallel.
# Naive parallelism (N copies of run-titan.sh) races on a long list of
# shared resources spelled out in TITAN_ARCHITECTURE.md §10 Q10:
#
#   - testing/report/manifest.json    (compare-screenshots overwrites)
#   - vite port 3000                   (only one bind per port)
#   - /tmp/style-converter-testall.lock (mkdir-atomic; second runner aborts)
#   - apps/web-harness/public/ir-components.json (vite serves this; one writer)
#   - apps/web-harness/screenshots/    (capture-screenshots wipes & rewrites)
#   - testing/wpt/refs/<sha>/...       (browser-ref cache)
#   - examples/wpt/_smoke-combined.json (build-combined-fixture writes)
#
# This script fixes each by routing every per-section artifact through a
# per-section work dir under testing/titan/runs/<runId>/sections/<section>/.
# The browser-ref cache is left at its global path because it's already
# section-keyed (section/stem).png — two sections never write the same file.
#
# Usage:
#   testing/titan/section-runner.sh <section> [--max-tests N] [--run-id ID]
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
# Outputs (under testing/titan/runs/<runId>/sections/<section>/):
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
#   1 — at least one stage failed (extract/capture/compare)
#   2 — infra error (missing corpus, missing tools, lock contention)

set -euo pipefail

# Track wall-clock so we can stamp the manifest's wpt.duration.captureMs with
# something more useful than null. inject-wpt-block reads this from the
# capture log via a `total elapsed: <N>s` regex, so we synthesize that line
# at the end (Step 5 closeout) using SECTION_START.
SECTION_START=$(date +%s)
PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TESTING_DIR="$PROJECT_ROOT/testing"
TITAN_DIR="$TESTING_DIR/titan"
WPT_DIR="$TESTING_DIR/wpt"
RUNS_DIR="$TITAN_DIR/runs"
BUCKETS_FILE="$TESTING_DIR/wpt-buckets.json"

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

# WPT git SHA — read from testing/titan/WPT_REF unless overridden.
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
  other_pid=$(cat "$LOCK/pid" 2>/dev/null || echo "?")
  err "another section-runner holds $LOCK (pid=$other_pid). rm -rf $LOCK if stale."
  exit 2
fi
echo "$$" > "$LOCK/pid"
trap 'rm -rf "$LOCK" 2>/dev/null || true; rm -rf "${VITE_TMPDIR:-/dev/null}" 2>/dev/null || true' EXIT INT TERM HUP

# ── Pre-flight ───────────────────────────────────────────────────────────────

step "Pre-flight"
[[ -d "$WPT_DIR/css" ]]      || { err "$WPT_DIR/css missing — run testing/titan/fetch-wpt.sh"; exit 2; }
[[ -f "$BUCKETS_FILE" ]]     || { err "$BUCKETS_FILE missing — run testing/titan/bucket-wpt.mjs"; exit 2; }
command -v node    >/dev/null || { err "node not found"; exit 2; }
command -v rsync   >/dev/null || { err "rsync not found"; exit 2; }
command -v lsof    >/dev/null || warn "lsof not found — skipping port-busy preflight check"

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
# extract-fixture.mjs writes to examples/wpt/<section>/<stem>.json — that's
# the SAME path other section-runners write to. SAFE: each section-runner
# only touches its own section's subdir (e.g. css-flexbox writes to
# examples/wpt/css-flexbox/, css-grid to examples/wpt/css-grid/), so no
# collision between agents working different sections. The intra-section
# write happens once per file from a single process.

step "Step 1: extract fixtures (n=$N)"
EXTRACT_LOG="$WORK_DIR/extract.log"
EXTRACT_RC=0
node "$TITAN_DIR/extract-fixture.mjs" "${TESTS[@]}" >"$EXTRACT_LOG" 2>&1 || EXTRACT_RC=$?
EXTRACTED_OK=$(grep -c '^extracted ' "$EXTRACT_LOG" || true)
EXTRACTED_FAIL=$(grep -c '^FAIL ' "$EXTRACT_LOG" || true)
log "extracted=$EXTRACTED_OK failed=$EXTRACTED_FAIL"
if [[ "$EXTRACT_RC" -ne 0 ]]; then
  warn "extract step exited non-zero ($EXTRACT_RC) — see $EXTRACT_LOG"
fi

# ── Step 2: browser-ref capture (cached) ─────────────────────────────────────
#
# Cache lives at testing/wpt/refs/<sha>/<section>/<stem>.png. Section-keyed
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
# examples/wpt/_smoke-combined.json. inject-wpt-block.mjs is taught to look
# at --combined so it can read this section's keyMap.

step "Step 3: build combined fixture"
COMBINED_FIXTURE="$PROJECT_ROOT/examples/wpt/_section-${SECTION}.json"
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
REL_INPUT="examples/wpt/_section-${SECTION}.json"
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
# Chain into the lock-release trap installed at top of script.
trap '_cleanup_vite; rm -rf "$LOCK" 2>/dev/null || true' EXIT INT TERM HUP

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
# testing/titan/investigations/pilot-001/
# css-backgrounds__background-color-animation-in-body.json for the
# original symptom that motivated this flag. Legacy flows (test-all.sh)
# don't pass WPT_MODE, so the placeholder text continues to render the
# way the visual-test baselines expect.
CAPTURE_LOG="$WORK_DIR/capture.log"
( cd "$WEB_ROOT" && WPT_MODE=1 node capture-screenshots.mjs --url "http://localhost:$PORT" --out "$WEB_SHOTS_DIR" ) \
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
trap 'rm -rf "$LOCK" 2>/dev/null || true' EXIT INT TERM HUP
set -m

# ── Step 6: compare (web-only — empty iOS/Android dirs) ──────────────────────
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
  cd "$TESTING_DIR"
  IOS_SCREENSHOTS_DIR="$EMPTY_DIR" \
  ANDROID_SCREENSHOTS_DIR="$EMPTY_DIR" \
  WEB_SCREENSHOTS_DIR="$WEB_SHOTS_DIR" \
  REPORT_DIR="$REPORT_DIR" \
  MANIFEST_OUT="$MANIFEST_OUT" \
  node compare-screenshots.mjs --input "$REL_INPUT"
) >"$COMPARE_LOG" 2>&1 || warn "compare-screenshots exited non-zero — see $COMPARE_LOG"

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
node "$TITAN_DIR/inject-wpt-block.mjs" \
  --manifest "$MANIFEST_OUT" \
  --tests "$TESTS_LIST" \
  --wpt-ref "$WPT_REF" \
  --run-id "$RUN_ID" \
  --refs-root "$WPT_DIR/refs/$WPT_REF" \
  --capture-log "$CAPTURE_LOG" \
  --combined "$COMBINED_FIXTURE" \
  --web-dir "$WEB_SHOTS_DIR"
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
  node "$TITAN_DIR/inject-wpt-block.mjs" \
    --manifest "$MANIFEST_OUT" \
    --tests "$TESTS_LIST" \
    --wpt-ref "$WPT_REF" \
    --run-id "$RUN_ID" \
    --refs-root "$WPT_DIR/refs/$WPT_REF" \
    --capture-log "$CAPTURE_LOG" \
    --combined "$COMBINED_FIXTURE" \
    --web-dir "$WEB_SHOTS_DIR"
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

log "done."
exit 0
