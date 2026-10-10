#!/usr/bin/env node
// tools/titan/results/wave54-rtl-marker-bake/w2-check.selftest.mjs — proves w2-check.mjs can PASS (it is proven able to
// FAIL by w2-check.selftest-wave53-probe.out.txt: the wave-53 li-owned shape → 10 ownership FAILs). Synthesises the
// PREDICTED unit-M′ counter-suffix document from wave53-probe's REAL converter output (same marker runs, same
// props) by re-parenting each marker run to its `<ol>` root (Left += item Left 48, Top += item Top 0/24, id
// `<root>__<2+k>`, placed after the root's subtree), writes a one-document out dir under the gitignored runs/ tree and
// runs the checker on it. This checks the CHECKER; it says nothing about what the converter will emit (that is [W2]).
import { readFileSync, writeFileSync, mkdirSync, rmSync } from 'node:fs';
import { execFileSync } from 'node:child_process';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const F = 'wpt__css-counter-styles__counter-suffix.json';
const probe = JSON.parse(readFileSync(join(ROOT, 'tools/titan/runs/wave53-probe/sections/css-counter-styles/per-test-ir', F), 'utf8'));
const base = (id) => id.replace(/-\d+$/, '');
const px = (c, k) => c.properties.find((p) => p.type === k)?.data?.px ?? 0;
const byId = new Map(probe.components.map((c) => [base(c.id), c]));
const out = [], moved = new Map();
for (const c of probe.components) {
  const id = base(c.id), parent = base(c.slot?.parent ?? '');
  // A marker run = a second-or-later child of an RTL item (`__0__4__0__1` …).
  const m = /^(counter-suffix__0__[45])__([01])__([1-9])$/.exec(id);
  if (!m) { out.push(c); continue; }
  const item = byId.get(`${m[1]}__${m[2]}`), root = m[1];
  const k = moved.get(root) ?? 0; moved.set(root, k + 1);
  const props = c.properties.map((p) => (p.type === 'Left' ? { ...p, data: { px: +(p.data.px + px(item, 'Left')).toFixed(2) } }
    : p.type === 'Top' ? { ...p, data: { px: p.data.px + px(item, 'Top') } } : p));
  out.push({ ...c, id: `${root}__${2 + k}-900${k}`, slot: { ...c.slot, parent: byId.get(root).id }, properties: props, __after: root });
}
// Root-owned runs sit after the root's subtree in the flat list (converter pre-order).
const flat = out.filter((c) => !c.__after);
for (const root of ['counter-suffix__0__5', 'counter-suffix__0__4']) {
  const runs = out.filter((c) => c.__after === root).map(({ __after, ...c }) => c);
  const last = flat.map((c) => base(c.id)).reduce((a, id, i) => (id.startsWith(root) ? i : a), -1);
  flat.splice(last + 1, 0, ...runs);
}
const dir = join(ROOT, 'tools/titan/runs/wave54-l1-w2-selftest');
rmSync(dir, { recursive: true, force: true }); mkdirSync(join(dir, 'per-test-ir'), { recursive: true });
writeFileSync(join(dir, 'per-test-ir', F), JSON.stringify({ ...probe, components: flat }, null, 2));
try {
  process.stdout.write(execFileSync('node', [join(HERE, 'w2-check.mjs'), 'tools/titan/runs/wave54-l1-w2-selftest', 'css-counter-styles', 'wave54-open'], { encoding: 'utf8' }));
  console.log('SELF-TEST HOLDS: the synthesised M′ document passes w2-check');
} catch (e) { process.stdout.write(e.stdout ?? ''); console.log('SELF-TEST FAILED'); process.exit(1); }
