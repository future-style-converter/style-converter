// skeptic-r2: file-level ownership across the seven lanes of PLAN.md §2. Every backticked token of each lane's
// "own:" block (and its "Seams:" line) is brace-expanded; basenames and path tails are compared across lanes.
import { readFileSync } from 'node:fs';
const plan = readFileSync(process.argv[2], 'utf8').split('\n');
const lanes = {}; let cur = null, inOwn = false;
for (const line of plan) {
  const h = line.match(/^### (L\d) · /); if (h) { cur = h[1]; lanes[cur] = { own: [], seams: [] }; inOwn = false; continue; }
  if (/^## /.test(line)) { cur = null; continue; }
  if (!cur) continue;
  if (/^- \*\*own:\*\*/.test(line)) { inOwn = true; }
  else if (/^- \*\*/.test(line)) { inOwn = false; }
  if (/^- \*\*Seams:\*\*/.test(line)) lanes[cur].seams.push(line);
  if (inOwn) for (const m of line.matchAll(/`([^`]+)`/g)) lanes[cur].own.push(m[1]);
}
const expand = (s) => { const m = s.match(/\{([^}]+)\}/); if (!m) return [s]; return m[1].split(',').flatMap((alt) => expand(s.replace(m[0], alt))); };
const files = {};
for (const [id, l] of Object.entries(lanes)) {
  files[id] = [...new Set(l.own.flatMap(expand).filter((t) => /[\/.]/.test(t) && !/^\d/.test(t) && !t.includes(' ')))];
  console.log(`${id}: ${files[id].length} owned tokens`); files[id].forEach((f) => console.log('   ' + f));
}
const base = (f) => f.replace(/\/$/, '').split('/').pop();
const seen = new Map(); let shared = 0;
for (const [id, fs] of Object.entries(files)) for (const f of fs) {
  const b = base(f); if (!b.includes('.') ) continue;     // directories (results dirs) are per-lane by name
  if (seen.has(b) && seen.get(b) !== id) { shared++; console.log(`SHARED basename ${b}: ${seen.get(b)} and ${id}`); }
  else seen.set(b, id);
}
console.log(`basename collisions across lanes: ${shared}`);
