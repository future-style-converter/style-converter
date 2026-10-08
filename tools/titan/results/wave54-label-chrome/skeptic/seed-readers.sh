#!/usr/bin/env bash
# skeptic/seed-readers.sh — wave 54 L7 SKEPTIC: U2-seed's radius INSIDE the tooling suite. Four tooling test files
# other than the tripwire enumerate tools/visual/baseline/ (column-presence-e2e pickTrio = first 5 sorted trios;
# cross-platform-gate-e2e = first Android__ / first other iOS__ file as its "agree"/"diverge" sources;
# png-color-space = every committed PNG's colour chunk; pad-canvas). Run them focused on a HEAD mirror
# (git archive HEAD -- tools/visual fixtures) without and with the 18 seeded 77fe41e8 PNGs (+ the U1 files).
#   bash seed-readers.sh
set -uo pipefail
ROOT="$(cd "$(dirname "$0")/../../../../.." && pwd)"
TESTS="tools/visual/column-presence-e2e.test.mjs tools/visual/cross-platform-gate-e2e.test.mjs tools/visual/png-color-space.test.mjs tools/visual/pad-canvas.test.mjs"
for MODE in head u1-seed; do
  M="$(mktemp -d "${TMPDIR:-/tmp}/l7-skeptic-readers.XXXXXX")"
  (cd "$ROOT" && git archive HEAD -- tools/visual fixtures) | tar -x -C "$M"
  ln -s "$ROOT/node_modules" "$M/node_modules"
  if [[ "$MODE" == u1-seed ]]; then
    cp "$ROOT"/tools/visual/{label-chrome-tripwire.test.mjs,label-chrome-check.mjs,label-chrome-exempt.json} "$M/tools/visual/"
    cp "$ROOT"/tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8/*.png "$M/tools/visual/baseline/"
  fi
  echo "######## $MODE PNGs=$(ls "$M/tools/visual/baseline" | grep -c '\.png$')"
  (cd "$M" && node --test $TESTS) > "$M/run.txt" 2>&1; rc=$?
  grep -E '^# (tests|pass|fail) |^not ok' "$M/run.txt"
  echo "exit=$rc"
  rm -rf "$M"
done
