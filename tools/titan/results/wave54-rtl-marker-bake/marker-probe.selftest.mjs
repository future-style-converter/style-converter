#!/usr/bin/env node
// tools/titan/results/wave54-rtl-marker-bake/marker-probe.selftest.mjs — proves window [W1]'s half-A acceptance
// (marker-probe-checks.mjs) can PASS and can FAIL, offline (no Chromium): the static extraction of counter-suffix
// (tools/wpt source, gitignored mirror) is baked IN-PROCESS with a recorded walk (the wave53-final wire geometry) and
// the CDP-measured marker facts (wave53-lists-bakes/marker-probe.out.txt), then checked three ways:
//   1. as the tree plans it (P + M′)           → expect ALL PASS;
//   2. the wave-53 shape (runs owned by the li) → expect FAIL (root-owned runs / one child);
//   3. M′ without P (the root keeps its padding) → expect FAIL (padding: 0 alone).
// Usage: node marker-probe.selftest.mjs ; exit 0 iff all three expectations hold.
import { join, resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';
const HERE = dirname(fileURLToPath(import.meta.url));
const T = resolve(HERE, '..', '..');
const { extractFixture } = await import(join(T, 'extract-fixture.mjs'));
const { planBidiBake, applyBidiBakePlan } = await import(join(T, 'bidi-bake.mjs'));
const { checkBakedCounterSuffix } = await import(join(HERE, 'marker-probe-checks.mjs'));

// The recorded walk: counter-suffix's RTL half (roots W160 H48 at bake y192/240; li Left 48 Top 0/24; text runs).
const STYLE = { color: 'rgb(0, 0, 0)', fontFamily: 'Inter, -apple-system, "system-ui", "Segoe UI", Roboto, Oxygen, Ubuntu, sans-serif',
  fontSize: '16px', fontStyle: 'normal', fontWeight: '400', letterSpacing: 'normal', wordSpacing: '0px', textTransform: 'none' };
const el = (path, o) => ({ path, tag: o.tag ?? 'div', rect: o.rect, rectCount: 1, borderLeft: 0, borderTop: 0, display: o.display ?? 'block',
  position: 'static', decoration: 'none', texts: o.texts ?? [], bidiAffected: o.bidiAffected ?? false, ...(o.extra ?? {}) });
const word = (text, x, y, adv) => { let at = x; return [{ style: STYLE, chars: [...text].map((c, i) => {
  const r = { x: +at.toFixed(2), y, w: adv[i], h: 20 }; at += adv[i]; return { c, rects: [r] }; }) }]; };
const list = (k, y0) => [
  el([0, k], { tag: 'ol', rect: { x: 0, y: y0, width: 160, height: 48 }, bidiAffected: true,
    extra: { padding: [0, 48, 0, 48], backgroundClip: 'border-box', backgroundOrigin: 'padding-box', overflow: ['visible', 'visible'] } }),
  el([0, k, 0], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: y0, width: 64, height: 24 }, texts: word('foo', 87.36, y0 + 2, [5.4, 9.62, 9.62]) }),
  el([0, k, 1], { tag: 'li', display: 'list-item', bidiAffected: true, rect: { x: 48, y: y0 + 24, width: 64, height: 24 }, texts: word('bar', 87.03, y0 + 26, [9.66, 8.72, 6.59]) }),
];
const facts = (type, text, boxW, y, glyphs) => ({ direction: 'rtl', position: 'outside', type, image: false, contentStart: 48, contentEnd: 112,
  style: { ...STYLE, fontVariantNumeric: 'tabular-nums' }, text, textModelled: false, cdpBox: { x: 112, y, width: boxW, height: 24 },
  glyphs: { width: +boxW.toFixed(2), chars: glyphs.map(([c, x, w]) => ({ c, rects: [{ x, y: 0, w, h: 20 }] })) } });
const markers = {
  '0.4.0': facts('decimal', '1. ', 18.96875, 192, [['1', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.4.1': facts('decimal', '2. ', 18.96875, 216, [['2', 8.59, 10.38], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.5.0': facts('hebrew', 'א. ', 18.921875, 240, [['א', 8.6, 10.32], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
  '0.5.1': facts('hebrew', 'ב. ', 17.609375, 264, [['ב', 8.6, 9.01], ['.', 4.3, 4.3], [' ', 0, 4.3]]),
};
const walk = { bodyTextIsBidi: false, markers, elements: [el([0], { rect: { x: 0, y: 0, width: 160, height: 288 } }), ...list(4, 192), ...list(5, 240)] };

const rel = 'css/css-counter-styles/counter-suffix.html';
// Bake one variant of the plan into a fresh static extraction and count the check failures.
const attempt = async (label, edit) => {
  const { plan } = planBidiBake(walk);
  edit(plan);
  const { fixture } = await extractFixture(rel);
  applyBidiBakePlan(fixture, 'counter-suffix', plan);
  const fails = [];
  checkBakedCounterSuffix(fixture, { status: 'baked', roots: plan.roots.length, runs: plan.runs.length }, (ok, what) => { if (!ok) fails.push(what); });
  console.log(`${label}: ${fails.length} FAIL${fails.length ? ` — ${fails.slice(0, 3).join(' | ')}` : ''}`);
  return fails.length;
};
const asPlanned = await attempt('1 as planned (P + M′)', () => {});
// Wave-53 shape: each root-owned marker run handed back to the item whose row it shares.
const liOwned = await attempt('2 wave-53 shape (runs owned by the li)', (plan) => {
  for (const r of plan.runs) if (r.ownerPath.length === 2) r.ownerPath = [...r.ownerPath, parseFloat(r.props.top) > 20 ? 1 : 0];
});
const noP = await attempt('3 M′ without P (root padding kept)', (plan) => { for (const r of plan.roots) delete r.props.padding; });
const ok = asPlanned === 0 && liOwned > 0 && noP > 0;
console.log(ok ? 'SELF-TEST HOLDS: the [W1] checks pass on the planned shape and fail on both wrong shapes' : 'SELF-TEST FAILED');
process.exit(ok ? 0 : 1);
