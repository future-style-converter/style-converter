#!/usr/bin/env bash
# Skeptic (wave 54, L3) — independent U3/U3b FULL-BYTES differential: HEAD's tools/titan/*.mjs vs + seam-3 vs + seam-3 + seam-3b,
# every test of wave53-final's tests.list. Unlike the lane's br-only listing, each fixture AND refFixture is compared after
# masking every line-break component's height: any residual difference is a NON-br change (a radius leak).
# Usage: sk-u3-fullbytes.sh <scratch-dir>
set -euo pipefail
S="$1"; R="$(cd "$(dirname "$0")/../../../../.." && pwd)"; H="$R/tools/titan/results/wave54-hyphenate-character"
for v in base u3 u3b; do
  rm -rf "$S/$v"; mkdir -p "$S/$v/tools/titan"; ln -s "$R/tools/wpt" "$S/$v/tools/wpt"
  for f in $(git -C "$R" ls-tree --name-only HEAD tools/titan/ | grep -E '\.(mjs|json)$'); do git -C "$R" show "HEAD:$f" > "$S/$v/$f"; done
  ln -s "$R/tools/titan/wpt-buckets.json" "$S/$v/tools/titan/wpt-buckets.json"
done
(cd "$S/u3" && git apply --include='tools/titan/extract-fixture.mjs' "$H/seam-3.patch")
(cd "$S/u3b" && git apply --include='tools/titan/extract-fixture.mjs' "$H/seam-3.patch" && git apply "$H/seam-3b.patch")
cat "$R"/tools/titan/runs/wave53-final/sections/*/tests.list > "$S/all.txt"
cat > "$S/sk.mjs" <<JS
import fs from 'node:fs';
const tests = fs.readFileSync('$S/all.txt','utf8').split('\n').filter(Boolean);
const V = {}; for (const v of ['base','u3','u3b']) V[v] = await import('$S/'+v+'/tools/titan/extract-fixture.mjs');
const mask = (x) => JSON.stringify(x, function (k, val) { if (val && typeof val === 'object' && val._role === 'line-break') { const c = { ...val, properties: { ...val.properties, height: 'MASK' } }; return c; } return val; });
const brs = (x) => { const o = {}; const w = (m) => { for (const [id, c] of Object.entries(m ?? {})) { if (c._role === 'line-break') o[id] = c.properties?.height; w(c.children); } }; w(x?.components); return o; };
const out = { tests: tests.length, err: 0, u3: { docs: [], brs: 0, nonBr: [], ref: [], refNonBr: [] }, u3b: { docs: [], brs: 0, nonBr: [], ref: [], refNonBr: [] } };
const L = console.log; console.log = () => {}; console.warn = () => {};
for (const t of tests) {
  const r = {}; for (const v of ['base','u3','u3b']) { try { r[v] = await V[v].extractFixture(t); } catch (e) { r[v] = null; } }
  if (!r.base || !r.u3 || !r.u3b) { out.err++; continue; }
  for (const [k, a, b] of [['u3', r.base, r.u3], ['u3b', r.u3, r.u3b]]) {
    if (JSON.stringify(a.fixture) !== JSON.stringify(b.fixture)) {
      const A = brs(a.fixture), B = brs(b.fixture); const moved = Object.keys(B).filter((id) => A[id] !== B[id]);
      out[k].docs.push(t + ' ' + moved.length + ' ' + [...new Set(moved.map((id) => A[id] + '->' + B[id]))].join(','));
      out[k].brs += moved.length;
      if (mask(a.fixture) !== mask(b.fixture)) out[k].nonBr.push(t);
    }
    if (JSON.stringify(a.refFixture) !== JSON.stringify(b.refFixture)) { out[k].ref.push(t); if (mask(a.refFixture) !== mask(b.refFixture)) out[k].refNonBr.push(t); }
  }
}
console.log = L;
for (const k of ['u3','u3b']) { const o = out[k]; console.log(k + ': fixtures changed ' + o.docs.length + ', brs ' + o.brs + ', NON-BR fixture changes ' + o.nonBr.length + ', refFixtures changed ' + o.ref.length + ' (non-br ' + o.refNonBr.length + ')'); o.docs.forEach((d) => console.log('   ' + d)); o.nonBr.forEach((d) => console.log('   NONBR ' + d)); o.refNonBr.forEach((d) => console.log('   REF-NONBR ' + d)); }
console.log('tests ' + out.tests + ' extraction errors ' + out.err);
JS
nice -n 19 node "$S/sk.mjs"
