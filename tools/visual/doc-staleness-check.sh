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

# Wave-44 helpers (context-aware count matching + gradle-XML executed
# totals) — split into a sourced lib so this file stays under the ~300-line
# ceiling and the helpers stay probe-able without running the whole check.
# shellcheck source=tools/visual/doc-staleness-lib.sh
source tools/visual/doc-staleness-lib.sh

# ── Coverage check (live-derived catalogue × 3 platforms) ───────────────────
echo -e "${B}━━━ coverage-audit.mjs vs README/CLAUDE/STATUS ━━━${N}"
COVERAGE_OUTPUT=$(node tools/visual/coverage-audit.mjs 2>&1)
ANDROID=$(echo "$COVERAGE_OUTPUT" | grep -oE "android=[0-9]+" | head -1 | cut -d= -f2)
IOS=$(echo "$COVERAGE_OUTPUT" | grep -oE "ios=[0-9]+" | head -1 | cut -d= -f2)
WEB=$(echo "$COVERAGE_OUTPUT" | grep -oE "web=[0-9]+" | head -1 | cut -d= -f2)
# Wave 24 — the catalogue denominator is DERIVED, not hardcoded: the
# gap-decorations family grew the IR catalogue 550 -> 558, and any future
# property addition moves it again. Count the *Property.kt files exactly the
# way coverage-audit's catalogue walk does.
CATALOG=$(find converter/src/main/kotlin/app/irmodels/properties -name "*Property.kt" | wc -l | tr -d " ")

if [[ -z "$ANDROID" || -z "$IOS" || -z "$WEB" ]]; then
    err "coverage-audit.mjs output missing one of android/ios/web — re-check the script"
else
    log "live: android=$ANDROID ios=$IOS web=$WEB"
    # Each living doc should mention the per-platform total in a `… / 550`
    # form. README.md + CLAUDE.md carry the claim in their "status"
    # sections; docs/STATUS.md is the standalone one-page summary.
    for doc in README.md CLAUDE.md docs/STATUS.md; do
        # Look for any "X / 550" or "X/$CATALOG" near the platform name. We don't
        # want to be over-precise: just confirm the live number appears at
        # least once in the doc.
        if ! grep -qE "$ANDROID *(/| / )$CATALOG" "$doc" 2>/dev/null; then
            err "$doc: does not mention android=$ANDROID/$CATALOG (live truth)"
        fi
        if ! grep -qE "$IOS *(/| / )$CATALOG" "$doc" 2>/dev/null; then
            err "$doc: does not mention ios=$IOS/$CATALOG (live truth)"
        fi
        if ! grep -qE "$WEB *(/| / )$CATALOG" "$doc" 2>/dev/null; then
            err "$doc: does not mention web=$WEB/$CATALOG (live truth)"
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
    # Non-fatal: this branch RUNS the suite to count it, which is
    # environment-sensitive (node --test glob/reporter quirks in CI). An
    # extraction failure is NOT a stale doc — warn and skip so CI stays
    # green; the check still fires (and hard-fails on a real MISMATCH)
    # wherever the count DOES extract, e.g. locally via smoke.sh.
    warn "could not extract live tooling test count in this environment — skipping tooling-count check (still enforced where extractable)"
else
    log "live: $TEST_COUNT unit tests"
    # The README.md + CLAUDE.md + docs/STATUS.md test-suite tables quote the
    # tooling count — keep them in lockstep with the live number. Wave-44:
    # the count must sit in the `| tooling … | N |` row (or an inline
    # `(N tests` phrase), not merely anywhere in the file — see
    # doc_quotes_suite_count for the wave-43 stale-row post-mortem.
    for doc in README.md CLAUDE.md docs/STATUS.md; do
        if doc_quotes_suite_count "tooling" "$TEST_COUNT" "$doc"; then
            log "✓ $doc mentions tooling test count $TEST_COUNT"
        else
            err "$doc does NOT mention live tooling test count $TEST_COUNT in its test-suite table — update it"
        fi
    done
fi

