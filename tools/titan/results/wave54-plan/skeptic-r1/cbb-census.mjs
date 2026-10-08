// Plan skeptic r1, check (2e): L4 CBB-android radius re-derived WITH propagation and with every containing-block reader.
// Model of DynamicValueResolver.childContainingBlock (:207-249): CB(child) = size(px, or % of the parent's KNOWN base on
// Width/InlineSize, Height/BlockSize) minus px padding + border bands. TODAY: bands always subtracted. AFTER (CBB, WPT
// capture): subtracted only when BoxSizing BORDER_BOX is declared (effectiveBoxSizing = declared ?: CONTENT_BOX).
// Roots get the canvas CB (358 × null, identical in both). A component is a READER when it carries a percentage carrier
// that DynamicValueResolver rewrites against the CB (any property but FontSize/LineHeight with {type:percentage} /
// {u:PERCENT} / a "%" inside a calc string), or is vertical-writing-mode text (VerticalTextFlowLayout heightPx).
// A document is in the radius when a reader's provided CB differs today vs after.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const px = (c, ...ts) => { for (const t of ts) { const d = prop(c, t); if (d && typeof d === 'object' && typeof d.px === 'number') return d.px; } return null; };
const pctv = (c, ...ts) => { for (const t of ts) { const d = prop(c, t); if (d && typeof d === 'object' && d.type === 'percentage' && typeof d.value === 'number') return d.value; } return null; };
const size = (c, base, ...ts) => { const p = px(c, ...ts); if (p != null) return p; const v = pctv(c, ...ts); return v != null && base != null ? v * base / 100 : null; };
const band = (c, a) => a === 'w' ? (px(c, 'PaddingLeft', 'PaddingInlineStart') ?? 0) + (px(c, 'PaddingRight', 'PaddingInlineEnd') ?? 0) + (px(c, 'BorderLeftWidth') ?? 0) + (px(c, 'BorderRightWidth') ?? 0)
  : (px(c, 'PaddingTop', 'PaddingBlockStart') ?? 0) + (px(c, 'PaddingBottom', 'PaddingBlockEnd') ?? 0) + (px(c, 'BorderTopWidth') ?? 0) + (px(c, 'BorderBottomWidth') ?? 0);
const childCB = (c, parent, after) => { const w = size(c, parent.w, 'Width', 'InlineSize'), h = size(c, parent.h, 'Height', 'BlockSize');
  const sub = !after || prop(c, 'BoxSizing') === 'BORDER_BOX'; return { w: w == null ? null : w - (sub ? band(c, 'w') : 0), h: h == null ? null : h - (sub ? band(c, 'h') : 0) }; };
// Refined after reading the consumers (LocalContainingBlock readers: PercentInsetPositioned, MarginApplier, PaddingApplier,
// ElementContainingBlock, ContentApplier, VerticalTextFlowLayout; DynamicValueResolver.needsResolution rewrites only
// expr / original-object / Generic-var carriers): sizes, insets and spacing with a %, plus any "expr" (calc) carrier.
// Transform (translate % = own box) and BackgroundImage (url %-encoding) are NOT CB readers in the Compose runtime.
const CB_TYPES = /^(Width|Height|InlineSize|BlockSize|MinWidth|MinHeight|MaxWidth|MaxHeight|MinInlineSize|MaxInlineSize|MinBlockSize|MaxBlockSize|Top|Left|Right|Bottom|Inset.*|Margin.*|Padding.*)$/;
const readerProps = (c) => (c.properties || []).filter((p) => (CB_TYPES.test(p.type) && /"type":"percentage"|"u":"PERCENT"/.test(JSON.stringify(p.data))) || /"expr":/.test(JSON.stringify(p.data))).map((p) => p.type);
const vertical = (c, par) => { const wm = prop(c, 'WritingMode') ?? (par && prop(par, 'WritingMode')); return !!c.text && typeof wm === 'string' && /VERTICAL|SIDEWAYS/.test(wm); };
const docs = new Map(); const propTypes = new Map();
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const kids = new Map();
    for (const c of comps) if (c.slot?.parent) (kids.get(c.slot.parent) ?? kids.set(c.slot.parent, []).get(c.slot.parent)).push(c);
    const key = `${sec}/${f.replace(/\.json$/, '')}`;
    const walk = (c, par, cbT, cbA) => {
      if (cbT.w !== cbA.w || cbT.h !== cbA.h) { const rp = readerProps(c); const vt = vertical(c, par);
        if (rp.length || vt) { (docs.get(key) ?? docs.set(key, []).get(key)).push(`${c.name} [${[...new Set(rp)].join(',')}${vt ? (rp.length ? ',' : '') + 'vertical-text' : ''}] CB ${JSON.stringify(cbT)}→${JSON.stringify(cbA)}`); rp.forEach((t) => propTypes.set(t, (propTypes.get(t) ?? 0) + 1)); } }
      for (const k of kids.get(c.id) ?? []) walk(k, c, childCB(c, cbT, false), childCB(c, cbA, true));
    };
    for (const r of comps.filter((c) => !c.slot?.parent)) walk(r, null, { w: 358, h: null }, { w: 358, h: null });
  }
}
console.log(`CBB radius (a CB reader whose provided containing block differs today vs after): ${docs.size} documents`);
for (const [k, v] of docs) console.log(`  ${k}  (${v.length})\n      ${v.slice(0, 3).join('\n      ')}`);
console.log('reader property types:', JSON.stringify([...propTypes.entries()].sort((a, b) => b[1] - a[1])));
