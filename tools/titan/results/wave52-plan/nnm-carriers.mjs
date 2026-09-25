// Read-only blast-radius census over the 1435 wave51-fix per-test IR documents.
// For each proposed fix family, list the carrier documents (IR shape match) with the
// document's three gate cells (P/f + ssim) so at-risk PASSING cells are visible.
// Usage: node nnm-carriers.mjs <out.json>
import fs from 'node:fs';
import path from 'node:path';
const ROOT = '/Users/dranak/Documents/Projects/Style-Converter/.claude/worktrees/trusting-bohr-bd6fbf';
const RUN = path.join(ROOT, 'tools/titan/runs/wave51-fix/sections');
const OUT = process.argv[2];
const UA_BLOCK_MARGIN_TAGS = new Set(['p', 'h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'ul', 'ol', 'blockquote', 'figure', 'pre', 'dl', 'hr', 'menu', 'dir']);
const INLINE_TAGS = new Set(['span', 'a', 'b', 'i', 'em', 'strong', 'u', 's', 'code', 'small', 'sub', 'sup', 'abbr', 'cite', 'q', 'mark', 'time', 'data', 'label', 'kbd', 'var', 'dfn', 'samp', 'bdi', 'bdo']);
const px = (c, t) => { const p = (c.properties || []).find(x => x.type === t); const d = p?.data; if (d == null) return undefined; if (typeof d === 'number') return d; if (d.px != null) return d.px; if (d.type === 'length' && d.px != null) return d.px; return null; };
const has = (c, t) => (c.properties || []).some(x => x.type === t);
const kw = (c, t) => { const p = (c.properties || []).find(x => x.type === t); const d = p?.data; return typeof d === 'string' ? d.toUpperCase() : (d && typeof d === 'object' && typeof d.value === 'string' ? d.value.toUpperCase() : null); };
const isOOF = (c) => ['ABSOLUTE', 'FIXED'].includes(kw(c, 'Position'));
const tag = (c) => c.meta?.sourceTag?.toLowerCase();
const fam = {
  A_rootFoldAbsposMarginJoin: [], B_selfCollapsingChild: [], C_absposInsetUaMargin: [], D_verticalSeamBakedGuard: [],
  E_rc1RootPaintOrder: [], F_inlineTagRenderedAsBlock: [], G_backfaceHiddenPreserve3dWithChildren: [], H_transformInheritKeyword: [],
};
let docs = 0;
for (const sec of fs.readdirSync(RUN).sort()) {
  const mp = path.join(RUN, sec, 'manifest.json');
  if (!fs.existsSync(mp)) continue;
  const m = JSON.parse(fs.readFileSync(mp, 'utf8'));
  for (const [test, r] of Object.entries(m.wpt.results)) {
    const short = test.replace(/^css\//, '').replace(/\.html$/, '');
    const irp = path.join(RUN, sec, 'per-test-ir', 'wpt__' + short.replace(/\//g, '__') + '.json');
    if (!fs.existsSync(irp)) continue;
    docs++;
    const ir = JSON.parse(fs.readFileSync(irp, 'utf8'));
    const comps = ir.components || [];
    const byId = new Map(comps.map(c => [c.id, c]));
    const childrenOf = new Map();
    const roots = [];
    for (const c of comps) { const p = c.slot?.parent; if (p) { (childrenOf.get(p) || childrenOf.set(p, []).get(p)).push(c); } else roots.push(c); }
    const d = r.browserRef?.diffs || {};
    const cells = Object.fromEntries(['web', 'ios', 'android'].map(p => [p, d[p + '-ref'] ? (d[p + '-ref'].wptPass === null ? 'NA' : (d[p + '-ref'].wptPass ? 'P' : 'f')) + (d[p + '-ref'].ssim ?? '-') : '-']));
    const rec = (family, detail) => fam[family].push({ test: short, scoreEligible: r.scoreEligible, postLoad: r.postLoadExtracted, cells, detail });
    // A: RC1 static-position ROOT (abspos, no block-axis inset) carrying a margin-top (declared or UA) after an in-flow root
    //    → the harness fold joins its margin into the collapse set (max) where CSS adds it (16+50 vs max(16,50)).
    roots.forEach((c, i) => {
      if (!isOOF(c) || has(c, 'Top') || has(c, 'Bottom') || has(c, 'InsetBlockStart') || has(c, 'InsetBlockEnd')) return;
      const prev = roots.slice(0, i).reverse().find(x => !isOOF(x));
      if (!prev) return;
      const ownTop = px(c, 'MarginTop') ?? (UA_BLOCK_MARGIN_TAGS.has(tag(c)) ? 'UA' : 0);
      const prevBottom = px(prev, 'MarginBottom') ?? (UA_BLOCK_MARGIN_TAGS.has(tag(prev)) ? 'UA' : 0);
      if (ownTop && prevBottom) rec('A_rootFoldAbsposMarginJoin', { root: i, ownTop, prevBottom, prevTag: tag(prev), tag: tag(c), hasLeft: has(c, 'Left'), hasRight: has(c, 'Right') });
    });
    // B: self-collapsing children (no text, no children, height 0/absent, both vertical margins declared, one > 0) — B9 bail today;
    //    with Height=0px declared the natives treat it as a floor and stack 16+16 (text-decoration-propagation-shadow).
    for (const c of comps) {
      if (c.text || (childrenOf.get(c.id) || []).length) continue;
      const mt = px(c, 'MarginTop'), mb = px(c, 'MarginBottom');
      if (mt === undefined || mb === undefined) continue;
      const h = px(c, 'Height');
      if (h !== undefined && h !== 0) continue;
      if (has(c, 'MinHeight') && px(c, 'MinHeight') !== 0) continue;
      if (isOOF(c) || kw(c, 'Float')) continue;
      // §8.3.1 collapse-through also needs zero border and padding on the block edges.
      const edgeBox = ['BorderTopWidth', 'BorderBottomWidth', 'PaddingTop', 'PaddingBottom'].some(t => (px(c, t) ?? 0) !== 0 || has(c, 'BorderTopStyle') && kw(c, 'BorderTopStyle') !== 'NONE' && px(c, 'BorderTopWidth') === undefined);
      if (edgeBox) continue;
      if ((mt ?? 0) > 0 || (mb ?? 0) > 0 || mt === null || mb === null) rec('B_selfCollapsingChild', { id: c.id.split('__').slice(-2).join('__'), root: !c.slot, tag: tag(c), display: kw(c, 'Display'), mt, mb, h, em: mt === null || mb === null });
    }
    // C: abspos/fixed root with a block-axis inset and a UA-margin tag but no declared vertical margin → inset applied to the border edge.
    for (const c of roots) {
      if (!isOOF(c) || !(has(c, 'Top') || has(c, 'Bottom'))) continue;
      if (!UA_BLOCK_MARGIN_TAGS.has(tag(c))) continue;
      if (has(c, 'MarginTop') || has(c, 'MarginBottom')) continue;
      rec('C_absposInsetUaMargin', { tag: tag(c), top: px(c, 'Top'), bottom: px(c, 'Bottom') });
    }
    // D: vertical-writing-mode block container (own or inherited via ancestor) whose in-flow block children all declare Width+Height
    //    (the seam's BAKED-LAYOUT guard fires) on a NON-post-load document → the seam is wrongly suppressed.
    const wmOf = (c) => { const v = kw(c, 'WritingMode'); if (v) return v; const p = c.slot?.parent ? byId.get(c.slot.parent) : null; return p ? wmOf(p) : null; };
    for (const c of comps) {
      const kids = (childrenOf.get(c.id) || []).filter(k => !isOOF(k));
      if (!kids.length || c.text) continue;
      const wm = wmOf(c); if (!wm || !/VERTICAL|SIDEWAYS/.test(wm)) continue;
      const disp = kw(c, 'Display'); if (disp && !['BLOCK', 'FLOW_ROOT', 'LIST_ITEM'].includes(disp) && !c.slot) { /* keep: flex items are block containers */ }
      if (disp && ['FLEX', 'GRID', 'INLINE_FLEX', 'INLINE_GRID', 'TABLE', 'INLINE', 'INLINE_BLOCK'].includes(disp)) continue;
      const inlineKid = kids.some(k => ['INLINE', 'INLINE_BLOCK', 'INLINE_FLEX', 'INLINE_GRID'].includes(kw(k, 'Display')));
      const orth = kids.some(k => has(k, 'WritingMode'));
      const baked = kids.some(k => has(k, 'Width') && has(k, 'Height'));
      if (baked && !inlineKid && !orth) rec('D_verticalSeamBakedGuard', { id: c.id.split('__').slice(-2).join('__'), wm, kids: kids.length });
    }
    // E: RC1 static-position root (abspos, no inset at all, no positioned ancestor) followed by a later in-flow root → paints below it today.
    roots.forEach((c, i) => {
      if (!isOOF(c) || ['Top', 'Bottom', 'Left', 'Right', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd'].some(t => has(c, t))) return;
      const later = roots.slice(i + 1).filter(x => !isOOF(x));
      if (later.length) rec('E_rc1RootPaintOrder', { root: i, laterInFlowRoots: later.length, hasBg: has(c, 'BackgroundColor') });
    });
    // F: inline-tag component (span/a/b/…) with no Display but with visible box decoration (background/border) → natives render a block box.
    for (const c of comps) {
      if (!INLINE_TAGS.has(tag(c)) || has(c, 'Display')) continue;
      const deco = ['BackgroundColor', 'BackgroundImage', 'BorderTopWidth', 'BorderBottomWidth', 'BorderLeftWidth', 'BorderRightWidth', 'OutlineWidth'].filter(t => has(c, t));
      if (!deco.length) continue;
      const parentIsText = c.slot ? !!byId.get(c.slot.parent)?.text : false;
      rec('F_inlineTagRenderedAsBlock', { id: c.id.split('__').slice(-2).join('__'), tag: tag(c), root: !c.slot, deco, text: !!c.text, parentHasText: parentIsText });
    }
    // G: backface-visibility hidden + transform-style preserve-3d on an element WITH children (the natives cull the subtree).
    for (const c of comps) {
      if (kw(c, 'BackfaceVisibility') !== 'HIDDEN') continue;
      const kids = childrenOf.get(c.id) || [];
      if (!kids.length) continue;
      rec('G_backfaceHiddenPreserve3dWithChildren', { id: c.id.split('__').slice(-2).join('__'), preserve3d: kw(c, 'TransformStyle') === 'PRESERVE_3D', kids: kids.length, transform: JSON.stringify((c.properties.find(p => p.type === 'Transform') || {}).data || null).slice(0, 80) });
    }
    // H: transform: inherit keyword carriers.
    for (const c of comps) {
      const t = (c.properties || []).find(p => p.type === 'Transform');
      if (t?.data?.type === 'keyword' && t.data.keyword === 'inherit') rec('H_transformInheritKeyword', { id: c.id.split('__').slice(-2).join('__') });
    }
  }
}
const summary = Object.fromEntries(Object.entries(fam).map(([k, v]) => [k, { carriers: v.length, tests: new Set(v.map(x => x.test)).size }]));
fs.writeFileSync(OUT, JSON.stringify({ generatedAt: new Date().toISOString(), docs, summary, families: fam }, null, 1));
console.log('docs', docs); console.log(summary);
for (const [k, v] of Object.entries(fam)) {
  console.log('\n== ' + k);
  const seen = new Set();
  for (const x of v) { if (seen.has(x.test)) continue; seen.add(x.test); console.log(`  ${x.test}  web ${x.cells.web} ios ${x.cells.ios} android ${x.cells.android}${x.postLoad ? ' [postload]' : ''}${x.scoreEligible ? '' : ' [NOT scored]'}  ${JSON.stringify(x.detail).slice(0, 110)}`); }
}
