// Plan skeptic r1, check (2f): L4 GAP radius — EVERY component carrying a ColumnRule*/RowRule* property (any display,
// multicol included), with its display, column-count/width, and gaps; flags the ones whose gap on a decorated axis is
// 0 / absent (flex/grid: absent = 0; multicol: absent = normal = 1em, not zero).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const prop = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const g = (c, t) => { const d = prop(c, t); return d == null ? null : (typeof d === 'object' ? (d.px ?? d.value ?? JSON.stringify(d)) : d); };
const rows = [];
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) for (const c of JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components) {
    const rules = [...new Set((c.properties || []).filter((p) => /^(ColumnRule|RowRule)/.test(p.type)).map((p) => p.type))]; if (!rules.length) continue;
    const disp = prop(c, 'Display'); const mc = prop(c, 'ColumnCount') != null || prop(c, 'ColumnWidth') != null;
    const cg = g(c, 'ColumnGap'), rg = g(c, 'RowGap');
    const zero = (v, isMc) => v === 0 || v === 'NORMAL' && !isMc || (v == null && !isMc);
    const z = (zero(cg, mc) && rules.some((r) => r.startsWith('Column'))) || (!mc && zero(rg, false) && rules.some((r) => r.startsWith('Row')));
    rows.push({ k: `${sec}/${f.replace(/\.json$/, '')}`, disp: typeof disp === 'string' ? disp : JSON.stringify(disp), mc, cg, rg, rules: rules.join(','), z });
  }
}
const byKind = {}; for (const r of rows) { const k = `${r.mc ? 'MULTICOL' : r.disp}`; (byKind[k] ||= []).push(r); }
for (const [k, v] of Object.entries(byKind)) console.log(`${k}: ${v.length} containers in ${new Set(v.map((r) => r.k)).size} docs; zero gap on a decorated axis: ${v.filter((r) => r.z).length}`);
console.log('zero-gap decorated containers:'); rows.filter((r) => r.z).forEach((r) => console.log(`  ${r.k} ${r.mc ? 'MULTICOL' : r.disp} cg=${r.cg} rg=${r.rg} ${r.rules}`));
