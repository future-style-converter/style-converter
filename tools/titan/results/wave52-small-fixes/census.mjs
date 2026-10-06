#!/usr/bin/env node
// wave52-small-fixes/census.mjs — lane L4's OWN blast-radius census over the
// 1435 wave51-fix per-test IR documents, re-deriving the four numbers the
// briefs quote (native-near-misses §4 T5/T7 = 1 carrier each; compose-
// containing-block-level §5 b″ = 4 carriers; b′ = 0 movers) with a script
// that shares no code with the planning censuses. READ-ONLY over the run
// dir; writes census.json beside itself.
//   node tools/titan/results/wave52-small-fixes/census.mjs
//   RUN_DIR=tools/titan/runs/<other> node …
import fs from 'node:fs';
import path from 'node:path';
const HERE = path.dirname(new URL(import.meta.url).pathname);
const ROOT = path.resolve(HERE, '../../../..');
const RUN = process.env.RUN_DIR ? path.resolve(process.env.RUN_DIR) : path.join(ROOT, 'tools/titan/runs/wave51-fix');
const SECTIONS = path.join(RUN, 'sections');

// ── corpus walk ─────────────────────────────────────────────────────────────
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
const prop = (c, t) => (c.properties || []).filter(p => p.type === t).at(-1)?.data;
const has = (c, t) => (c.properties || []).some(p => p.type === t);

// ── T5: backface-visibility: hidden × transform-style ───────────────────────
// BackfaceCull.isBackFacing transcribed: sum of rotateX / rotateY degrees in
// the `transform` list plus the Rotate longhand's axis form; |normalised| > 90.
function backFacing(c) {
  const list = prop(c, 'Transform')?.list || [];
  let rx = 0, ry = 0;
  for (const fn of list) {
    const d = fn?.a?.deg ?? fn?.angle?.deg ?? 0;
    if (/^rotatex$/i.test(fn.fn)) rx += d;
    if (/^rotatey$/i.test(fn.fn)) ry += d;
  }
  const norm = v => { let d = v % 360; if (d > 180) d -= 360; if (d < -180) d += 360; return d; };
  return Math.abs(norm(rx)) > 90 || Math.abs(norm(ry)) > 90;
}
// Own box decoration a culled preserve-3d element would still paint on iOS
// (the documented approximation) — background, painted border side, text —
// plus (fix pass 2026-10-05) the two parts Compose keeps too: box-shadow
// (BackfaceCull.SHADOW_KEPT_BREADCRUMB) and outline (stripped on Compose).
function ownDecoration(c) {
  const bg = prop(c, 'BackgroundColor');
  const bgPaints = bg && !(bg.srgb && bg.srgb.a === 0);
  const border = ['Top', 'Right', 'Bottom', 'Left'].some(s => {
    const st = prop(c, `Border${s}Style`); const w = prop(c, `Border${s}Width`);
    return st && st !== 'NONE' && st !== 'HIDDEN' && (w?.px ?? 3) > 0;
  });
  // An outline paints only with a visible style (css-ui-4 §3: `none` = no outline).
  const os = prop(c, 'OutlineStyle');
  return { background: !!bgPaints, backgroundImage: has(c, 'BackgroundImage'), border, text: !!c.text,
    boxShadow: has(c, 'BoxShadow'), outline: !!os && os !== 'NONE' };
}

// ── b″: percentage margin / padding, both containing-block levels ────────────
// DynamicValueResolver.childContainingBlock transcribed for px + percentage-
// of-known-parent sizes; raw `px` padding/border bands subtracted only.
const SPACING = /^(Margin|Padding)(Top|Right|Bottom|Left|BlockStart|BlockEnd|InlineStart|InlineEnd)$/;
const isPct = d => typeof d === 'number' || (d && typeof d === 'object' && d.type === 'percentage');
const pctOf = d => typeof d === 'number' ? d : d.value ?? d.percentage;
const pxOf = (c, ...types) => { for (const t of types) { const d = prop(c, t); if (d && typeof d === 'object' && typeof d.px === 'number') return d.px; } return null; };
const sizeOf = (c, base, ...types) => {
  const px = pxOf(c, ...types); if (px !== null) return px;
  for (const t of types) { const d = prop(c, t); if (d && typeof d === 'object' && d.type === 'percentage' && typeof d.value === 'number') return base === null ? null : d.value * base / 100; }
  return null;
};
function childCb(c, parent) {
  const w = sizeOf(c, parent[0], 'Width', 'InlineSize'), h = sizeOf(c, parent[1], 'Height', 'BlockSize');
  return [
    w === null ? null : w - (pxOf(c, 'PaddingLeft') ?? 0) - (pxOf(c, 'PaddingRight') ?? 0) - (pxOf(c, 'BorderLeftWidth') ?? 0) - (pxOf(c, 'BorderRightWidth') ?? 0),
    h === null ? null : h - (pxOf(c, 'PaddingTop') ?? 0) - (pxOf(c, 'PaddingBottom') ?? 0) - (pxOf(c, 'BorderTopWidth') ?? 0) - (pxOf(c, 'BorderBottomWidth') ?? 0),
  ];
}
// SpacingResolve.percentBasePx under WPT: definite base → pct × base; indefinite → 0.
const usedPx = (pct, cb) => cb[0] === null ? 0 : pct * cb[0] / 100;
const ROOT_CB = [358, null]; // ScreenshotCaptureScreen's 358 dp canvas content width

