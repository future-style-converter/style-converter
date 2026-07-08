#!/usr/bin/env bash
#
# tools/visual/smoke.sh — one-command audit across Tier 5, Tier 11, and the
# visual-test BASELINE=1 regression test.
#
# Built round 55. Previously each tier needed manual invocation:
#   - Tier 5  interaction-state harness  (tools/visual/interaction-states.mjs)
#   - Tier 11 a11y color-contrast audit  (tools/visual/a11y-audit.mjs)
#   - visual-test 327-component baseline (BASELINE=1 ./test-all.sh fixtures/visual-test.json)
#
# Each catches a different class of regression that the others can't:
#   - Tier 5 fails on FixtureCanvas / Phase 5a route breakage
#   - Tier 11 fails on a11y regressions in fixture authoring
#   - BASELINE=1 fails on iOS/Android/web renderer drift in any direction
#
# Running them all in one shot gives a single "is the campaign-wide
# infrastructure healthy?" signal. Designed to be the first thing a
# new contributor runs to confirm their environment works, and the last
# thing a release branch runs before merging.
#
# Usage:
#     ./tools/visual/smoke.sh [--quick]
#
# --quick: skip the visual-test BASELINE=1 (saves ~3 min of iOS+Android+web
#          captures); runs only the web-only Tier 5 + Tier 11 harnesses.
#          Useful when you've just landed a web-only change and don't need
#          to re-verify the native baselines.

set -euo pipefail

PROJECT_ROOT="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$PROJECT_ROOT"

QUICK=0
for arg in "$@"; do
    case "$arg" in
        --quick) QUICK=1 ;;
        *) echo "unknown arg: $arg"; echo "usage: $0 [--quick]"; exit 1 ;;
    esac
done

# Color codes (matches test-all.sh's palette so the combined output reads
# as one stream in a CI log).
B="\033[1;34m"; G="\033[0;32m"; Y="\033[1;33m"; R="\033[0;31m"; N="\033[0m"

log()  { echo -e "${G}[smoke]${N} $*"; }
warn() { echo -e "${Y}[smoke]${N} $*" >&2; }
err()  { echo -e "${R}[smoke]${N} $*" >&2; }

# ── Vite lifecycle (used by Tier 5 + Tier 11) ───────────────────────────────
#
# Both web-side harnesses need the vite dev server on :3000. We bring it up
# once at the start, leave it running across both, then tear it down. Each
# harness has its own pre-flight check that fails fast if vite isn't there.
VITE_PID=""
start_vite() {
    log "starting vite on :3000…"
    ( cd apps/web-harness && npm run dev > /tmp/smoke-vite.log 2>&1 ) &
    VITE_PID=$!
    # Poll until vite responds or 30s elapses. Faster than a fixed sleep,
    # avoids the "vite not ready yet" race that the per-harness pre-flight
    # would also catch but with a less actionable error message.
    local elapsed=0
    while (( elapsed < 30 )); do
        if curl -s -o /dev/null -w "%{http_code}" http://localhost:3000/ 2>/dev/null | grep -q "^2"; then
            log "vite ready (${elapsed}s)"
            return 0
        fi
        sleep 1
        elapsed=$((elapsed + 1))
    done
    err "vite failed to start within 30s; tail of /tmp/smoke-vite.log:"
    tail -20 /tmp/smoke-vite.log >&2
    return 1
}

stop_vite() {
    if [[ -n "$VITE_PID" ]]; then
        log "stopping vite (pid=$VITE_PID)"
        kill "$VITE_PID" 2>/dev/null || true
        # Also kill any vite child processes (npm run dev forks one).
        pkill -P "$VITE_PID" 2>/dev/null || true
        VITE_PID=""
    fi
}
# Always tear down vite no matter how we exit (script-error, ctrl-C, etc).
trap stop_vite EXIT INT TERM HUP

# ── Result tracking ──────────────────────────────────────────────────────────
RESULTS=()
add_result() { RESULTS+=("$1"); }

# ── Tier 5: interaction-state harness ────────────────────────────────────────
run_tier5() {
    echo -e "\n${B}━━━ Tier 5: interaction states (90 captures expected) ━━━${N}"
    if node tools/visual/interaction-states.mjs > /tmp/smoke-tier5.log 2>&1; then
        local summary
        summary=$(grep -E "captured · .* failed · .* skipped" /tmp/smoke-tier5.log | tail -1)
        if [[ -z "$summary" ]]; then
            err "Tier 5 finished but no summary line; check /tmp/smoke-tier5.log"
            add_result "Tier 5: NO-SUMMARY"
            return 1
        fi
        log "$summary"
        add_result "Tier 5: $summary"
    else
        err "Tier 5 FAILED; tail of /tmp/smoke-tier5.log:"
        tail -20 /tmp/smoke-tier5.log >&2
        add_result "Tier 5: FAILED"
        return 1
    fi
}

