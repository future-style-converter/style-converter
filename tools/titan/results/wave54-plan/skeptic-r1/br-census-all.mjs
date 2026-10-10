// Plan skeptic r1, check (2b'): U3b reach over EVERY br on the wire (not only meta.runs hosts): a br whose height stays
// 20 after U3 and whose parent component (slot.parent; a top-level br's parent is "none") carries a LineHeight on the
// wire. Resolved box: px, or multiplier × parent FontSize px (16 when absent). Also lists brs whose parent carries NO
// LineHeight but an ANCESTOR does (an inherited line-height: the U3b rule says "a host whose cascade declares").
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const u3 = JSON.parse(JSON.stringify({})); // the U3 set is recomputed by br-census.mjs; here: today's 20px brs
let n20 = 0; const docs20 = new Set(); const own = new Map(), inh = new Map();
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const byId = new Map(comps.map((c) => [c.id, c]));
    for (const c of comps) {
      if (!(c.meta?.role === 'line-break' || c.meta?.sourceTag === 'br')) continue;
      const h = prop(c, 'Height'); if (!(h && h.px === 20)) continue;
      n20++; docs20.add(`${sec}/${f}`);
      const par = c.slot?.parent ? byId.get(c.slot.parent) : null;
      const lh = par ? prop(par, 'LineHeight') : undefined;
      const key = `${sec}/${f}`;
      if (lh !== undefined) { const fpx = prop(par, 'FontSize')?.px ?? 16; const box = lh.px ?? (lh.multiplier != null ? +(lh.multiplier * fpx).toFixed(3) : JSON.stringify(lh));
        (own.get(key) ?? own.set(key, []).get(key)).push(`${c.name}→${box}${box === 20 ? '' : ' (≠20)'}`); continue; }
      let a = par ? (par.slot?.parent ? byId.get(par.slot.parent) : null) : null;
      while (a && prop(a, 'LineHeight') === undefined) a = a.slot?.parent ? byId.get(a.slot.parent) : null;
      if (a) (inh.get(key) ?? inh.set(key, []).get(key)).push(`${c.name} (ancestor ${a.name} LineHeight ${JSON.stringify(prop(a, 'LineHeight')).slice(0, 50)})`);
    }
  }
}
console.log(`today: ${n20} brs at Height 20 in ${docs20.size} documents (U3 turns 54 of them to 0: br-census.out.txt)`);
console.log(`20px brs whose PARENT carries LineHeight: ${[...own.values()].flat().length} in ${own.size} docs`); for (const [k, v] of own) console.log(`  ${k.padEnd(76)} ${v.join(' ')}`);
console.log(`20px brs whose parent carries none but an ANCESTOR does: ${[...inh.values()].flat().length} in ${inh.size} docs`); for (const [k, v] of inh) console.log(`  ${k.padEnd(76)} ${v.length} e.g. ${v[0]}`);