// ── b′: is the slot parent a block container (CSS 2.1 §10.1)? ───────────────
const NON_BLOCK_DISPLAY = new Set(['INLINE', 'CONTENTS', 'TABLE_ROW', 'TABLE_ROW_GROUP', 'TABLE_HEADER_GROUP', 'TABLE_FOOTER_GROUP', 'TABLE_COLUMN', 'TABLE_COLUMN_GROUP', 'NONE']);
const NON_BLOCK_TAGS = new Set(['span', 'b', 'i', 'em', 'strong', 'a', 'code', 'label', 'tbody', 'thead', 'tfoot', 'tr', 'colgroup', 'col']);
function isBlockContainer(c) {
  const d = prop(c, 'Display');
  if (typeof d === 'string') return !NON_BLOCK_DISPLAY.has(d);
  return !NON_BLOCK_TAGS.has((c.meta?.sourceTag || '').toLowerCase());
}
const INSET = /^(Top|Right|Bottom|Left|InsetBlockStart|InsetBlockEnd|InsetInlineStart|InsetInlineEnd)$/;
const SIZE = /^(Width|Height|MinWidth|MaxWidth|MinHeight|MaxHeight|InlineSize|BlockSize|MinInlineSize|MaxInlineSize|MinBlockSize|MaxBlockSize)$/;

