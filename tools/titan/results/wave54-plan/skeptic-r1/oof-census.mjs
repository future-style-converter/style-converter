// Plan skeptic r1, check (2d): L4 OOF radius re-derived with the PLAN §2 L4 rule table exactly (NOT the scout census's
// otherCB, which also counts container-type): establishes(c) = Contain ∋ LAYOUT|PAINT|STRICT|CONTENT, non-empty Filter or
// BackdropFilter, or WillChange ∋ filter|backdrop-filter|contain — and false when c is itself ABSOLUTE|FIXED.
// Per out-of-flow box: today's CB (transform clause; ABSOLUTE also positioned ancestors) vs after (+ establishes);
// F3: FIXED hoists only with an inset; a no-inset FIXED with no positioned ancestor joins RC1 (static position).
// Reported per native: Android = every CB change + every F3 change; iOS = FIXED boxes only (FixedHoist :309/:347/:115).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const pos = (c) => prop(c, 'Position') ?? 'STATIC';
const INSETS = ['Top', 'Bottom', 'Left', 'Right', 'InsetBlockStart', 'InsetBlockEnd', 'InsetInlineStart', 'InsetInlineEnd', 'Inset', 'InsetBlock', 'InsetInline'];
const inset = (c) => INSETS.some((t) => prop(c, t) !== undefined);
const tcb = (c) => { for (const p of c.properties || []) { const d = p.data;
  if (p.type === 'Transform' && d && typeof d === 'object' && !(d.type === 'functions' && Array.isArray(d.list) && !d.list.length)) return true;
  if (['Translate', 'Rotate', 'Scale', 'Perspective'].includes(p.type) && d && typeof d === 'object' && d.type !== 'none') return true;
  if (p.type === 'TransformStyle' && d === 'PRESERVE_3D') return true;
  if (p.type === 'WillChange' && Array.isArray(d) && d.some((h) => h?.name === 'transform' || h?.name === 'perspective')) return true; } return false; };
const est = (c) => { if (['ABSOLUTE', 'FIXED'].includes(pos(c))) return false;
  const ct = prop(c, 'Contain'); if (Array.isArray(ct) && ct.some((v) => ['LAYOUT', 'PAINT', 'STRICT', 'CONTENT'].includes(v))) return true;
  const f = prop(c, 'Filter'); if (Array.isArray(f) && f.length) return true; const bf = prop(c, 'BackdropFilter'); if (Array.isArray(bf) && bf.length) return true;
  const wc = prop(c, 'WillChange'); return Array.isArray(wc) && wc.some((h) => ['filter', 'backdrop-filter', 'contain'].includes(h?.name)); };
const estAny = (c) => { const ct = prop(c, 'Contain'); const f = prop(c, 'Filter'), bf = prop(c, 'BackdropFilter'), wc = prop(c, 'WillChange');
  return (Array.isArray(ct) && ct.some((v) => ['LAYOUT', 'PAINT', 'STRICT', 'CONTENT'].includes(v))) || (Array.isArray(f) && f.length > 0) || (Array.isArray(bf) && bf.length > 0) || (Array.isArray(wc) && wc.some((h) => ['filter', 'backdrop-filter', 'contain'].includes(h?.name))); };
const R = { android: new Map(), ios: new Map(), hostFlips: [], positionedEstablisherWithFixedDesc: [], containerTypeOnly: [], estComponents: 0, estDocs: new Set() };
const add = (m, k, v) => (m.get(k) ?? m.set(k, []).get(k)).push(v);
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const byId = new Map(comps.map((c) => [c.id, c]));
    const key = `${sec}/${f.replace(/\.json$/, '')}`;
    const anc = (c) => { const a = []; let p = c.slot?.parent && byId.get(c.slot.parent); while (p) { a.push(p); p = p.slot?.parent && byId.get(p.slot.parent); } return a; };
    for (const c of comps) if (est(c)) { R.estComponents++; R.estDocs.add(key); }
    for (const c of comps) {
      const p = pos(c); if (p !== 'ABSOLUTE' && p !== 'FIXED') continue;
      const a = anc(c);
      const nowIdx = a.findIndex((x) => tcb(x) || (p === 'ABSOLUTE' && pos(x) !== 'STATIC'));
      const aftIdx = a.findIndex((x) => tcb(x) || est(x) || (p === 'ABSOLUTE' && pos(x) !== 'STATIC'));
      const positionedAnc = a.some((x) => pos(x) !== 'STATIC');
      if (aftIdx !== nowIdx) { const why = `CB ${nowIdx < 0 ? 'canvas' : a[nowIdx].name} -> ${a[aftIdx].name}`; add(R.android, key, `${c.name} ${p} ${why}`); if (p === 'FIXED') add(R.ios, key, `${c.name} FIXED ${why}`); continue; }
      if (p === 'FIXED' && nowIdx < 0 && !inset(c)) { const why = positionedAnc ? 'F3 no-inset FIXED: stops hoisting (stays under its positioned ancestor)' : 'F3 no-inset FIXED: joins RC1 static position';
        add(R.android, key, `${c.name} ${why}`); add(R.ios, key, `${c.name} ${why}`); }
      if (p === 'FIXED' && a.some((x) => ['ABSOLUTE', 'FIXED'].includes(pos(x)) && estAny(x))) R.positionedEstablisherWithFixedDesc.push(`${key} ${c.name}`);
    }
    // host activation (CanvasRootHoist.anyOutOfFlowBox model, before vs after)
    const host = (after) => { const walk = (c, ancPos, ancT) => { const p = pos(c), ins = inset(c);
        const hoist = p === 'FIXED' ? (!ancT && (!after || ins)) : p === 'ABSOLUTE' ? (!ancPos && !ancT && ins) : false;
        const rc1 = (p === 'ABSOLUTE' || (after && p === 'FIXED')) && !ancPos && !ins; if (hoist || rc1) return true;
        const cT = ancT || tcb(c) || (after && est(c)); const cP = ancPos || p !== 'STATIC';
        return comps.filter((k) => k.slot?.parent === c.id).some((k) => walk(k, cP, cT)); };
      return comps.filter((c) => !c.slot?.parent).some((c) => walk(c, false, false)); };
    if (host(false) !== host(true)) R.hostFlips.push(`${key} ${host(false)} -> ${host(true)}`);
  }
}
for (const pf of ['android', 'ios']) { console.log(`${pf}: ${R[pf].size} documents`); for (const [k, v] of R[pf]) console.log(`  ${k}\n      ${v.join('\n      ')}`); }
console.log(`hostFlips (${R.hostFlips.length}):\n  ${R.hostFlips.join('\n  ')}`);
console.log(`establisher components (rule table, excluding self-positioned): ${R.estComponents} in ${R.estDocs.size} documents`);
console.log(`FIXED boxes under a SELF-POSITIONED establisher (F1's inert clause; spec would make it the CB): ${R.positionedEstablisherWithFixedDesc.length}`); R.positionedEstablisherWithFixedDesc.forEach((x) => console.log('  ' + x));
