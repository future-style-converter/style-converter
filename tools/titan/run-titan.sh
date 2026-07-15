#!/usr/bin/env bash
#
# tools/titan/run-titan.sh — Phase 1 orchestrator.
#
# Implements TITAN_ARCHITECTURE.md §6.1 (the orchestration shape) and §8
# Phase 1 success criteria:
#
#   WPT_SMOKE=1 tools/titan/run-titan.sh
#     produces:
#       - manifest.wpt.totalTests === 100
#       - manifest.wpt.results populated for all 100 entries
#       - per spec section ≥1 entry with non-`unknown` classifier label
#       - manifestVersion: 4
#       - wall-time ≤15 min on M-series Mac (web-only)
#
# Pipeline (Phase 1):
#
#   1. Pick the smoke list (tools/titan/SMOKE_TESTS or all of bucket-A).
#   2. Extract each test → fixtures/wpt/<section>/<test-stem>.json (+__ref).
#   3. Capture browser-ref PNGs (cached per WPT_REF SHA).
#   4. Synthesize the combined IR fixture (one component per WPT test) at
#      fixtures/wpt/_smoke-combined.json. This rides the existing
#      test-all.sh pipeline (gradle convert → 3-platform capture → compare).
#   5. Run compare-screenshots.mjs on the captures to produce v3 manifest.
#   6. Post-process manifest into v4 by injecting the wpt: block via
#      tools/titan/inject-wpt-block.mjs.
#
# Background mode (TITAN-PREP-A contract — NON-NEGOTIABLE):
#   - Defaults to BACKGROUND_MODE=1 (no Simulator.app GUI, no emulator
#     window, no `open tools/visual/report/index.html`). TITAN runs are
#     unattended and hours long; opening a GUI mid-run steals focus from
#     the user's other work.
#   - Pass `BACKGROUND_MODE=0` (or `--foreground`) to opt back into the
#     visible GUI for debugging.
#
# Lock semantics (Section 10 Q10):
#   - Acquires /tmp/style-converter-testall.lock the same way test-all.sh
#     does, so a TITAN run and a hand `./test-all.sh` invocation can't
#     stomp on each other (same iOS sim, same Android emulator, same
#     vite port).
#
# 200-test chunking (Section 6.5):
#   - Phase 1 smoke is 100 tests, so we process linearly. The chunking
#     hook is marked CHUNK_BOUNDARY below — Phase 2's bucket-A pass will
#     wrap that block in a per-chunk loop with a progress.json checkpoint.
#
# Per-platform scope (TITAN spec hard rule B / pragmatic rule):
#   - --web-only: capture web only. Default for the unattended smoke run
#     when iOS/Android can't be validated (see "Pragmatic rule" below).
#   - --all-platforms: capture iOS + Android too. Requires Xcode +
#     Android emulator to be reachable AND the inbox-poll changes to be
#     validated (Phase 1.5 work).
#
# Exit codes:
#   0 — full success
#   1 — at least one chunk failed (smoke: any test failed extraction or
#       its component failed to render on at least one platform)
#   2 — infra error (missing corpus, missing tools, etc)

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
TOOLS_DIR="$PROJECT_ROOT/tools"
TITAN_DIR="$TOOLS_DIR/titan"
WPT_DIR="$TOOLS_DIR/wpt"
SMOKE_LIST="$TITAN_DIR/SMOKE_TESTS"
COMBINED_FIXTURE="$PROJECT_ROOT/fixtures/wpt/_smoke-combined.json"
RUNS_DIR="$TITAN_DIR/runs"

# ── Defaults ─────────────────────────────────────────────────────────────────

# Background mode is the spec-mandated default. Set to 0 to allow GUI
# windows (Simulator.app, emulator viewport, automatic `open` of report).
export BACKGROUND_MODE="${BACKGROUND_MODE:-1}"

# Smoke mode iterates SMOKE_TESTS (100 tests) instead of full bucket-A.
# WPT_SMOKE=1 is the explicit env-var form expected by the spec; --smoke
# is a CLI alias for human convenience.
WPT_SMOKE="${WPT_SMOKE:-1}"

# Platform scope. Default to --web-only because the iOS/Android inbox-poll
# changes ship as code-only in Phase 1; --all-platforms requires
# Phase 1.5's Xcode/emulator validation pass.
PLATFORM_SCOPE="web-only"