// ── the walk ────────────────────────────────────────────────────────────────
const out = {
  run: RUN, docs: 0,
  t5: { hiddenTotal: 0, hiddenPreserve3d: 0, hiddenPreserve3dWithChildren: 0, hiddenFlat: 0, hiddenFlatWithChildren: 0,
        backFacingHidden: 0, ownDecorationOnCulledPreserve3d: 0, carriers: [], flatBackFacingCarriers: [] },
  t7: { inheritCarriers: [], otherKeywordCarriers: [] },
  b2: { carriers: 0, tests: new Set(), cbDiffer: 0, usedDiffer: 0, rows: [], shapes: {} },
  b1: { percentCarriers: 0, nonBlockContainerParents: [], parentTags: {} },
};
for (const { sec, test, doc } of documents()) {
  out.docs++;
  const comps = doc.components || [];
  const byId = new Map(comps.map(c => [c.id, c]));
  const children = new Map();
  for (const c of comps) if (c.slot?.parent) children.set(c.slot.parent, [...(children.get(c.slot.parent) || []), c]);
  // Each component's element-level cb: the block its slot parent publishes.
  const cbOf = new Map();
  const elementCb = c => {
    if (cbOf.has(c.id)) return cbOf.get(c.id);
    const parent = c.slot?.parent ? byId.get(c.slot.parent) : null;
    const v = parent ? childCb(parent, elementCb(parent)) : ROOT_CB;
    cbOf.set(c.id, v); return v;
  };
  for (const c of comps) {
    const kids = children.get(c.id) || [];
    // T5
    if (prop(c, 'BackfaceVisibility') === 'HIDDEN') {
      out.t5.hiddenTotal++;
      const p3d = prop(c, 'TransformStyle') === 'PRESERVE_3D';
      const bf = backFacing(c);
      if (bf) out.t5.backFacingHidden++;
      if (p3d) {
        out.t5.hiddenPreserve3d++;
        if (kids.length) out.t5.hiddenPreserve3dWithChildren++;
        const deco = ownDecoration(c);
        if (bf && Object.values(deco).some(Boolean)) out.t5.ownDecorationOnCulledPreserve3d++;
        out.t5.carriers.push({ sec, test, id: c.id, backFacing: bf, children: kids.length, ownDecoration: deco });
      } else {
        out.t5.hiddenFlat++;
        if (kids.length) out.t5.hiddenFlatWithChildren++;
        if (bf) out.t5.flatBackFacingCarriers.push({ sec, test, id: c.id, children: kids.length });
      }
    }
    // T7
    const tr = prop(c, 'Transform');
    if (tr && typeof tr === 'object' && tr.type === 'keyword') {
      const row = { sec, test, id: c.id, keyword: tr.keyword, parentHasTransform: !!(c.slot?.parent && has(byId.get(c.slot.parent) || {}, 'Transform')) };
      (String(tr.keyword).toLowerCase() === 'inherit' ? out.t7.inheritCarriers : out.t7.otherKeywordCarriers).push(row);
    }
    // b″ + b′
    let pctSpacing = false;
    for (const p of c.properties || []) {
      if (!SPACING.test(p.type)) continue;
      const shape = typeof p.data === 'number' ? 'bare-number' : (p.data && typeof p.data === 'object' ? (p.data.type || (p.data.px !== undefined ? 'px' : 'object')) : typeof p.data);
      out.b2.shapes[shape] = (out.b2.shapes[shape] || 0) + 1;
      if (!isPct(p.data)) continue;
      pctSpacing = true;
      const eCb = elementCb(c), aCb = childCb(c, eCb);
      const row = { sec, test, id: c.id, side: p.type, pct: pctOf(p.data), elementCb: eCb, childCb: aCb,
        elementPx: usedPx(pctOf(p.data), eCb), childPx: usedPx(pctOf(p.data), aCb),
        paints: !!(prop(c, 'BackgroundColor') || has(c, 'BackgroundImage') || ownDecoration(c).border), children: kids.length, text: !!c.text };
      out.b2.carriers++; out.b2.tests.add(`${sec}/${test}`);
      if (String(eCb) !== String(aCb)) out.b2.cbDiffer++;
      if (row.elementPx !== row.childPx) out.b2.usedDiffer++;
      out.b2.rows.push(row);
    }
    const pctInset = (c.properties || []).some(p => INSET.test(p.type) && isPct(p.data));
    // Percentage SIZES consume the same channel (DynamicValueResolver rewrites
    // % Width/Height against LocalContainingBlock) — the brief's b′ scan
    // covered insets + spacing only, so they are counted here too.
    const pctSize = (c.properties || []).filter(p => SIZE.test(p.type) && p.data && typeof p.data === 'object' && p.data.type === 'percentage');
    if (pctSpacing || pctInset || pctSize.length) {
      out.b1.percentCarriers++;
      const parent = c.slot?.parent ? byId.get(c.slot.parent) : null;
      const tag = parent ? (prop(parent, 'Display') || parent.meta?.sourceTag || '(untagged)') : '(root)';
      out.b1.parentTags[tag] = (out.b1.parentTags[tag] || 0) + 1;
      if (parent && !isBlockContainer(parent)) {
        // What the parent publishes today vs what the §10.1 republish would hand down (its own block).
        const published = elementCb(c), republished = elementCb(parent);
        out.b1.nonBlockContainerParents.push({ sec, test, id: c.id, parentId: parent.id, parentTag: parent.meta?.sourceTag, parentDisplay: prop(parent, 'Display'),
          consumers: (c.properties || []).filter(p => (SIZE.test(p.type) && pctSize.includes(p)) || ((INSET.test(p.type) || SPACING.test(p.type)) && isPct(p.data))).map(p => `${p.type}=${JSON.stringify(p.data)}`),
          elementCb: published, parentOwnCb: republished, baseWouldChange: String(published) !== String(republished) });
      }
    }
  }
}
out.b2.tests = [...out.b2.tests];
out.summary = {
  docs: out.docs,
  t5: { carriers: out.t5.carriers.filter(r => r.backFacing && r.children > 0).length, hiddenTotal: out.t5.hiddenTotal, hiddenFlat: out.t5.hiddenFlat, flatBackFacing: out.t5.flatBackFacingCarriers.length, ownDecorationOnCulledPreserve3d: out.t5.ownDecorationOnCulledPreserve3d },
  t7: { inheritCarriers: out.t7.inheritCarriers.length, otherKeywordCarriers: out.t7.otherKeywordCarriers.length },
  b2: { carriers: out.b2.carriers, tests: out.b2.tests.length, cbDiffer: out.b2.cbDiffer, usedDiffer: out.b2.usedDiffer, unpaintedChildless: out.b2.rows.filter(r => !r.paints && !r.children && !r.text).length },
  b1: { percentCarriers: out.b1.percentCarriers, nonBlockContainerParents: out.b1.nonBlockContainerParents.length,
        baseWouldChange: out.b1.nonBlockContainerParents.filter(r => r.baseWouldChange).length,
        tests: [...new Set(out.b1.nonBlockContainerParents.map(r => `${r.sec}/${r.test}`))] },
};
fs.writeFileSync(path.join(HERE, 'census.json'), JSON.stringify(out, null, 1));
console.log(JSON.stringify(out.summary, null, 1));
console.log('T5 preserve-3d carriers:', out.t5.carriers.map(r => `${r.sec}/${r.test} backFacing=${r.backFacing} children=${r.children}`).join('; '));
console.log('T7 inherit carriers:', out.t7.inheritCarriers.map(r => `${r.sec}/${r.test} parentHasTransform=${r.parentHasTransform}`).join('; '));
console.log('b″ carriers:', out.b2.rows.map(r => `${r.test} ${r.side} ${r.pct}% element=${r.elementPx} child=${r.childPx} paints=${r.paints} children=${r.children}`).join('\n  '));
console.log('b′ percent consumers under NON-block-container parents (the §10.1 republish would hand them parentOwnCb instead of elementCb):');
for (const r of out.b1.nonBlockContainerParents) console.log(`  ${r.baseWouldChange ? 'BASE CHANGES' : 'same base   '} ${r.sec}/${r.test} ${r.id} parent=${r.parentTag || r.parentDisplay} today=${r.elementCb} republished=${r.parentOwnCb} :: ${r.consumers.join(' ')}`);
