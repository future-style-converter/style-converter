// Wave-50 lane B11 census: every wave49-final component that declares CSS
// `visibility`, plus the ancestor/descendant relation that decides whether
// the natives' subtree-wide alpha(0)/opacity(0) shortcut is spec-equivalent.
//
//   node tools/titan/results/wave50-B11/visibility-census.mjs   (from the repo root)
//
// Frozen output: visibility-census.json beside this file.
import fs from 'node:fs';
import path from 'node:path';
const ROOT = 'tools/titan/runs/wave49-final/sections';
const rows = [];
for (const sec of fs.readdirSync(ROOT)) {
  const dir = path.join(ROOT, sec, 'per-test-ir');
  if (!fs.existsSync(dir)) continue;
  for (const f of fs.readdirSync(dir)) {
    const doc = JSON.parse(fs.readFileSync(path.join(dir, f), 'utf8'));
    const comps = doc.components || [];
    if (!comps.some(c => (c.properties || []).some(p => p.type === 'Visibility'))) continue;
    const byId = new Map(comps.map(c => [c.id, c]));
    const vis = c => ((c.properties || []).find(p => p.type === 'Visibility') || {}).data;
    const ancestors = c => { const out = []; let p = (c.slot || {}).parent; while (p && byId.has(p)) { out.push(byId.get(p)); p = (byId.get(p).slot || {}).parent; } return out; };
    const declared = comps.filter(c => vis(c));
    const hidden = declared.filter(c => vis(c) === 'HIDDEN' || vis(c) === 'COLLAPSE');
    const hiddenWithKids = hidden.filter(h => comps.some(c => (c.slot || {}).parent === h.id));
    const escapes = comps.filter(c => vis(c) === 'VISIBLE' && ancestors(c).some(a => vis(a) === 'HIDDEN' || vis(a) === 'COLLAPSE'));
    rows.push({
      section: sec,
      test: f.replace(/\.json$/, ''),
      declaredVisibility: declared.map(c => `${c.id}=${vis(c)}`),
      hiddenCount: hidden.length,
      hiddenWithChildren: hiddenWithKids.map(c => c.id),
      visibleDescendantsOfHidden: escapes.map(c => c.id),
    });
  }
}
rows.sort((a, b) => (a.section + a.test).localeCompare(b.section + b.test));
console.log(JSON.stringify(rows, null, 1));
