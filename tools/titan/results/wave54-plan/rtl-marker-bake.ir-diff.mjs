#!/usr/bin/env node
// tools/titan/results/wave54-plan/rtl-marker-bake.ir-diff.mjs
// What the reverted L1 U2 (hunks M + P, a8ffd1c6) changed on the wire, read from the two frozen gate runs that bracket
// it: wave53-open (before) vs wave53-probe (U2 in the tree). Component ids are compared with the per-section id counter
// stripped (the "-NNN" suffix), so an id shadow does not read as a change. Read-only; no images.
// Usage: node tools/titan/results/wave54-plan/rtl-marker-bake.ir-diff.mjs [before=wave53-open] [after=wave53-probe]
import { readFileSync } from 'node:fs';
import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
const [before = 'wave53-open', after = 'wave53-probe'] = process.argv.slice(2);
// The four documents the U2 census named as content carriers.
const DOCS = [['css-text', 'wpt__css-text__bidi__bidi-lines-001'], ['css-text', 'wpt__css-text__bidi__bidi-lines-002'],
  ['css-anchor-position', 'wpt__css-anchor-position__anchor-center-safe-rtl'], ['css-counter-styles', 'wpt__css-counter-styles__counter-suffix']];
const load = (run, s, t) => JSON.parse(readFileSync(`${ROOT}/tools/titan/runs/${run}/sections/${s}/per-test-ir/${t}.json`, 'utf8'));
const strip = (id) => id.replace(/-\d+$/, '');
for (const [s, t] of DOCS) {
  const a = load(before, s, t), b = load(after, s, t);
  const ma = new Map(a.components.map((c) => [strip(c.id), c])), mb = new Map(b.components.map((c) => [strip(c.id), c]));
  console.log(`${t}: ${a.components.length} → ${b.components.length} components`);
  for (const [k, c] of mb) {
    const o = ma.get(k);
    if (!o) { console.log(`   ADDED   ${k} text=${JSON.stringify(c.text ?? null)} parent=${strip(c.slot?.parent ?? '')}`); continue; }
    const od = new Map(o.properties.map((p) => [p.type, JSON.stringify(p.data)]));
    const ch = c.properties.filter((p) => od.get(p.type) !== JSON.stringify(p.data)).map((p) => `${p.type}:${od.get(p.type) ?? '∅'}→${JSON.stringify(p.data)}`);
    if (ch.length) console.log(`   CHANGED ${k} ${ch.join(' ')}`);
    if (JSON.stringify(o.meta) !== JSON.stringify(c.meta)) console.log(`   META    ${k} ${JSON.stringify(o.meta)}→${JSON.stringify(c.meta)}`);
  }
  for (const k of ma.keys()) if (!mb.has(k)) console.log(`   REMOVED ${k}`);
}
