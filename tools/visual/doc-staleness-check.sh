#!/usr/bin/env bash
#
# tools/visual/doc-staleness-check.sh — verify that doc claims match live
# source-of-truth output.
#
# Built round 70 after rounds 65-68 caught 4 consecutive stale-doc
# instances, each found manually (coverage totals, snapshot counts, …).
#
# Prevent the same drift from accumulating: re-run each live
# source-of-truth script + grep the docs for the current values; fail on
# mismatch. Designed to run fast (~10s — the vitest live-count run added
# by the R7 docs rewrite costs ~3s) so it can sit at the front of
# smoke.sh as a static check.
#
# The living numeric claims live in the root README.md + CLAUDE.md,
# docs/STATUS.md (the one-page status summary that replaced the retired
# docs/reports/ campaign tree), and the tiny runtimes/*/README.md files —
# the greps below target exactly those. Checks whose source of truth was
# retired with docs/reports/ (degenerate-fixture audit, ssim-history
# snapshots, the Tier 1 tracker awk) were removed with it; the verified-
# coverage number is now pinned by a consistency check against
# docs/STATUS.md instead.
#
# Exit codes:
#   0 = all doc-quoted numbers match live source-of-truth output
#   1 = at least one stale claim found (with details printed)

set -uo pipefail
cd "$(dirname "$0")/../.."

B="\033[1;34m"; G="\033[0;32m"; Y="\033[1;33m"; R="\033[0;31m"; N="\033[0m"

FAILED=0
log()  { echo -e "${G}[doc-check]${N} $*"; }
warn() { echo -e "${Y}[doc-check]${N} $*" >&2; }
err()  { echo -e "${R}[doc-check]${N} $*" >&2; FAILED=1; }

# ── Coverage check (550-property catalogue × 3 platforms) ───────────────────
echo -e "${B}━━━ coverage-audit.mjs vs README/CLAUDE/STATUS ━━━${N}"
COVERAGE_OUTPUT=$(node tools/visual/coverage-audit.mjs 2>&1)
ANDROID=$(echo "$COVERAGE_OUTPUT" | grep -oE "android=[0-9]+" | head -1 | cut -d= -f2)
IOS=$(echo "$COVERAGE_OUTPUT" | grep -oE "ios=[0-9]+" | head -1 | cut -d= -f2)
WEB=$(echo "$COVERAGE_OUTPUT" | grep -oE "web=[0-9]+" | head -1 | cut -d= -f2)

if [[ -z "$ANDROID" || -z "$IOS" || -z "$WEB" ]]; then
    err "coverage-audit.mjs output missing one of android/ios/web — re-check the script"
else
    log "live: android=$ANDROID ios=$IOS web=$WEB"
    # Each living doc should mention the per-platform total in a `… / 550`
    # form. README.md + CLAUDE.md carry the claim in their "status"
    # sections; docs/STATUS.md is the standalone one-page summary.
    for doc in README.md CLAUDE.md docs/STATUS.md; do
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

