#!/usr/bin/env bash
# skeptic/seeded-equiv.sh — wave 54 L7 SKEPTIC: is label-chrome-check.mjs `fixtureSeeded` really test-all.sh
# `_gate_fixture_has_baselines` re-typed? Run BOTH on every tracked fixture against the committed baseline dir and
# against a dir holding committed + the 18 seeded PNGs; print every disagreement.
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"; cd "$ROOT"
eval "$(sed -n '/^_gate_fixture_has_baselines() {/,/^}/p' test-all.sh)"          # the shell rule, verbatim
D2="$(mktemp -d "${TMPDIR:-/tmp}/l7-skeptic-seeded.XXXXXX")"; trap 'rm -rf "$D2"' EXIT
cp tools/visual/baseline/*.png tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/*.png "$D2/"
for B in tools/visual/baseline "$D2"; do
  sh=0; js=0; dis=0; n=0
  while read -r f; do
    n=$((n+1)); _gate_fixture_has_baselines "$f" "$B"; a=$(( $? == 0 ? 1 : 0 ))
    b=$(node --input-type=module -e "import { fixtureSeeded, readFixture } from '$ROOT/tools/visual/label-chrome-check.mjs'; import { readdirSync } from 'node:fs'; const d = readFixture('$ROOT', '$f'); console.log(d && fixtureSeeded(d, readdirSync('$B')) ? 1 : 0)")
    sh=$((sh+a)); js=$((js+b)); [[ "$a" != "$b" ]] && { dis=$((dis+1)); echo "DISAGREE $f shell=$a js=$b"; }
  done < <(git ls-files fixtures | grep '\.json$')
  echo "baseline dir $( [[ $B == tools/visual/baseline ]] && echo committed || echo committed+seed ) ($(ls "$B" | grep -c png) PNGs): fixtures $n, shell-seeded $sh, js-seeded $js, disagreements $dis"
done
