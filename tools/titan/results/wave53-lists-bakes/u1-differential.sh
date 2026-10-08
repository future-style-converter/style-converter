#!/usr/bin/env bash
# tools/titan/results/wave53-lists-bakes/u1-differential.sh — the in-process extractor differential for Unit 1
# (PLAN §2 L1 "May run": pure node over the 1435 sources, NO gate flags — the gate-flag differential is an
# orchestrator window, see _note.md). BASE = HEAD's extract-fixture.mjs + counter-bake.mjs, copied to <scratch>
# with sibling imports made absolute; FIX = the live tree's modules (run it with seam-1.patch applied under the
# per-file lock, and the lane's counter-bake.mjs in place). Every corpus test of wave52-ship's tests.list is
# extracted by both, and the static fixture (+ ref fixture) JSON compared byte for byte.
# Usage: u1-differential.sh <scratch-dir>      → prints {tests, changed[], errors[]} JSON
set -euo pipefail
S="$1"; R="$(cd "$(dirname "$0")/../../../.." && pwd)"; T="$R/tools/titan"
mkdir -p "$S/base"
# HEAD copies; every './x.mjs' import → absolute tree path except the per-variant counter bake.
git -C "$R" show HEAD:tools/titan/extract-fixture.mjs \
  | sed -e "s#from '\./\([a-z0-9-]*\.mjs\)'#from '$T/\1'#g" -e "s#import('\./#import('$T/#g" \
        -e "s#^const REPO_ROOT  = resolve(__dirname, '..', '..');#const REPO_ROOT = '$R';#" \
        -e "s#from '$T/counter-bake.mjs'#from './counter-bake.mjs'#" > "$S/base/extract-fixture.mjs"
git -C "$R" show HEAD:tools/titan/counter-bake.mjs \
  | sed -e "s#from '\./\([a-z0-9-]*\.mjs\)'#from '$T/\1'#g" > "$S/base/counter-bake.mjs"
cat "$R"/tools/titan/runs/wave52-ship/sections/*/tests.list > "$S/all-tests.txt"
cat > "$S/diff.mjs" <<JS
import fs from 'node:fs';
const tests = fs.readFileSync('$S/all-tests.txt', 'utf8').split('\n').filter(Boolean);
const a = await import('$S/base/extract-fixture.mjs');
const b = await import('$T/extract-fixture.mjs');
const changed = [], errors = [];
for (const t of tests) {
  let x, y;
  try { const r = await a.extractFixture(t); x = JSON.stringify([r.fixture, r.refFixture]); } catch (e) { x = 'ERR ' + e.message; }
  try { const r = await b.extractFixture(t); y = JSON.stringify([r.fixture, r.refFixture]); } catch (e) { y = 'ERR ' + e.message; }
  if (x.startsWith('ERR') || y.startsWith('ERR')) errors.push({ t, base: x.slice(0, 80), fix: y.slice(0, 80) });
  if (x !== y) changed.push(t);
}
console.log(JSON.stringify({ tests: tests.length, changed, errors: errors.length, errorSample: errors.slice(0, 3) }, null, 1));
JS
nice -n 19 node "$S/diff.mjs" 2>/dev/null
