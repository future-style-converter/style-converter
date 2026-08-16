# tools/visual/doc-staleness-lib.sh — helpers sourced by
# doc-staleness-check.sh (wave-44 hardening; split out to keep the main
# checker under the repo's ~300-line file ceiling).
#
# This file only DEFINES functions — no side effects — so it can also be
# sourced by ad-hoc probes/tests without running the whole checker. It
# assumes the caller has already cd'd to the repo root (the checker does).

# ── Context-aware count matching (wave-44 hardening) ────────────────────────
#
# Wave-43 S1 post-mortem: the suite-count checks grepped the BARE live
# number anywhere in the doc (`(^|[^0-9])N([^0-9]|$)`), so a stale
# test-suite row passed whenever the live count coincided with an unrelated
# figure — the then-live converter count matched "363 committed baseline
# PNGs" in docs/STATUS.md and the stale row sailed through. A number only
# vouches for a doc when it sits where the doc actually QUOTES the suite
# total; exactly two such contexts exist today:
#   1. the test-suite table row whose FIRST cell names the suite:
#      `| converter (Kotlin) | … | 368 |` (README/CLAUDE/STATUS tables)
#   2. the per-runtime READMEs' inline phrasing: `(1263 tests, …` / `(2475 tests)`
# Prose numbers, other tables, and other suites' rows no longer satisfy it.
doc_quotes_suite_count() { # $1=suite keyword $2=live count $3=doc path
    local kw="$1" count="$2" doc="$3"
    # Table-row context: the line is a markdown row (`^|`), its first cell
    # contains the suite keyword, and the count stands alone as a later
    # `| N |` cell. Cells cannot contain `|`, so a standalone cell can
    # never be a substring of a longer number or an unrelated sentence.
    grep -qE "^\|[^|]*${kw}[^|]*\|.*\| *${count} *\|" "$doc" 2>/dev/null && return 0
    # Inline context: accept exactly the `(N tests` phrasing the tiny
    # runtime READMEs use (open-paren + count + " tests"), nothing looser.
    grep -qE "\(${count} tests" "$doc" 2>/dev/null && return 0
    return 1  # count absent from both quoting contexts → treat as stale
}

# ── Executed-total extraction from gradle JUnit XML (wave-44) ───────────────
#
# Wave-43 S1 measured compose executed (2468) > annotated (2465) and asked
# whether the `@Test` grep under-counts. Wave-44 verdict: at a FIXED tree
# state the two counts agree exactly (both 2475 at 4e3a68f4), and the
# compose tree has no annotation-invisible constructs (no
# @ParameterizedTest / @RepeatedTest / @TestFactory, no inherited-@Test
# base classes, no @Ignore, no commented-out @Test) — the wave-43 delta was
# tree-state skew: the annotation grep and the executed run sampled
# different mid-wave trees (the tree gained 61 compose tests that wave).
# The executed XML total is still the stronger source of truth — it stays
# correct if parameterized tests ever land — so prefer it whenever the
# latest recorded run is FRESH (no test source edited after it) and
# COMPLETE (total >= the annotation floor: `tests=` includes skipped, so a
# genuine run can only meet or exceed the annotation count; a lower total
# means gradle is mid-run — it wipes the dir and writes one XML per class
# as classes finish, observed live in wave 44: a concurrent run read 32 of
# 2488 — or a run from a smaller past tree). Fall back to the annotation
# grep in every doubtful case.
xml_test_total() { # $1=gradle test-results dir $2=test source tree $3=annotation floor
    local dir="$1" src="$2" floor="$3" newest_xml total
    [[ -d "$dir" ]] || return 1  # no recorded run → caller falls back to annotations
    # newest XML file of the run — the freshness reference point below
    newest_xml=$(ls -t "$dir"/TEST-*.xml 2>/dev/null | head -1)
    [[ -n "$newest_xml" ]] || return 1  # dir exists but holds no XML → fall back
    # Any test source newer than the newest XML means the run pre-dates the
    # current tree — its total describes an older suite and would wrongly
    # flag freshly-updated docs as stale, so fall back to annotations.
    [[ -n "$(find "$src" -name '*.kt' -newer "$newest_xml" -print -quit 2>/dev/null)" ]] && return 1
    # Sum the per-class `tests=` attributes — the same totals the gradle
    # HTML report shows and the docs' headline numbers quote.
    total=$(grep -ho 'tests="[0-9]*"' "$dir"/TEST-*.xml 2>/dev/null \
        | grep -oE '[0-9]+' | awk '{s+=$1} END {if (NR==0) exit 1; print s}') || return 1
    # Completeness gate (see header): below the annotation floor the run is
    # partial or from a smaller tree — annotations are the safer truth.
    [[ "$total" -ge "$floor" ]] || return 1
    echo "$total"  # fresh + complete → the executed total wins
}
