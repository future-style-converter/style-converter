#!/usr/bin/env node
// wave52-plan/percent-spacing-census.mjs — queue 0(b″): every percentage
// margin/padding carrier in the frozen wave51-fix per-test IR, with the
// containing block its PARENT publishes for it (element level — what
// ElementContainingBlock.containingBlockFor returns once the appliers are
// ported) and the one it publishes for its OWN children (child level — what
// MarginApplier.kt:93 / PaddingApplier.kt:58 read today), and the USED px each
// side resolves to under the WPT rule (SpacingResolve.percentBasePx: definite
// base → pct × base; indefinite + percentIndefiniteAsZero → 0).
// READ-ONLY over the run dir; writes percent-spacing-census.json beside itself.
//   node tools/titan/results/wave52-plan/percent-spacing-census.mjs
//   RUN_DIR=tools/titan/runs/<other> node …
// LIMITS: static transcription of DynamicValueResolver.childContainingBlock —
// calc()/var() sizes are not evaluated, `display: contents` re-parenting and
// the wave-33 absHeightPx side channel are not modelled; none differs on a
// carrier below (every carrier's ancestor chain is px-or-nothing).
import fs from 'node:fs';
import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const RUN = process.env.RUN_DIR ? path.resolve(process.env.RUN_DIR) : path.join(ROOT, 'tools/titan/runs/wave51-fix');
const SECTIONS = path.join(RUN, 'sections');
// Harness root channel: ScreenshotCaptureScreen.kt:1036 provides the 358dp
// canvas content width, height unknown.
const ROOT_CB = [Number(process.env.ROOT_W || 358), null];
const SPACING = /^(Margin|Padding)(Top|Right|Bottom|Left|BlockStart|BlockEnd|InlineStart|InlineEnd)$/;

