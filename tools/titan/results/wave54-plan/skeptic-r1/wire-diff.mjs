// Plan skeptic r1, check (2a): an independent per-test-IR differ between two runs, over every section both hold.
// Classes: identical (bytes), renumbered (equal once the per-section id counter "-NNN" is stripped from id and
// slot.parent), content (anything else; component count printed). Used on wave53-final -> wave53-probe, where the
// probe tree carried wave-53 L1's hunks P + M (the reverted unit, whose P is re-landed as wave-54 L1 P).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const [a, b] = process.argv.slice(2);
const sd = (r) => path.join(ROOT, 'tools/titan/runs', r, 'sections');
const strip = (s) => s.replace(/("(?:id|parent)":\s*"[^"]*?)-\d{3,}"/g, '$1"');
const tot = { identical: 0, renumbered: 0, content: 0, onlyOne: 0 };
for (const sec of fs.readdirSync(sd(b)).sort()) {
  const da = path.join(sd(a), sec, 'per-test-ir'), db = path.join(sd(b), sec, 'per-test-ir');
  if (!fs.existsSync(da) || !fs.existsSync(db)) continue;
  for (const f of fs.readdirSync(db).filter((x) => x.endsWith('.json')).sort()) {
    const fa = path.join(da, f); if (!fs.existsSync(fa)) { tot.onlyOne++; console.log(`ONLY-IN-${b} ${sec}/${f}`); continue; }
    const x = fs.readFileSync(fa, 'utf8'), y = fs.readFileSync(path.join(db, f), 'utf8');
    if (x === y) { tot.identical++; continue; }
    if (strip(x) === strip(y)) { tot.renumbered++; console.log(`renumbered ${sec}/${f}`); continue; }
    tot.content++; console.log(`CONTENT    ${sec}/${f} components ${JSON.parse(x).components.length} -> ${JSON.parse(y).components.length}`);
  }
}
console.log(JSON.stringify(tot));