# ── Per-suite test counts vs root README/CLAUDE tables (R7 addition) ────────
#
# The R7 docs rewrite put a test-suite table in README.md + CLAUDE.md
# (converter / web / compose / swiftui counts). Derive each count live so
# the tables can't drift:
#   - converter + compose: executed total from the latest gradle JUnit XML
#     when that run is fresh (see xml_test_total — the wave-43 post-mortem),
#     falling back to the JUnit `@Test` annotation count (1 annotation ==
#     1 runtime test today: both trees verified free of parameterized/
#     repeated/factory/inherited-@Test constructs, wave 44)
#   - swiftui: XCTest `func test` declaration count (same 1:1 property)
#   - web: a real vitest run (~3s) — loops/`it.each` generate tests, so a
#     static grep undercounts (723 declarations vs 786 runtime tests)
echo -e "\n${B}━━━ per-suite test counts vs README/CLAUDE tables ━━━${N}"
# Annotation counts first — the always-available static baseline for the
# two JUnit trees (exact today: 1 annotation == 1 executed test, see above).
CONVERTER_ANNOTATED=$(grep -rE "@Test" converter/src/test --include="*.kt" | wc -l | tr -d ' ')
COMPOSE_ANNOTATED=$(grep -rE "@Test" runtimes/compose/src/test --include="*.kt" | wc -l | tr -d ' ')
# Executed totals from the latest gradle run's JUnit XML — empty when no
# run is recorded, the run pre-dates a test-source edit, or the run is
# incomplete/undersized vs the annotation floor (xml_test_total).
CONVERTER_XML=$(xml_test_total converter/build/test-results/test converter/src/test "$CONVERTER_ANNOTATED" || true)
COMPOSE_XML=$(xml_test_total runtimes/compose/build/test-results/testDebugUnitTest runtimes/compose/src/test "$COMPOSE_ANNOTATED" || true)
# Prefer the executed truth when available; annotations otherwise.
CONVERTER_TESTS=${CONVERTER_XML:-$CONVERTER_ANNOTATED}
COMPOSE_TESTS=${COMPOSE_XML:-$COMPOSE_ANNOTATED}
# A fresh, complete XML total EXCEEDING the annotation grep means
# annotation-invisible tests exist (parameterized/repeated/…). Surface the
# delta and hold the docs to the EXECUTED number — the figure a runner
# actually reports. (Wave-43's 2468-vs-2465 reading was not this case: the
# two numbers were sampled from different mid-wave trees.)
[[ -n "$CONVERTER_XML" && "$CONVERTER_XML" != "$CONVERTER_ANNOTATED" ]] && \
    warn "converter executed=$CONVERTER_XML != annotated=$CONVERTER_ANNOTATED — annotation-invisible tests; docs held to the executed total"
[[ -n "$COMPOSE_XML" && "$COMPOSE_XML" != "$COMPOSE_ANNOTATED" ]] && \
    warn "compose executed=$COMPOSE_XML != annotated=$COMPOSE_ANNOTATED — annotation-invisible tests; docs held to the executed total"
