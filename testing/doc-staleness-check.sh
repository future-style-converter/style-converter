#!/usr/bin/env bash
#
# testing/doc-staleness-check.sh — verify that doc claims match live
# source-of-truth output.
#
# Built round 70 after rounds 65-68 caught 4 consecutive stale-doc
# instances, each manually:
#   - DEGENERATE_FIXTURES.md said 102, reality 94
#   - README.md said 545/545/533, reality 550/550/550
#   - ROLLOUT.md had same stale 545/545/533
#   - ssim-history had 1 snapshot from 2 days ago, reality 1154 fixtures
#
# Prevent the same drift from accumulating: re-run each source-of-truth
# script + grep the docs for known-historical and current values; warn on
# mismatch. Designed to run fast (~5s) so it can sit at the front of
# smoke.sh as a static check.
#
# Exit codes:
#   0 = all doc-quoted numbers match live source-of-truth output
#   1 = at least one stale claim found (with details printed)

set -uo pipefail
cd "$(dirname "$0")/.."

B="\033[1;34m"; G="\033[0;32m"; Y="\033[1;33m"; R="\033[0;31m"; N="\033[0m"

FAILED=0
log()  { echo -e "${G}[doc-check]${N} $*"; }
warn() { echo -e "${Y}[doc-check]${N} $*" >&2; }
err()  { echo -e "${R}[doc-check]${N} $*" >&2; FAILED=1; }

# ── Coverage check (Phase 11 catalogue: 550 properties × 3 platforms) ───────
echo -e "${B}━━━ coverage-audit.mjs vs README/ROLLOUT/COVERAGE ━━━${N}"
COVERAGE_OUTPUT=$(node testing/coverage-audit.mjs 2>&1)
ANDROID=$(echo "$COVERAGE_OUTPUT" | grep -oE "android=[0-9]+" | head -1 | cut -d= -f2)
IOS=$(echo "$COVERAGE_OUTPUT" | grep -oE "ios=[0-9]+" | head -1 | cut -d= -f2)
WEB=$(echo "$COVERAGE_OUTPUT" | grep -oE "web=[0-9]+" | head -1 | cut -d= -f2)

if [[ -z "$ANDROID" || -z "$IOS" || -z "$WEB" ]]; then
    err "coverage-audit.mjs output missing one of android/ios/web — re-check the script"
else
    log "live: android=$ANDROID ios=$IOS web=$WEB"
    # Each doc should mention the per-platform total in a `… / 550` form.
    for doc in testing/README.md testing/ROLLOUT.md testing/COVERAGE.md; do
        # Look for any "X / 550" or "X/550" near the platform name. We don't
        # want to be over-precise: just confirm the live number appears at
        # least once in the doc.
        if ! grep -qE "$ANDROID *(/| / )550" "$doc" 2>/dev/null; then
            err "$doc: does not mention android=$ANDROID/550 (live truth)"
        fi
        if ! grep -qE "$IOS *(/| / )550" "$doc" 2>/dev/null; then
            err "$doc: does not mention ios=$IOS/550 (live truth)"
        fi
        if ! grep -qE "$WEB *(/| / )550" "$doc" 2>/dev/null; then
            err "$doc: does not mention web=$WEB/550 (live truth)"
        fi
    done
    [[ "$FAILED" -eq 0 ]] && log "✓ all 3 docs match live coverage"
fi

# ── Degenerate-fixture count (DEGENERATE_FIXTURES.md headline) ──────────────
echo -e "\n${B}━━━ degenerate_audit.py vs DEGENERATE_FIXTURES.md ━━━${N}"
DEGEN_OUTPUT=$(python3 testing/loops/degenerate_audit.py 2>&1)
DEGEN_LIVE=$(echo "$DEGEN_OUTPUT" | grep -oE "TOTAL DEGENERATE: [0-9]+" | grep -oE "[0-9]+")
if [[ -z "$DEGEN_LIVE" ]]; then
    err "degenerate_audit.py output missing TOTAL DEGENERATE line"