# ── Unit test count vs README claim (round 75 addition) ────────────────────
echo -e "\n${B}━━━ unit-test count vs README/CLAUDE/STATUS ━━━${N}"
# The glob includes tools/titan/ so the extract-fixture /
# capture-browser-ref unit suites count toward the docs' headline number —
# the same glob smoke.sh's unit-test stage runs, so both agree on the total.
TEST_COUNT=$(node --test tools/visual/*.test.mjs tools/titan/*.test.mjs 2>&1 | grep -E "^# tests " | tail -1 | grep -oE "[0-9]+")
if [[ -z "$TEST_COUNT" ]]; then
    err "could not extract test count from \`node --test tools/visual/*.test.mjs\` output"
else
    log "live: $TEST_COUNT unit tests"
    # The README.md + CLAUDE.md + docs/STATUS.md test-suite tables quote the
    # tooling count — keep them in lockstep with the live number.
    for doc in README.md CLAUDE.md docs/STATUS.md; do
        if grep -qE "(^|[^0-9])$TEST_COUNT([^0-9]|$)" "$doc"; then
            log "✓ $doc mentions tooling test count $TEST_COUNT"
        else
            err "$doc does NOT mention live tooling test count $TEST_COUNT — update its test-suite table"
        fi
    done
fi

# ── Per-suite test counts vs root README/CLAUDE tables (R7 addition) ────────
#
# The R7 docs rewrite put a test-suite table in README.md + CLAUDE.md
# (converter / web / compose / swiftui counts). Derive each count live so
# the tables can't drift:
#   - converter + compose: JUnit `@Test` annotation count (1 annotation ==
#     1 runtime test today; if parameterized tests ever appear, switch to
#     parsing the gradle test-results XML instead)
#   - swiftui: XCTest `func test` declaration count (same 1:1 property)
#   - web: a real vitest run (~3s) — loops/`it.each` generate tests, so a
#     static grep undercounts (723 declarations vs 786 runtime tests)
echo -e "\n${B}━━━ per-suite test counts vs README/CLAUDE tables ━━━${N}"
CONVERTER_TESTS=$(grep -rE "@Test" converter/src/test --include="*.kt" | wc -l | tr -d ' ')
COMPOSE_TESTS=$(grep -rE "@Test" runtimes/compose/src/test --include="*.kt" | wc -l | tr -d ' ')
SWIFTUI_TESTS=$(grep -rE "func test" runtimes/swiftui/Tests --include="*.swift" -r | wc -l | tr -d ' ')
WEB_TESTS=$(npm -w runtimes/web run test 2>&1 | grep -E "Tests +[0-9]+ passed" | grep -oE "[0-9]+" | head -1)
log "live: converter=$CONVERTER_TESTS web=$WEB_TESTS compose=$COMPOSE_TESTS swiftui=$SWIFTUI_TESTS"
check_suite_count() { # $1=label $2=count $3…=docs that must quote it
    local label="$1" count="$2"; shift 2
    if [[ -z "$count" || "$count" == "0" ]]; then
        err "could not derive live $label test count — re-check this script's extraction"
        return
    fi
    for doc in "$@"; do
        if grep -qE "(^|[^0-9])$count([^0-9]|$)" "$doc"; then
            log "✓ $doc mentions $label=$count"
        else
            err "$doc does NOT mention live $label test count $count — update its test-suite table"
        fi
    done
}
check_suite_count "converter" "$CONVERTER_TESTS" README.md CLAUDE.md docs/STATUS.md
check_suite_count "web"       "$WEB_TESTS"       README.md CLAUDE.md docs/STATUS.md runtimes/web/README.md
check_suite_count "compose"   "$COMPOSE_TESTS"   README.md CLAUDE.md docs/STATUS.md runtimes/compose/README.md apps/android-harness/README.md
check_suite_count "swiftui"   "$SWIFTUI_TESTS"   README.md CLAUDE.md docs/STATUS.md runtimes/swiftui/README.md

# ── Fixture category count vs root README/CLAUDE (R7 addition) ──────────────
#
# The "33 categories" claim (the canonical irmodels/fixtures taxonomy)
# appears in README.md + CLAUDE.md. Count the real category directories
# under fixtures/properties/ so the claim tracks reality.
echo -e "\n${B}━━━ fixture category count vs README/CLAUDE ━━━${N}"
CATEGORY_COUNT=$(find fixtures/properties -mindepth 1 -maxdepth 1 -type d | wc -l | tr -d ' ')
log "live: $CATEGORY_COUNT category directories under fixtures/properties/"
for doc in README.md CLAUDE.md; do
    if grep -q "$CATEGORY_COUNT categor" "$doc"; then
        log "✓ $doc mentions $CATEGORY_COUNT categories"
    else
        err "$doc does NOT mention live category count \"$CATEGORY_COUNT categories\""
    fi
done

# ── Round 75 regression-prevention checks (round 78 addition) ───────────────
#
# Round 75 caught two silent regressions that broke smoke for rounds 71-74:
#   1. FixtureCanvas's double-RAF hangs in headless puppeteer
#      (fixed by switching to setTimeout)
#   2. puppeteer.launch's recent default protocolTimeout dropped
#      (fixed by passing { protocolTimeout: 5*60*1000 })
#
# Both fixes are in apps/web-harness/src/ui/FixtureCanvas.tsx,
# tools/visual/interaction-states.mjs, tools/visual/a11y-audit.mjs. If a future
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
RAF_USE=$(grep -nE "requestAnimationFrame" apps/web-harness/src/ui/FixtureCanvas.tsx 2>/dev/null \
            | grep -vE "^\s*[0-9]+:\s*(//|\*|/\*)" \
            | head -1)
if [[ -n "$RAF_USE" ]]; then
    err "FixtureCanvas.tsx contains requestAnimationFrame in non-comment line ($RAF_USE) — round 75 caught this hangs in headless puppeteer. Use setTimeout instead."
elif grep -q "setTimeout" apps/web-harness/src/ui/FixtureCanvas.tsx 2>/dev/null; then
    log "✓ FixtureCanvas.tsx uses setTimeout (not RAF) — round 75 fix preserved"
else
    err "FixtureCanvas.tsx has neither setTimeout nor RAF — verify ready-flag mechanism"
fi

# Pattern 10 (protocolTimeout drop): all puppeteer.launch calls must
# explicitly set protocolTimeout. Otherwise the recent puppeteer default
# is too low and per-state CDP round-trips fail mid-harness.
for f in tools/visual/interaction-states.mjs tools/visual/a11y-audit.mjs; do
    if grep -q "puppeteer.*launch" "$f" 2>/dev/null; then
        if grep -q "protocolTimeout" "$f"; then
            log "✓ $f passes protocolTimeout to puppeteer.launch — round 75 fix preserved"
        else
            err "$f calls puppeteer.launch without protocolTimeout — round 75 caught this is now too low by default"
        fi
    fi
done

# ── Verified-coverage consistency (docs/STATUS.md vs README/CLAUDE) ─────────
#
# The per-property Tier 1 tracker was retired with docs/reports/; its
# converged headline (91/550 verified) is now a fixed historical record in
# docs/STATUS.md. Keep the living docs from silently diverging: extract
# the number STATUS quotes and require README.md + CLAUDE.md to quote the
# same one.
echo -e "\n${B}━━━ verified-coverage consistency (STATUS vs README/CLAUDE) ━━━${N}"
PASS=$(grep -m1 -i "verified rendering coverage" docs/STATUS.md | grep -oE "[0-9]+ ?/ ?550" | head -1 | grep -oE "^[0-9]+")
if [[ -z "$PASS" ]]; then
    err "docs/STATUS.md: could not extract the verified-coverage \"N/550\" headline"
else
    log "docs/STATUS.md quotes verified coverage $PASS/550"
    for doc in README.md CLAUDE.md; do
        if grep -qE "$PASS ?/ ?550" "$doc"; then
            log "✓ $doc mentions verified coverage $PASS/550"
        else
            err "$doc does NOT mention verified coverage $PASS/550 (the STATUS.md headline)"
        fi
    done
fi

echo
if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${G}✓ all doc claims match live source-of-truth output${N}"
else
    echo -e "${R}✗ stale-doc claims found — re-run the affected source-of-truth scripts and update the docs${N}"
fi
exit "$FAILED"