SWIFTUI_TESTS=$(grep -rE "func test" runtimes/swiftui/Tests --include="*.swift" -r | wc -l | tr -d ' ')
WEB_TESTS=$(npm -w runtimes/web run test 2>&1 | grep -E "Tests +[0-9]+ passed" | grep -oE "[0-9]+" | head -1)
log "live: converter=$CONVERTER_TESTS web=$WEB_TESTS compose=$COMPOSE_TESTS swiftui=$SWIFTUI_TESTS"
check_suite_count() { # $1=suite keyword (must appear in its table row's first cell) $2=count $3…=docs that must quote it
    local label="$1" count="$2"; shift 2
    if [[ -z "$count" || "$count" == "0" ]]; then
        # Non-fatal: web count needs a live vitest run whose reporter output
        # differs in CI (no TTY) so the parse can come back empty. That is an
        # extraction limitation, not a stale doc — warn and skip rather than
        # red-failing the build; a real MISMATCH still hard-fails (below).
        warn "could not derive live $label test count in this environment — skipping (still enforced where extractable, e.g. smoke.sh)"
        return
    fi
    for doc in "$@"; do
        # Wave-44 hardening: the count must sit in the suite's own table
        # row or an inline `(N tests` phrase — a bare number elsewhere in
        # the file no longer vouches for the row (the wave-43 stale-row
        # escape; see doc_quotes_suite_count).
        if doc_quotes_suite_count "$label" "$count" "$doc"; then
            log "✓ $doc mentions $label=$count"
        else
            err "$doc does NOT mention live $label test count $count in its test-suite table/inline count — update it"
        fi
    done
}
check_suite_count "converter" "$CONVERTER_TESTS" README.md CLAUDE.md docs/STATUS.md
check_suite_count "web"       "$WEB_TESTS"       README.md CLAUDE.md docs/STATUS.md runtimes/web/README.md
check_suite_count "compose"   "$COMPOSE_TESTS"   README.md CLAUDE.md docs/STATUS.md runtimes/compose/README.md apps/android-harness/README.md
check_suite_count "swiftui"   "$SWIFTUI_TESTS"   README.md CLAUDE.md docs/STATUS.md runtimes/swiftui/README.md

# ── Harness suites (retrospective A8#4) ─────────────────────────────────────
#
# The sweep tables enumerated the four runtime suites + tooling and nothing
# else — yet the Android half of the wave-45 em-margin four-rung ladder is
# pinned ONLY by apps/android-harness's :app JUnit tree (StaticEmMarginsTest
# 18 + UaBlockMarginsTest 37), and the wave-40 flow-root and wave-49 root-clip
# web pins live in apps/web-harness/tests (CI runs it via `npm -ws run test`;
# no local table listed it). A suite outside every documented row is a suite
# nobody runs. Derive both exactly like the runtime suites above and hold the
# same three docs to a row whose FIRST cell names the harness.
APP_ANNOTATED=$(grep -rE "@Test" apps/android-harness/app/src/test --include="*.kt" | wc -l | tr -d ' ')
APP_XML=$(xml_test_total apps/android-harness/app/build/test-results/testDebugUnitTest apps/android-harness/app/src/test "$APP_ANNOTATED" || true)
APP_TESTS=${APP_XML:-$APP_ANNOTATED}
[[ -n "$APP_XML" && "$APP_XML" != "$APP_ANNOTATED" ]] && \
    warn "android-harness :app executed=$APP_XML != annotated=$APP_ANNOTATED — annotation-invisible tests; docs held to the executed total"
# vitest run (~3s) — same reporter parse as the runtimes/web row above.
WEB_HARNESS_TESTS=$(npm -w apps/web-harness run test 2>&1 | grep -E "Tests +[0-9]+ passed" | grep -oE "[0-9]+" | head -1)
log "live: android-harness(:app)=$APP_TESTS web-harness=$WEB_HARNESS_TESTS"
check_suite_count "android-harness" "$APP_TESTS"         README.md CLAUDE.md docs/STATUS.md
check_suite_count "web-harness"     "$WEB_HARNESS_TESTS" README.md CLAUDE.md docs/STATUS.md
# The ios-harness XCTest bundle (project.yml target StyleConverterTestTests)
# is defined but no documented command runs it and it needs a booted
# simulator, so it cannot be derived here — counted and surfaced as a
# WARNING so the gap stays visible until it is either wired into a row or
# deleted (A8#4's prescription offers both).
IOS_HARNESS_DECLS=$(grep -rE "func test" apps/ios-harness/StyleConverterTestTests --include="*.swift" 2>/dev/null | wc -l | tr -d ' ')
warn "ios-harness XCTest bundle declares $IOS_HARNESS_DECLS tests but no documented sweep row runs it (needs a simulator) — wire it into the tables or delete the target"

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
# explicitly set protocolTimeout ≥ 5*60*1000 ms. Otherwise the recent
# puppeteer default is too low and per-state CDP round-trips fail
# mid-harness.
#
# LANE E hardening (2026-08-29): the old `grep -q protocolTimeout` here was
# satisfied by the fix's own explanatory COMMENT — deleting the actual
# launch option left this check green (verified by mutation: strip
# `protocolTimeout: 5 * 60 * 1000` from the launch call, keep the round-75
# comment, grep still matches). Delegate to check-protocol-timeout.mjs,
# which blanks comments/strings, walks each launch() call's balanced-paren
# argument list, and statically evaluates the configured value against the
# 5-minute floor. --expect-launch makes a vanished launch() call fail too
# (these files' whole job is to drive puppeteer), so the guard cannot be
# dodged by moving the call. Pinned by check-protocol-timeout.test.mjs.
if PT_OUT=$(node tools/visual/check-protocol-timeout.mjs --expect-launch \
        tools/visual/interaction-states.mjs tools/visual/a11y-audit.mjs 2>&1); then
    log "✓ puppeteer.launch protocolTimeout verified in-call (≥ 5 min) — round 75 fix preserved"
