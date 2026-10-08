#!/usr/bin/env node
// tools/titan/results/wave54-rtl-marker-bake/w2-check.mjs — reads a w2-converter-hop.sh out dir and classifies every
// per-test IR document against <base>'s (default wave54-open): IDENTICAL (bytes), RENUMBERED (equal once every
// converter `-<n>` id suffix is stripped), CONTENT-CHANGED. Expected (expectations.json lanes["L1-rtl-marker-bake"]):
// content-changed ⊆ the lane's wire carriers; renumbered = the documents after counter-suffix in css-counter-styles
// (wireShadow, 15, only when the WHOLE section ran); the three zero-padding selectors documents identical.
// For counter-suffix it then checks the unit-M′ + unit-P wire component by component (rtl-marker-bake.md §4):
//   - 29 components (23 at base); every base component kept, its only changes = the RTL roots' Padding* → {px:0} and
//     the 4 RTL items' ListStyleType NONE / meta.markerText removed;
//   - 6 new components owned by (slot.parent) the two `<ol>` roots at Left 116.3 / 120.59 (±0.05), Top 2 / 26,
//     FontVariantNumeric TABULAR_NUMS, Direction RTL on the Hebrew runs; every RTL item keeps exactly ONE child.
// For bidi-lines-001/-002 and anchor-center-safe-rtl: the only change is Padding* → {px:0} on the bake roots.
// Usage: node w2-check.mjs <out-dir> <section> [base]  → exit 0 iff every expectation holds.
import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const ROOT = join(HERE, '..', '..', '..', '..');
const [OUT, SECTION, BASE = 'wave54-open'] = process.argv.slice(2);
const exp = JSON.parse(readFileSync(join(ROOT, 'tools/titan/results/wave54-plan/expectations.json'), 'utf8')).lanes['L1-rtl-marker-bake'];
const baseDir = join(ROOT, 'tools/titan/runs', BASE, 'sections', SECTION, 'per-test-ir');
const outDir = join(ROOT, OUT, 'per-test-ir');
let bad = 0;
const say = (ok, what) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${what}`); if (!ok) bad++; };
// Strip the converter's global `-<n>` counter from every id / parent reference.
const norm = (s) => s.replace(/("(?:id|parent)":\s*"[^"]*?)-\d+"/g, '$1"');
const docs = readdirSync(outDir).filter((f) => f.endsWith('.json')).sort();
const classes = { identical: [], renumbered: [], changed: [] };
for (const f of docs) {
  const a = existsSync(join(baseDir, f)) ? readFileSync(join(baseDir, f), 'utf8') : null;
  const b = readFileSync(join(outDir, f), 'utf8');
  const stem = f.replace(/\.json$/, '');
  if (a === b) classes.identical.push(stem);
  else if (a !== null && norm(a) === norm(b)) classes.renumbered.push(stem);
  else classes.changed.push(stem);
}
console.log(`documents ${docs.length}: identical ${classes.identical.length} · renumbered ${classes.renumbered.length} · content-changed ${classes.changed.length}`);
// A SUBSET run (fewer tests than the base section) restarts the converter's id counter, so an untouched document can
// only be equal MODULO the counter there; the whole-section run compares bytes.
const listOf = (d) => (existsSync(d) ? readFileSync(d, 'utf8').trim().split('\n') : []);
const full = JSON.stringify(listOf(join(ROOT, OUT, 'tests.list'))) === JSON.stringify(listOf(join(ROOT, 'tools/titan/runs', BASE, 'sections', SECTION, 'tests.list')));
if (!full) console.log('(subset run: an untouched document is compared modulo the converter id counter)');
const same = (s) => classes.identical.includes(s) || (!full && classes.renumbered.includes(s));
const carriers = new Set(exp.wireCarriers);
say(classes.changed.every((s) => carriers.has(s)), `content-changed ⊆ L1 wire carriers: ${JSON.stringify(classes.changed)}`);
for (const s of exp.carrierRule.zeroPaddingBakeRootsMustStayByteIdentical) {
  if (docs.includes(`${s}.json`)) say(same(s), `${s} ${full ? 'byte-identical' : 'identical modulo ids'} (zero-padding roots, pin V3b)`);
}
if (SECTION === 'css-counter-styles' && docs.length > 40) {
  say(classes.renumbered.length === exp.revertUnits.Mprime.wireShadow['css-counter-styles'].documents,
    `renumbered = ${exp.revertUnits.Mprime.wireShadow['css-counter-styles'].documents} (got ${classes.renumbered.length}: ${classes.renumbered.map((s) => s.split('__').pop()).join(', ')})`);
}
// Component-level reading of one carrier: base vs out, ids normalised.
const comps = (txt) => new Map(JSON.parse(norm(txt)).components.map((c) => [c.id, c]));
const props = (c) => Object.fromEntries((c?.properties ?? []).map((p) => [p.type, JSON.stringify(p.data)]));
const diffProps = (a, b) => [...new Set([...Object.keys(props(a)), ...Object.keys(props(b))])].filter((k) => props(a)[k] !== props(b)[k]);
const ZERO = JSON.stringify({ px: 0 });
const readPair = (stem) => [comps(readFileSync(join(baseDir, `${stem}.json`), 'utf8')), comps(readFileSync(join(outDir, `${stem}.json`), 'utf8'))];
for (const stem of ['wpt__css-text__bidi__bidi-lines-001', 'wpt__css-text__bidi__bidi-lines-002', 'wpt__css-anchor-position__anchor-center-safe-rtl']) {
  if (!docs.includes(`${stem}.json`)) continue;
  const [a, b] = readPair(stem);
  say(a.size === b.size, `${stem}: ${a.size} → ${b.size} components (no component added)`);
  for (const [id, c] of a) {
    const d = diffProps(c, b.get(id)), meta = JSON.stringify(c.meta) === JSON.stringify(b.get(id)?.meta);
    if (d.length || !meta) say(d.every((k) => /^Padding/.test(k) && props(b.get(id))[k] === ZERO) && meta, `${stem} ${id}: changed ${JSON.stringify(d)} → all Padding* {px:0}`);
  }
}
const CS = 'wpt__css-counter-styles__counter-suffix';
if (docs.includes(`${CS}.json`)) {
  const [a, b] = readPair(CS);
  say(a.size === 23 && b.size === 29, `counter-suffix components ${a.size} → ${b.size} (expect 23 → 29)`);
  const roots = ['counter-suffix__0__4', 'counter-suffix__0__5'], items = ['__0', '__1'].flatMap((i) => roots.map((r) => r + i));
  for (const [id, c] of a) {
    const d = diffProps(c, b.get(id)), n = b.get(id);
    if (!n) { say(false, `${id} missing after the bake`); continue; }
    // A root's Top/Bottom were already {px:0} (`padding: 0 3em`): only the non-zero sides change, all to {px:0}.
    if (roots.includes(id)) say(d.length > 0 && d.every((k) => /^Padding/.test(k) && props(n)[k] === ZERO), `${id} (root): only Padding* → {px:0} (${JSON.stringify(d)})`);
    else if (items.includes(id)) say(JSON.stringify(d) === '["ListStyleType"]' && /^"none"$/i.test(props(n).ListStyleType ?? '') && n.meta?.markerText === undefined
      && JSON.stringify({ ...c.meta, markerText: undefined }) === JSON.stringify({ ...n.meta, markerText: undefined }), `${id} (item): + ListStyleType NONE, − meta.markerText (${JSON.stringify(d)}, meta ${JSON.stringify(n.meta)})`);
    else say(d.length === 0 && JSON.stringify(c.meta) === JSON.stringify(n.meta) && JSON.stringify(c.slot) === JSON.stringify(n.slot), `${id}: unchanged`);
  }
  const added = [...b.keys()].filter((id) => !a.has(id));
  const want = [['counter-suffix__0__4', '.', 116.3, 2], ['counter-suffix__0__4', '1', 120.59, 2], ['counter-suffix__0__4', '.', 116.3, 26],
    ['counter-suffix__0__4', '2', 120.59, 26], ['counter-suffix__0__5', 'א.', 116.3, 2], ['counter-suffix__0__5', 'ב.', 116.3, 26]];
  say(added.length === 6, `6 added components: ${JSON.stringify(added)}`);
  const px = (c, k) => JSON.parse(props(c)[k] ?? 'null')?.px;
  added.forEach((id, i) => {
    const c = b.get(id), [root, , left, top] = want[i] ?? [];
    say(c.slot?.parent?.endsWith(root ?? '?') && Math.abs(px(c, 'Left') - left) <= 0.05 && px(c, 'Top') === top
      && (props(c).FontVariantNumeric ?? '').includes('TABULAR_NUMS') && (i < 4 || props(c).Direction === '"RTL"'),
      `${id}: parent ${c.slot?.parent} Left ${px(c, 'Left')} Top ${px(c, 'Top')} FVN ${props(c).FontVariantNumeric} Dir ${props(c).Direction} (want ${root} ${left} ${top})`);
  });
  for (const it of items) say([...b.values()].filter((c) => c.slot?.parent === it).length === 1, `${it} keeps exactly ONE child`);
}
console.log(bad ? `W2 CHECK: ${bad} FAIL` : 'W2 CHECK: ALL PASS');
process.exit(bad ? 1 : 0);
