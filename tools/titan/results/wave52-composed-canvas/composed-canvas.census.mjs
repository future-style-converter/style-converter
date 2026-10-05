#!/usr/bin/env node
// wave-52 lane L2 (composed-canvas) — read-only corpus census over the 1435
// wave51-fix per-test IR docs. Re-derives every L2 blast radius from the IR
// itself (never from a brief's numbers) and joins each carrier to its three
// wave51-fix verdicts. Usage: node composed-canvas.census.mjs [> json]
// Writes composed-canvas.census.json beside itself and prints a summary.
//
// Mechanisms (PLAN.md §2 L2): M1 body margin · T1 RC1 root keeps own margin
// (no join) · T2 empty in-flow root collapses through · T3 RC1 root before an
// in-flow root (zIndex; fix pass: which ones the lift rule keeps) · T6 hoisted UA-tag root · Fix A frame ink.
// T1/T2/M1 are replayed through a JS port of the natives' root-stack fold
// (UaBlockMargins.kt collapsedRootStackGapsPx) on OLD and NEW plans; a root
// "moves" when its slot (or RC1 ink) offset from the canvas content top
// changes. Heights cancel (no plan changes a box height), so the delta is exact
// up to the port's simplifications: em leaves resolve at 16 px (flagged), hoist
// bands and the R4/R5 bail flavours are not modelled (flagged per doc).
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));            // results dir
const ROOT = path.resolve(HERE, '..', '..', '..', '..');              // repo root
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');  // gate of record
const PLAN = path.join(ROOT, 'tools/titan/results/wave52-plan');      // plan artifacts
const PLATS = ['web', 'ios', 'android'];

// --- wave51-fix verdicts: per-test-ir stem -> {key, section, web|ios|android: "P 0.99"} ---
const verdicts = new Map();
for (const sec of fs.readdirSync(RUN).sort()) {
  const mf = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(mf)) continue;                                   // a section without a manifest
  const res = JSON.parse(fs.readFileSync(mf, 'utf8')).wpt?.results ?? {};
  for (const [key, r] of Object.entries(res)) {
    const stem = (r.components?.[0] ?? '').replace(/__\d+$/, '');     // component name minus its index
    const row = { key, section: sec };
    for (const p of PLATS) {
      const d = r.browserRef?.diffs?.[`${p}-ref`];
      // Scored exactly as score-gate.mjs isScored: numeric ssim, no exclusion stamp.
      row[p] = d && typeof d.ssim === 'number' && !d.scoreExcluded ? `${d.wptPass ? 'P' : 'f'} ${d.ssim}` : '-';
    }
    if (stem) verdicts.set(stem, row);
  }
}