else
    err "puppeteer.launch protocolTimeout check failed — round 75 regression: ${PT_OUT}"
fi

# ── Verified-coverage consistency (docs/STATUS.md vs README/CLAUDE) ─────────
#
# The per-property Tier 1 tracker was retired with docs/reports/; its
# converged headline (91/550 verified) is now a fixed historical record in
# docs/STATUS.md. Keep the living docs from silently diverging: extract
# the number STATUS quotes and require README.md + CLAUDE.md to quote the
# same one.
echo -e "\n${B}━━━ verified-coverage consistency (STATUS vs README/CLAUDE) ━━━${N}"
# The verified headline is the round-40 HISTORICAL record: 91 of the
# then-550 catalogue. It does not track the live catalogue (new properties
# start unverified), so the extraction pins the historical denominator.
PASS=$(grep -m1 -i "verified rendering coverage" docs/STATUS.md | grep -oE "[0-9]+ ?/ ?550" | head -1 | grep -oE "^[0-9]+")
if [[ -z "$PASS" ]]; then
    err "docs/STATUS.md: could not extract the verified-coverage \"N/$CATALOG\" headline"
else
    # Print the denominator the grep below actually pins (550, the round-40
    # historical record) — it used to print the live $CATALOG (558) while the
    # assertion checked 550, so message and check disagreed (retro A9#9 (d)).
    log "docs/STATUS.md quotes verified coverage $PASS/550 (historical denominator; live catalogue is $CATALOG)"
    for doc in README.md CLAUDE.md; do
        if grep -qE "$PASS ?/ ?550" "$doc"; then
            log "✓ $doc mentions verified coverage $PASS/550" # 550 = the denominator the grep above pins; it printed the live $CATALOG (558) while checking 550 (retro S1 #3 / A9#9 (d))
        else
            err "$doc does NOT mention verified coverage $PASS/550 (the STATUS.md headline)"
        fi
    done
fi

