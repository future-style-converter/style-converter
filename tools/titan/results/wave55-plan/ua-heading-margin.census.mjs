#!/usr/bin/env node
// tools/titan/results/wave55-plan/ua-heading-margin.census.mjs
//
// Census for the wave-55 lane L4 brief "ua-heading-margin" (BACKLOG obligation
// 0(h)): the UA `margin-block` (HTML §15.3.3 / §15.3.7, Chromium html.css) of an
// OUT-OF-FLOW box (position: absolute | fixed — css-position-3 §2.1), which
// neither native gives today unless the box is a canvas-HOISTED root
// (wave-52 L2 T6 `withUaBlockMarginOnHoistedRoot`, both harnesses).
//
// READ-ONLY over a gate run: every per-test IR document of every section,
// joined to that run's scored cells through the scorer's own loader (a cell
// counts only if typeof ssim === "number" && !scoreExcluded; P = wptPass).
//
// For every component whose `meta.sourceTag` has a UA block margin
// (p ul ol blockquote pre figure h1…h6) AND whose OWN list declares
// Position ABSOLUTE | FIXED, it prints:
//   class   hoisted  root (no slot.parent) that is FIXED, or ABSOLUTE with an
//                    inset — T6 already appends the UA margin (both natives);
//           rc1      root, ABSOLUTE, no inset — the RC1 static-position slot
//                    (Shape 1/2 of the root-stack plan: (0,0), no UA margin);
//           nested   has a slot parent — never reaches any UA margin today.
//   decl    which block edges the element declares itself (author > UA, per edge)
//   face    the UA-cascade font-size the em resolves against (css-values-4
//           §6.1.1): own px; own em × inherited; else the UA heading multiplier
//           (h1 2, h2 1.5, h3 1.17, h4 1, h5 .83, h6 .67) × the em base, where
//           the em base is the inherited px, else 13 for a monospace first
//           family (Chromium defaultFixedFontSize, the MonospaceUAFontSize
//           table both natives own), else 16.
//   margin  em factor (p/ul/ol/blockquote/pre/figure 1, h1 .67, h2 .83, h3 1,
//           h4 1.33, h5 1.67, h6 2.33) × face, on each UNDECLARED edge.
//   table   what the T6 / child-fold table basis would give instead
//           (UaBlockMarginFontBasis.ownFontSizePx → null for an em-sized
//           heading → the Round-4 16px-root table: h1 21, h2 19, h3 16, …).
//
// Usage: node ua-heading-margin.census.mjs [run-id=wave54-final] [--json out.json]
import fs from 'node:fs';
import path from 'node:path';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const args = process.argv.slice(2);
const runId = args.find((a) => !a.startsWith('--')) ?? 'wave54-final';
const jsonOut = args.includes('--json') ? args[args.indexOf('--json') + 1] : null;
const runDir = resolveRunDir(runId);
const run = loadRun(runDir);

// HTML §15.3 UA block-margin em factors (UaBlockMarginFontBasis.uaBlockMarginEm).
const MARGIN_EM = { p: 1, ul: 1, ol: 1, blockquote: 1, pre: 1, figure: 1, h1: 0.67, h2: 0.83, h3: 1, h4: 1.33, h5: 1.67, h6: 2.33 };
// The Round-4 16px-root table (uaVerticalBlockMargins / UABlockMargin.vertical).
const TABLE = { p: 16, ul: 16, ol: 16, blockquote: 16, pre: 16, figure: 16, h1: 21, h2: 19, h3: 16, h4: 21, h5: 27, h6: 37 };
// HTML §15.3.7 UA font-size multipliers (UAElementFontRule.sizeMultiplier).
const FACE_MULT = { h1: 2, h2: 1.5, h3: 1.17, h4: 1, h5: 0.83, h6: 0.67 };
const TOP = new Set(['MarginTop', 'MarginBlockStart']);
const BOTTOM = new Set(['MarginBottom', 'MarginBlockEnd']);
const INSETS = new Set(['Top', 'Right', 'Bottom', 'Left', 'Inset', 'InsetBlock', 'InsetInline',
  'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd']);