// --- IR readers (the same leaves the three resolvers read) ---
const prop = (c, t) => (c.properties ?? []).find((p) => p.type === t)?.data;
const has = (c, t) => (c.properties ?? []).some((p) => p.type === t);
let emSeen = false;                                                   // flags an em approximation per doc
const px = (d) => {
  if (d == null) return null;
  if (typeof d.px === 'number') return d.px;                          // top-level {px:N}
  if (typeof d.original?.px === 'number') return d.original.px;       // typed wrapper {original:{px:N}}
  if (d.original?.u === 'EM' && typeof d.original.v === 'number') { emSeen = true; return d.original.v * 16; }
  return null;                                                        // auto / % / calc — runtime-dependent
};
const pos = (c) => String(prop(c, 'Position') ?? '').toUpperCase();
const INSETS = ['Top', 'Right', 'Bottom', 'Left', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd'];
// An inset that anchors: present and not `auto` (css-position-3 §3.1 all-auto = static position).
const anchors = (d) => d != null && !(typeof d === 'string' && d.toUpperCase() === 'AUTO') && d?.keyword !== 'auto';
const hasInset = (c) => INSETS.some((t) => anchors(prop(c, t)));
const isRc1 = (c) => pos(c) === 'ABSOLUTE' && !hasInset(c);           // RC1 static-position root
const isHoisted = (c) => pos(c) === 'FIXED' || (pos(c) === 'ABSOLUTE' && hasInset(c));
// T3 fix pass: the PAINT_LAYER_TYPES twin (presence alone makes a step-8+ layer).
const LAYER = new Set(['ZIndex', 'Opacity', 'Transform', 'Translate', 'Rotate', 'Scale', 'Perspective', 'TransformStyle', 'Filter',
  'BackdropFilter', 'ClipPath', 'MaskImage', 'MixBlendMode', 'Isolation', 'WillChange', 'Contain', 'ContainerType', 'ViewTransitionName']);
// The natives' UA block-margin table (UaBlockChildMargins.kt, 16-px basis).
const UA = { p: 16, h1: 21, h2: 19, h3: 16, h4: 21, h5: 27, h6: 37, ul: 16, ol: 16, blockquote: 16, pre: 16, figure: 16 };
const uaOf = (c) => UA[(c.meta?.sourceTag ?? '').toLowerCase()] ?? 0;
const declTop = (c) => px(prop(c, 'MarginTop') ?? prop(c, 'MarginBlockStart'));
const declBot = (c) => px(prop(c, 'MarginBottom') ?? prop(c, 'MarginBlockEnd'));
const edge = (c, d, ua) => (d != null ? d : ua);                      // author > UA per edge (css-cascade-4 §6.1)
// Kotlin isSelfCollapsingRoot, ported: in-flow, not body-root, no content/pseudo, block, h/minh 0, no block edges.
const ZERO_EDGES = ['PaddingTop', 'PaddingBottom', 'PaddingBlockStart', 'PaddingBlockEnd', 'BorderTopWidth', 'BorderBottomWidth', 'BorderBlockStartWidth', 'BorderBlockEndWidth'];
const zeroOrAbsent = (c, t) => !has(c, t) || px(prop(c, t)) === 0;
const isEmptyRoot = (c, kids) => !['ABSOLUTE', 'FIXED'].includes(pos(c)) && c.meta?.role !== 'body-root'
  && !c.text && !kids && !c.meta?.runs && !c.meta?.markerText && !c.pseudos
  && (!has(c, 'Display') || String(prop(c, 'Display')).toUpperCase() === 'BLOCK')
  && zeroOrAbsent(c, 'Height') && zeroOrAbsent(c, 'MinHeight') && ZERO_EDGES.every((t) => zeroOrAbsent(c, t));
// css-display-3 table-internal boxes: CSS 2.1 §8.3 — margins do not apply.
const TABLE_INTERNAL = new Set(['TABLE_CELL', 'TABLE_ROW', 'TABLE_ROW_GROUP', 'TABLE_HEADER_GROUP', 'TABLE_FOOTER_GROUP', 'TABLE_COLUMN', 'TABLE_COLUMN_GROUP']);
// M1 resolver (all three canvases): concrete px per physical side; zero for a table-internal body.
const bodyMargin = (b) => {
  const m = { top: 0, right: 0, bottom: 0, left: 0 };
  if (!b || TABLE_INTERNAL.has(String(prop(b, 'Display') ?? '').toUpperCase())) return m;
  for (const [s, t] of [['top', 'MarginTop'], ['right', 'MarginRight'], ['bottom', 'MarginBottom'], ['left', 'MarginLeft']]) {
    const d = prop(b, t); const v = typeof d?.px === 'number' ? d.px : typeof d?.original?.px === 'number' ? d.original.px : null;
    if (v != null) m[s] = v;                                          // em/auto/% keep 0 (resolver contract)
  }
  return m;
};

// --- JS port of collapsedRootStackGapsPx (UaBlockMargins.kt) ---
function fold(plans) {
  const gaps = new Array(plans.length + 1).fill(0); let run = 0; let emitted = 0;
  plans.forEach((p, i) => {
    run = Math.max(run, Math.max(0, p.top)); gaps[i] = run - emitted;  // prefix max minus what the set emitted
    if (p.t) { emitted += gaps[i]; run = Math.max(run, Math.max(0, p.bottom)); } // set stays open
    else { run = Math.max(0, p.bottom); emitted = 0; }                // opaque root closes the set
  });
  gaps[plans.length] = run - emitted;
  return gaps;
}
// Slot offsets (cumulative gaps above each root; box heights cancel between OLD and NEW).
const slots = (gaps, n) => { const o = []; let acc = 0; for (let i = 0; i < n; i++) { acc += gaps[i]; o.push(acc); } return o; };

// --- the census walk ---
const out = { run: 'wave51-fix', docs: 0, m1: [], t1: [], t1UaOnly: [], t2: [], t3: [], t3Nested: [], t6: [], flaggedEm: [] };
for (const sec of fs.readdirSync(RUN).sort()) {
  const dir = path.join(RUN, sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); out.docs++; emSeen = false;
    const stem = f.replace(/\.json$/, ''); const v = verdicts.get(stem) ?? { key: stem };
    const cells = { test: v.key, web: v.web, ios: v.ios, android: v.android };
    const comps = doc.components ?? [];
    const kidsOf = new Set(comps.filter((c) => c.slot?.parent).map((c) => c.slot.parent));
    const roots = comps.filter((c) => !c.slot?.parent);
    const hostActive = comps.some((c) => ['ABSOLUTE', 'FIXED'].includes(pos(c)));
    const body = roots.find((c) => c.meta?.role === 'body-root'); const bm = bodyMargin(body);
    const m1 = bm.top || bm.right || bm.bottom || bm.left;
    if (body && m1) out.m1.push({ ...cells, margin: bm, display: prop(body, 'Display') ?? null });
    // OLD (wave 51) and NEW (wave 52 L2) plans per flow root (the natives' Column list).
    const flow = roots.filter((c) => !isHoisted(c) || !hostActive);
    const plan = (c, neu) => {
      if (isHoisted(c) && hostActive) return { top: 0, bottom: 0, t: true };
      const strip = neu && m1 && c === body;                          // M1: the canvas owns the body margin
      const dt = strip ? null : declTop(c); const db = strip ? null : declBot(c);
      if (isRc1(c) && hostActive) return neu ? { top: 0, bottom: 0, t: true }
        : { top: edge(c, dt, uaOf(c)), bottom: edge(c, db, uaOf(c)), t: true };
      const t = neu && isEmptyRoot(c, kidsOf.has(c.id));
      return { top: edge(c, dt, uaOf(c)), bottom: edge(c, db, uaOf(c)), t };
    };
    const oldS = slots(fold(flow.map((c) => plan(c, false))), flow.length);
    const newS = slots(fold(flow.map((c) => plan(c, true))), flow.length);
    const shift = m1 ? Math.max(0, bm.top) + Math.min(0, bm.top) : 0; // NEW Column inset/offset on top
    flow.forEach((c, i) => {
      // RC1 ink: NEW renders its declared top margin on the box (the join is gone).
      const inkNew = newS[i] + shift + (isRc1(c) && hostActive ? Math.max(0, declTop(c) ?? 0) : 0);
      const d = inkNew - oldS[i];
      if (!d) return;
      const row = { ...cells, root: comps.indexOf(c), tag: c.meta?.sourceTag ?? null, old: oldS[i], new: inkNew, delta: d };
      if (m1) return;                                                 // body-margin docs reported under m1
      if (isRc1(c) && hostActive) (declTop(c) ? out.t1 : out.t1UaOnly).push(row);
      else out.t2.push(row);                                          // an in-flow root moved by T1/T2 upstream
    });
    // T3: an RC1 root followed by at least one in-flow (non-hoisted) root, and the
    // fix-pass LIFT RULE (UaBlockMargins.kt composedRootsPaintingAboveFlow, ported):
    // lifted = RC1, no declared ZIndex, a single painted box, and every later
    // non-hoisted root lifted or free of step-8+ content (positioned / layer types).
    const kidsBy = (c) => comps.filter((k) => k.slot?.parent === c.id);
    const layer = (c) => (pos(c) && pos(c) !== 'STATIC') || (c.properties ?? []).some((p) => LAYER.has(p.type)) || kidsBy(c).some(layer);
    const lifted = new Array(roots.length).fill(false);
    for (let i = roots.length - 1; i >= 0; i--) {
      const c = roots[i]; const z = prop(c, 'ZIndex')?.value;
      const why = !(hostActive && isRc1(c)) ? 'not-rc1' : typeof z === 'number' ? `declared-z ${z}`
        : (kidsOf.has(c.id) || c.text || c.pseudos || c.meta?.runs) ? 'content-bearing'
        : roots.slice(i + 1).some((n, k) => !lifted[i + 1 + k] && !(isHoisted(n) && hostActive) && layer(n)) ? 'later-step8-root' : 'lifted';
      lifted[i] = why === 'lifted';
      if (why !== 'not-rc1' && roots.slice(i + 1).some((n) => !['ABSOLUTE', 'FIXED'].includes(pos(n))))
        out.t3.push({ ...cells, root: comps.indexOf(c), why });
    }
    // T3-nested (NOT fixed by the harness wrap — reported so the gap is measured):
    // a no-inset absolute CHILD with no positioned ancestor and a later in-flow sibling.
    const byId = new Map(comps.map((c) => [c.id, c]));
    const positionedUp = (c) => { for (let p = byId.get(c.slot?.parent); p; p = byId.get(p.slot?.parent)) if (pos(p) && pos(p) !== 'STATIC') return true; return false; };
    comps.forEach((c) => {
      if (!c.slot?.parent || !isRc1(c) || positionedUp(c)) return;
      const sibs = comps.filter((s) => s.slot?.parent === c.slot.parent);
      if (sibs.slice(sibs.indexOf(c) + 1).some((n) => !['ABSOLUTE', 'FIXED'].includes(pos(n)))) out.t3Nested.push({ ...cells, comp: comps.indexOf(c) });
    });
    // T6: a canvas-hoisted root with a UA-margin tag and no declared block margin.
    roots.forEach((c) => {
      if (isHoisted(c) && uaOf(c) && declTop(c) == null && declBot(c) == null && !has(c, 'MarginTop') && !has(c, 'MarginBottom'))
        out.t6.push({ ...cells, root: comps.indexOf(c), tag: c.meta?.sourceTag });
    });
    if (emSeen) out.flaggedEm.push(v.key);
  }
}
// --- Fix A tripwire, re-read from the plan's pixel census (the honest oracle) ---
const fi = JSON.parse(fs.readFileSync(path.join(PLAN, 'frame-ink-census.json'), 'utf8'));
// SCORED cells only (no exclusion stamp — PLAN §2 L2's ONE denominator, 208 + 84 = 292),
// split by platform, plus the same-size subset the SSIM simulation covers (192 + 82 = 274).
const tw = { 'overrun-right': {}, 'overrun-left': {}, sameSize: { 'overrun-right': 0, 'overrun-left': 0 } };
for (const c of fi.cells) if (tw[c.class] && !c.scoreExcluded) {
  tw[c.class][c.platform] = (tw[c.class][c.platform] ?? 0) + 1;
  if (c.status === 'ok') tw.sameSize[c.class]++;                      // same-size capture (SSIM-predicted rows)
}
out.fixATripwire = tw;
fs.writeFileSync(path.join(HERE, 'composed-canvas.census.json'), JSON.stringify(out, null, 1) + '\n');
const uniq = (a) => new Set(a.map((r) => r.test)).size;
console.log(`docs ${out.docs}`);
console.log(`M1 body-margin docs ${out.m1.length}: ${out.m1.map((r) => r.test.replace(/^css\//, '')).join(', ')}`);
console.log(`T1 RC1 declared-margin movers: ${out.t1.length} rows / ${uniq(out.t1)} tests`);
console.log(`T1 RC1 UA-only movers: ${out.t1UaOnly.length} rows / ${uniq(out.t1UaOnly)} tests`);
console.log(`T2/downstream in-flow movers: ${out.t2.length} rows / ${uniq(out.t2)} tests`);
console.log(`T3 RC1-before-in-flow docs: ${out.t3.length} rows / ${uniq(out.t3)} tests; nested (not covered): ${out.t3Nested.length} rows / ${uniq(out.t3Nested)} tests`);
const byWhy = {}; for (const r of out.t3) (byWhy[r.why.replace(/ -?\d+$/, '')] ??= []).push(r);
for (const [w, rs] of Object.entries(byWhy)) console.log(`  T3 ${w}: ${rs.length} rows / ${uniq(rs)} tests — ${rs.map((r) => `${r.test.replace(/^css\//, '')}#${r.root}${r.why.startsWith('declared') ? ` (${r.why.slice(11)})` : ''}`).join(', ')}`);
console.log(`T6 hoisted UA-tag roots without a block margin: ${out.t6.length} rows / ${uniq(out.t6)} tests`);
console.log(`Fix A tripwire: ${JSON.stringify(tw)}  em-approximated docs: ${out.flaggedEm.length}`);
