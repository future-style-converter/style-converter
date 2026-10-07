#!/usr/bin/env bash
# Wave-53 brief nested-list-extractor — the differential behind the census:
# extract every corpus test with the UNMODIFIED extractor + counter bake and
# with scratch copies carrying (a) the nested-list implied-close scope and
# (b) the css-lists-3 §4.4.2 step-4 term, then compare the fixtures.
# Usage: nested-list-extractor.differential.sh <scratch-dir> [base|fix|extonly|bakeonly vs base]
# Writes ONLY under <scratch-dir>; reads the repo. Pure node, no browser.
set -euo pipefail
S="$1"; V="${2:-fix}"
R="$(cd "$(dirname "$0")/../../../.." && pwd)"; T="$R/tools/titan"; P="$(cd "$(dirname "$0")" && pwd)"
mkdir -p "$S"/{base,fix,extonly,bakeonly}
for v in base fix extonly bakeonly; do
  # Absolute imports for every sibling module except the counter bake, which is per-variant.
  sed -e "s#from '\./\([a-z-]*\.mjs\)'#from '$T/\1'#g" -e "s#import('\./#import('$T/#g" \
      -e "s#^const REPO_ROOT  = resolve(__dirname, '..', '..');#const REPO_ROOT = '$R';#" \
      -e "s#from '$T/counter-bake.mjs'#from './counter-bake.mjs'#" "$T/extract-fixture.mjs" > "$S/$v/extract-fixture.mjs"
  sed -e "s#from '\./\([a-z-]*\.mjs\)'#from '$T/\1'#g" "$T/counter-bake.mjs" > "$S/$v/counter-bake.mjs"
done
python3 "$P/nested-list-extractor.patch-extractor.py" "$S/fix/extract-fixture.mjs"
python3 "$P/nested-list-extractor.patch-extractor.py" "$S/extonly/extract-fixture.mjs"
python3 "$P/nested-list-extractor.patch-bake.py" "$S/fix/counter-bake.mjs"
python3 "$P/nested-list-extractor.patch-bake.py" "$S/bakeonly/counter-bake.mjs"
cat "$R"/tools/titan/runs/wave52-ship/sections/*/tests.list > "$S/all-tests.txt"
cat > "$S/diff.mjs" <<JS
import fs from 'node:fs';
const tests = fs.readFileSync('$S/all-tests.txt', 'utf8').split('\n').filter(Boolean);
const a = await import('$S/base/extract-fixture.mjs');
const b = await import('$S/$V/extract-fixture.mjs');
const diff = [];
for (const t of tests) {
  const x = JSON.stringify((await a.extractFixture(t)).fixture);
  const y = JSON.stringify((await b.extractFixture(t)).fixture);
  if (x !== y) diff.push(t);
}
console.log(JSON.stringify({ variant: '$V', tests: tests.length, changed: diff }, null, 1));
JS
nice -n 19 node "$S/diff.mjs" 2>/dev/null