# WPT SHA — read from tools/titan/WPT_REF unless overridden.
WPT_REF="${WPT_REF:-$(awk '!/^#/ && NF { print $1; exit }' "$TITAN_DIR/WPT_REF")}"

# ── Args ─────────────────────────────────────────────────────────────────────

while [[ $# -gt 0 ]]; do
  case "$1" in
    --smoke)        WPT_SMOKE=1; shift ;;
    --all-platforms) PLATFORM_SCOPE="all"; shift ;;
    --web-only)     PLATFORM_SCOPE="web-only"; shift ;;
    --foreground)   BACKGROUND_MODE=0; shift ;;
    --background)   BACKGROUND_MODE=1; shift ;;
    --help|-h)
      sed -n '1,80p' "$0"
      exit 0
      ;;
    *)
      echo "[run-titan] unknown arg: $1" >&2
      exit 2
      ;;
  esac
done

# ── Colors ───────────────────────────────────────────────────────────────────

R='\033[0;31m'; G='\033[0;32m'; Y='\033[1;33m'; B='\033[0;34m'; N='\033[0m'
log()  { echo -e "${G}[run-titan]${N} $*"; }
warn() { echo -e "${Y}[run-titan]${N} $*" >&2; }
err()  { echo -e "${R}[run-titan]${N} $*" >&2; }
step() { echo -e "\n${B}━━━ $* ━━━${N}"; }

# ── Lock (Section 10 Q10) ────────────────────────────────────────────────────
#
# Same lock as test-all.sh — mkdir-atomic on macOS. Matters because TITAN
# uses the same iOS simulator, the same Android emulator, the same vite
# port; running both concurrently corrupts both. The TESTALL_SKIP_LOCK env
# escape exists so we can pass through to test-all.sh from here without
# deadlocking on the same lock.
LOCK="/tmp/style-converter-testall.lock"
if [[ -z "${TESTALL_SKIP_LOCK:-}" ]]; then
  if ! mkdir "$LOCK" 2>/dev/null; then
    other_pid=$(cat "$LOCK/pid" 2>/dev/null || echo "")
    # Stale-lock self-heal (mirrors test-all.sh): if the recorded pid is
    # numeric and provably dead, the holder crashed — reclaim the lock
    # instead of demanding a manual `rm -rf`. A live pid, or a missing/
    # garbled pid file (could be a holder that hasn't written its pid
    # yet), still aborts.
    if [[ "$other_pid" =~ ^[0-9]+$ ]] && ! kill -0 "$other_pid" 2>/dev/null; then
      warn "stale lock: pid $other_pid is dead — reclaiming $LOCK"
      rm -rf "$LOCK"
      # Re-acquire atomically — a concurrent invocation may have raced us
      # to the same reclaim; whoever wins mkdir runs, the loser aborts.
      if ! mkdir "$LOCK" 2>/dev/null; then
        err "another run grabbed the lock during reclaim — aborting"
        exit 2
      fi
    else
      err "another run holds $LOCK (pid=${other_pid:-?}). rm -rf $LOCK if stale."
      exit 2
    fi
  fi
  echo "$$" > "$LOCK/pid"
  # Release on EXIT; signals route through `exit` so the release fires
  # exactly once AND the script actually stops on Ctrl-C (trapping the
  # release on the signals directly made bash resume the script after the
  # handler, with the lock already gone).
  trap 'rm -rf "$LOCK" 2>/dev/null || true' EXIT
  trap 'exit 130' INT
  trap 'exit 143' TERM
  trap 'exit 129' HUP
fi

# Now that WE hold the lock, any sub-script (test-all.sh, etc.) must skip
# its own lock acquisition or it will deadlock on the same path.
export TESTALL_SKIP_LOCK=1

# ── Prereqs ──────────────────────────────────────────────────────────────────

step "Pre-flight"
[[ -d "$WPT_DIR/css" ]] || { err "$WPT_DIR/css missing. Run tools/titan/fetch-wpt.sh first."; exit 2; }
[[ -f "$TITAN_DIR/wpt-buckets.json" ]] || { err "wpt-buckets.json missing. Run tools/titan/bucket-wpt.mjs first."; exit 2; }
[[ -f "$SMOKE_LIST" ]] || { err "$SMOKE_LIST missing — Phase 1 deliverable not in place."; exit 2; }
command -v node >/dev/null || { err "node not found"; exit 2; }

