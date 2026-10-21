#!/usr/bin/env node
// tools/titan/results/wave55-plan/cbb-android.census.mjs — read-only carrier census for wave-55 lane L2-cbb-android
// (brief: cbb-android.md, same directory). Nothing here builds, captures or writes outside this directory's stdout.
//
// Why: the wave-54 unit CBB-android (5b333c8a, reverted by 8deddc19) fixed the containing-block CHANNEL and left a
// 20-px source on backdrop-filter-nested-border-radius-clip-3 android. The brief splits the corrected work into two
// units, and each needs its own radius, re-derived from the per-test IR rather than copied:
//   U1  the restored channel rule (ContainingBlockBands: under WPT capture an effective content-box box publishes its
//       declared size, CSS 2.1 §10.1/§10.2). The radius is every box whose INCOMING block differs between the frozen
//       reading and the WPT reading AND that has a reader of that block (oof: an out-of-flow % size / inset stretch;
//       spc: a % margin/padding/inset; vtext: text under a vertical writing mode; dyn: a calc/var/relative payload).
//       This mirrors the wave-54 JUnit census (`DynamicValueResolverTest` "CBB census" in 5b333c8a) at the wire level.
//   U2  the NEW in-flow rule (PercentContentBoxBand): an in-flow box whose EFFECTIVE box-sizing is content-box and whose
//       Width/Height is a percentage draws a frame of `fraction × base + bands` (css-sizing-3 §3 + CSS 2.1 §10.2), where
//       SizingApplier.inflateForContentBox today passes PERCENT through and fillMaxWidth/Height(fraction) puts the
//       padding and border INSIDE the percentage. The radius is every such box with a non-zero band on the axis.
//   C   the dark stage: committed (non-WPT) fixtures that declare `box-sizing: content-box` together with a % width or
//       height and a padding/border band — the population U2's dark-stage breadcrumb would name (U2 is WPT-gated).
//
// Usage: node tools/titan/results/wave55-plan/cbb-android.census.mjs [run-id]   (default wave54-final)
// Output: cbb-android.census.out.txt beside this file is this script's stdout, verbatim.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
// The scorer's own loader: "scored" and "P" mean what the gate means (typeof ssim number && !scoreExcluded; wptPass).
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const ROOT = path.resolve(HERE, '..', '..', '..', '..');
const RUN = process.argv[2] || 'wave54-final';
const SECTIONS = path.join(ROOT, 'tools/titan/runs', RUN, 'sections');
// The census root block: the same 358-px canvas width the wave-54 JUnit census threaded (height unknown).
const ROOT_CB = { w: 358, h: null };

