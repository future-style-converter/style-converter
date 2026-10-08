// Plan skeptic r1, check (2c): L3 U1 wire radius — every per-test IR document carrying hyphenate-character, as the
// typed HyphenateCharacter property or a Generic {propertyName:"hyphenate-character"} fallback, with its value. U1 changes
// a document iff its value is "" (Generic today) or holds a css-syntax-3 escape (a backslash) or other quoted form
// that today's quote-strip mis-decodes.
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const rows = [];
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    for (const c of JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components) for (const p of c.properties || []) {
      if (p.type === 'HyphenateCharacter') rows.push(`${sec}/${f} ${c.name} HyphenateCharacter ${JSON.stringify(p.data)}`);
      else if (p.type === 'Generic' && /hyphenate-character/i.test(JSON.stringify(p.data))) rows.push(`${sec}/${f} ${c.name} Generic ${JSON.stringify(p.data)}`);
    }
  }
}
rows.forEach((r) => console.log(r)); console.log(`${rows.length} declarations in ${new Set(rows.map((r) => r.split(' ')[0])).size} documents`);
