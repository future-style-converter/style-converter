#!/usr/bin/env node
// Wave 52 (lane L3 · failure-ink) — the corpus census behind F1 / F2 / F3
// over every wave51-fix per-test IR document (v2 flat list, parent =
// slot.parent), re-derivable by a skeptic with `node census.mjs [run]`.
//   F1  the vertical block-flow / multicol seam's BAKED-LAYOUT guard:
//       carriers = vertical-writing-mode block containers with an in-flow
//       Width+Height child; old guard (Width ∧ Height) vs new guard
//       (BakedLayoutSignature: Width ∧ Height ∧ BoxSizing ∧ (PaddingTop ∨
//       BorderTopStyle)), each against the manifest's postLoadExtracted stamp.
//       Plus the corpus-wide separation check of the signature itself over
//       EVERY Width+Height component (false positives / negatives).
//   F2  Display=CONTENTS components, and those with a non-none Float.
//   F3  ColumnSpan=ALL components with an ABSOLUTE/FIXED descendant.
//   F1' the Swift fourth site (VerticalMulticolPlan sole-child gate):
//       vertical multicol containers whose sole in-flow child is Width+Height;
//       F1'-used re-reads it with the call site's own gates (used writing
//       mode, flow-sibling count, every non-spanner child) — census.json f1swiftUsed.
import { readdirSync, readFileSync, existsSync, writeFileSync } from 'node:fs';
import { join, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

const here = dirname(fileURLToPath(import.meta.url));
const run = process.argv[2] ?? 'wave51-fix';
const root = join(here, '../../runs', run, 'sections');
// The predicate under test — mirrors BakedLayoutSignature.{kt,swift} exactly.
const has = (c, t) => c.properties.some(p => p.type === t);
const oldGuard = c => has(c, 'Width') && has(c, 'Height');
const newGuard = c => oldGuard(c) && has(c, 'BoxSizing') && (has(c, 'PaddingTop') || has(c, 'BorderTopStyle'));
const kw = (c, t) => { const p = c.properties.filter(x => x.type === t).at(-1); return typeof p?.data === 'string' ? p.data.toUpperCase() : (p?.data?.type ?? p?.data?.keyword ?? null); };
const VERTICAL = new Set(['VERTICAL_RL', 'VERTICAL_LR', 'SIDEWAYS_RL', 'SIDEWAYS_LR']);
const INLINE_LEVEL = new Set(['INLINE', 'INLINE_BLOCK', 'INLINE_FLEX', 'INLINE_GRID', 'INLINE_TABLE']);
const outOfFlow = c => ['ABSOLUTE', 'FIXED'].includes(kw(c, 'Position'));
// The seams' own inline-level set (Compose INLINE_LEVEL_DISPLAY_KEYWORDS, iOS `inlineLevel`).
const SEAM_INLINE_LEVEL = new Set(['INLINE', 'INLINE_BLOCK', 'INLINE_FLEX', 'INLINE_GRID']);
// Display keywords whose container never reaches the block arm (flex/grid/table/inline/none arms).
const NON_BLOCK_PATH = new Set(['FLEX', 'INLINE_FLEX', 'GRID', 'INLINE_GRID', 'TABLE', 'INLINE_TABLE', 'NONE', 'INLINE']);
// HTML table boxes the table path consumes (Compose rowsOf/consumableRowList, iOS tableTrackPlan).
const TABLE_TAGS = new Set(['table', 'tbody', 'thead', 'tfoot', 'tr']);
// Used writing mode = the nearest self-or-ancestor WritingMode declaration (it inherits).
const usedWm = (c, byId) => { for (let k = c; k; k = byId.get(k.slot?.parent)) { const w = kw(k, 'WritingMode'); if (w) return w; } return null; };
// Compose's UA table fold (TableBoxTree.consumableRowList): from a `<tr>`, find the
// enclosing undeclared `<table>` (through one row group) and require EVERY spliced
// row of it — group children inlined, col/colgroup dropped — to be an undeclared `<tr>`.
const GROUP_TAGS = new Set(['tbody', 'thead', 'tfoot']);
const tableFolds = (tr, byId, kids) => {
  let t = byId.get(tr.slot?.parent);
  if (t && GROUP_TAGS.has(t.meta?.sourceTag)) t = byId.get(t.slot?.parent);
  if (!t || t.meta?.sourceTag !== 'table' || has(t, 'Display')) return false;
  const rows = (kids.get(t.id) ?? []).flatMap(k => GROUP_TAGS.has(k.meta?.sourceTag) ? (kids.get(k.id) ?? []) : ['col', 'colgroup'].includes(k.meta?.sourceTag) ? [] : [k]);
  return rows.length > 0 && rows.every(r => r.meta?.sourceTag === 'tr' && !has(r, 'Display'));
};

const out = { run, docs: 0, f1: { carriers: [], oldGuardTrue: 0, newGuardTrue: 0, bakedStayGuarded: 0, bakedSlipThrough: 0, authoredUnguarded: 0, authoredStillGuarded: 0 },
  signature: { widthHeightComponents: 0, baked: { newTrue: 0, newFalse: 0 }, authored: { newTrue: 0, newFalse: 0 }, authoredNewTrue: [] },
  f2: { displayContents: 0, floated: [] }, f3: { spanners: 0, spannerWithPositionedDescendant: [] },
  f1swift: { verticalMulticolSoleChild: [] }, f1swiftUsed: [], f1Used: { containers: [], movers: [], rowConsumed: [] } };

for (const sec of readdirSync(root)) {
  const manifestPath = join(root, sec, 'manifest.json');
  if (!existsSync(manifestPath)) continue;
  const manifest = JSON.parse(readFileSync(manifestPath, 'utf8'));
  const results = manifest.wpt?.results ?? {};
  const irDir = join(root, sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  for (const file of readdirSync(irDir).filter(f => f.endsWith('.json'))) {
    out.docs++;
    const doc = JSON.parse(readFileSync(join(irDir, file), 'utf8'));
    const comps = doc.components ?? [];
    // Manifest join: per-test-ir `wpt__<sec>__<path>.json` ↔ `css/<sec>/<path>.html`.
    const key = 'css/' + file.replace(/^wpt__/, '').replace(/\.json$/, '.html').replace(/__/g, '/');
    const res = results[key] ?? {};
    const baked = res.postLoadExtracted === true;
    const verdict = plat => { const d = res.browserRef?.diffs?.[`${plat}-ref`]; return d ? { pass: d.wptPass ?? null, ssim: d.ssim ?? null } : null; };
    const cell = { test: key, postLoadExtracted: baked, web: verdict('web'), ios: verdict('ios'), android: verdict('android') };
    const byId = new Map(comps.map(c => [c.id, c]));
    const kids = new Map();
    for (const c of comps) { const p = c.slot?.parent ?? null; if (!kids.has(p)) kids.set(p, []); kids.get(p).push(c); }
    // Signature separation over every Width+Height component.
    for (const c of comps) if (oldGuard(c)) {
      out.signature.widthHeightComponents++;
      const b = baked ? out.signature.baked : out.signature.authored;
      if (newGuard(c)) { b.newTrue++; if (!baked) out.signature.authoredNewTrue.push({ test: key, component: c.id }); } else b.newFalse++;
    }
    for (const c of comps) {
      const children = kids.get(c.id) ?? [];
      // F1 carriers (the brief's recipe): vertical container, no own text, ≥1 in-flow child,
      // no inline-level Display child, no child declaring WritingMode, ≥1 in-flow Width+Height child.
      if (VERTICAL.has(kw(c, 'WritingMode')) && !(c.text?.length) && children.length) {
        const inflow = children.filter(k => !outOfFlow(k));
        const inlineKid = children.some(k => INLINE_LEVEL.has((kw(k, 'Display') ?? '').replace(/-/g, '_')));
        const orthogonal = inflow.some(k => has(k, 'WritingMode'));
        const wh = inflow.filter(oldGuard);
        if (inflow.length && !inlineKid && !orthogonal && wh.length) {
          const oldT = wh.some(oldGuard), newT = wh.some(newGuard);
          out.f1.carriers.push({ ...cell, component: c.id, containerDisplay: kw(c, 'Display'), inflow: inflow.length, widthHeightKids: wh.length, oldGuard: oldT, newGuard: newT });
          if (oldT) out.f1.oldGuardTrue++; if (newT) out.f1.newGuardTrue++;
          if (baked) { if (newT) out.f1.bakedStayGuarded++; else out.f1.bakedSlipThrough++; }
          else { if (newT) out.f1.authoredStillGuarded++; else out.f1.authoredUnguarded++; }
        }
      }
      // F2: display: contents, with/without a float.
      if (kw(c, 'Display') === 'CONTENTS') {
        out.f2.displayContents++;
        const f = kw(c, 'Float');
        if (f && f !== 'NONE') out.f2.floated.push({ ...cell, component: c.id, float: f, text: c.text ?? null });
      }
      // F3: a spanner with a positioned descendant (any depth).
      if (kw(c, 'ColumnSpan') === 'ALL') {
        out.f3.spanners++;
        const stack = [...children]; let positioned = 0;
        while (stack.length) { const k = stack.pop(); if (outOfFlow(k)) positioned++; stack.push(...(kids.get(k.id) ?? [])); }
        if (positioned) out.f3.spannerWithPositionedDescendant.push({ ...cell, component: c.id, positionedDescendants: positioned });
      }
      // F1' (Swift VerticalMulticolPlan sole-child gate): a vertical multicol container.
      if ((has(c, 'ColumnCount') || has(c, 'ColumnWidth')) && VERTICAL.has(kw(c, 'WritingMode'))) {
        const inflow = children.filter(k => !outOfFlow(k) && kw(k, 'ColumnSpan') !== 'ALL');
        if (inflow.length === 1 && oldGuard(inflow[0]))
          out.f1swift.verticalMulticolSoleChild.push({ ...cell, component: c.id, oldGuard: true, newGuard: newGuard(inflow[0]) });
      }
      // F1'-used (fix pass, 2026-10-05 — the skeptic's must-fix on the hand-off hunk): the
      // REAL call site (ComponentRenderer.swift fragmentPlan, inside the per-child loop) reads
      // the USED writing mode (WritingModeExtractor over resolvedProperties), counts FLOW
      // siblings under capture (MulticolSpannerFlow.flowCount: not ABSOLUTE/FIXED, not span
      // ALL), and evaluates the bake gate for EVERY child it renders — the spanner returns nil
      // before the gate (ColumnsApplier.fragmentPlan's ColumnSpan check), so all the others.
      // isMulticolContainer = numeric ColumnCount or a px ColumnWidth (ColumnsConfig).
      const multicol = c.properties.some(p => (p.type === 'ColumnCount' && typeof p.data === 'number') || (p.type === 'ColumnWidth' && typeof p.data?.px === 'number'));
      if (multicol && VERTICAL.has(usedWm(c, byId))) {
        const flow = children.filter(k => !outOfFlow(k) && kw(k, 'ColumnSpan') !== 'ALL');
        if (flow.length === 1) for (const k of children.filter(k => kw(k, 'ColumnSpan') !== 'ALL'))
          if (oldGuard(k)) out.f1swiftUsed.push({ ...cell, container: c.id, child: k.id, childFlow: !outOfFlow(k), oldGuard: true, newGuard: newGuard(k) });
      }
      // F1-used (resume pass, 2026-10-05): the brief's recipe above reads the
      // container's OWN WritingMode, but both real seams read the USED one —
      // Compose `extractWritingModeConfig` over the merged (inherited) view,
      // iOS `WritingModeExtractor.extract(from: resolvedProperties)` — so an
      // ancestor's declaration counts. This pass mirrors the seams' gates
      // exactly (4-keyword inline-level set, own-WritingMode orthogonal child,
      // text-only-leaves, no own text) and records every container where the
      // OLD guard held, so a skeptic sees the inherited carriers the recipe hid.
      const used = usedWm(c, byId);
      if (VERTICAL.has(used) && !(c.text?.length) && children.length) {
        const inflow = children.filter(k => !outOfFlow(k));
        const display = kw(c, 'Display');
        const otherPath = NON_BLOCK_PATH.has(display) || has(c, 'ColumnCount') || has(c, 'ColumnWidth');
        const inlineKid = inflow.some(k => SEAM_INLINE_LEVEL.has((kw(k, 'Display') ?? '').replace(/-/g, '_')));
        const orthogonal = inflow.some(k => has(k, 'WritingMode'));
        const textLeaves = inflow.length && inflow.every(k => !(kids.get(k.id)?.length) && k.text?.length && !has(k, 'Display'));
        const oldT = inflow.some(oldGuard), newT = inflow.some(newGuard);
        if (inflow.length && !inlineKid && !orthogonal && !textLeaves && oldT) {
          const tag = c.meta?.sourceTag ?? null;
          // A `<tr>` never reaches either seam when its table is consumed by the
          // table path: iOS `tableTrackPlan` gives role .row an `.inlineRow`
          // track BEFORE the Z2 branch; Compose `RenderTableContent` renders rows
          // as TableRow (never RenderComponent) once the UA fold accepts the
          // table (`consumableRowList`: every spliced row is a `<tr>`).
          const rowConsumed = tag === 'tr' && !has(c, 'Display') && tableFolds(c, byId, kids);
          const row = { ...cell, component: c.id, sourceTag: tag, containerDisplay: display, ownWm: VERTICAL.has(kw(c, 'WritingMode')),
            otherPath, tableInternal: TABLE_TAGS.has(tag), rowConsumed, childTags: inflow.map(k => k.meta?.sourceTag ?? null), oldGuard: oldT, newGuard: newT };
          out.f1Used.containers.push(row);
          // A MOVER: the seam declined before (old guard) and engages now.
          if (!newT && !otherPath) (rowConsumed ? out.f1Used.rowConsumed : out.f1Used.movers).push(row);
        }
      }
    }
  }
}
writeFileSync(join(here, 'census.json'), JSON.stringify(out, null, 2) + '\n');
const f1 = out.f1;
console.log(`docs ${out.docs}`);
console.log(`F1 carriers ${f1.carriers.length}: oldGuard true ${f1.oldGuardTrue}, newGuard true ${f1.newGuardTrue}; baked stay guarded ${f1.bakedStayGuarded}, baked slip through ${f1.bakedSlipThrough}, authored unguarded ${f1.authoredUnguarded}, authored still guarded ${f1.authoredStillGuarded}`);
for (const c of f1.carriers) console.log(`   ${c.test} baked=${c.postLoadExtracted} display=${c.containerDisplay} old=${c.oldGuard} new=${c.newGuard} ios=${c.ios?.pass}/${c.ios?.ssim} android=${c.android?.pass}/${c.android?.ssim}`);
console.log(`signature over ${out.signature.widthHeightComponents} Width+Height components: baked new=true ${out.signature.baked.newTrue} / new=false ${out.signature.baked.newFalse}; authored new=true ${out.signature.authored.newTrue} / new=false ${out.signature.authored.newFalse}`);
console.log(`F2 display:contents ${out.f2.displayContents}, floated ${out.f2.floated.length}: ${out.f2.floated.map(x => x.test).join(', ')}`);
console.log(`F3 spanners ${out.f3.spanners}, with positioned descendant ${out.f3.spannerWithPositionedDescendant.length}: ${out.f3.spannerWithPositionedDescendant.map(x => x.test).join(', ')}`);
console.log(`F1' vertical multicol sole Width+Height child ${out.f1swift.verticalMulticolSoleChild.length}: ${out.f1swift.verticalMulticolSoleChild.map(x => `${x.test} new=${x.newGuard}`).join(', ')}`);
// The hand-off hunk's real movement: an old-guarded child the NEW predicate no longer guards.
const sw = out.f1swiftUsed, swMovers = sw.filter(x => !x.newGuard);
console.log(`F1'-used (call-site gates) old-guarded children ${sw.length}, new guard true ${sw.length - swMovers.length}, MOVERS ${swMovers.length}${sw.map(x => `\n   ${x.newGuard ? 'stay ' : 'MOVER'} ${x.test} ${x.child} flow=${x.childFlow} baked=${x.postLoadExtracted} ios=${x.ios?.pass}/${x.ios?.ssim}`).join('')}`);
// The used-writing-mode pass: every old-guarded container, then the movers.
const u = out.f1Used;
console.log(`F1-used old-guarded containers ${u.containers.length} (own-WM ${u.containers.filter(x => x.ownWm).length}, inherited-only ${u.containers.filter(x => !x.ownWm).length}); new guard true ${u.containers.filter(x => x.newGuard).length}; movers (block arm, now engaged) ${u.movers.length}; consumed <tr> rows (no seam on either native) ${u.rowConsumed.length}`);
for (const c of u.containers) console.log(`   ${c.newGuard ? 'stay ' : c.otherPath ? 'other' : c.rowConsumed ? 'row  ' : 'MOVER'} ${c.test} ${c.component.slice(-14)} tag=${c.sourceTag} display=${c.containerDisplay} ownWm=${c.ownWm} table=${c.tableInternal} baked=${c.postLoadExtracted} kids=${c.childTags.join('/')} ios=${c.ios?.pass}/${c.ios?.ssim} android=${c.android?.pass}/${c.android?.ssim}`);
