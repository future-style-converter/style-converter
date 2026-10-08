#!/usr/bin/env node
// tools/titan/results/wave54-plan/rtl-marker-bake.census.mjs
//
// Wave-54 plan, family rtl-marker-bake (BACKLOG 0(a)(1) / 2(c⁴)). Read-only census over the per-test IR of a gate
// run (the wire every runtime rendered) — no images, no device, no build. It answers the three questions the re-do
// brief needs:
//   (1) THE COMPOSE STACKING SHAPE. An out-of-flow parent (Position ABSOLUTE | FIXED | STICKY — the set
//       PositionedParentFlowSlot.COLUMN_LOOP_CONTAINING_BLOCKS routes through ComponentRenderer's block Column loop)
//       with ≥ 2 out-of-flow children, in a document whose CanvasRootHoist.Host does NOT activate
//       (hostActivates = anyOutOfFlowBox: some box hoists or is a no-inset static-position absolute with NO
//       positioned ancestor). Under an inactive host LocalActive is false, so the Column loop never applies
//       PositionedParentFlowSlot.mount and every out-of-flow child keeps its full flow footprint: child k lands
//       at cursor Σ heights(0..k-1) + its own top. The activation walk here MIRRORS CanvasRootHoist.anyOutOfFlowBox
//       (shouldHoistToCanvasRoot ∪ rendersInFlowAsStaticPosition) with two stated approximations: the multicol
//       spanner chain restart is not modelled, and "transform CB" is read as a Transform / Perspective /
//       TransformStyle(preserve-3d) / WillChange(transform) declaration.
//   (2) HUNK M carriers: meta.markerText on an out-of-flow box, and the bidi-bake BOXES that are list items.
//   (3) HUNK P carriers: bidi-bake roots (rootProperties signature: Direction LTR + UnicodeBidi NORMAL + TextAlign
//       LEFT + BoxSizing BORDER_BOX) whose Padding* is non-zero on the wire.
// Usage: node tools/titan/results/wave54-plan/rtl-marker-bake.census.mjs [run=wave53-final] > rtl-marker-bake.census.json
import { readdirSync, readFileSync, existsSync } from 'node:fs';
import path from 'node:path';

// Repo root: four levels above this file (tools/titan/results/wave54-plan/).
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../..');
// The run whose wire is censused; the gate of record by default.
const run = process.argv[2] ?? 'wave53-final';
const secDir = path.join(ROOT, 'tools/titan/runs', run, 'sections');
// Last declaration of a type wins (the decoder's own rule for duplicated types).
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
// Keyword normaliser: the wire spells keywords upper-case, the fixtures lower-case with hyphens.
const up = (v) => String(v ?? '').toUpperCase().replace(/-/g, '_');
const posOf = (c) => up(prop(c, 'Position')) || 'STATIC';
const OOF = new Set(['ABSOLUTE', 'FIXED']);
// PositionedParentFlowSlot.COLUMN_LOOP_CONTAINING_BLOCKS.
const COLUMN_LOOP_CB = new Set(['ABSOLUTE', 'FIXED', 'STICKY']);
// CanvasRootHoist.hasAnyInset: any physical or logical inset declared.
const INSET_TYPES = ['Left', 'Right', 'Top', 'Bottom', 'InsetInlineStart', 'InsetInlineEnd', 'InsetBlockStart',
  'InsetBlockEnd', 'Inset', 'InsetInline', 'InsetBlock'];
const hasInset = (c) => INSET_TYPES.some((t) => prop(c, t) !== undefined);
// Approximation of TransformContainingBlock.establishes (stated in the banner).
const transformCb = (c) => {
  const t = prop(c, 'Transform');
  if (t !== undefined && up(typeof t === 'string' ? t : 'x') !== 'NONE') return true;
  if (prop(c, 'Perspective') !== undefined && up(prop(c, 'Perspective')) !== 'NONE') return true;
  if (up(prop(c, 'TransformStyle')) === 'PRESERVE_3D') return true;
  return JSON.stringify(prop(c, 'WillChange') ?? '').toLowerCase().includes('transform');
};
// A Padding* side is non-zero when its px is non-zero or it carries an unresolved original (em, ch, %).
const padSides = (c) => ['PaddingTop', 'PaddingRight', 'PaddingBottom', 'PaddingLeft'].map((t) => prop(c, t));
const nonZeroPad = (d) => d !== undefined && (d.px === undefined ? !!d.original && d.original.v !== 0 : d.px !== 0);