// ── wire helpers (mirror DynamicValueResolver.pxOf / sizeOf and SizingExtractor.contentBoxInflation) ────────────────
const first = (props, t) => props.find((p) => p.type === t);
const last = (props, t) => [...props].reverse().find((p) => p.type === t);
function pxOf(props, ...types) {
  // DynamicValueResolver.childContainingBlock.pxOf: first property of each type in order whose data carries a numeric px.
  for (const t of types) {
    const d = first(props, t)?.data;
    if (d && typeof d === 'object' && typeof d.px === 'number') return d.px;
  }
  return null;
}
function pctOf(props, ...types) {
  // The typed percentage wire {"type":"percentage","value":N} (the only % shape Width/Height carry in this corpus).
  for (const t of types) {
    const d = first(props, t)?.data;
    if (d && typeof d === 'object' && d.type === 'percentage' && typeof d.value === 'number') return d.value;
  }
  return null;
}
function sizeOf(props, base, ...types) {
  // sizeOf(): resolved px wins; a percentage resolves against a KNOWN base, else the axis stays unknown (null).
  const px = pxOf(props, ...types);
  if (px != null) return px;
  const pct = pctOf(props, ...types);
  if (pct != null) return base == null ? null : (pct * base) / 100;
  return null;
}
// The declared box-sizing keyword, last one wins (SizingExtractor fold order); null = unset.
const declaredBoxSizing = (props) => last(props, 'BoxSizing')?.data ?? null;
// SizingApplier.effectiveBoxSizing: declared wins; unset is CONTENT_BOX under WPT capture, null on the dark stage.
const effectiveBoxSizing = (props, wpt) => declaredBoxSizing(props) ?? (wpt ? 'CONTENT_BOX' : null);
// ContainingBlockBands.subtracts (5b333c8a): WPT subtracts only for an effective non-content-box; dark stage always.
const subtracts = (props, wpt) => (wpt ? effectiveBoxSizing(props, wpt) !== 'CONTENT_BOX' : true);
function childCB(props, parent, wpt) {
  // DynamicValueResolver.childContainingBlock(properties, parent, wptCaptureMode) — raw px band reads, as the runtime.
  const w = sizeOf(props, parent.w, 'Width', 'InlineSize');
  const h = sizeOf(props, parent.h, 'Height', 'BlockSize');
  if (!subtracts(props, wpt)) return { w, h };
  const bx = (pxOf(props, 'PaddingLeft', 'PaddingInlineStart') ?? 0) + (pxOf(props, 'PaddingRight', 'PaddingInlineEnd') ?? 0)
    + (pxOf(props, 'BorderLeftWidth') ?? 0) + (pxOf(props, 'BorderRightWidth') ?? 0);
  const by = (pxOf(props, 'PaddingTop', 'PaddingBlockStart') ?? 0) + (pxOf(props, 'PaddingBottom', 'PaddingBlockEnd') ?? 0)
    + (pxOf(props, 'BorderTopWidth') ?? 0) + (pxOf(props, 'BorderBottomWidth') ?? 0);
  return { w: w == null ? null : w - bx, h: h == null ? null : h - by };
}
function paintedBorder(props, side) {
  // BorderSideConfig.hasBorder: width > 0 AND a style that is present and not NONE/HIDDEN.
  const w = pxOf(props, `Border${side}Width`) ?? 0;
  const s = last(props, `Border${side}Style`)?.data;
  return w > 0 && typeof s === 'string' && s !== 'NONE' && s !== 'HIDDEN' ? w : 0;
}
function bands(props) {
  // SizingExtractor.contentBoxInflation: px padding (a % padding counts 0 — percentIndefiniteAsZero) + painted borders.
  const pad = (...t) => Math.max(0, pxOf(props, ...t) ?? 0);
  return {
    x: pad('PaddingLeft', 'PaddingInlineStart') + pad('PaddingRight', 'PaddingInlineEnd') + paintedBorder(props, 'Left') + paintedBorder(props, 'Right'),
    y: pad('PaddingTop', 'PaddingBlockStart') + pad('PaddingBottom', 'PaddingBlockEnd') + paintedBorder(props, 'Top') + paintedBorder(props, 'Bottom'),
  };
}
const posOf = (props) => last(props, 'Position')?.data ?? null;
const isOutOfFlow = (props) => posOf(props) === 'ABSOLUTE' || posOf(props) === 'FIXED';
const has = (props, t) => props.some((p) => p.type === t);
const isAutoOrAbsent = (props, t) => { const d = last(props, t)?.data; return d == null || d === 'auto'; };
const sameCB = (a, b) => a.w === b.w && a.h === b.h;
const fmt = (cb) => `${cb.w ?? 'null'}x${cb.h ?? 'null'}`;

// ── load every per-test IR document of the run (1435 at wave54-final) ─────────────────────────────────────────────
const docs = [];
for (const sec of fs.readdirSync(SECTIONS).sort()) {
  const dir = path.join(SECTIONS, sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    // Capture stem = per-test IR stem (wpt__<sec>__<test with / → __>); scorer key = css/<sec>/<test>.html.
    const stem = f.replace(/\.json$/, '');
    const test = stem.slice(`wpt__${sec}__`.length).split('__').join('/');
    docs.push({ sec, stem, key: `css/${sec}/${test}.html`, comps: doc.components ?? [] });
  }
}
// Cells at RUN through the scorer's loader (P / f with ssim, per platform).
const run = loadRun(resolveRunDir(RUN));
function cells(d) {
  const s = run.sections[d.sec];
  return ['web', 'ios', 'android'].map((p) => { const c = s?.cells.get(`${d.key}|${p}`); return `${p} ${c ? `${c.pass ? 'P' : 'f'} ${c.ssim}` : '—'}`; }).join(' · ');
}
// The tree: slot.parent names the parent id; roots have no slot.
function tree(d) {
  const byId = new Map(d.comps.map((c) => [c.id, { ...c, props: c.properties ?? [], kids: [] }]));
  const roots = [];
  for (const n of byId.values()) {
    const p = n.slot?.parent && byId.get(n.slot.parent);
    if (p) p.kids.push(n); else roots.push(n);
  }
  return roots;
}
const short = (id) => id.split('__').slice(-1)[0];
const VERT = /^(VERTICAL|SIDEWAYS)/;