function* documents() {
  for (const sec of fs.readdirSync(SECTIONS).sort()) {
    const dir = path.join(SECTIONS, sec, 'per-test-ir');
    if (!fs.existsSync(dir)) continue;
    for (const f of fs.readdirSync(dir).sort()) {
      if (!f.endsWith('.json')) continue;
      yield { sec, test: f.replace(/\.json$/, ''), doc: JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')) };
    }
  }
}
// DynamicValueResolver.childContainingBlock (DynamicValueResolver.kt:207-249),
// transcribed INCLUDING the percentage-of-known-parent leg (sizeOf). Padding /
// border bands are raw `px` reads only — a bare-number (percentage) padding is
// NOT subtracted, exactly as pxOf's `as? JsonObject` skips it.
const pxOf = (props, ...types) => {
  for (const t of types) { const d = (props || []).find(p => p.type === t)?.data; if (d && typeof d === 'object' && typeof d.px === 'number') return d.px; }
  return null;
};
const sizeOf = (props, base, ...types) => {
  const px = pxOf(props, ...types); if (px !== null) return px;
  for (const t of types) { const d = (props || []).find(p => p.type === t)?.data; if (d && typeof d === 'object' && d.type === 'percentage' && typeof d.value === 'number') return base === null ? null : d.value * base / 100; }
  return null;
};
const childCb = (props, parent) => {
  const w = sizeOf(props, parent[0], 'Width', 'InlineSize');
  const h = sizeOf(props, parent[1], 'Height', 'BlockSize');
  return [
    w === null ? null : w - (pxOf(props, 'PaddingLeft', 'PaddingInlineStart') ?? 0) - (pxOf(props, 'PaddingRight', 'PaddingInlineEnd') ?? 0) - (pxOf(props, 'BorderLeftWidth') ?? 0) - (pxOf(props, 'BorderRightWidth') ?? 0),
    h === null ? null : h - (pxOf(props, 'PaddingTop', 'PaddingBlockStart') ?? 0) - (pxOf(props, 'PaddingBottom', 'PaddingBlockEnd') ?? 0) - (pxOf(props, 'BorderTopWidth') ?? 0) - (pxOf(props, 'BorderBottomWidth') ?? 0),
  ];
};
// LengthValue.kt:132-136 / :209 — a BARE NUMBER is the PERCENT wire; the typed
// {type:"percentage"} shapes decode to the same Relative(PERCENT).
const pct = (d) => {
  if (typeof d === 'number') return d;
  if (d && typeof d === 'object' && d.type === 'percentage') return typeof d.value === 'number' ? d.value : (typeof d.percentage === 'number' ? d.percentage : null);
  if (d && typeof d === 'object' && d.px == null && typeof d.percent === 'number') return d.percent;
  return null;
};
// The element's OWN containing block: what its slot.parent publishes for it,
// threaded top-down from the harness root (ComponentRenderer.kt:1886 → :2004).
function elementCbOf(c, byId, memo) {
  if (memo.has(c.id)) return memo.get(c.id);
  const parent = c.slot?.parent ? byId.get(c.slot.parent) : null;
  const cb = parent ? childCb(parent.properties, elementCbOf(parent, byId, memo)) : ROOT_CB;
  memo.set(c.id, cb); return cb;
}
const verdicts = (sec, test) => {
  const mp = path.join(SECTIONS, sec, 'manifest.json'); if (!fs.existsSync(mp)) return {};
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  const key = Object.keys(m.wpt.results).find(k => 'wpt__' + k.replace(/^css\//, '').replace(/\.html$/, '').replace(/\//g, '__') === test);
  const d = key ? (m.wpt.results[key].browserRef?.diffs || {}) : {};
  const out = {}; for (const p of ['web', 'ios', 'android']) { const x = d[p + '-ref']; if (x) out[p] = { ssim: x.ssim, pass: !!x.wptPass, cF: !!x.colorFailed, nF: !!x.novelInkFailed, ex: !!x.scoreExcluded }; }
  return out;
};
const shapes = {}; const rows = []; let docs = 0;
for (const { sec, test, doc } of documents()) {
  docs++;
  const comps = doc.components || []; const byId = new Map(comps.map(c => [c.id, c])); const memo = new Map();
  for (const c of comps) for (const p of c.properties || []) {
    if (!/^(Margin|Padding)/.test(p.type)) continue;
    const shape = typeof p.data === 'number' ? 'bare-number' : (p.data && typeof p.data === 'object' ? (p.data.type || (p.data.px != null ? 'px' : Object.keys(p.data).join(','))) : typeof p.data);
    shapes[p.type + ':' + shape] = (shapes[p.type + ':' + shape] || 0) + 1;
  }
  for (const c of comps) {
    const carriers = (c.properties || []).filter(p => SPACING.test(p.type) && pct(p.data) !== null);
    if (!carriers.length) continue;
    const elem = elementCbOf(c, byId, memo); const child = childCb(c.properties, elem);
    // CSS 2.1 §8.3 / §8.4: every side's percentage refers to the containing
    // block's WIDTH; SpacingResolve.percentBasePx applies the P10/P11 rule.
    const used = (v, cb) => cb[0] === null ? 0 : v * cb[0] / 100;
    const sides = carriers.map(p => { const v = pct(p.data); const e = used(v, elem), k = used(v, child); return { type: p.type, pct: v, elementPx: e, childPx: k, moves: e !== k }; });
    rows.push({ sec, test, id: c.id, elementCb: elem, childCb: child, cbDiffer: JSON.stringify(elem) !== JSON.stringify(child), sides, moves: sides.some(s => s.moves), paints: (c.properties || []).some(p => /^(Background|Border|Outline)/.test(p.type)), hasChildren: comps.some(k => k.slot?.parent === c.id), verdicts: verdicts(sec, test) });
  }
}
const summary = { docs, carriers: rows.length, tests: [...new Set(rows.map(r => r.sec + '/' + r.test))].length, cbDiffer: rows.filter(r => r.cbDiffer).length, usedDiffer: rows.filter(r => r.moves).length };
fs.writeFileSync(path.join(HERE, 'percent-spacing-census.json'), JSON.stringify({ run: RUN, rootCb: ROOT_CB, summary, shapes, rows }, null, 1));
console.log(JSON.stringify(summary));
console.log('SHAPES', JSON.stringify(shapes));
for (const r of rows) console.log(`${r.moves ? '≠' : ' '} ${r.sec}/${r.test}\n    ${r.id} paints=${r.paints} children=${r.hasChildren} elem=${JSON.stringify(r.elementCb)} child=${JSON.stringify(r.childCb)}` + r.sides.map(s => `\n    ${s.type}=${s.pct}% element→${s.elementPx}px child→${s.childPx}px${s.moves ? ' ← MOVES' : ''}`).join('') + `\n    ${Object.entries(r.verdicts).map(([p, v]) => `${p}:${v.pass ? 'P' : 'f'}/${v.ssim}${v.cF ? ' cF' : ''}${v.nF ? ' nF' : ''}${v.ex ? ' EX' : ''}`).join('  ')}`);
