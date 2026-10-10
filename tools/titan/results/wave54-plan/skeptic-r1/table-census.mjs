// Plan skeptic r1, check (2i): L2 TB-android radius — (a) body-roots (meta.role body-root) whose LAST Display is TABLE /
// INLINE_TABLE (the only shape TableBodyForest synthesises anonymous boxes for); (b) every document with any component
// whose last Display is TABLE / INLINE_TABLE (the Compose DisplayType.TABLE population the plan holds must-not-move).
import fs from 'node:fs'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const SD = path.join(ROOT, 'tools/titan/runs', process.argv[2] || 'wave53-final', 'sections');
const last = (c, t) => (c.properties || []).filter((p) => p.type === t).at(-1)?.data;
const bodyTables = [], tableDocs = [];
for (const sec of fs.readdirSync(SD).sort()) {
  const d = path.join(SD, sec, 'per-test-ir'); if (!fs.existsSync(d)) continue;
  for (const f of fs.readdirSync(d).filter((x) => x.endsWith('.json')).sort()) {
    const comps = JSON.parse(fs.readFileSync(path.join(d, f), 'utf8')).components; const k = `${sec}/${f.replace(/\.json$/, '')}`;
    const isT = (c) => ['TABLE', 'INLINE_TABLE'].includes(last(c, 'Display'));
    for (const c of comps) if (c.meta?.role === 'body-root' && isT(c)) bodyTables.push(`${k} ${c.name} ${last(c, 'Display')}`);
    if (comps.some(isT)) tableDocs.push(k);
  }
}
console.log(`(a) body-root with last Display TABLE|INLINE_TABLE: ${bodyTables.length}`); bodyTables.forEach((x) => console.log('  ' + x));
console.log(`(b) documents with any TABLE|INLINE_TABLE component: ${tableDocs.length}`); console.log('  ' + tableDocs.join('\n  '));