# ── Newest corpus snapshot vs BACKLOG "Current:" + STATUS wave paragraph ────
#
# Retrospective A9#9 (a): BACKLOG.md's "Current: corpus-v6.15 (web 1205/1379
# …)" line and STATUS.md's latest wave paragraph were checked by hand only.
# Derive the newest tools/titan/results/corpus-v<M>-<n>.json by version
# (dotted in the docs, hyphenated on disk; `-cal` calibration snapshots are
# not gate snapshots and are excluded) and require both docs to name it and
# to quote its three passing/measured fractions. Parameterised so the
# checker can be pointed at scratch copies (doc-staleness-checks.test.mjs).
echo -e "\n${B}━━━ newest corpus snapshot vs docs/BACKLOG.md + docs/STATUS.md ━━━${N}"
newest_corpus_snapshot() { # $1=results dir → path of the highest-versioned corpus-v<M>[-<n>].json
    node -p '
      const fs = require("fs"), d = process.argv[1];
      const v = (f) => { const m = f.match(/^corpus-v(\d+)(?:-(\d+))?\.json$/); return m && [+m[1], +(m[2] ?? 0)]; };
      fs.readdirSync(d).filter(v).sort((a, b) => { const A = v(a), B = v(b); return A[0] - B[0] || A[1] - B[1]; }).map((f) => d + "/" + f).pop() ?? ""
    ' "$1"
}
check_corpus_current() { # $1=snapshot json $2=BACKLOG.md path (its "Current:" line must name it) $3=STATUS.md path
    local snap="$1" backlog="$2" status="$3" id fr f
    id=$(basename "$snap" .json | sed 's/^corpus-v//; s/-/./')        # corpus-v6-15.json → 6.15
    fr=$(node -p '
      const t = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8")).totals;
      ["web", "ios", "android"].map((k) => t[k].passing + "/" + t[k].measured).join(" ")
    ' "$snap" 2>/dev/null) || { err "$snap: no totals.{web,ios,android}.{passing,measured} block"; return; }
    log "live: corpus-v$id → web/iOS/Android = $fr"
    # BACKLOG: the "Current:" line itself must name the newest snapshot.
    if grep -qE "Current:.*corpus-v${id//./\\.}" "$backlog"; then
        log "✓ $backlog Current: names corpus-v$id"
    else
        err "$backlog: the 'Current:' line does not name the newest snapshot corpus-v$id"
    fi
    # STATUS: the wave paragraph names the snapshot somewhere.
    if grep -qE "corpus-v${id//./\\.}" "$status"; then
        log "✓ $status names corpus-v$id"
    else
        err "$status: does not name the newest snapshot corpus-v$id"
    fi
    # Both docs quote all three fractions (fixed strings — a fraction is
    # specific enough that context matching adds nothing).
    for f in $fr; do
        grep -qF "$f" "$backlog" || err "$backlog does not quote $f from corpus-v$id"
        grep -qF "$f" "$status"  || err "$status does not quote $f from corpus-v$id"
    done
}
NEWEST_CORPUS=$(newest_corpus_snapshot tools/titan/results)
if [[ -z "$NEWEST_CORPUS" ]]; then
    err "no corpus-v<M>-<n>.json snapshot found under tools/titan/results"
else
    check_corpus_current "$NEWEST_CORPUS" docs/BACKLOG.md docs/STATUS.md
fi

# ── coverage-audit real / applier-file counts vs README + COVERAGE.md ───────
#
# Retrospective A6#16: the coverage check above pins only the `registered:`
# totals, so README's real-applier row (19 vs live 20) and COVERAGE.md's whole
# body (last regenerated pre-campaign: 550/550, real 18/73/508, files
# 59/115/522) drifted unguarded. Pin the `real:` totals and the dedicated
# `*Applier` file counts from `coverage-audit.mjs --json` against README's row
# and COVERAGE.md's total row + "Dedicated" line. Fix: edit README's row and
# `node tools/visual/coverage-audit.mjs --md` for COVERAGE.md.
echo -e "\n${B}━━━ coverage-audit real/applier-file counts vs README.md + COVERAGE.md ━━━${N}"
check_real_floor() { # $1=coverage-audit --json output file $2=README.md $3=COVERAGE.md (uses $CATALOG)
    local j="$1" readme="$2" cov="$3" ra ri rw fa fi fw
    read -r ra ri rw fa fi fw < <(node -p '
      const j = JSON.parse(require("fs").readFileSync(process.argv[1], "utf8"));
      [j.realTotals.android, j.realTotals.ios, j.realTotals.web, j.applierFiles.android, j.applierFiles.ios, j.applierFiles.web].join(" ")
    ' "$j")
    log "live: real android=$ra ios=$ri web=$rw · applier files android=$fa ios=$fi web=$fw"
    # README's row: "**Android 20 / 558 · iOS 74 / 558 · Web 516 / 558**".
    if grep -qE "Android *$ra */ *$CATALOG *· *iOS *$ri */ *$CATALOG *· *Web *$rw */ *$CATALOG" "$readme"; then
        log "✓ $readme real-applier row matches"
    else
        err "$readme: real-applier floor row is not 'Android $ra / $CATALOG · iOS $ri / $CATALOG · Web $rw / $CATALOG' (live coverage-audit real: line)"
    fi
    # COVERAGE.md total row: | **total** | **reg/N** | **real/N** | … three times.
    if grep -qE "^\| \*\*total\*\* \| \*\*[0-9]+/$CATALOG\*\* \| \*\*$ra/$CATALOG\*\* \| \*\*[0-9]+/$CATALOG\*\* \| \*\*$ri/$CATALOG\*\* \| \*\*[0-9]+/$CATALOG\*\* \| \*\*$rw/$CATALOG\*\* \|" "$cov"; then
        log "✓ $cov total row matches"
    else
        err "$cov: total row does not carry real $ra/$ri/$rw of $CATALOG — regenerate: node tools/visual/coverage-audit.mjs --md"
    fi
    if grep -qF "Android $fa · iOS $fi · Web $fw" "$cov"; then
        log "✓ $cov dedicated-applier-file counts match"
    else
        err "$cov: dedicated *Applier file counts are not 'Android $fa · iOS $fi · Web $fw' — regenerate: node tools/visual/coverage-audit.mjs --md"
    fi
}
COVERAGE_JSON=$(mktemp -t doc-staleness-coverage)
if node tools/visual/coverage-audit.mjs --json > "$COVERAGE_JSON" 2>/dev/null; then
    check_real_floor "$COVERAGE_JSON" README.md tools/visual/COVERAGE.md
else
    err "coverage-audit.mjs --json failed — cannot pin the real/applier-file counts"
fi
rm -f "$COVERAGE_JSON"

# ── Committed baselines vs fixture components (orphan PNGs) ─────────────────
#
# Retrospective A12#4: syncBaseline() upserts and never deletes, so the 12
# AR_* components of a fixture pruned on 2026-07-08 survived as 36 orphan
# PNGs through 49 waves (and were the only carriers of the SSIM downsample
# caveat). Every `{platform}__{NNN}_{Name}.png` under the baseline dir must
# name a component (top-level or nested child) declared by SOME fixture under
# fixtures/. Fix: `git rm tools/visual/baseline/*__NNN_<Name>.png`.
echo -e "\n${B}━━━ committed baselines vs fixture components (orphan PNGs) ━━━${N}"
check_baseline_orphans() { # $1=baseline dir $2=fixtures root → err naming every component no fixture declares
    local out total list
    out=$(node -e '
      const fs = require("fs"), path = require("path");
      const [dir, root] = process.argv.slice(1);
      const names = new Set();
      const rec = (c) => { for (const [k, v] of Object.entries(c ?? {})) { names.add(k); if (v && v.children) rec(v.children); } };
      const walk = (d) => { for (const e of fs.readdirSync(d, { withFileTypes: true })) {
        const p = path.join(d, e.name);
        if (e.isDirectory()) walk(p);
        else if (e.name.endsWith(".json")) { let doc; try { doc = JSON.parse(fs.readFileSync(p, "utf8")); } catch { continue; } if (doc && doc.components) rec(doc.components); }
      } };
      walk(root);
      const orphans = new Set(); let total = 0;
      for (const f of fs.readdirSync(dir)) { const m = f.match(/^(?:iOS|Android|web)__\d+_(.+)\.png$/); if (!m) continue; total++; if (!names.has(m[1])) orphans.add(m[1]); }
      console.log(total + " " + [...orphans].sort().join(","));
    ' "$1" "$2") || { err "orphan scan failed for $1 vs $2"; return; }
    total="${out%% *}"; list="${out#* }"
    if [[ -z "$list" || "$list" == "$total" ]]; then
        log "✓ all $total committed baseline PNGs name a component declared by a fixture under $2"
    else
        err "orphan baselines under $1 — components declared by NO fixture under $2: ${list//,/ }  (git rm the *__NNN_<Name>.png files; syncBaseline upserts and never deletes)"
    fi
}
check_baseline_orphans tools/visual/baseline fixtures

echo
if [[ "$FAILED" -eq 0 ]]; then
    echo -e "${G}✓ all doc claims match live source-of-truth output${N}"
else
    echo -e "${R}✗ stale-doc claims found — re-run the affected source-of-truth scripts and update the docs${N}"
fi
exit "$FAILED"