# Install testing deps once (puppeteer for browser-ref + capture-screenshots,
# pngjs/sharp for compare). Idempotent — npm exits fast if up to date.
if [[ ! -d "$PROJECT_ROOT/node_modules/puppeteer" ]]; then
  log "installing testing deps…"
  ( cd "$PROJECT_ROOT" && npm install --silent )
fi

# ── Smoke list parse ─────────────────────────────────────────────────────────

# `awk` strips comments + blank lines so the SMOKE_TESTS file can carry
# documentation alongside the test paths.
#
# `mapfile` is bash 4+; macOS still ships bash 3.2. Use a while-read fallback
# that works on both. Pulled lessons from test-all.sh's "we can't assume
# bash 4" guard rail (see the CAPTURED_IOS plain-var pattern there).
SMOKE=()
while IFS= read -r _line; do
  SMOKE+=("$_line")
done < <(awk '!/^#/ && NF' "$SMOKE_LIST")
N="${#SMOKE[@]}"
log "smoke list: $N tests, wptRef=${WPT_REF:0:12}"
log "platform scope: $PLATFORM_SCOPE  background=$BACKGROUND_MODE"

if [[ "$WPT_SMOKE" != "1" ]]; then
  warn "WPT_SMOKE=0 mode is reserved for Phase 2 (full bucket-A). Phase 1 currently always runs the smoke list."
fi

# ── Run ID + run-record dir ─────────────────────────────────────────────────

RUN_ID="$(date -u +%Y%m%dT%H%M%SZ)"
RUN_DIR="$RUNS_DIR/$RUN_ID"
mkdir -p "$RUN_DIR"
log "run id: $RUN_ID  →  $RUN_DIR"

# ── Step 1: extract fixtures ─────────────────────────────────────────────────

# CHUNK_BOUNDARY: Phase 2 will wrap the next three steps (extract → ref →
# capture+compare) in a per-200-test chunk loop with a progress.json
# checkpoint per spec §6.5. For Phase 1 smoke (100 tests) we process
# linearly — one chunk's worth — so the loop body is unwrapped here.

step "Step 1: extract fixtures (n=$N)"
EXTRACT_LOG="$RUN_DIR/extract.log"
EXTRACT_RC=0
node "$TITAN_DIR/extract-fixture.mjs" "${SMOKE[@]}" >"$EXTRACT_LOG" 2>&1 || EXTRACT_RC=$?
EXTRACTED_OK=$(grep -c '^extracted ' "$EXTRACT_LOG" || true)
EXTRACTED_FAIL=$(grep -c '^FAIL ' "$EXTRACT_LOG" || true)
log "extracted=$EXTRACTED_OK  failed=$EXTRACTED_FAIL"
if [[ "$EXTRACT_RC" -ne 0 ]]; then
  warn "extract step exited non-zero ($EXTRACT_RC) — see $EXTRACT_LOG"
fi

# ── Step 2: capture browser-refs ─────────────────────────────────────────────

step "Step 2: capture browser-ref PNGs (cached per WPT_REF)"
REF_LOG="$RUN_DIR/browser-ref.log"
node "$TITAN_DIR/capture-browser-ref.mjs" "${SMOKE[@]}" >"$REF_LOG" 2>&1 || warn "browser-ref had failures — see $REF_LOG"
REFS_RENDERED=$(grep -c 'rendered ' "$REF_LOG" || true)
REFS_CACHED=$(grep -c 'cache '    "$REF_LOG" || true)
log "browser-refs: rendered=$REFS_RENDERED cached=$REFS_CACHED"

# ── Step 3: synthesize combined fixture for the test-all pipeline ───────────

step "Step 3: synthesize combined IR fixture"
mkdir -p "$(dirname "$COMBINED_FIXTURE")"
node "$TITAN_DIR/build-combined-fixture.mjs" --out "$COMBINED_FIXTURE" --tests "$SMOKE_LIST"
COMP_COUNT=$(grep -o '"properties"' "$COMBINED_FIXTURE" | wc -l | tr -d '[:space:]')
log "combined fixture: $COMP_COUNT components → $(realpath "$COMBINED_FIXTURE")"

