// Plan skeptic r1, check (2g): L5 U1-android radius — every component keyed h1…h6 / sub / sup (meta.sourceTag) on the
// wave53-final wire, with what the UA rule would add on Android (FontSize when the element declares none; FontWeight bold
// for h1-h6 when it declares none), whether it has element children (UAHeadingFoldGate decides those), and its doc's
// android cell. The Compose runtime has NO UAElementFontRule twin today, so every such element is reachable.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const idx = JSON.parse(fs.readFileSync(path.join(path.dirname(new URL(import.meta.url).pathname), 'idx-wave53-final.json'))).cells;
const TAGS = new Set(['h1', 'h2', 'h3', 'h4', 'h5', 'h6', 'sub', 'sup']);
const has = (c, t) => (c.properties || []).some((p) => p.type === t);
const docs = new Map();
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const kidsOf = (c) => comps.filter((k) => k.slot?.parent === c.id);
    for (const c of comps) { const t = c.meta?.sourceTag; if (!TAGS.has(t)) continue;
      const addSize = !has(c, 'FontSize') && !has(c, 'Font'); const addBold = /^h/.test(t) && !has(c, 'FontWeight') && !has(c, 'Font');
      if (!addSize && !addBold) continue;
      const k = `${sec}/${f.replace(/^wpt__[^_]+(?:-[^_]+)*__/, '').replace(/__/g, '/').replace(/\.json$/, '')}`;
      (docs.get(k) ?? docs.set(k, []).get(k)).push(`${t}${addSize ? '+size' : ''}${addBold ? '+bold' : ''}${kidsOf(c).length ? `(${kidsOf(c).length} kids)` : ''}`); }
  }
}
for (const [k, v] of docs) { const cell = idx[`${k}.html android`] ?? '—'; console.log(`${k.padEnd(76)} android ${cell.padEnd(9)} ${v.join(' ')}`); }
console.log(`${docs.size} documents carry an h1-h6/sub/sup the Compose UA rule would restyle`);