// ── A: U1 radius (the restored channel rule) ────────────────────────────────────────────────────────────────────
const SPACING = /^(Margin|Padding|Top|Right|Bottom|Left|Inset)/;
const aLines = [];
for (const d of docs) {
  const walk = (n, o, w, vert) => {
    const wm = last(n.props, 'WritingMode')?.data;
    const v = typeof wm === 'string' ? VERT.test(wm) : vert;
    if (!sameCB(o, w)) {
      const why = [];
      if (isOutOfFlow(n.props)) {
        // oof: resolveOutOfFlowPercentSizes (a % size on an axis whose base moved) or AbsposInsetStretch (both
        // opposing insets with an auto size on that axis) or AbsposAutoMargin (auto margins solve against the block).
        const wMoved = o.w !== w.w, hMoved = o.h !== w.h;
        const pctW = pctOf(n.props, 'Width', 'InlineSize') != null, pctH = pctOf(n.props, 'Height', 'BlockSize') != null;
        const strW = has(n.props, 'Left') && has(n.props, 'Right') && isAutoOrAbsent(n.props, 'Width');
        const strH = has(n.props, 'Top') && has(n.props, 'Bottom') && isAutoOrAbsent(n.props, 'Height');
        // AbsposAutoMargin solves auto margins only between two declared opposing insets (CSS 2.1 §10.3.7 / §10.6.4).
        const autoMW = has(n.props, 'Left') && has(n.props, 'Right') && n.props.some((p) => /^Margin(Left|Right)/.test(p.type) && p.data === 'auto');
        const autoMH = has(n.props, 'Top') && has(n.props, 'Bottom') && n.props.some((p) => /^Margin(Top|Bottom)/.test(p.type) && p.data === 'auto');
        const autoM = (wMoved && autoMW) || (hMoved && autoMH);
        if ((wMoved && (pctW || strW)) || (hMoved && (pctH || strH)) || autoM) why.push('oof');
      }
      if (n.props.some((p) => SPACING.test(p.type) && /"type":"percentage"|"PERCENT"/.test(JSON.stringify(p.data)))) why.push('spc');
      if (v && (n.text || n.meta?.runs?.length)) why.push('vtext');
      // dyn: only a payload the resolve pre-pass resolves AGAINST THE BLOCK — a relative original in PERCENT, or an
      // expression carrying `%` (em/ch/lh originals resolve against the font, not the block, and cannot move).
      if (n.props.some((p) => p.data && typeof p.data === 'object' && !('srgb' in p.data) &&
        ((p.data.original && typeof p.data.original === 'object' && p.data.original.u === 'PERCENT') ||
         (typeof p.data.expr === 'string' && p.data.expr.includes('%'))))) why.push('dyn');
      if (why.length) aLines.push({ d, line: `${short(n.id)} [${why.join(',')}] ${fmt(o)}->${fmt(w)}` });
    }
    const co = childCB(n.props, o, false), cw = childCB(n.props, w, true);
    n.kids.forEach((k) => walk(k, co, cw, v));
  };
  tree(d).forEach((r) => walk(r, ROOT_CB, ROOT_CB, false));
}
const aDocs = [...new Set(aLines.map((x) => x.d))];
console.log(`# cbb-android census over ${RUN}: ${docs.length} per-test IR documents`);
console.log(`\n## A. U1 (restored ContainingBlockBands) — boxes whose incoming block differs AND that read it: ${aLines.length} boxes in ${aDocs.length} documents`);
for (const d of aDocs) {
  console.log(`${d.stem}  [${cells(d)}]`);
  for (const x of aLines.filter((y) => y.d === d)) console.log(`    ${x.line}`);
}
// The wave-54 probe decision's 14 Android capture carriers (expectations.json probeDecisions.reverted[0]).
const exp = JSON.parse(fs.readFileSync(path.join(ROOT, 'tools/titan/results/wave54-plan/expectations.json'), 'utf8'));
const want = exp.probeDecisions.reverted.find((r) => r.unit === 'CBB-android').carriersWithdrawn.captures.android.slice().sort();
const got = aDocs.map((d) => d.stem).sort();
console.log(`A vs wave-54 probeDecisions CBB-android android captures (${want.length}): ` +
  (JSON.stringify(want) === JSON.stringify(got) ? 'IDENTICAL' : `DIFFER — missing ${JSON.stringify(want.filter((x) => !got.includes(x)))} extra ${JSON.stringify(got.filter((x) => !want.includes(x)))}`));