else
    log "live: $DEGEN_LIVE degenerate fixtures"
    # The doc's headline section should mention the live count at least once.
    if grep -q "$DEGEN_LIVE" testing/DEGENERATE_FIXTURES.md; then
        log "✓ DEGENERATE_FIXTURES.md mentions live count $DEGEN_LIVE"
    else
        err "DEGENERATE_FIXTURES.md does NOT mention live count $DEGEN_LIVE"
    fi
fi

# ── SSIM history snapshot count (TIER8_HISTORY.md row 3) ────────────────────
echo -e "\n${B}━━━ ssim-history.json snapshot count vs TIER8_HISTORY.md ━━━${N}"
if [[ -f testing/ssim-history.json ]]; then
    SNAP_COUNT=$(node -e "
        const d = require('./testing/ssim-history.json');
        console.log((d.snapshots || []).length);
    " 2>/dev/null)
    if [[ -n "$SNAP_COUNT" ]]; then
        log "live: $SNAP_COUNT snapshots"
        if grep -qE "$SNAP_COUNT snapshots? in" testing/TIER8_HISTORY.md; then
            log "✓ TIER8_HISTORY.md mentions $SNAP_COUNT snapshots"
        else
            warn "TIER8_HISTORY.md does NOT mention $SNAP_COUNT snapshots — re-run \`node testing/ssim-history.mjs\` to add a current snapshot"
        fi
    fi
fi

# ── Unit test count vs README claim (round 75 addition) ────────────────────
echo -e "\n${B}━━━ unit-test count vs README ━━━${N}"
# TITAN Phase 1: glob recurses into testing/titan/ so the new
# extract-fixture / capture-browser-ref unit suites count toward the README's
# headline number. The smoke runner's `node --test testing/*.test.mjs` only
# picks up top-level tests; we also run the titan suites separately in
# smoke.sh (round-95-style fan-out). Both paths must agree on the total.
TEST_COUNT=$(node --test testing/*.test.mjs testing/titan/*.test.mjs 2>&1 | grep -E "^# tests " | tail -1 | grep -oE "[0-9]+")
if [[ -z "$TEST_COUNT" ]]; then
    err "could not extract test count from \`node --test testing/*.test.mjs\` output"
else
    log "live: $TEST_COUNT unit tests"
    if grep -q "$TEST_COUNT unit tests" testing/README.md || grep -q "$TEST_COUNT total" testing/README.md; then
        log "✓ README mentions $TEST_COUNT (unit tests / total)"
    else
        err "README does NOT mention live test count $TEST_COUNT — update the audit-infrastructure table"
    fi
fi

# ── Round 75 regression-prevention checks (round 78 addition) ───────────────
#
# Round 75 caught two silent regressions that broke smoke for rounds 71-74:
#   1. FixtureCanvas's double-RAF hangs in headless puppeteer
#      (fixed by switching to setTimeout)
#   2. puppeteer.launch's recent default protocolTimeout dropped
#      (fixed by passing { protocolTimeout: 5*60*1000 })
#
# Both fixes are in testing/web/src/ui/FixtureCanvas.tsx,
# testing/interaction-states.mjs, testing/a11y-audit.mjs. If a future
# refactor removes them (e.g. "let's use RAF for proper paint sync"),
# the smoke harness goes 0/90 again. Pin the fixes with grep checks
# so the next regression fails fast at PR time, not after merge.
echo -e "\n${B}━━━ Round 75 regression-prevention checks ━━━${N}"

# Pattern 9 (RAF hang): FixtureCanvas's setAttribute('data-fixture-ready')
# must be inside a setTimeout, not a requestAnimationFrame.
#
# Strip comment lines (//, *, /*) before checking — the round 75 fix
# explicitly documents the prior RAF approach in a comment, which would
# otherwise false-trigger this check.
RAF_USE=$(grep -nE "requestAnimationFrame" testing/web/src/ui/FixtureCanvas.tsx 2>/dev/null \
            | grep -vE "^\s*[0-9]+:\s*(//|\*|/\*)" \
            | head -1)
if [[ -n "$RAF_USE" ]]; then
    err "FixtureCanvas.tsx contains requestAnimationFrame in non-comment line ($RAF_USE) — round 75 caught this hangs in headless puppeteer (Pattern 9 in LESSONS_LEARNED.md). Use setTimeout instead."
elif grep -q "setTimeout" testing/web/src/ui/FixtureCanvas.tsx 2>/dev/null; then
    log "✓ FixtureCanvas.tsx uses setTimeout (not RAF) — round 75 fix preserved"
else
    err "FixtureCanvas.tsx has neither setTimeout nor RAF — verify ready-flag mechanism"
fi

# Pattern 10 (protocolTimeout drop): all puppeteer.launch calls must
# explicitly set protocolTimeout. Otherwise the recent puppeteer default
# is too low and per-state CDP round-trips fail mid-harness.
for f in testing/interaction-states.mjs testing/a11y-audit.mjs; do
    if grep -q "puppeteer.*launch" "$f" 2>/dev/null; then
        if grep -q "protocolTimeout" "$f"; then
            log "✓ $f passes protocolTimeout to puppeteer.launch — round 75 fix preserved"
        else
            err "$f calls puppeteer.launch without protocolTimeout — round 75 caught this is now too low by default (Pattern 10 in LESSONS_LEARNED.md)"
        fi
    fi
done

# ── Tier 1 tracker headline (TIER1_VARIANT_DEPTH.md FREEZE banner) ──────────
echo -e "\n${B}━━━ Tier 1 tracker headline numbers ━━━${N}"
PASS=$(awk -F'|' '/^\| [0-9]+ \|/ { gsub(/^ +| +$/, "", $7); if ($7=="passing") n++ } END { print n+0 }' testing/TIER1_VARIANT_DEPTH.md)
BLOCKED=$(awk -F'|' '/^\| [0-9]+ \|/ { gsub(/^ +| +$/, "", $7); if ($7=="blocked-platform") n++ } END { print n+0 }' testing/TIER1_VARIANT_DEPTH.md)
EXHAUSTED=$(awk -F'|' '/^\| [0-9]+ \|/ { gsub(/^ +| +$/, "", $7); if ($7=="exhausted") n++ } END { print n+0 }' testing/TIER1_VARIANT_DEPTH.md)
FAILING=$(awk -F'|' '/^\| [0-9]+ \|/ { gsub(/^ +| +$/, "", $7); if ($7=="failing") n++ } END { print n+0 }' testing/TIER1_VARIANT_DEPTH.md)
log "live: $PASS passing · $BLOCKED blocked-platform · $EXHAUSTED exhausted · $FAILING failing"
# FREEZE banner at top of tracker should include all 4 numbers.
EXPECTED_BANNER="$PASS/550 passing"
if grep -q "$EXPECTED_BANNER" testing/TIER1_VARIANT_DEPTH.md; then
    log "✓ FREEZE banner mentions $EXPECTED_BANNER"
else
    err "FREEZE banner does NOT mention $EXPECTED_BANNER (the live count)"
fi
# Same for TIERS.md row 1 — check it mentions the passing count.
if grep -q "$PASS/550" testing/TIERS.md; then
    log "✓ TIERS.md row 1 mentions $PASS/550"
else
    err "TIERS.md row 1 does NOT mention $PASS/550 (the live count)"
fi

echo
if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${G}✓ all doc claims match live source-of-truth output${N}"
else
    echo -e "${R}✗ stale-doc claims found — re-run the affected source-of-truth scripts and update the docs${N}"
fi
exit "$FAILED"
