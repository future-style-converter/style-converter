// Plan skeptic r1, check (3): file-level ownership disjointness across the seven lanes, from PLAN.md §2 "own:" blocks
// (backticked paths, brace-expanded), resolved against the tree (git ls-files + NEW markers), plus the §3 seam files.
import fs from 'node:fs'; import { execSync } from 'node:child_process'; import path from 'node:path';
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..');
const plan = fs.readFileSync(path.join(ROOT, 'tools/titan/results/wave54-plan/PLAN.md'), 'utf8').split('\n');
const tracked = execSync('git ls-files', { cwd: ROOT, maxBuffer: 1 << 28 }).toString().split('\n');
const expand = (s) => { const m = s.match(/\{([^}]*)\}/); if (!m) return [s]; return m[1].split(',').flatMap((x) => expand(s.replace(m[0], x))); };
const lanes = {}; let lane = null, inOwn = false;
for (const l of plan) {
  const h = l.match(/^### (L\d) /); if (h) { lane = h[1]; inOwn = false; continue; }
  if (/^## /.test(l)) { lane = null; continue; }
  if (!lane) continue;
  if (/^- \*\*own:\*\*/.test(l)) { inOwn = true; }
  else if (/^- \*\*/.test(l)) inOwn = false;
  if (!inOwn) continue;
  for (const t of l.match(/`[^`]+`/g) || []) { const raw = t.slice(1, -1).trim(); if (!/\.(kt|swift|ts|tsx|mjs|json|js|png)$|\/$/.test(raw) && !raw.includes('{')) continue;
    for (const e of expand(raw)) (lanes[lane] ||= new Set()).add(e); }
}
const resolve = (p) => { if (p.endsWith('/')) return [p]; const tail = p.replace(/^.*?…\//, '').replace(/^src\/test\/…\//, ''); const hits = tracked.filter((f) => f.endsWith('/' + tail) || f === tail); return hits.length ? hits : [`NEW:${tail}`]; };
const owner = new Map();
for (const [ln, set] of Object.entries(lanes)) for (const p of set) for (const r of resolve(p)) { (owner.get(r) ?? owner.set(r, new Set()).get(r)).add(ln); }
let shared = 0;
for (const [f, s] of owner) if (s.size > 1) { shared++; console.log(`SHARED ${f}: ${[...s].join(', ')}`); }
for (const [ln, set] of Object.entries(lanes)) console.log(`${ln}: ${[...set].length} entries → ${[...set].flatMap(resolve).length} paths: ${[...set].flatMap(resolve).map((x) => x.replace(/^.*\//, '')).join(' ')}`);
console.log(`shared files across lanes (outside the four registered seam files): ${shared}`);
