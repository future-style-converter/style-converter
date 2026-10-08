// skeptic-r2 OWN census of L4 CBB-android: DynamicValueResolver.childContainingBlock subtracts px padding + border from
// an element's EXPLICIT Width/Height for its children; under WPT capture the change stops subtracting for a non-border-box
// element. Reached: a child (and, through a %-sized child, its subtree) that reads the block — any percentage on a
// Margin*/Padding*/inset/size property, or an out-of-flow child with a % size or inset. Readers per LocalContainingBlock /
// LocalElementContainingBlock (MarginApplier, PaddingApplier, PercentInsetPositioned, resolveOutOfFlowPercentSizes,
// ContentApplier, VerticalTextFlowLayout). Over-approximates on purpose (any % on the listed families).
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const EXP = JSON.parse(fs.readFileSync('expectations.json', 'utf8'));
const L4 = EXP.lanes['L4-oof-layout']; const car = new Set(L4.captureCarriers.android);
const mnmA = new Set(L4.mustNotMove.filter((c) => c.endsWith(' android')).map((c) => 'wpt__' + c.slice(0, -13).replace(/\//g, '__')));
const prop = (c, t) => (c.properties || []).find((p) => p.type === t);
const px = (c, ...ts) => { for (const t of ts) { const p = prop(c, t); if (p && p.data && typeof p.data.px === 'number') return p.data.px; } return 0; };
const definite = (c, ...ts) => ts.some((t) => { const p = prop(c, t); return p && p.data && (typeof p.data.px === 'number' || p.data.type === 'percentage'); });
const borderBox = (c) => { const p = prop(c, 'BoxSizing'); return p && p.data === 'BORDER_BOX'; };
const pct = (p) => { const s = JSON.stringify(p.data || ''); return /percentage|"u":"PERCENT"|%/.test(s); };
const READ = /^(Margin|Padding|Top$|Right$|Bottom$|Left$|Inset|Width$|Height$|MinWidth|MaxWidth|MinHeight|MaxHeight|InlineSize|BlockSize)/;
const reads = (c) => (c.properties || []).some((p) => READ.test(p.type) && pct(p));
const oof = (c) => { const p = prop(c, 'Position'); return p && (p.data === 'ABSOLUTE' || p.data === 'FIXED'); };
const res = [];
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const stem = f.replace(/\.json$/, '');
    const kids = new Map(); for (const c of doc.components) if (c.slot && c.slot.parent) { if (!kids.has(c.slot.parent)) kids.set(c.slot.parent, []); kids.get(c.slot.parent).push(c); }
    const why = [];
    for (const e of doc.components) {
      if (borderBox(e)) continue;
      const bandW = px(e, 'PaddingLeft', 'PaddingInlineStart') + px(e, 'PaddingRight', 'PaddingInlineEnd') + px(e, 'BorderLeftWidth') + px(e, 'BorderRightWidth');
      const bandH = px(e, 'PaddingTop', 'PaddingBlockStart') + px(e, 'PaddingBottom', 'PaddingBlockEnd') + px(e, 'BorderTopWidth') + px(e, 'BorderBottomWidth');
      const w = definite(e, 'Width', 'InlineSize') && bandW > 0, h = definite(e, 'Height', 'BlockSize') && bandH > 0;
      if (!w && !h) continue;
      const stack = [...(kids.get(e.id) || [])];
      while (stack.length) {
        const c = stack.pop();
        if (reads(c)) { why.push(`${e.name}${w ? ' W' : ''}${h ? ' H' : ''} -> ${c.name}${oof(c) ? ' (out-of-flow)' : ' (in-flow)'}`);
          // a %-sized child publishes a changed block to its own children: follow it
          if (['Width', 'Height', 'InlineSize', 'BlockSize'].some((t) => prop(c, t) && pct(prop(c, t)))) stack.push(...(kids.get(c.id) || [])); }
      }
    }
    if (why.length) res.push({ stem, why, cls: car.has(stem) ? 'android CARRIER' : mnmA.has(stem) ? 'android must-not-move' : 'NOT IN L4 LISTS' });
  }
}
for (const r of res) console.log(`${r.stem.padEnd(72)} ${r.cls}\n     ${r.why.slice(0, 4).join('\n     ')}${r.why.length > 4 ? `\n     … +${r.why.length - 4}` : ''}`);
console.log(`documents reached: ${res.length}; not in any L4 list: ${res.filter((r) => r.cls === 'NOT IN L4 LISTS').length}`);