# ── Step 4: capture (web / all-platforms) via test-all.sh ───────────────────
#
# We delegate to test-all.sh so the captured PNGs go through the same
# CaptureCanvas contract (#1A1A2E bg, 390×N, 16 px padding). The wrinkle
# is that test-all.sh needs an INPUT_JSON path RELATIVE to PROJECT_ROOT.

step "Step 4: capture (scope=$PLATFORM_SCOPE)"
REL_INPUT="fixtures/wpt/_smoke-combined.json"

CAPTURE_LOG="$RUN_DIR/capture.log"
# Stale-screenshot guard. In web-only mode, prior iOS / Android captures
# (from a hand `./test-all.sh fixtures/visual-test.json` or a previous
# TITAN run with --all-platforms) live in apps/ios-harness/screenshots/ and
# apps/android-harness/screenshots/. compare-screenshots picks them up by
# directory walk and merges them into the manifest's rows[] array — they
# don't share component names with this run's web captures, so they end
# up as "row with web=missing", which corrupts the classifier label
# distribution downstream. Wipe them up-front so the manifest is clean.
if [[ "$PLATFORM_SCOPE" == "web-only" ]]; then
  rm -rf "$PROJECT_ROOT/apps/ios-harness/screenshots" "$PROJECT_ROOT/apps/android-harness/screenshots"
  log "wiped stale iOS/Android screenshots (web-only scope)"
fi

# `env -S` would let us pass the env list cleanly but isn't on macOS bash 3;
# fall back to invoking the script with the right env in two branches.
# `set -u` makes empty-array splatting (`${X[@]}`) explode under bash 3.2,
# so we hard-code the two scopes rather than building an array.
case "$PLATFORM_SCOPE" in
  web-only)
    (
      cd "$PROJECT_ROOT"
      # WPT_MODE=1 suppresses placeholder name text in captures — without it
      # every ref diff is text-contaminated and reports false
      # structural-divergence (the pilot-001 symptom; stale-path defect 4).
      # section-runner.sh has set this at its Step 5 since the fix landed.
      # WPT_COMPOSED=1 renders each test's components COMPOSED on ONE
      # ref-matching canvas (one PNG/test) instead of per-component + stitch —
      # the honest per-page comparison (the stitch inflated SSIM via dark
      # padding). inject prefers the composed PNG, stitch is the fallback.
      WPT_MODE=1 \
      WPT_COMPOSED=1 \
      BACKGROUND_MODE="$BACKGROUND_MODE" \
      TESTALL_SKIP_LOCK=1 \
      SKIP_IOS=1 SKIP_ANDROID=1 \
      ./test-all.sh "$REL_INPUT"
    ) >"$CAPTURE_LOG" 2>&1 || warn "test-all.sh exited non-zero — see $CAPTURE_LOG"
    ;;
  all)
    # WEB via test-all (natives SKIPPED here): same web capture as web-only,
    # and this is what converts the combined fixture → out/tmpOutput.json.
    (
      cd "$PROJECT_ROOT"
      WPT_MODE=1 \
      WPT_COMPOSED=1 \
      BACKGROUND_MODE="$BACKGROUND_MODE" \
      TESTALL_SKIP_LOCK=1 \
      SKIP_IOS=1 SKIP_ANDROID=1 \
      ./test-all.sh "$REL_INPUT"
    ) >"$CAPTURE_LOG" 2>&1 || warn "test-all.sh (web) exited non-zero — see $CAPTURE_LOG"

    # NATIVES via the inbox feeders — NOT test-all's bundled native path.
    # Why: the bundled path (a) has no WPT-mode placeholder suppression, so
    # empty WPT boxes get component-name text painted on them → false
    # divergence vs the browser-ref, and (b) renders all ~700 components in
    # ONE giant capture, which on Android drops a large fraction (observed
    # 195/737). The feeders launch the app in titan-inbox mode (placeholder
    # suppressed) and STREAM small per-test docs (robust: per-fixture
    # timeout + pull-retry), producing `<idx>_<safeName>.png` names that
    # drop straight into inject-wpt-block's --ios-dir/--android-dir globs.
    PERTEST_DIR="$RUN_DIR/per-test-ir"
    log "splitting combined IR into per-test docs for the inbox feeders"
    node "$TITAN_DIR/split-combined-ir.mjs" --in "$PROJECT_ROOT/out/tmpOutput.json" --out "$PERTEST_DIR" \
      >>"$CAPTURE_LOG" 2>&1 || warn "split-combined-ir failed — natives will be absent"
    # Fresh host capture dirs (the feeders reset device state themselves).
    rm -rf "$PROJECT_ROOT/apps/android-harness/screenshots" "$PROJECT_ROOT/apps/ios-harness/screenshots"
    if [[ -d "$PERTEST_DIR" ]]; then
      # Android: feed-android self-locates adb (ANDROID_HOME / default SDK).
      # 180s/fixture covers the largest split doc (~66 components).
      # --composed: render each fed test COMPOSED on ONE ref-matching canvas
      # (one <safe(testKey)>.png per test) instead of per-component — the
      # honest per-page comparison, matching the web WPT_COMPOSED path.
      # inject's diffComposedVsRef prefers that PNG (stitch is the fallback).
      log "feeding Android inbox (feed-android.mjs, composed)…"
      node "$TITAN_DIR/feed-android.mjs" --fixtures "$PERTEST_DIR" --composed \
        --out "$PROJECT_ROOT/apps/android-harness/screenshots" --timeout-per-fixture 180 \
        >>"$CAPTURE_LOG" 2>&1 || warn "feed-android exited non-zero — Android column may be partial"
      # iOS: --timeout-per-fixture is SECONDS, same unit as feed-android above
      # (both feeders were unified on seconds; 180 = 180s).
      log "feeding iOS inbox (feed-ios.mjs, composed)…"
      node "$TITAN_DIR/feed-ios.mjs" --fixtures "$PERTEST_DIR" --composed \
        --out "$PROJECT_ROOT/apps/ios-harness/screenshots" --timeout-per-fixture 180 \
        >>"$CAPTURE_LOG" 2>&1 || warn "feed-ios exited non-zero — iOS column may be partial"
    fi
    ;;