# ── Tier 11: a11y color-contrast audit ───────────────────────────────────────
run_tier11() {
    echo -e "\n${B}━━━ Tier 11: a11y color-contrast (15 fixtures expected) ━━━${N}"
    if node tools/visual/a11y-audit.mjs > /tmp/smoke-tier11.log 2>&1; then
        local summary
        summary=$(grep -E "fixtures scored on color-contrast" /tmp/smoke-tier11.log | tail -1)
        if [[ -z "$summary" ]]; then
            err "Tier 11 finished but no summary line; check /tmp/smoke-tier11.log"
            add_result "Tier 11: NO-SUMMARY"
            return 1
        fi
        log "$summary"
        add_result "Tier 11: $summary"
    else
        err "Tier 11 FAILED; tail of /tmp/smoke-tier11.log:"
        tail -20 /tmp/smoke-tier11.log >&2
        add_result "Tier 11: FAILED"
        return 1
    fi
}

# ── visual-test BASELINE=1 (327 platform-comparisons) ────────────────────────
run_baseline() {
    echo -e "\n${B}━━━ visual-test BASELINE=1 (327 captures, ~3-5 min) ━━━${N}"
    # BASELINE=1 fails the test-all script with non-zero exit on any
    # regression. We capture stdout for the summary line; stderr passes
    # through so the user sees per-fixture progress.
    local exit_code=0
    BASELINE=1 ./test-all.sh fixtures/visual-test.json > /tmp/smoke-baseline.log 2>&1 || exit_code=$?
    local summary
    summary=$(grep -E "regressions vs baseline|regressed beyond" /tmp/smoke-baseline.log | tail -1)
    if [[ "$exit_code" -eq 0 ]]; then
        log "$summary"
        add_result "BASELINE=1: $summary"
    else
        err "BASELINE=1 FAILED (exit=$exit_code): $summary"
        # Extract the regressed fixture names so the user knows where to
        # look without grep'ing the log themselves.
        grep "REGRESSION" /tmp/smoke-baseline.log >&2 || true
        add_result "BASELINE=1: FAILED — $summary"
        return 1
    fi
}

# ── Doc-staleness check (round 70 — automates rounds 65-68's manual catches) ──
#
# Re-runs each live source-of-truth script (coverage-audit, the unit-test
# and per-suite counts, fixture category count) and verifies the claims in
# the living docs (README.md / CLAUDE.md / docs/STATUS.md) match. Fails if
# any headline number drifted from reality.
#
# Why this matters: rounds 65-68 caught 4 stale-doc instances manually,
# each taking a manual round to find. This step catches them automatically
# going forward — the next time someone changes the IR catalogue or the
# test suites, the doc-staleness check fails fast in CI before the drift
# compounds.
run_doc_staleness() {
    echo -e "\n${B}━━━ Doc-staleness check (live source-of-truth vs doc claims) ━━━${N}"
    if bash tools/visual/doc-staleness-check.sh > /tmp/smoke-docstaleness.log 2>&1; then
        # The success line has color codes prefixing the ✓ — strip them
        # before looking for the canonical "all doc claims match" suffix.
        local summary
        summary=$(grep -oE "✓ all doc claims match live source-of-truth output" /tmp/smoke-docstaleness.log | tail -1)
        if [[ -z "$summary" ]]; then summary="✓ doc-staleness check passed"; fi
        log "$summary"
        add_result "Doc staleness: $summary"
    else
        err "Doc staleness check FAILED. Tail of log:"
        tail -25 /tmp/smoke-docstaleness.log >&2
        add_result "Doc staleness: FAILED — re-run the affected source-of-truth scripts and update docs"
        return 1
    fi
}