const out = {
  run, docs: 0, hostInactiveDocs: 0,
  stackShape: [],              // (1) every out-of-flow parent with ≥2 out-of-flow children, host state + section
  markerTextAndOutOfFlow: [],  // (2a) meta.markerText on an ABSOLUTE|FIXED box
  bakeBoxListItems: [],        // (2b) bidi-bake BOX list items (non-root, sourceTag li, inside a bake root)
  bakeDocs: [],                // (3) every doc with a bake root: host state, roots, padded roots, multi-run boxes
};
for (const sec of readdirSync(secDir).sort()) {
  const irDir = path.join(secDir, sec, 'per-test-ir');
  if (!existsSync(irDir)) continue;
  for (const f of readdirSync(irDir).filter((x) => x.endsWith('.json')).sort()) {
    const doc = JSON.parse(readFileSync(path.join(irDir, f), 'utf8'));
    out.docs++;
    const stem = f.replace(/\.json$/, '');
    const comps = Array.isArray(doc.components) ? doc.components : Object.values(doc.components || {});
    const byId = new Map(comps.map((c) => [c.id, c]));
    // Flat v2 wire: children by slot.parent, in wire (document) order.
    const kids = new Map();
    for (const c of comps) {
      const p = c.slot?.parent;
      if (p && byId.has(p)) (kids.get(p) ?? kids.set(p, []).get(p)).push(c);
    }
    const roots = comps.filter((c) => !c.slot?.parent || !byId.has(c.slot.parent));
    // (1) CanvasRootHoist.anyOutOfFlowBox, mirrored: hoist (FIXED w/o transformed ancestor; ABSOLUTE w/o positioned
    // or transformed ancestor and with an inset) or static-position (ABSOLUTE, no positioned ancestor, no inset).
    const activates = (function walk(list, ancPos, ancTf) {
      for (const n of list) {
        const p = posOf(n);
        if (p === 'FIXED' && !ancTf) return true;
        if (p === 'ABSOLUTE' && !ancPos && !ancTf && hasInset(n)) return true;
        if (p === 'ABSOLUTE' && !ancPos && !hasInset(n)) return true;
        // Children see a positioned ancestor if one existed or this node establishes a containing block.
        const childPos = ancPos || p !== 'STATIC' || transformCb(n);
        if (walk(kids.get(n.id) ?? [], childPos, ancTf || transformCb(n))) return true;
      }
      return false;
    })(roots, false, false);
    if (!activates) out.hostInactiveDocs++;
    for (const c of comps) {
      const ch = kids.get(c.id) ?? [];
      const oofKids = ch.filter((k) => OOF.has(posOf(k)));
      if (COLUMN_LOOP_CB.has(posOf(c)) && oofKids.length >= 2) {
        out.stackShape.push({ section: sec, stem, id: c.id, sourceTag: c.meta?.sourceTag ?? null, hostActivates: activates,
          oofChildren: oofKids.length, inFlowChildren: ch.length - oofKids.length,
          childTexts: oofKids.map((k) => k.text ?? null) });
      }
      if (typeof c.meta?.markerText === 'string' && OOF.has(posOf(c))) {
        out.markerTextAndOutOfFlow.push({ section: sec, stem, id: c.id, markerText: c.meta.markerText });
      }
    }
    // (3) bidi-bake roots by the rootProperties wire signature.
    const isBakeRoot = (c) => up(prop(c, 'Direction')) === 'LTR' && up(prop(c, 'UnicodeBidi')) === 'NORMAL'
      && up(prop(c, 'TextAlign')) === 'LEFT' && up(prop(c, 'BoxSizing')) === 'BORDER_BOX';
    const bakeRoots = comps.filter(isBakeRoot);
    if (!bakeRoots.length) continue;
    const under = (c, r) => { for (let p = byId.get(c.slot?.parent); p; p = byId.get(p.slot?.parent)) if (p === r) return true; return false; };
    const inBake = (c) => bakeRoots.some((r) => under(c, r));
    // A baked BOX: an out-of-flow non-root component inside a bake root; a RUN: a text leaf with WhiteSpace PRE there.
    const boxes = comps.filter((c) => inBake(c) && !isBakeRoot(c) && OOF.has(posOf(c)) && typeof c.text !== 'string');
    const multiRunBoxes = boxes.map((b) => ({ id: b.id, sourceTag: b.meta?.sourceTag ?? null,
      runs: (kids.get(b.id) ?? []).filter((k) => typeof k.text === 'string' && OOF.has(posOf(k))).map((k) => k.text) }))
      .filter((b) => b.runs.length >= 2);
    for (const b of boxes) if (b.meta?.sourceTag === 'li') {
      out.bakeBoxListItems.push({ section: sec, stem, id: b.id, markerText: b.meta?.markerText ?? null,
        listStyleType: prop(b, 'ListStyleType') ?? null });
    }
    out.bakeDocs.push({ section: sec, stem, hostActivates: activates, roots: bakeRoots.length,
      paddedRoots: bakeRoots.filter((r) => padSides(r).some(nonZeroPad)).map((r) => ({ id: r.id, sourceTag: r.meta?.sourceTag ?? null,
        padding: padSides(r).map((d) => (d === undefined ? null : d.px ?? d.original)) })),
      boxes: boxes.length, multiRunBoxes });
  }
}
// Summary counts, so the brief can quote one line.
out.summary = {
  stackShapeParents: out.stackShape.length,
  stackShapeHostInactive: out.stackShape.filter((s) => !s.hostActivates).length,
  stackShapeHostInactiveDocs: [...new Set(out.stackShape.filter((s) => !s.hostActivates).map((s) => `${s.section}/${s.stem}`))],
  bakeDocs: out.bakeDocs.length,
  bakeDocsHostInactive: out.bakeDocs.filter((d) => !d.hostActivates).map((d) => d.stem),
  bakeDocsWithMultiRunBox: out.bakeDocs.filter((d) => d.multiRunBoxes.length).map((d) => `${d.stem} (${d.multiRunBoxes.length})`),
  paddedRoots: out.bakeDocs.reduce((n, d) => n + d.paddedRoots.length, 0),
  paddedRootDocs: out.bakeDocs.filter((d) => d.paddedRoots.length).map((d) => d.stem),
};
console.log(JSON.stringify(out, null, 1));
