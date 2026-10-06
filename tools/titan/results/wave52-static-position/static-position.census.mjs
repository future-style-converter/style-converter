#!/usr/bin/env node
// wave-52 lane L7 · static-position — the blast-radius census of every L7
// mechanism over the 1435 per-test IR docs of a run (default wave51-fix),
// re-derived from the IR (never from the brief), joined to the scored cells
// with score-gate.mjs's OWN loadRun (no re-implementation of the scorer).
// Read-only; prints a summary and writes static-position.census.json.
//
// Each section ports the decision of the code it censuses, OLD (HEAD) vs NEW
// (this lane), and lists only components whose outcome CHANGES:
//  T1   Compose §9.2 grid overlay: an ABSOLUTE, inset-less child of a GRID
//       parent with no positioned ancestor took the RC1 zeroFlowAnchor (0×0
//       placeable → ink at factor×content); with LocalStaticPositionOwner it
//       is aligned by its real size (factor×(content−child)). Block spec also
//       moves through the T3 typed-wire / baseOf arms and the FOLD.
//  FOLD Compose AlignItems fold: SELF_END → FLEX_END, SELF_START → FLEX_START
//       (was STRETCH) — every component carrying those keywords.
//  T2   iOS flex overlay: AbsposStaticPosition.staticOffset — padding-box
//       extents, no padding term, no align-items (OLD) vs content-box
//       extents + padding-start + align-items fallback (NEW).
//  T3   every Generic `align-self` (any component) and every typed
//       AlignSelf BASELINE on an out-of-flow child: per-platform effect.
// Usage: node static-position.census.mjs [RUN=wave51-fix]
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const { loadRun, resolveRunDir } = await import(path.join(TITAN, 'score-gate.mjs'));
const RUN = process.env.RUN || 'wave51-fix';
const runDir = resolveRunDir(RUN);
const run = loadRun(runDir);
const SECTIONS = path.join(runDir, 'sections');

// ── IR readers (tolerant, like ValueExtractors.extractKeyword) ──
const kw = (d) => typeof d === 'string' ? d
  : d && typeof d === 'object' ? (typeof d.value === 'string' ? d.value
    : typeof d.keyword === 'string' ? d.keyword
      : (typeof d.type === 'string' && Object.keys(d).length === 1 ? d.type : null)) : null;