esac

# Surface the test-all timing line so the user sees what took how long.
grep -E 'took [0-9]+s|total elapsed' "$CAPTURE_LOG" | tail -10 || true

# Re-check: the manifest must exist after compare-screenshots ran inside
# test-all.sh. If not, the capture step blew up before comparison.
MANIFEST="$TOOLS_DIR/visual/report/manifest.json"
if [[ ! -f "$MANIFEST" ]]; then
  err "no $MANIFEST after capture — pipeline failed pre-compare. Tail of $CAPTURE_LOG:"
  tail -30 "$CAPTURE_LOG" >&2 || true
  exit 1
fi

# ── Step 5: inject the wpt: block (manifest v3 → v4) ────────────────────────

step "Step 5: inject wpt: block (manifest v4)"
node "$TITAN_DIR/inject-wpt-block.mjs" \
  --manifest "$MANIFEST" \
  --tests "$SMOKE_LIST" \
  --wpt-ref "$WPT_REF" \
  --run-id "$RUN_ID" \
  --refs-root "$WPT_DIR/refs/$WPT_REF" \
  --capture-log "$CAPTURE_LOG"

# Move (not copy) the WPT-injected manifest out of the legacy slot so it
# doesn't shadow the visual-test 327-pair manifest that baseline-stats.mjs
# reads. tools/visual/report/manifest.json is the legacy pipeline's slot;
# tools/visual/report/titan-manifest.json is TITAN's slot. The per-run timestamped
# copy under $RUN_DIR/manifest.json is the source of truth for archival.
TITAN_MANIFEST="$TOOLS_DIR/visual/report/titan-manifest.json"
cp "$MANIFEST" "$RUN_DIR/manifest.json"
mv "$MANIFEST" "$TITAN_MANIFEST"
log "manifest v4 written → $TITAN_MANIFEST (run copy: $RUN_DIR/manifest.json; legacy slot $MANIFEST left vacant for next test-all.sh run to repopulate)"
# All references below switch to $TITAN_MANIFEST so the summary block reads
# the right file.
MANIFEST="$TITAN_MANIFEST"

# ── Summary ──────────────────────────────────────────────────────────────────

step "Summary"
node -e "
const m = require('$MANIFEST');
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
const sections = {};
for (const r of Object.values(m.wpt?.results ?? {})) {
  const s = r.specSection ?? 'unknown';
  sections[s] = (sections[s] ?? 0) + 1;
}
console.log('per spec section:', Object.entries(sections).sort().map(([k,v])=>k+'('+v+')').join(' '));
"

log "done."
exit 0
