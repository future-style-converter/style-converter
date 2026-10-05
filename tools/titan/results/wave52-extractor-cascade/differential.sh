#!/usr/bin/env bash
# wave-52 lane L5 (extractor-cascade) — the extract+convert DIFFERENTIAL census.
#
# Reproduces the tools/titan/results/wave50-S2/_note.md "Method (reproduce)"
# recipe with ONE correction learned this wave: every `tools/titan` module is
# a FROZEN COPY of HEAD (`git archive HEAD`), never a symlink into the live
# tree. Twelve lanes edit the shared tree concurrently, and a symlinked
# `counter-style-bake.mjs` handed L6's in-progress `@counter-style` bake to
# this lane's dev side between two extractions (8 cssom documents gained
# `markerText` that no extractor change could have produced). Only
# `extract-fixture.mjs` — the file under test — differs between sides.
# WPT_DIR points at the repo corpus, WPT_FIXTURES_ROOT at the side's own
# fixtures/wpt, the STATIC path by default (POST_LOAD_EXTRACT / BIDI_BAKE /
# VT_BAKE unset). With DIFF_POST_LOAD=1 in the environment the extract step
# runs with POST_LOAD_EXTRACT=1 instead (BIDI_BAKE / VT_BAKE still unset), so
# the ~335 extraction-wall tests take the live-browser overlay — the plan's
# "POST_LOAD_EXTRACT unset AND set" requirement (PLAN.md §2 L5). Post-load
# reads the harness fonts from <side>/apps/web-harness/public/fonts and the
# mono pin from <side>/tools/titan/fonts, so setup links both (read-only
# assets no lane edits). Conversion uses ONE jar for both sides so the
# per-test IR diff attributes to the extractor alone.
#
# Usage (all paths absolute; ROOT is the scratch dir holding the sides):
#   differential.sh setup   <ROOT> <side> <extract-fixture.mjs to copy in>
#   differential.sh extract <ROOT> <side>                 # 1435 tests, static
#   differential.sh convert <ROOT> <side> <jar-lib-dir>   # combine+convert+split per section
#   differential.sh compare <ROOT> <sideA> <sideB> <out.json>
#   differential.sh run     <ROOT> <side> <extractor> <jar-lib-dir>   # setup+extract+convert
#
# The repo root is derived from this script's location (the results dir is
# three levels below tools/titan/results/<lane>/).
set -euo pipefail
HERE="$(cd "$(dirname "$0")" && pwd)"
REPO="$(cd "$HERE/../../../.." && pwd)"
RUN_SECTIONS="$REPO/tools/titan/runs/wave51-fix/sections"
JAVA_HOME="${JAVA_HOME:-$(/usr/libexec/java_home -v 21)}"

