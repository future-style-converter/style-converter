#!/usr/bin/env bash
# tools/titan/results/wave54-hyphenate-character/wl3-wire-differential.sh — [W-L3], the L3 GATE-FLAG wire differential
# (PLAN §2 L3 "ORCHESTRATOR WINDOW REQUESTS", §4 step 4). STARTS CHROMIUM (post-load / bidi / view-transition bakes)
# and runs the converter over every section: an ORCHESTRATOR WINDOW only, never from a builder lane.
#
# For each tree and each section of <run> it runs EXACTLY the gate's own steps (tools/titan/section-runner.sh):
#   :375  POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 node tools/titan/extract-fixture.mjs <tests.list…>
#   :416  node tools/titan/build-combined-fixture.mjs --out fixtures/wpt/_section-<sec>.json --tests <tests.list>
#   :435  ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i fixtures/wpt/_section-<sec>.json -o <out>"
#   :620  node tools/titan/split-combined-ir.mjs --in <out>/tmpOutput.json --out <per-test-ir>
# then compares the two per-test-ir trees document by document:
#   identical · RENUMBERED (equal once every `-<n>` id suffix is stripped from ids and slot.parent) · CONTENT-CHANGED.
#
# Usage: wl3-wire-differential.sh <pre-tree> <post-tree> <out-dir> [run=wave54-open] [section…]
#   <pre-tree>/<post-tree>: worktrees with node_modules, tools/wpt, tools/titan/wpt-buckets.json and
#   apps/android-harness/local.properties symlinked from the main checkout (all gitignored); JDK 21 on JAVA_HOME.
# EXPECTED (decides U1/U3/U3b's R4b wire class; PLAN §4 step 4):
#   L3 alone — pre = the landed tree just before L3 U1, post = after L3 U3b:
#     CONTENT-CHANGED exactly 19 = hyphenate-character-001/-002/-003/-005 (U1: the value inside the existing string
#     variant; 001/002 Generic → {"type":"string","value":""}) ∪ the 18 U3 documents (revert-layer-006,
#     revert-val-001/-002, clip-path-filter-order, balance-grid-container, column-height-009, block-ellipsis-002/-004/
#     -005/-006, hyphenate-character-001…004, backdrop-filter-clip-rect/-edge-clipping/-paint-order/-plus-filter);
#     RENUMBERED 0; every other document identical. U3b's 12 brs live inside hyphenate-character-001…004.
#   Union — pre = HEAD-before-landing, post = fully landed: CONTENT-CHANGED 23 (L1's 4 + L3's 19), RENUMBERED 15
#     (css-counter-styles/cssom/*, +6 after counter-suffix, M′); the three zero-padding selectors docs identical.
#   Any other changed document is an extraction/converter LEAK: stop (revert rule 5).
set -uo pipefail
PRE="$1"; POST="$2"; OUT="$3"; RUN="${4:-wave54-open}"; shift $(( $# < 4 ? $# : 4 ))
RUNS="$(cd "$(dirname "$0")/../../runs" && pwd)/$RUN/sections"
SECTIONS=("$@"); [ ${#SECTIONS[@]} -eq 0 ] && SECTIONS=($(ls "$RUNS"))
mkdir -p "$OUT"
for tag in pre post; do
  TREE=$([ $tag = pre ] && echo "$PRE" || echo "$POST")
  for sec in "${SECTIONS[@]}"; do
    [ -f "$RUNS/$sec/tests.list" ] || continue
    TESTS=(); while IFS= read -r t; do [ -n "$t" ] && TESTS+=("$t"); done < "$RUNS/$sec/tests.list"   # bash 3.2-safe
    W="$OUT/$tag/$sec"; mkdir -p "$W/out"
    ( cd "$TREE" || exit 1
      POST_LOAD_EXTRACT=1 BIDI_BAKE=1 VT_BAKE=1 nice -n 10 node tools/titan/extract-fixture.mjs "${TESTS[@]}" > "$W/extract.log" 2>&1 \
      ; node tools/titan/build-combined-fixture.mjs --out "fixtures/wpt/_section-$sec.json" --tests "$RUNS/$sec/tests.list" > "$W/combine.log" 2>&1 \
      && ./gradlew --no-daemon :converter:run --args="convert --from css --to ir -i fixtures/wpt/_section-$sec.json -o $W/out" --quiet > "$W/convert.log" 2>&1 \
      && node tools/titan/split-combined-ir.mjs --in "$W/out/tmpOutput.json" --out "$W/per-test-ir" > "$W/split.log" 2>&1 ) \
      || echo "WARN pipeline rc≠0 ($tag $sec) — see $W/*.log"
  done
done
node - "$OUT" <<'JS'
const fs = require('fs'), path = require('path'); const OUT = process.argv[2];
const norm = (s) => s.replace(/("(?:id|parent)":\s*"[^"]*?)-\d+"/g, '$1"');
let same = 0, ren = [], chg = [], only = [];
for (const sec of fs.readdirSync(path.join(OUT, 'pre'))) {
  const a = path.join(OUT, 'pre', sec, 'per-test-ir'), b = path.join(OUT, 'post', sec, 'per-test-ir');
  if (!fs.existsSync(a) || !fs.existsSync(b)) { only.push(sec + ' (missing per-test-ir)'); continue; }
  for (const f of new Set([...fs.readdirSync(a), ...fs.readdirSync(b)])) {
    const x = fs.existsSync(path.join(a, f)) && fs.readFileSync(path.join(a, f), 'utf8');
    const y = fs.existsSync(path.join(b, f)) && fs.readFileSync(path.join(b, f), 'utf8');
    if (!x || !y) { only.push(`${sec}/${f}`); continue; }
    if (x === y) same++; else if (norm(x) === norm(y)) ren.push(`${sec}/${f}`); else chg.push(`${sec}/${f}`);
  }
}
for (const f of chg) console.log('CONTENT-CHANGED ' + f);
for (const f of ren) console.log('RENUMBERED ' + f);
for (const f of only) console.log('ONE-SIDED ' + f);
console.log(`identical ${same} · content-changed ${chg.length} · renumbered ${ren.length} · one-sided ${only.length}`);
JS
