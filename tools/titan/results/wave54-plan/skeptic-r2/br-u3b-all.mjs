// skeptic-r2: U3b's reach over EVERY 20-px <br> of the corpus (not only runs hosts): the br's parent carries
// LineHeight / Font on the wire (host-declared reading) vs only an ancestor does (inherited reading).
import fs from 'node:fs'; import path from 'node:path';
const SEC = '../../runs/wave53-final/sections';
const host = new Map(), anc = new Map(), none = new Map(); let total = 0;
for (const sec of fs.readdirSync(SEC).sort()) {
  const dir = path.join(SEC, sec, 'per-test-ir'); if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir).sort()) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8')); const stem = f.replace(/\.json$/, '');
    const byId = new Map(doc.components.map((c) => [c.id, c]));
    const lh = (c) => (c.properties || []).filter((p) => p.type === 'LineHeight' || p.type === 'Font').map((p) => p.type + JSON.stringify(p.data).slice(0, 60));
    for (const c of doc.components) {
      if (!(c.meta && c.meta.sourceTag === 'br')) continue;
      if (!(c.properties || []).some((p) => p.type === 'Height' && p.data && p.data.px === 20)) continue;
      total++;
      const par = c.slot && byId.get(c.slot.parent);
      const add = (m, why) => { if (!m.has(stem)) m.set(stem, { n: 0, why }); m.get(stem).n++; };
      if (!par) { add(none, 'root-level br'); continue; }
      if (lh(par).length) { add(host, lh(par).join(' ')); continue; }
      let a = par.slot && byId.get(par.slot.parent), w = null;
      while (a && !w) { if (lh(a).length) w = `${a.name}: ${lh(a).join(' ')}`; a = a.slot && byId.get(a.slot.parent); }
      add(w ? anc : none, w || '-');
    }
  }
}
const show = (t, m) => { let n = 0; for (const v of m.values()) n += v.n; console.log(`${t}: ${n} brs in ${m.size} docs`); for (const [s, v] of m) console.log(`   ${s.padEnd(64)} ${String(v.n).padStart(3)}  ${v.why}`); };
console.log(`20-px brs on the wire: ${total}`);
show('parent carries LineHeight/Font on the wire (host reading, before U3)', host);
show('only an ancestor carries it (inherited reading)', anc);
console.log(`neither: ${[...none.values()].reduce((a, v) => a + v.n, 0)} brs in ${none.size} docs`);
