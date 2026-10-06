#!/usr/bin/env node
// wave-52 lane L5 — compare two differential sides (see differential.sh) and
// write a JSON report: which per-test FIXTURE files and which per-test IR
// documents differ, and for each changed IR document the property-level
// delta (component → property name → before/after), so every changed cell can
// be READ and LISTED rather than merely counted ("a lane's own census
// under-reporting its radius is the dangerous direction").
//
// Usage: differential-report.mjs <sideA-dir> <sideB-dir> <out.json>
import { promises as fs } from 'node:fs';
import { join, relative } from 'node:path';

const [A, B, OUT] = process.argv.slice(2);
if (!A || !B || !OUT) { console.error('usage: differential-report.mjs <sideA> <sideB> <out.json>'); process.exit(1); }

// Every regular file under `dir`, as paths relative to it.
async function walk(dir, rel = '') {
  const out = [];
  for (const e of await fs.readdir(join(dir, rel), { withFileTypes: true })) {
    const r = rel ? `${rel}/${e.name}` : e.name;
    if (e.isDirectory()) out.push(...await walk(dir, r));
    else if (e.isFile()) out.push(r);
  }
  return out.sort();
}

// Byte-compare the files of one subtree present on both sides.
async function diffTree(sub) {
  const fa = new Set(await walk(join(A, sub)));
  const fb = new Set(await walk(join(B, sub)));
  const onlyA = [...fa].filter((f) => !fb.has(f));
  const onlyB = [...fb].filter((f) => !fa.has(f));
  const changed = [];
  for (const f of fa) {
    if (!fb.has(f)) continue;
    const [ba, bb] = await Promise.all([fs.readFile(join(A, sub, f)), fs.readFile(join(B, sub, f))]);
    if (!ba.equals(bb)) changed.push(f);
  }
  return { compared: [...fa].filter((f) => fb.has(f)).length, onlyA, onlyB, changed };
}

// Property-level delta of one per-test IR document (flat v2 component list):
// keyed by component id, then by property type — records the JSON of the
// property's data before/after, or "(absent)" on one side.
function irDelta(docA, docB) {
  const byId = (doc) => new Map((doc.components ?? []).map((c) => [c.id ?? c.name, c]));
  const ma = byId(docA), mb = byId(docB);
  const out = {};
  for (const id of new Set([...ma.keys(), ...mb.keys()])) {
    const ca = ma.get(id), cb = mb.get(id);
    if (!ca || !cb) { out[id] = ca ? '(component removed)' : '(component added)'; continue; }
    const pa = new Map((ca.properties ?? []).map((p) => [p.type, JSON.stringify(p.data ?? p)]));
    const pb = new Map((cb.properties ?? []).map((p) => [p.type, JSON.stringify(p.data ?? p)]));
    const d = {};
    for (const t of new Set([...pa.keys(), ...pb.keys()])) {
      if (pa.get(t) !== pb.get(t)) d[t] = { before: pa.get(t) ?? '(absent)', after: pb.get(t) ?? '(absent)' };
    }
    // Non-property differences (text, meta, children) are reported by key.
    for (const k of new Set([...Object.keys(ca), ...Object.keys(cb)])) {
      if (k === 'properties') continue;
      const sa = JSON.stringify(ca[k]), sb = JSON.stringify(cb[k]);
      if (sa !== sb) d[`<${k}>`] = { before: sa ?? '(absent)', after: sb ?? '(absent)' };
    }
    if (Object.keys(d).length) out[id] = d;
  }
  return out;
}

const fixtures = await diffTree('fixtures/wpt');
const ir = await diffTree('per-test-ir');
const irDeltas = {};
for (const f of ir.changed) {
  const [da, db] = await Promise.all([
    fs.readFile(join(A, 'per-test-ir', f), 'utf8').then(JSON.parse),
    fs.readFile(join(B, 'per-test-ir', f), 'utf8').then(JSON.parse),
  ]);
  irDeltas[f] = irDelta(da, db);
}
const report = {
  sideA: A, sideB: B,
  fixtures: { compared: fixtures.compared, changed: fixtures.changed.length, onlyA: fixtures.onlyA, onlyB: fixtures.onlyB, changedFiles: fixtures.changed },
  perTestIr: { compared: ir.compared, changed: ir.changed.length, onlyA: ir.onlyA, onlyB: ir.onlyB, changedDocs: ir.changed, deltas: irDeltas },
};
await fs.writeFile(OUT, JSON.stringify(report, null, 1) + '\n');
console.log(`fixtures: ${fixtures.compared} compared, ${fixtures.changed.length} changed (+${fixtures.onlyB.length}/-${fixtures.onlyA.length}); ` +
            `per-test IR: ${ir.compared} compared, ${ir.changed.length} changed (+${ir.onlyB.length}/-${ir.onlyA.length}) → ${relative(process.cwd(), OUT)}`);
for (const f of ir.changed) console.log(`  IR changed: ${f}`);
