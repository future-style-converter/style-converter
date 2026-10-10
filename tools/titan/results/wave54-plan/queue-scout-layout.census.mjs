#!/usr/bin/env node
// tools/titan/results/wave54-plan/queue-scout-layout.census.mjs
//
// Wave-54 planning census for the queue-scout-layout brief (READ-ONLY: it reads
// tools/titan/runs/<run>/sections/*/{manifest.json,per-test-ir/*.json} and the
// red-square census JSON beside this file; it writes only its own JSON).
//
// Usage: node queue-scout-layout.census.mjs [runId=wave53-final] [--out <json>]
//
// Families censused (each a predicate over the v2 flat component list, parent =
// slot.parent):
//   oofcb   — out-of-flow boxes whose containing block (CB) the natives get wrong:
//             M1  `position: fixed` with ALL-auto insets that the natives hoist to
//                 the canvas origin (css-position-3 §3.1 gives it its STATIC
//                 position; CanvasRootHoist.shouldHoistToCanvasRoot FIXED arm /
//                 FixedHoist.split hoist it whenever no transform ancestor exists);
//             M2  an absolute/fixed box whose spec CB is a NON-positioned ancestor
//                 that establishes one through contain (layout|paint|strict|content,
//                 css-contain-2 §3.2/§3.4), filter ≠ none (filter-effects-1 §5),
//                 backdrop-filter ≠ none (filter-effects-2 §3) or will-change of
//                 those — none of which TransformContainingBlock.establishes reads.
//   floatroots — ≥2 CONSECUTIVE same-side float ROOTS (no slot.parent): the
//             composed canvases stack roots in a Column/VStack and only segment
//             inline-block roots (InlineBlockAtom.rootSegments), never floats.
//   gapzero — gap-decoration containers (ColumnRule*/RowRule*) whose gap on the
//             decorated axis is 0 / absent (css-gaps 033's shape).
//   cbborder — Compose DynamicValueResolver.childContainingBlock subtracts padding
//             AND border from a declared px Width/Height even under the default
//             content-box sizing: components with a px Width/Height, no BoxSizing
//             BORDER_BOX, a non-zero border/padding, and a percentage-sized or
//             percentage-inset child.
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const here = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const runId = args.find((a) => !a.startsWith('--')) ?? 'wave53-final';
const outIdx = args.indexOf('--out');
const outPath = outIdx >= 0 ? args[outIdx + 1] : path.join(here, 'queue-scout-layout.census.json');
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);
// Every scored cell, keyed "css/<sec>/<test>.html|<platform>" (the scorer's own key).
const cells = {};
for (const s of Object.values(run.sections)) for (const [k, c] of s.cells) cells[k] = c;
// Red-square flags (strict red in the capture where the ref has none) — the
// committed predicate, run by `node tools/titan/red-square-census.mjs <run> --json`.
const redPath = path.join(here, 'queue-scout-layout.red-square.json');
const redFlag = new Set();
if (fs.existsSync(redPath)) for (const c of JSON.parse(fs.readFileSync(redPath, 'utf8')).cells) if (c.flagged) redFlag.add(`${c.test}|${c.platform}`);
const cellStr = (test) => ['web', 'ios', 'android'].map((p) => {
  const c = cells[`${test}|${p}`];
  return c ? `${p}:${c.pass ? 'P' : 'f'}${c.ssim}${redFlag.has(`${test}|${p}`) ? 'R' : ''}` : `${p}:—`;
}).join(' ');