function keyIndex(sec) {
  const m = JSON.parse(fs.readFileSync(path.join(runDir, 'sections', sec, 'manifest.json'), 'utf8'));
  const idx = new Map();
  for (const k of Object.keys(m.wpt?.results ?? {})) {
    idx.set('wpt__' + k.replace(/^css\//, '').replace(/\.[a-z]+$/, '').split('/').join('__'), k);
  }
  return idx;
}
const positionOf = (c) => {
  const p = (c.properties ?? []).filter((q) => q.type === 'Position').pop();
  return typeof p?.data === 'string' ? p.data.toUpperCase() : (p ? JSON.stringify(p.data) : null);
};
const pxOf = (d) => (d && typeof d === 'object' && typeof d.px === 'number' ? d.px : null);
// own computed font-size px, or null when not statically resolvable here.
function ownSizePx(c, inheritedPx) {
  const fs_ = (c.properties ?? []).filter((q) => q.type === 'FontSize').pop();
  if (!fs_) return undefined; // no own declaration
  const px = pxOf(fs_.data); if (px) return px;
  const o = fs_.data?.original;
  if (o?.type === 'length' && o.original?.u === 'EM') return o.original.v * inheritedPx;
  if (o?.type === 'percentage') return (o.value / 100) * inheritedPx;
  return null;
}
function monospaceFirst(c, byId) {
  // FontFamily is inherited: own first, else the nearest ancestor's.
  for (let p = c; p; p = p.slot?.parent ? byId.get(p.slot.parent) : null) {
    const ff = (p.properties ?? []).filter((q) => q.type === 'FontFamily').pop();
    if (ff) return Array.isArray(ff.data) && String(ff.data[0]).toLowerCase() === 'monospace';
  }
  return false;
}
function inheritedPx(c, byId) {
  // nearest ancestor with a statically resolvable FontSize px (the inheritance channel).
  for (let p = c.slot?.parent ? byId.get(c.slot.parent) : null; p; p = p.slot?.parent ? byId.get(p.slot.parent) : null) {
    const v = ownSizePx(p, 16); if (typeof v === 'number') return v;
  }
  return null;
}

const rows = [];
let docs = 0;
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const idx = keyIndex(sec);
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const comps = doc.components ?? [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    const kids = new Map();
    for (const c of comps) if (c.slot?.parent) { if (!kids.has(c.slot.parent)) kids.set(c.slot.parent, []); kids.get(c.slot.parent).push(c); }
    const hits = [];
    for (const c of comps) {
      const tag = (c.meta?.sourceTag ?? '').toLowerCase();
      if (!(tag in MARGIN_EM)) continue;
      const pos = positionOf(c);
      // bucketed Position (selectors / media) is reported, never counted as a carrier.
      const bucketPos = JSON.stringify([c.selectors ?? null, c.media ?? null]).includes('"Position"');
      if (pos !== 'ABSOLUTE' && pos !== 'FIXED') { if (bucketPos) hits.push({ tag, id: c.id, cls: 'bucket-only', pos }); continue; }
      const own = (c.properties ?? []).map((q) => q.type);
      const isRoot = !c.slot?.parent;
      const hasInset = own.some((t) => INSETS.has(t));
      const cls = isRoot ? ((pos === 'FIXED' || hasInset) ? 'hoisted' : 'rc1') : 'nested';
      let posAnc = false;
      for (let p = c.slot?.parent ? byId.get(c.slot.parent) : null; p; p = p.slot?.parent ? byId.get(p.slot.parent) : null) {
        const pp = positionOf(p); if (pp && pp !== 'STATIC') { posAnc = pp; break; }
      }
      const declTop = own.some((t) => TOP.has(t)); const declBottom = own.some((t) => BOTTOM.has(t));
      const inh = inheritedPx(c, byId);
      const mono = monospaceFirst(c, byId);
      const own_ = ownSizePx(c, inh ?? 16);
      const emBase = inh ?? (mono ? 13 : 16);
      let face;
      if (typeof own_ === 'number') face = own_;
      else if (own_ === null) face = null; // declared but unresolvable here
      else face = (FACE_MULT[tag] ?? 1) * emBase;
      const margin = face == null ? null : +(MARGIN_EM[tag] * face).toFixed(2);
      const wm = (c.properties ?? []).some((q) => q.type === 'WritingMode');
      const k = kids.get(c.id) ?? [];
      hits.push({
        tag, id: c.id, cls, pos, hasInset, posAnc, declTop, declBottom,
        ownFontSize: own.includes('FontSize'), mono, face: face == null ? null : +face.toFixed(2), margin,
        table: TABLE[tag], fires: !(declTop && declBottom), shape: k.length ? (c.meta?.runs ? 'runs-host' : 'children') : 'leaf',
        wm, lossy: c.meta?.lossy ?? null,
      });
    }
    if (!hits.length) continue;
    const stem = f.replace(/\.json$/, '');
    const key = idx.get(stem) ?? null;
    const cells = {};
    for (const p of ['web', 'ios', 'android']) {
      const x = key && run.sections[sec].cells.get(`${key}|${p}`);
      cells[p] = x ? `${x.pass ? 'P' : 'f'} ${x.ssim}` : '—';
    }
    rows.push({ sec, stem, key, hits, cells });
  }
}

const out = [];
out.push(`run ${runId}: ${docs} per-test IR documents; ${rows.length} carry an out-of-flow (ABSOLUTE|FIXED) UA-margin tag`);
const all = rows.flatMap((r) => r.hits.map((h) => ({ ...h, sec: r.sec, stem: r.stem, cells: r.cells })));
const real = all.filter((h) => h.cls !== 'bucket-only');
const byCls = (c) => real.filter((h) => h.cls === c);
out.push(`components: ${real.length} (hoisted ${byCls('hoisted').length} · rc1 ${byCls('rc1').length} · nested ${byCls('nested').length}); bucket-only Position: ${all.length - real.length}`);
const firing = real.filter((h) => h.cls !== 'hoisted' && h.fires);
out.push(`NEW-RULE FIRES (rc1 + nested, at least one undeclared block edge): ${firing.length} components in ${new Set(firing.map((h) => h.stem)).size} documents`);
out.push('');
for (const r of rows) {
  out.push(`${r.sec}/${r.stem.replace(/^wpt__[^_-]+(?:-[^_-]+)*__/, '')}   web ${r.cells.web} · ios ${r.cells.ios} · android ${r.cells.android}`);
  for (const h of r.hits) {
    if (h.cls === 'bucket-only') { out.push(`   ${h.tag} ${h.id}  bucket-only Position (not a carrier)`); continue; }
    const decl = `${h.declTop ? 'T' : '-'}${h.declBottom ? 'B' : '-'}`;
    const verdict = h.cls === 'hoisted' ? 'T6 (already has the UA margin)' : (h.fires ? `FIRES margin ${h.margin} px on ${h.declTop ? '' : 'top'}${!h.declTop && !h.declBottom ? '+' : ''}${h.declBottom ? '' : 'bottom'} (table basis would give ${h.table})` : 'declares both edges (author wins: identity)');
    out.push(`   ${h.tag} ${h.id}  ${h.cls} ${h.pos}${h.hasInset ? '+inset' : ''} posAnc=${h.posAnc || '-'} decl=${decl} ownFS=${h.ownFontSize ? 'y' : 'n'} mono=${h.mono ? 'y' : 'n'} face=${h.face} shape=${h.shape}${h.wm ? ' WRITING-MODE' : ''}  → ${verdict}`);
  }
}
out.push('');
out.push('CARRIER STEMS (the new rule fires on ≥1 component):');
const carrierStems = [...new Set(firing.map((h) => `${h.sec}/${h.stem}`))].sort();
for (const s of carrierStems) out.push(`   ${s}`);
// U2 radius (the HELD folded-host face): EVERY h1–h6 that hosts slot children, any position — the only components
// whose heading half the fold-aware gate can turn on (a leaf heading already takes the face on both natives).
out.push('');
out.push('U2 RADIUS — every h1–h6 with slot children (any position):');
let u2 = 0;
for (const sec of Object.keys(run.sections).sort()) {
  const irDir = path.join(runDir, 'sections', sec, 'per-test-ir');
  if (!fs.existsSync(irDir) || !run.sections[sec].hasManifest) continue;
  const idx = keyIndex(sec);
  for (const f of fs.readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(irDir, f), 'utf8'));
    const comps = doc.components ?? [];
    const parents = new Set(comps.map((c) => c.slot?.parent).filter(Boolean));
    for (const c of comps) {
      const tag = (c.meta?.sourceTag ?? '').toLowerCase();
      if (!/^h[1-6]$/.test(tag) || !parents.has(c.id)) continue;
      u2++;
      const own = (c.properties ?? []).map((q) => q.type);
      const key = idx.get(f.replace(/\.json$/, ''));
      const cells = ['web', 'ios', 'android'].map((p) => { const x = key && run.sections[sec].cells.get(`${key}|${p}`); return x ? `${p} ${x.pass ? 'P' : 'f'} ${x.ssim}` : `${p} —`; }).join(' · ');
      out.push(`   ${sec}/${f.replace(/\.json$/, '')} ${tag} ${c.id} pos=${positionOf(c) ?? '-'} runs=${c.meta?.runs ? 'y' : 'n'} ownFS=${own.includes('FontSize') || own.includes('Font') ? 'y' : 'n'} ownFW=${own.includes('FontWeight') || own.includes('Font') ? 'y' : 'n'}   ${cells}`);
    }
  }
}
out.push(`   total: ${u2} heading hosts with children`);
const text = out.join('\n');
console.log(text);
if (jsonOut) fs.writeFileSync(jsonOut, JSON.stringify({ runId, docs, rows }, null, 1));