// ── B: U2 radius (the new in-flow percentage content-box band) ──────────────────────────────────────────────────
const bRows = [];
for (const d of docs) {
  const walk = (n, parent, pcb) => {
    const eff = effectiveBoxSizing(n.props, true);
    const b = bands(n.props);
    for (const [axis, sizeT, minT, maxT, band, pBase] of [
      ['W', ['Width', 'InlineSize'], 'MinWidth', 'MaxWidth', b.x, pcb.w],
      ['H', ['Height', 'BlockSize'], 'MinHeight', 'MaxHeight', b.y, pcb.h],
    ]) {
      const pct = pctOf(n.props, ...sizeT);
      if (pct == null) continue;
      // Class: who draws this axis today.
      let cls;
      if (eff !== 'CONTENT_BOX') cls = 'border-box (declared) — untouched';
      else if (band === 0) cls = 'zero band — untouched';
      else if (isOutOfFlow(n.props)) cls = pBase != null ? 'out-of-flow, base known: % → px then Exact inflation (already right) — untouched'
        : 'out-of-flow, base unknown: unbounded measure, fill no-op — untouched';
      else if (has(n.props, minT) || has(n.props, maxT)) cls = 'IN-FLOW + min/max: PercentSizeClamp lane — NOT covered (named limitation)';
      else cls = 'IN-FLOW CARRIER: fill(fraction) + band';
      const pDisp = parent ? (last(parent.props, 'Display')?.data ?? 'BLOCK(default)') : 'ROOT';
      bRows.push({ d, cls, line: `${short(n.id)} ${axis} ${pct}% band ${band} base ${pBase ?? 'unknown'} parent ${pDisp} → ${cls}` });
    }
    const ccb = childCB(n.props, pcb, true);
    n.kids.forEach((k) => walk(k, n, ccb));
  };
  tree(d).forEach((r) => walk(r, null, ROOT_CB));
}
const carriers = bRows.filter((r) => r.cls.startsWith('IN-FLOW CARRIER'));
const bDocs = [...new Set(carriers.map((r) => r.d))];
const classes = {};
for (const r of bRows) classes[r.cls] = (classes[r.cls] ?? 0) + 1;
console.log(`\n## B. U2 (in-flow % size under an effective content-box with a non-zero band) — ${bRows.length} percentage axes in the corpus, by class:`);
for (const [c, n] of Object.entries(classes).sort()) console.log(`    ${String(n).padStart(4)}  ${c}`);
console.log(`B carriers: ${carriers.length} axes in ${bDocs.length} documents`);
for (const d of bDocs) {
  console.log(`${d.stem}  [${cells(d)}]${aDocs.includes(d) ? '  (also a U1 document)' : ''}`);
  for (const r of carriers.filter((x) => x.d === d)) console.log(`    ${r.line}`);
}
const minmax = [...new Set(bRows.filter((r) => r.cls.includes('min/max')).map((r) => r.d))];
console.log(`B named limitation (min/max lane): ${minmax.length} documents ${JSON.stringify(minmax.map((d) => d.stem))}`);

// ── C: dark stage (committed non-WPT fixtures; U2 is WPT-gated, so this is the breadcrumb's population) ─────────────
const cHits = [];
function scanFixture(file) {
  let j; try { j = JSON.parse(fs.readFileSync(file, 'utf8')); } catch { return; }
  const visit = (o, name) => {
    if (!o || typeof o !== 'object') return;
    const p = o.properties;
    if (p && typeof p === 'object' && !Array.isArray(p) && p['box-sizing'] === 'content-box') {
      const pctSize = ['width', 'height', 'inline-size', 'block-size'].filter((k) => typeof p[k] === 'string' && p[k].trim().endsWith('%'));
      const band = Object.keys(p).some((k) => /^(padding|border)(-|$)/.test(k) && !/radius|color|style|image|collapse|spacing/.test(k));
      if (pctSize.length && band) cHits.push(`${path.relative(ROOT, file)} ${name} ${pctSize.join(',')}`);
    }
    for (const [k, v] of Object.entries(o)) if (v && typeof v === 'object') visit(v, k);
  };
  visit(j, '(root)');
}
const walkDir = (dir) => { for (const e of fs.readdirSync(dir, { withFileTypes: true })) {
  const f = path.join(dir, e.name);
  if (e.isDirectory()) { if (e.name !== 'wpt') walkDir(f); } else if (e.name.endsWith('.json')) scanFixture(f);
} };
walkDir(path.join(ROOT, 'fixtures'));
console.log(`\n## C. dark stage: committed fixtures declaring content-box with a % size and a padding/border band: ${cHits.length}`);
for (const h of cHits) console.log(`    ${h}`);