cmd="$1"; shift
case "$cmd" in
  setup)
    ROOT="$1"; SIDE="$2"; SRC="$3"
    D="$ROOT/$SIDE"
    rm -rf "$D"; mkdir -p "$D/fixtures/wpt" "$D/combined" "$D/per-test-ir"
    # FROZEN tools/titan at HEAD: every tracked top-level module and data file
    # (results/, runs/, fixtures/, fonts/ are not needed by the static path).
    (cd "$REPO" && git archive HEAD -- $(git ls-files tools/titan \
      | grep -v '^tools/titan/results/\|^tools/titan/runs/\|^tools/titan/fixtures/\|^tools/titan/fonts/')) \
      | tar -x -C "$D"
    # post-load-extract.mjs imports inject-wpt-block.mjs, which imports
    # tools/visual/classify-divergence.mjs — frozen at HEAD the same way
    # (baselines and reports excluded; they are data, not modules).
    (cd "$REPO" && git archive HEAD -- $(git ls-files tools/visual \
      | grep -v '^tools/visual/baseline/\|^tools/visual/report/')) | tar -x -C "$D"
    # The bucket index is generated (gitignored) data the extractor reads from
    # REPO_ROOT/tools/titan; copied from the tree (no lane edits it).
    cp "$REPO/tools/titan/wpt-buckets.json" "$D/tools/titan/wpt-buckets.json"
    # Node resolves bare specifiers (puppeteer, pngjs) upward from the importing
    # file; the frozen copies live outside the repo, so the repo's node_modules
    # is linked beside them (needed by the test files, not by the static path).
    ln -s "$REPO/node_modules" "$D/node_modules"
    # Read-only font assets the post-load page embeds (see header).
    mkdir -p "$D/apps/web-harness/public"
    ln -s "$REPO/apps/web-harness/public/fonts" "$D/apps/web-harness/public/fonts"
    ln -s "$REPO/tools/titan/fonts" "$D/tools/titan/fonts"
    # The file under test.
    cp "$SRC" "$D/tools/titan/extract-fixture.mjs"
    echo "setup $SIDE ← $(shasum -a 256 "$SRC" | cut -c1-16)… (tools/titan frozen at $(cd "$REPO" && git rev-parse --short HEAD))"
    ;;
  extract)
    ROOT="$1"; SIDE="$2"; D="$ROOT/$SIDE"
    rm -rf "$D/fixtures/wpt"; mkdir -p "$D/fixtures/wpt"
    # The 1435-test union of the 30 sections' tests.list (sorted, unique).
    cat "$RUN_SECTIONS"/*/tests.list | sort -u > "$D/tests.all"
    # Static path unless DIFF_POST_LOAD=1; BIDI_BAKE / VT_BAKE never set.
    PL=(); [ "${DIFF_POST_LOAD:-}" = "1" ] && PL=(POST_LOAD_EXTRACT=1)
    env -u POST_LOAD_EXTRACT -u BIDI_BAKE -u VT_BAKE ${PL[@]+"${PL[@]}"} \
      WPT_DIR="$REPO/tools/wpt" WPT_FIXTURES_ROOT="$D/fixtures/wpt" \
      node "$D/tools/titan/extract-fixture.mjs" $(cat "$D/tests.all") > "$D/extract.log" 2>&1 || true
    tail -1 "$D/extract.log"
    # A side whose extractor never reached its summary line is not a side: a
    # fatal module error once left fixtures/ empty and the convert step still
    # emitted 1435 (empty) per-test docs. The summary must account for every
    # test; per-test FAILs are listed (both sides must fail the same tests —
    # the compare step's onlyA/onlyB would show any asymmetry).
    total=$(wc -l < "$D/tests.all" | tr -d ' ')
    summary=$(grep -E '^extract-fixture: [0-9]+ ok, [0-9]+ failed' "$D/extract.log" | tail -1)
    okn=$(echo "$summary" | sed -E 's/^extract-fixture: ([0-9]+) ok, ([0-9]+) failed.*/\1/')
    failn=$(echo "$summary" | sed -E 's/^extract-fixture: ([0-9]+) ok, ([0-9]+) failed.*/\2/')
    [ -n "$summary" ] && [ $((okn + failn)) -eq "$total" ] \
      || { echo "extract $SIDE: no complete summary — see $D/extract.log" >&2; exit 2; }
    grep '^FAIL ' "$D/extract.log" || true
    ;;
  convert)
    ROOT="$1"; SIDE="$2"; JARS="$3"; D="$ROOT/$SIDE"
    rm -rf "$D/combined" "$D/per-test-ir" "$D/ir-out"; mkdir -p "$D/combined" "$D/per-test-ir"
    for sec in "$RUN_SECTIONS"/*/; do
      s="$(basename "$sec")"
      # build-combined-fixture.mjs resolves REPO_ROOT/fixtures/wpt from its own
      # __dirname — the frozen copy under $D sees $D/fixtures/wpt.
      node "$D/tools/titan/build-combined-fixture.mjs" --tests "$sec/tests.list" --out "$D/combined/$s.json" >> "$D/convert.log" 2>&1
      mkdir -p "$D/ir-out/$s"
      "$JAVA_HOME/bin/java" -cp "$JARS/*" app.MainKt convert --from css --to ir -i "$D/combined/$s.json" -o "$D/ir-out/$s" >> "$D/convert.log" 2>&1
      # split-combined-ir.mjs's IS_CLI guard compares realpath(argv[1]) with
      # its own URL — invoking the frozen copy by its real path satisfies it.
      node "$D/tools/titan/split-combined-ir.mjs" --in "$D/ir-out/$s/tmpOutput.json" --out "$D/per-test-ir/$s" >> "$D/convert.log" 2>&1
    done
    echo "convert $SIDE: $(ls "$D"/per-test-ir/*/*.json | wc -l | tr -d ' ') per-test IR docs"
    ;;
  compare)
    ROOT="$1"; A="$2"; B="$3"; OUT="$4"
    node "$HERE/differential-report.mjs" "$ROOT/$A" "$ROOT/$B" "$OUT"
    ;;
  run)
    ROOT="$1"; SIDE="$2"; SRC="$3"; JARS="$4"
    "$0" setup "$ROOT" "$SIDE" "$SRC"
    "$0" extract "$ROOT" "$SIDE"
    "$0" convert "$ROOT" "$SIDE" "$JARS"
    ;;
  *) echo "unknown command $cmd" >&2; exit 1 ;;
esac
