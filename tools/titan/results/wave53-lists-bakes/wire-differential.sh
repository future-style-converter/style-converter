#!/usr/bin/env bash
# tools/titan/results/wave53-lists-bakes/wire-differential.sh — the L1 GATE-FLAG wire differential (PLAN §2 L1
# "Only in an orchestrator-granted host window"; §4 step 3; expectations.json lanes.L1-lists-bakes.revertUnits.*.wire).
# Starts Chromium (the post-load / bidi / view-transition bakes), so it runs ONLY in a device-idle window.
#
# For each section, each tree runs EXACTLY the gate's extraction command (tools/titan/section-runner.sh:375,
# `POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node tools/titan/extract-fixture.mjs <tests…>`) over the section's
# tests.list from <run>; the fixtures it writes (that tree's fixtures/wpt/, newer than a stamp file) are copied
# to <out>/<tag>/<section>/, and the two copies are compared byte for byte. Fixture identity ⇒ per-test IR
# identity (the converter is deterministic); the converse is what the closing gate's R4b checks.
#
# Usage: wire-differential.sh <pre-tree> <post-tree> <out-dir> [run=wave53-open] [section…]
#   <pre-tree>  e.g. a `git worktree add --detach <dir> cdb8a845` (U1) or the U1-commit tree (U2), with
#               node_modules and tools/wpt symlinked from the main checkout (both are gitignored).
#   <post-tree> the integrated tree carrying the unit(s) under test.
# Prints one `CHANGED <section>/<file>` line per differing fixture and a summary; expected (PLAN §4 step 3):
#   U1 vs cdb8a845: counter-reset-reversed-nested only · U2 vs U1: counter-suffix, bidi-lines-001, bidi-lines-002,
#   anchor-center-safe-rtl · cumulative: those 5 · P-narrow U2: counter-suffix only; selectors/dir-style-02a,
#   dir-selector-change-003/-004 identical every time.
set -uo pipefail
PRE="$1"; POST="$2"; OUT="$3"; RUN="${4:-wave53-open}"; shift $(( $# < 4 ? $# : 4 ))
RUNS="$(cd "$(dirname "$0")/../../runs" && pwd)/$RUN/sections"
SECTIONS=("$@"); [ ${#SECTIONS[@]} -eq 0 ] && SECTIONS=($(ls "$RUNS"))
mkdir -p "$OUT"
for tag in pre post; do
  TREE=$([ $tag = pre ] && echo "$PRE" || echo "$POST")
  for sec in "${SECTIONS[@]}"; do
    [ -f "$RUNS/$sec/tests.list" ] || continue
    TESTS=(); while IFS= read -r t; do [ -n "$t" ] && TESTS+=("$t"); done < "$RUNS/$sec/tests.list"   # bash 3.2-safe
    stamp="$OUT/.stamp-$tag-$sec"; : > "$stamp"; sleep 1
    ( cd "$TREE" && POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 nice -n 10 node tools/titan/extract-fixture.mjs "${TESTS[@]}" ) \
      > "$OUT/extract-$tag-$sec.log" 2>&1 || echo "WARN extractor rc≠0 ($tag $sec) — see $OUT/extract-$tag-$sec.log"
    mkdir -p "$OUT/$tag"
    ( cd "$TREE/fixtures/wpt" && find . -name '*.json' -newer "$stamp" -print0 | xargs -0 -I{} rsync -R {} "$OUT/$tag/" )
  done
done
changed=0; same=0
while IFS= read -r f; do
  if cmp -s "$OUT/pre/$f" "$OUT/post/$f"; then same=$((same+1)); else changed=$((changed+1)); echo "CHANGED $f"; fi
done < <(cd "$OUT/pre" && find . -name '*.json' | sort)
only_post=$(cd "$OUT/post" && find . -name '*.json' | sort | while read -r f; do [ -f "$OUT/pre/$f" ] || echo "$f"; done)
[ -n "$only_post" ] && echo "ONLY-IN-POST: $only_post"
echo "wire-differential: changed=$changed identical=$same (sections: ${SECTIONS[*]})"
