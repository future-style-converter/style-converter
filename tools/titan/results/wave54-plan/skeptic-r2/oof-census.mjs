// skeptic-r2 OWN census of L4 OOF (F1 rule table, F2 OR into the transform-CB choke point, F3 FIXED needs an inset to
// hoist and joins RC1) over wave53-final per-test IR. Mirrors CanvasRootHoist.shouldHoistToCanvasRoot /
// rendersInFlowAsStaticPosition / anyOutOfFlowBox and ComponentRenderer.kt :3471 (Box branch) by reading the code, not
// the scout's script. Approximations: the multicol-spanner positioned nuance is ignored; Swift = FIXED decisions only.
import fs from 'node:fs'; import path from 'node:path';
const RUN = process.argv[2] || 'wave53-final';
const SEC = `../../runs/${RUN}/sections`;
const has = (c, t) => (c.properties || []).filter((p) => p.type === t);
const pos = (c) => { const p = has(c, 'Position').pop(); return p ? p.data : 'STATIC'; };
const isAuto = (d) => d && typeof d === 'object' && (d.type === 'auto' || d.keyword === 'auto');
const INSETS = /^(Top|Right|Bottom|Left|Inset|InsetBlock|InsetInline|InsetBlockStart|InsetBlockEnd|InsetInlineStart|InsetInlineEnd)$/;
const hasInset = (c) => (c.properties || []).some((p) => INSETS.test(p.type) && !isAuto(p.data));
const transformCB = (c) => (c.properties || []).some((p) => {
  if (p.type === 'Transform') return !(p.data && p.data.type === 'functions' && !(p.data.list || []).length);
  if (['Translate', 'Rotate', 'Scale', 'Perspective'].includes(p.type)) return !(p.data && p.data.type === 'none');
  if (p.type === 'TransformStyle') return p.data === 'PRESERVE_3D';
  if (p.type === 'WillChange') return (p.data || []).some((h) => ['transform', 'perspective'].includes(h.name));
  return false; });
const f1 = (c) => !['ABSOLUTE', 'FIXED'].includes(pos(c)) && (c.properties || []).some((p) => {
  if (p.type === 'Contain') return (p.data || []).some((v) => ['LAYOUT', 'PAINT', 'STRICT', 'CONTENT'].includes(v));
  if (p.type === 'Filter' || p.type === 'BackdropFilter') return Array.isArray(p.data) && p.data.length > 0;
  if (p.type === 'WillChange') return (p.data || []).some((h) => ['filter', 'backdrop-filter', 'contain'].includes(h.name));
  return false; });
const f1Positioned = (c) => ['ABSOLUTE', 'FIXED'].includes(pos(c)) && (c.properties || []).some((p) =>
  (p.type === 'Contain' && (p.data || []).some((v) => ['LAYOUT', 'PAINT', 'STRICT', 'CONTENT'].includes(v)))
  || ((p.type === 'Filter' || p.type === 'BackdropFilter') && Array.isArray(p.data) && p.data.length > 0));
const clip = (c) => has(c, 'ClipPath').length > 0;
const out = { android: [], ios: [], hostFlips: [], boxBranch: [], fixedUnderPositionedEstablisher: [] };
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const comps = doc.components;
    const kids = new Map(); const roots = [];
    for (const c of comps) { const par = c.slot && c.slot.parent; if (par) { if (!kids.has(par)) kids.set(par, []); kids.get(par).push(c); } else roots.push(c); }
    const stem = f.replace(/\.json$/, '');
    const andBoxes = [], iosBoxes = []; let anyOld = false, anyNew = false;
    const walk = (c, P, Told, Tnew, C, posEst) => {
      const p = pos(c), ins = hasInset(c);
      let hoOld = false, hoNew = false, rcOld = false, rcNew = false;
      if (!C) {
        if (p === 'FIXED') { hoOld = !Told; hoNew = !Tnew && ins; }
        if (p === 'ABSOLUTE') { hoOld = !P && !Told && ins; hoNew = !P && !Tnew && ins; }
      }
      if (p === 'ABSOLUTE' && !P && !ins) { rcOld = true; rcNew = true; }
      if (p === 'FIXED' && !P && !ins) rcNew = true;
      if (hoOld || rcOld) anyOld = true; if (hoNew || rcNew) anyNew = true;
      if (hoOld !== hoNew || rcOld !== rcNew) {
        (andBoxes).push(`${c.name} ${p}${ins ? '' : ' no-inset'} hoist ${hoOld}->${hoNew} rc1 ${rcOld}->${rcNew}`);
        if (p === 'FIXED') iosBoxes.push(c.name);
      }
      if (p === 'FIXED' && posEst) out.fixedUnderPositionedEstablisher.push(`${stem} ${c.name}`);
      const ch = kids.get(c.id) || [];
      if (f1(c) && !transformCB(c) && p !== 'RELATIVE' && ch.some((k) => ['ABSOLUTE', 'FIXED'].includes(pos(k)))) out.boxBranch.push(`${stem} ${c.name}`);
      for (const k of ch) walk(k, P || p !== 'STATIC', Told || transformCB(c), Tnew || transformCB(c) || f1(c), C || clip(c), posEst || f1Positioned(c));
    };
    for (const r of roots) walk(r, false, false, false, false, false);
    if (andBoxes.length) out.android.push(`${stem}: ${andBoxes.length} box(es) — ${andBoxes.join('; ')}`);
    if (iosBoxes.length) out.ios.push(`${stem}: ${iosBoxes.join(', ')}`);
    if (anyOld !== anyNew) out.hostFlips.push(`${stem} ${anyOld}->${anyNew}`);
  }
}
for (const [k, v] of Object.entries(out)) { console.log(`${k}: ${v.length}`); v.forEach((x) => console.log('   ' + x)); }