const prop = (c, t) => (c.properties || []).find((p) => p.type === t);
const has = (c, t) => !!prop(c, t);
const posOf = (c) => prop(c, 'Position')?.data ?? 'STATIC';
const INSETS = ['Top', 'Bottom', 'Left', 'Right', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd', 'Inset', 'InsetBlock', 'InsetInline'];
const anyInset = (c) => INSETS.some((t) => has(c, t));
// TransformContainingBlock.establishes (both natives, wave 35) — mirrored here.
const transformCB = (c) => {
  for (const p of c.properties || []) {
    const d = p.data;
    if (p.type === 'Transform') { if (d && typeof d === 'object' && !(d.type === 'functions' && Array.isArray(d.list) && !d.list.length)) return true; }
    if (['Translate', 'Rotate', 'Scale', 'Perspective'].includes(p.type)) { if (d && typeof d === 'object' && d.type !== 'none') return true; }
    if (p.type === 'TransformStyle' && d === 'PRESERVE_3D') return true;
    if (p.type === 'WillChange' && Array.isArray(d) && d.some((h) => h?.name === 'transform' || h?.name === 'perspective')) return true;
  }
  return false;
};
// The clauses NEITHER native reads (the M2 establishers).
const otherCB = (c) => {
  const why = [];
  const ct = prop(c, 'Contain')?.data;
  if (Array.isArray(ct) && ct.some((v) => ['LAYOUT', 'PAINT', 'STRICT', 'CONTENT'].includes(v))) why.push(`contain:${ct.join('+')}`);
  const f = prop(c, 'Filter')?.data;
  if (Array.isArray(f) && f.length) why.push('filter');
  const bf = prop(c, 'BackdropFilter')?.data;
  if (Array.isArray(bf) && bf.length) why.push('backdrop-filter');
  const wc = prop(c, 'WillChange')?.data;
  if (Array.isArray(wc) && wc.some((h) => ['filter', 'backdrop-filter', 'contain'].includes(h?.name))) why.push('will-change');
  const ctype = prop(c, 'ContainerType')?.data;
  if (typeof ctype === 'string' && ctype !== 'NORMAL') why.push(`container-type:${ctype}`);
  return why;
};

const out = { runId, generatedFrom: path.relative(path.resolve(here, '../../../..'), runDir), oofcb: { m1: [], m2: [], fixedControls: [], establisherControls: [] }, floatroots: [], gapzero: [], cbborder: [] };
const sectionsDir = path.join(runDir, 'sections');
for (const sec of fs.readdirSync(sectionsDir).sort()) {
  const manPath = path.join(sectionsDir, sec, 'manifest.json');
  if (!fs.existsSync(manPath)) continue;
  const results = JSON.parse(fs.readFileSync(manPath, 'utf8')).wpt?.results ?? {};
  for (const [test, r] of Object.entries(results)) {
    const rel = test.replace(/^css\/[^/]+\//, '').replace(/\.html?$/, '');
    const irPath = path.join(sectionsDir, sec, 'per-test-ir', `wpt__${sec}__${rel.replace(/\//g, '__')}.json`);
    if (!fs.existsSync(irPath)) continue;
    const comps = JSON.parse(fs.readFileSync(irPath, 'utf8')).components;
    out.docsScanned = (out.docsScanned ?? 0) + 1;
    const byId = Object.fromEntries(comps.map((c) => [c.id, c]));
    const parentOf = (c) => (c.slot?.parent ? byId[c.slot.parent] : undefined);
    const ancestors = (c) => { const a = []; let p = parentOf(c); while (p) { a.push(p); p = parentOf(p); } return a; };
    const base = { test: test.replace(/^css\//, ''), postLoad: !!r.postLoadExtracted, cells: cellStr(test) };

    // ── oofcb ────────────────────────────────────────────────────────────
    for (const c of comps) {
      const pos = posOf(c);
      if (pos !== 'FIXED' && pos !== 'ABSOLUTE') continue;
      const anc = ancestors(c);
      // Native CB: fixed → nearest transform establisher; absolute → nearest
      // positioned-or-transform ancestor. Spec CB adds the otherCB clauses.
      const nativeIdx = anc.findIndex((a) => transformCB(a) || (pos === 'ABSOLUTE' && posOf(a) !== 'STATIC'));
      const specIdx = anc.findIndex((a) => transformCB(a) || otherCB(a).length || (pos === 'ABSOLUTE' && posOf(a) !== 'STATIC'));
      if (specIdx >= 0 && specIdx !== nativeIdx) {
        out.oofcb.m2.push({ ...base, id: c.id, position: pos, insets: INSETS.filter((t) => has(c, t)), establisher: anc[specIdx].id, why: otherCB(anc[specIdx]), nativeCB: nativeIdx >= 0 ? anc[nativeIdx].id : 'canvas' });
      } else if (pos === 'FIXED' && !anyInset(c) && nativeIdx < 0) {
        out.oofcb.m1.push({ ...base, id: c.id, depth: anc.length, ancestors: anc.map((a) => `${a.meta?.sourceTag ?? 'div'}:${posOf(a)}`) });
      }
    }

    // Compose CanvasRootHoist.hostActivates model (anyOutOfFlowBox; multicol restart and
    // clip veto ignored — none of the carriers has either), BEFORE vs AFTER the lane's
    // rules: after = FIXED hoists only with an inset; all-auto FIXED joins the RC1
    // static-position class; the non-transform establishers veto unless they are
    // themselves absolutely/fixed positioned.
    const hostModel = (after) => {
      const walk = (c, ancPos, ancT) => {
        const pos = posOf(c); const ins = anyInset(c);
        const hoist = pos === 'FIXED' ? (!ancT && (!after || ins)) : pos === 'ABSOLUTE' ? (!ancPos && !ancT && ins) : false;
        const rc1 = (pos === 'ABSOLUTE' || (after && pos === 'FIXED')) && !ancPos && !ins;
        if (hoist || rc1) return true;
        const childT = ancT || transformCB(c) || (after && otherCB(c).length > 0 && pos !== 'ABSOLUTE' && pos !== 'FIXED');
        const childP = ancPos || pos !== 'STATIC';
        return comps.filter((k) => k.slot?.parent === c.id).some((k) => walk(k, childP, childT));
      };
      return comps.filter((c) => !c.slot?.parent).some((c) => walk(c, false, false));
    };
    const hb = hostModel(false), ha = hostModel(true);
    if (hb !== ha) (out.oofcb.hostFlips ||= []).push({ ...base, hostBefore: hb, hostAfter: ha });

    // oofcb controls: every OTHER fixed-carrying doc (must stay byte-identical), and every
    // establisher with an out-of-flow descendant whose containing block does NOT change
    // (the establisher is itself positioned, so the natives already anchor there).
    const carrierIds = new Set([...out.oofcb.m1, ...out.oofcb.m2].filter((x) => x.test === base.test).map((x) => x.id));
    if (comps.some((c) => posOf(c) === 'FIXED') && !comps.some((c) => carrierIds.has(c.id))) out.oofcb.fixedControls.push({ ...base });
    for (const c of comps) {
      if (!otherCB(c).length) continue;
      const desc = []; const stack = comps.filter((k) => k.slot?.parent === c.id);
      while (stack.length) { const k = stack.pop(); desc.push(k); stack.push(...comps.filter((j) => j.slot?.parent === k.id)); }
      const oof = desc.filter((k) => ['ABSOLUTE', 'FIXED'].includes(posOf(k)));
      if (oof.length && !oof.some((k) => carrierIds.has(k.id))) out.oofcb.establisherControls.push({ ...base, id: c.id, position: posOf(c), why: otherCB(c), oof: oof.map((k) => posOf(k)[0]).join('') });
    }

    // establisher totals (every component the new rule table claims, with or without
    // out-of-flow descendants) and the cbborder side consumer: vertical-writing-mode text
    // whose parent is a banded content-box box with a px Height (VerticalTextFlowLayout
    // reads LocalContainingBlock.heightPx for the upright fallback budget).
    for (const c of comps) if (otherCB(c).length) { out.establishers = (out.establishers ?? 0) + 1; (out.establisherDocs ||= new Set()).add(base.test); }

    // ── floatroots ───────────────────────────────────────────────────────
    const roots = comps.filter((c) => !c.slot?.parent);
    const side = (c) => { const f = prop(c, 'Float')?.data; return f === 'LEFT' || f === 'INLINE_START' ? 'L' : f === 'RIGHT' || f === 'INLINE_END' ? 'R' : null; };
    let runs = [], cur = [];
    roots.forEach((c, i) => { const s = side(c); if (s && cur.length && side(roots[cur[0]]) === s) cur.push(i); else { if (cur.length >= 2) runs.push(cur); cur = s ? [i] : []; } });
    if (cur.length >= 2) runs.push(cur);
    if (runs.length) out.floatroots.push({ ...base, roots: roots.length, runs: runs.map((r) => `${side(roots[r[0]])}×${r.length}`).join(' ') });

    // ── gapzero ──────────────────────────────────────────────────────────
    for (const c of comps) {
      const rules = (c.properties || []).filter((p) => /^(ColumnRule|RowRule)/.test(p.type)).map((p) => p.type);
      if (!rules.length) continue;
      const display = prop(c, 'Display')?.data;
      if (!['FLEX', 'INLINE_FLEX', 'GRID', 'INLINE_GRID'].includes(display)) continue;
      const gpx = (t) => { const d = prop(c, t)?.data; return d == null ? null : (d.px ?? d.value ?? d); };
      const cg = gpx('ColumnGap'), rg = gpx('RowGap');
      const zero = (v) => v == null || v === 0 || v === 'NORMAL' || v?.type === 'normal';
      if (zero(cg) || zero(rg)) out.gapzero.push({ ...base, id: c.id, display, columnGap: cg, rowGap: rg, rules: [...new Set(rules)].join(',') });
    }

    // ── cbborder ─────────────────────────────────────────────────────────
    const pxOf = (c, t) => { const d = prop(c, t)?.data; return d && typeof d === 'object' && typeof d.px === 'number' ? d.px : null; };
    const pct = (c, t) => { const d = prop(c, t)?.data; return (d && typeof d === 'object' && d.type === 'percentage') || typeof d === 'number'; };
    for (const c of comps) {
      if (prop(c, 'BoxSizing')?.data === 'BORDER_BOX') continue;
      const w = pxOf(c, 'Width'), h = pxOf(c, 'Height');
      if (w == null && h == null) continue;
      const band = ['PaddingLeft', 'PaddingRight', 'PaddingTop', 'PaddingBottom', 'BorderLeftWidth', 'BorderRightWidth', 'BorderTopWidth', 'BorderBottomWidth'].filter((t) => (pxOf(c, t) ?? 0) > 0);
      if (!band.length) continue;
      const kids = comps.filter((k) => k.slot?.parent === c.id && (pct(k, 'Width') || pct(k, 'Height') || ['Top', 'Left', 'Right', 'Bottom'].some((t) => pct(k, t))));
      if (!kids.length) continue;
      out.cbborder.push({ ...base, id: c.id, band: band.join(','), pctKids: kids.map((k) => `${k.id}:${posOf(k)}`) });
    }
    for (const c of comps) {
      const par = parentOf(c); const wm = prop(c, 'WritingMode')?.data ?? (par && prop(par, 'WritingMode')?.data);
      if (!c.text || typeof wm !== 'string' || !/VERTICAL|SIDEWAYS/.test(wm) || !par || pxOf(par, 'Height') == null) continue;
      if (prop(par, 'BoxSizing')?.data === 'BORDER_BOX') continue;
      if (['PaddingTop', 'PaddingBottom', 'BorderTopWidth', 'BorderBottomWidth'].some((t) => (pxOf(par, t) ?? 0) > 0)) (out.cbborderVerticalText ||= []).push({ ...base, id: c.id });
    }
  }
}
const n = (a) => a.length;
const tests = (a) => new Set(a.map((x) => x.test)).size;
out.establisherDocs = [...(out.establisherDocs ?? [])].length;
out.summary = {
  docsScanned: out.docsScanned,
  establishers: `${out.establishers} components / ${out.establisherDocs} tests`,
  cbborderVerticalText: `${(out.cbborderVerticalText || []).length} text runs`,
  oofcb_m1: `${n(out.oofcb.m1)} boxes / ${tests(out.oofcb.m1)} tests`,
  oofcb_m2: `${n(out.oofcb.m2)} boxes / ${tests(out.oofcb.m2)} tests`,
  oofcb_fixedControls: `${tests(out.oofcb.fixedControls)} tests`,
  oofcb_hostFlips: `${(out.oofcb.hostFlips || []).length} tests`,
  oofcb_establisherControls: `${n(out.oofcb.establisherControls)} establishers / ${tests(out.oofcb.establisherControls)} tests`,
  floatroots: `${tests(out.floatroots)} tests`,
  gapzero: `${n(out.gapzero)} containers / ${tests(out.gapzero)} tests`,
  cbborder: `${n(out.cbborder)} containers / ${tests(out.cbborder)} tests`,
};
fs.writeFileSync(outPath, JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify(out.summary));