const pxOf = (d) => d && typeof d === 'object' && typeof d.px === 'number' ? d.px : null;
const first = (c, t) => (c.properties || []).find((p) => p.type === t);
const K = (c, t) => { const p = first(c, t); return p ? kw(p.data)?.toUpperCase().replace(/-/g, '_') ?? null : null; };
const P = (c, t) => { const p = first(c, t); return p ? pxOf(p.data) : null; };
const generic = (c, name) => (c.properties || []).find((p) => p.type === 'Generic' && p.data?.propertyName === name)?.data?.rawValue ?? null;
const posType = (c) => K(c, 'Position') || 'STATIC';
const INSETS = ['Top', 'Right', 'Bottom', 'Left', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd', 'InsetBlock', 'InsetInline', 'Inset'];
const VERT = new Set(['Top', 'Bottom', 'InsetBlockStart', 'InsetBlockEnd']);
const HORZ = new Set(['Left', 'Right', 'InsetInlineStart', 'InsetInlineEnd']);
const isAuto = (p) => kw(p.data)?.toLowerCase() === 'auto';
const hasAnyInset = (c) => (c.properties || []).some((p) => INSETS.includes(p.type) && !isAuto(p));
const hasAxisInset = (c, vertical, autoTolerant) => (c.properties || []).some((p) => (vertical ? VERT : HORZ).has(p.type) && !(autoTolerant && isAuto(p)));

// ── the converter's NEW AlignSelf parse applied to a Generic rawValue ──
function newAlignSelfWire(raw) {
  const t = raw.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const s = new Set(t);
  if (t.length === 2 && s.has('last') && s.has('baseline')) return { typed: 'LAST_BASELINE' };
  if (t.length === 2 && s.has('first') && s.has('baseline')) return { typed: 'BASELINE' };
  if (t.length === 2 && ['safe', 'unsafe'].includes(t[0]) && ['left', 'right'].includes(t[1])) return { dropped: true };
  if (t.length === 1 && t[0] === 'normal') return { typed: 'NORMAL' };
  if (t.length === 1 && ['left', 'right'].includes(t[0])) return { dropped: true };
  return { generic: raw };
}
// ── the mobile baseOf tables (both twins identical), OLD vs NEW ──
function baseOf(k, NEW) {
  const u = k?.toUpperCase().replace(/-/g, '_');
  if (['FLEX_START', 'START', 'SELF_START', 'LEFT'].includes(u)) return 'START';
  if (['CENTER', 'ANCHOR_CENTER'].includes(u)) return 'CENTER';
  if (['FLEX_END', 'END', 'SELF_END', 'RIGHT'].includes(u)) return 'END';
  if (NEW && u === 'LAST_BASELINE') return 'END';                       // §4.2 safe self-end
  if (NEW && (u === 'BASELINE' || u === 'FIRST_BASELINE')) return 'START'; // §4.2 safe self-start
  return null;
}
function parseRaw(raw, NEW) {
  const t = raw.trim().toLowerCase().split(/\s+/).filter(Boolean);
  const safe = t.includes('safe');
  if (NEW) { const s = new Set(t); if (s.has('baseline')) return s.has('last') ? { base: 'END', safe: true } : { base: 'START', safe: true }; }
  const b = baseOf(t.find((x) => x !== 'safe' && x !== 'unsafe'), NEW);
  return b ? { base: b, safe } : null;
}
// resolveCross (Compose) / resolveSelf("AlignSelf") (iOS) on the OLD or NEW wire.
function resolveCross(c, NEW) {
  const typed = K(c, 'AlignSelf');
  const raw = generic(c, 'align-self');
  let t = typed; let g = raw;
  if (NEW && raw != null) { const w = newAlignSelfWire(raw); t = w.typed ?? null; g = w.generic ?? null; }
  // §4.2: a baseline fallback is `safe self-start|self-end` → safe true.
  if (t) { const b = baseOf(t, NEW); if (b) return { base: b, safe: NEW && /BASELINE/.test(t) }; }
  if (g != null) return parseRaw(g, NEW);
  return null;
}
// the Compose AlignItems fold → GridRenderer.alignItemsBase.
function foldAlignItems(k, NEW) {
  if (k === 'CENTER') return 'CENTER';
  if (['FLEX_START', 'START'].includes(k) || (NEW && k === 'SELF_START')) return 'START';
  if (['FLEX_END', 'END'].includes(k) || (NEW && k === 'SELF_END')) return 'END';
  return null;                                                           // STRETCH / BASELINE / absent
}
const factor = { START: 0, CENTER: 0.5, END: 1 };
const offset = (spec, child, box) => {
  const free = box - child;
  if (spec.safe && free < 0) return 0;
  return factor[spec.base] * free;
};
// box extents: declared content-box size + padding + border (content-box WPT default) + px margins.
function bandPx(c, vertical) {
  const a = vertical ? ['Top', 'Bottom'] : ['Left', 'Right'];
  let pad = 0; let bor = 0; let mar = 0;
  for (const s of a) {
    pad += P(c, `Padding${s}`) ?? 0;
    const st = K(c, `Border${s}Style`);
    if (st && !['NONE', 'HIDDEN'].includes(st)) bor += P(c, `Border${s}Width`) ?? 0;
    mar += P(c, `Margin${s}`) ?? 0;
  }
  return { pad, bor, mar };
}
const borderBox = (c) => K(c, 'BoxSizing') === 'BORDER_BOX';
function contentExtent(c, vertical) {
  const d = P(c, vertical ? 'Height' : 'Width'); if (d == null) return null;
  const b = bandPx(c, vertical);
  return borderBox(c) ? Math.max(0, d - b.pad - b.bor) : d;
}
function paddingExtent(c, vertical) {
  const d = P(c, vertical ? 'Height' : 'Width'); if (d == null) return null;
  const b = bandPx(c, vertical);
  return borderBox(c) ? Math.max(0, d - b.bor) : d + b.pad;
}
function marginBoxExtent(c, vertical) {
  const d = P(c, vertical ? 'Height' : 'Width'); if (d == null) return null;
  const b = bandPx(c, vertical);
  return (borderBox(c) ? d : d + b.pad + b.bor) + b.mar;
}
const padStart = (c, vertical) => P(c, vertical ? 'PaddingTop' : 'PaddingLeft') ?? 0;

// ── the walk ──
const out = { run: RUN, docs: 0, T1: [], FOLD: [], T2: [], T3: [], moves: [] };
// MOVES: the per-platform pixel delta (new − old, px) of every out-of-flow
// mark L7 moves — the input of png-replay.mjs. `path` names the mechanism.
const testOf = (section, file) => 'css/' + file.replace(/^wpt__/, '').replace(/\.json$/, '').split('__').join('/') + '.html';
const cellsOf = (section, test) => Object.fromEntries(['web', 'ios', 'android']
  .map((pf) => [pf, run.sections[section]?.cells.get(`${test}|${pf}`) ?? null]).filter(([, v]) => v)
  .map(([pf, v]) => [pf, `${v.pass ? 'P' : 'f'} ${v.ssim}`]));
for (const section of fs.readdirSync(SECTIONS).sort()) {
  const dir = path.join(SECTIONS, section, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const file of fs.readdirSync(dir).filter((f) => f.endsWith('.json')).sort()) {
    out.docs++;
    const doc = JSON.parse(fs.readFileSync(path.join(dir, file), 'utf8'));
    const comps = doc.components || [];
    const byId = new Map(comps.map((c) => [c.id, c]));
    const parentOf = (c) => byId.get(c.slot?.parent);
    const ancestors = (c) => { const a = []; for (let p = parentOf(c); p; p = parentOf(p)) a.push(p); return a; };
    const test = testOf(section, file);
    const row = (extra) => ({ section, test, ...extra, cells: cellsOf(section, test) });
    for (const c of comps) {
      const parent = parentOf(c);
      const pos = posType(c);
      const pd = parent ? K(parent, 'Display') : null;
      // ── T1: Compose grid overlay RC1 cancel ──
      if (pos === 'ABSOLUTE' && parent && ['GRID', 'INLINE_GRID'].includes(pd) && !hasAnyInset(c) &&
          !ancestors(c).some((a) => posType(a) !== 'STATIC')) {
        const cw = contentExtent(parent, false); const ch = contentExtent(parent, true);
        const xw = marginBoxExtent(c, false); const xh = marginBoxExtent(c, true);
        const blockSpec = (NEW) => resolveCross(c, NEW) ?? (foldAlignItems(K(parent, 'AlignItems'), NEW) ? { base: foldAlignItems(K(parent, 'AlignItems'), NEW), safe: false } : { base: 'START', safe: false });
        const js = K(c, 'JustifySelf'); const ji = K(parent, 'JustifyItems');
        const jb = (k) => ({ START: 'START', SELF_START: 'START', FLEX_START: 'START', LEFT: 'START', CENTER: 'CENTER', END: 'END', SELF_END: 'END', FLEX_END: 'END', RIGHT: 'END' })[k] ?? null;
        const inline = { base: jb(js) ?? jb(ji) ?? 'START', safe: false };
        const bo = blockSpec(false); const bn = blockSpec(true);
        // OLD: the 0×0 placeable aligned → ink origin at factor×content.
        const old = { x: inline.base === 'START' ? 0 : (cw == null ? null : factor[inline.base] * cw), y: bo.base === 'START' ? 0 : (ch == null ? null : factor[bo.base] * ch) };
        // NEW: the real reported box aligned → factor×(content−child) (safe-aware).
        // START needs no extents (offset 0); otherwise unknown extents stay null.
        const at = (spec, child, box) => spec.base === 'START' ? 0 : (child == null || box == null ? null : offset(spec, child, box));
        const nw = { x: at(inline, xw, cw), y: at(bn, xh, ch) };
        const moved = old.x !== nw.x || old.y !== nw.y;
        out.T1.push(row({ id: c.id, inline: inline.base, blockOld: bo.base, blockNew: bn.base, content: [cw, ch], child: [xw, xh], old, new: nw, moved }));
        if (moved && old.x != null && old.y != null && nw.x != null && nw.y != null) {
          out.moves.push({ section, test, platform: 'android', parent: parent.id, id: c.id, dx: nw.x - old.x, dy: nw.y - old.y, path: 'T1 grid overlay (Compose)' });
        }
      }
      // ── T3 on the grid overlay where the box is aligned by its REAL size
      //    already: iOS (every grid parent) and Compose under a positioned
      //    ancestor (no RC1) — only the block spec can change (baseline arms,
      //    dropped left/right, the SELF_* fold on Compose). ──
      if (pos === 'ABSOLUTE' && parent && ['GRID', 'INLINE_GRID'].includes(pd) && !hasAxisInset(c, true, false)) {
        const ch = contentExtent(parent, true); const xh = marginBoxExtent(c, true);
        const ai = K(parent, 'AlignItems');
        const iosItems = (k) => ({ CENTER: 'CENTER', FLEX_START: 'START', START: 'START', SELF_START: 'START', FLEX_END: 'END', END: 'END', SELF_END: 'END' })[k] ?? null;
        const spec = (NEW, items) => resolveCross(c, NEW) ?? (items ? { base: items, safe: false } : { base: 'START', safe: false });
        const y = (sp) => sp.base === 'START' ? 0 : (ch == null || xh == null ? null : offset(sp, xh, ch));
        const iosOld = y(spec(false, iosItems(ai))); const iosNew = y(spec(true, iosItems(ai)));
        if (iosOld != null && iosNew != null && iosOld !== iosNew) {
          out.moves.push({ section, test, platform: 'ios', parent: parent.id, id: c.id, dx: 0, dy: iosNew - iosOld, path: 'T3 grid overlay (iOS)' });
        }
        const positionedChain = ancestors(c).some((a) => posType(a) !== 'STATIC');
        if (positionedChain) {
          const aOld = y(spec(false, foldAlignItems(ai, false))); const aNew = y(spec(true, foldAlignItems(ai, true)));
          if (aOld != null && aNew != null && aOld !== aNew) {
            out.moves.push({ section, test, platform: 'android', parent: parent.id, id: c.id, dx: 0, dy: aNew - aOld, path: 'T3 positioned grid (Compose, overlay path assumed)' });
          }
        }
      }
      // ── FOLD: AlignItems SELF_END / SELF_START ──
      const ai = K(c, 'AlignItems');
      if (ai === 'SELF_END' || ai === 'SELF_START') {
        const kids = comps.filter((k) => k.slot?.parent === c.id);
        out.FOLD.push(row({ id: c.id, display: K(c, 'Display'), alignItems: ai, inFlowKids: kids.filter((k) => !['ABSOLUTE', 'FIXED'].includes(posType(k))).length, oofKids: kids.filter((k) => ['ABSOLUTE', 'FIXED'].includes(posType(k))).length }));
      }
      // ── T2: iOS flex overlay staticOffset ──
      if (pos === 'ABSOLUTE' && parent && ['FLEX', 'INLINE_FLEX'].includes(pd)) {
        const fd = K(parent, 'FlexDirection'); const wm = K(parent, 'WritingMode'); const dir = K(parent, 'Direction');
        const vw = ['VERTICAL_RL', 'VERTICAL_LR', 'SIDEWAYS_RL', 'SIDEWAYS_LR'].includes(wm);
        const rowLike = !fd || fd === 'ROW' || fd === 'ROW_REVERSE';
        const mainH = rowLike ? !vw : vw;
        const dirRev = fd === 'ROW_REVERSE' || fd === 'COLUMN_REVERSE';
        const blockRev = wm === 'VERTICAL_RL' || wm === 'SIDEWAYS_RL';
        const mainRev = (rowLike ? dir === 'RTL' : blockRev) !== dirRev;
        const crossRev = rowLike ? blockRev : dir === 'RTL';
        const jc = K(parent, 'JustifyContent');
        const jmap = { FLEX_START: 'START', START: 'START', LEFT: 'START', NORMAL: 'START', STRETCH: 'START', CENTER: 'CENTER', FLEX_END: 'END', END: 'END', RIGHT: 'END', SPACE_BETWEEN: 'START', SPACE_AROUND: 'CENTER', SPACE_EVENLY: 'CENTER' };
        const gj = generic(parent, 'justify-content');
        const mainSpec = (jc && jmap[jc] ? { base: jmap[jc], safe: false } : null) ?? (gj ? parseRaw(gj, false) : null) ?? { base: 'START', safe: false };
        const own = (NEW) => resolveCross(c, NEW);
        const selfDeclared = (NEW) => {
          const t = K(c, 'AlignSelf'); const g = generic(c, 'align-self');
          if (NEW && g != null) { const w = newAlignSelfWire(g); return (w.typed && w.typed !== 'AUTO') || w.generic != null; }
          return (t && t !== 'AUTO') || g != null;
        };
        const aiBase = (() => { const k = K(parent, 'AlignItems'); return ({ CENTER: 'CENTER', FLEX_START: 'START', START: 'START', SELF_START: 'START', FLEX_END: 'END', END: 'END', SELF_END: 'END' })[k] ?? null; })();
        const crossSpec = (NEW) => own(NEW) ?? (NEW && !selfDeclared(true) && aiBase ? { base: aiBase, safe: false } : null);
        const axis = (vertical, NEW) => {
          const isMain = vertical === !mainH;
          const inset = hasAxisInset(c, vertical, true);
          const spec = inset ? null : (isMain ? mainSpec : crossSpec(NEW));
          const rev = isMain ? mainRev : crossRev;
          const child = marginBoxExtent(c, vertical);
          const box = NEW ? contentExtent(parent, vertical) : paddingExtent(parent, vertical);
          let off = 0;
          if (spec && child != null && box != null) { const l = offset(spec, child, box); off = rev ? (box - child) - l : l; }
          if (NEW && !inset) off += padStart(parent, vertical);
          return off;
        };
        const old = { x: axis(false, false), y: axis(true, false) }; const nw = { x: axis(false, true), y: axis(true, true) };
        const heightIndef = P(parent, 'Height') == null;
        out.T2.push(row({ id: c.id, old, new: nw, moved: old.x !== nw.x || old.y !== nw.y, heightIndefinite: heightIndef, alignItems: K(parent, 'AlignItems') }));
        if (old.x !== nw.x || old.y !== nw.y) {
          out.moves.push({ section, test, platform: 'ios', parent: parent.id, id: c.id, dx: nw.x - old.x, dy: nw.y - old.y, path: 'T2 flex overlay (iOS)' });
        }
      }
      // ── T3: Generic align-self anywhere + typed BASELINE on an out-of-flow child ──
      const g = generic(c, 'align-self'); const ta = K(c, 'AlignSelf');
      if (g != null || (ta === 'BASELINE' && ['ABSOLUTE', 'FIXED'].includes(pos))) {
        const oof = ['ABSOLUTE', 'FIXED'].includes(pos);
        const w = g != null ? newAlignSelfWire(g) : { typed: 'BASELINE', preTyped: true };
        out.T3.push(row({ id: c.id, raw: g ?? `typed ${ta}`, wireNew: w, outOfFlow: oof, parentDisplay: pd, parentAlignItems: parent ? K(parent, 'AlignItems') : null,
          mobileOld: oof ? resolveCross(c, false)?.base ?? null : 'n/a', mobileNew: oof ? resolveCross(c, true)?.base ?? null : 'n/a' }));
      }
    }
  }
}
// ── summary ──
const docsOf = (rows) => [...new Set(rows.map((r) => r.test))];
const summary = {
  T1: { carriers: out.T1.length, docs: docsOf(out.T1).length, movedComponents: out.T1.filter((r) => r.moved).length, movedDocs: docsOf(out.T1.filter((r) => r.moved)).length, indefinite: out.T1.filter((r) => r.new.x == null || r.new.y == null).length },
  FOLD: { components: out.FOLD.length, docs: docsOf(out.FOLD).length, byDisplay: out.FOLD.reduce((m, r) => ((m[r.display ?? 'BLOCK'] = (m[r.display ?? 'BLOCK'] || 0) + 1), m), {}) },
  T2: { carriers: out.T2.length, docs: docsOf(out.T2).length, movedComponents: out.T2.filter((r) => r.moved).length, movedDocs: docsOf(out.T2.filter((r) => r.moved)).length },
  T3: { components: out.T3.length, docs: docsOf(out.T3).length, inFlow: out.T3.filter((r) => !r.outOfFlow).length,
    byRaw: out.T3.reduce((m, r) => ((m[r.raw] = (m[r.raw] || 0) + 1), m), {}),
    mobileMoved: out.T3.filter((r) => r.outOfFlow && r.mobileOld !== r.mobileNew).length },
};
summary.moves = ['ios', 'android'].map((pf) => ({ platform: pf, components: out.moves.filter((m) => m.platform === pf).length,
  docs: docsOf(out.moves.filter((m) => m.platform === pf)).length }));
out.summary = summary;
fs.writeFileSync(path.join(HERE, 'static-position.census.json'), JSON.stringify(out, null, 1) + '\n');
console.log(JSON.stringify({ run: RUN, docs: out.docs, ...summary }, null, 1));