# ── Unit tests for testing-side scripts ─────────────────────────────────────
#
# Node's built-in --test runner pins the contracts of the compare/classify
# pipeline (tools/visual/*.test.mjs) and the TITAN extractor + browser-ref
# helpers (tools/titan/*.test.mjs). Fast (~seconds) — runs first so a
# broken helper fails fast before we boot vite or run captures.
run_unit_tests() {
    echo -e "\n${B}━━━ Unit tests: tools/visual/*.test.mjs + tools/titan/*.test.mjs ━━━${N}"
    # TITAN Phase 1: include tools/titan/*.test.mjs so the WPT extractor
    # + browser-ref helpers are pinned by the same suite that smoke.sh
    # runs. Without this glob, `doc-staleness-check.sh` (which DOES include
    # them) would disagree with smoke and the unit-test count would
    # bounce between values.
    if node --test tools/visual/*.test.mjs tools/titan/*.test.mjs > /tmp/smoke-units.log 2>&1; then
        local summary
        summary=$(grep -E "^# (tests|pass|fail) " /tmp/smoke-units.log | tr '\n' ' ')
        log "$summary"
        add_result "Unit tests: $summary"
    else
        err "Unit tests FAILED; tail of /tmp/smoke-units.log:"
        tail -30 /tmp/smoke-units.log >&2
        add_result "Unit tests: FAILED"
        return 1
    fi
}

# ── Baseline classifier (B-FOUNDATION pass, round 90+) ─────────────────────
#
# Walks tools/visual/report/manifest.json with the 10-label divergence classifier
# (tools/visual/classify-divergence.mjs; round 91 added no-content gate to the
# original 7; FIX-E added test-not-applicable as the highest-severity
# pre-gate; swarm-002 added glyph-metric-noise between sub-pixel-noise and
# color-drift) and writes tools/visual/baseline-stats.json
# with the empirical distribution per label. Catches: a manifest that's been
# regenerated but isn't classifiable (e.g. metric helper module errored out
# silently and left null fields), or a wholesale shift in the distribution
# vs what previous rounds saw.
#
# Cheap (~1s). Skips cleanly if no manifest exists yet (e.g. fresh checkout
# before any test-all run). Phase B regression-check (in compare-screenshots
# --baseline) gates on per-pair label downgrades; this step only runs the
# classifier pass and surfaces the headline distribution. Phase C will add
# distribution-shift gating in a future round.
run_baseline_stats() {
    echo -e "\n${B}━━━ Baseline classifier (B-FOUNDATION 10-label pass) ━━━${N}"
    if [[ ! -f tools/visual/report/manifest.json ]]; then
        warn "no tools/visual/report/manifest.json — skipping (run BASELINE=1 or test-all.sh first)"
        add_result "Baseline classifier: SKIPPED (no manifest)"
        return 0
    fi
    if node tools/visual/baseline-stats.mjs > /tmp/smoke-baseline-stats.log 2>&1; then
        # The script's stdout summary line looks like:
        # "identical: 2 · sub-pixel-noise: 77 · color-drift: 24 · …"
        local summary
        summary=$(grep -E "identical:.*sub-pixel-noise:" /tmp/smoke-baseline-stats.log | tail -1)
        log "$summary"
        add_result "Baseline classifier: $summary"
    else
        err "Baseline classifier FAILED; tail of /tmp/smoke-baseline-stats.log:"
        tail -20 /tmp/smoke-baseline-stats.log >&2
        add_result "Baseline classifier: FAILED"
        return 1
    fi
}

# ── Run ─────────────────────────────────────────────────────────────────────
EXIT_CODE=0

# Static checks first — fastest signal, no runtime needed.
run_doc_staleness || EXIT_CODE=1

# Cheap unit tests next — any failure here means the adapter logic is
# broken and the downstream captures would mismeasure.
run_unit_tests || EXIT_CODE=1

# Tier 5 + 11 both need vite. Bring it up once, run both, tear down.
start_vite || { EXIT_CODE=1; }
if [[ -n "$VITE_PID" ]]; then
    run_tier5  || EXIT_CODE=1
    run_tier11 || EXIT_CODE=1
fi
stop_vite

# BASELINE=1 doesn't need vite — it owns its own vite lifecycle inside
# test-all.sh. Skip when --quick is set.
if [[ "$QUICK" -eq 0 ]]; then
    run_baseline || EXIT_CODE=1
else
    log "--quick: skipping visual-test BASELINE=1"
    add_result "BASELINE=1: SKIPPED (--quick)"
fi

# Classifier runs after BASELINE=1 so it sees the freshest manifest. In
# --quick mode it operates on the last cached manifest (skipped cleanly
# if no manifest exists yet). Either way it doesn't need vite.
run_baseline_stats || EXIT_CODE=1

# ── Summary ─────────────────────────────────────────────────────────────────
echo -e "\n${B}━━━ Smoke test summary ━━━${N}"
for r in "${RESULTS[@]}"; do
    echo "  $r"
done

if [[ "$EXIT_CODE" -eq 0 ]]; then
    echo -e "\n${G}✓ all smoke tests passed${N}"
else
    echo -e "\n${R}✗ smoke tests FAILED${N}"
fi
exit "$EXIT_CODE"
