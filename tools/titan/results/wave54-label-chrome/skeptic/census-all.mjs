// skeptic/census-all.mjs — wave 54 L7 SKEPTIC census (independent of the lane's census.mjs): every per-test IR document
// of a run whose property lists carry an IR property of type "All" (deep walk: components[].properties[] and any nested
// object), by section; then the same for a second run and a byte comparison of every per-test IR file between the runs.
//   node census-all.mjs <run> [<run2>]
import { readdirSync, readFileSync, existsSync } from 'node:fs';   // per-test IR files
import path from 'node:path';                                     // joins
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..'); // repo root
const [run, run2] = process.argv.slice(2);                         // run ids
const docs = (r) => { const out = new Map(); const S = path.join(ROOT, 'tools/titan/runs', r, 'sections');
  for (const s of readdirSync(S)) { const d = path.join(S, s, 'per-test-ir'); if (!existsSync(d)) continue;
    for (const f of readdirSync(d).filter((n) => n.endsWith('.json'))) out.set(`${s}/${f}`, readFileSync(path.join(d, f))); } return out; };
const hasAll = (buf) => { let hit = false; const walk = (v) => { if (hit || v === null || typeof v !== 'object') return; if (!Array.isArray(v) && v.type === 'All') { hit = true; return; } for (const x of Object.values(v)) walk(x); }; walk(JSON.parse(buf.toString())); return hit; };
const A = docs(run); const carriers = [...A].filter(([, b]) => hasAll(b)).map(([k]) => k).sort();
console.log(`${run}: ${A.size} per-test IR documents; carrying an "All" property: ${carriers.length}`); carriers.forEach((k) => console.log('  ' + k));
const raw = [...A].filter(([, b]) => b.toString().includes('"All"')).length; console.log(`  (raw substring "All" in ${raw} documents)`);
if (run2) { const B = docs(run2); let same = 0, differ = 0, only = 0; for (const [k, b] of A) { const c = B.get(k); if (!c) only++; else if (Buffer.compare(b, c) === 0) same++; else differ++; }
  console.log(`${run} vs ${run2}: ${A.size} vs ${B.size} documents; identical ${same}, differ ${differ}, missing in ${run2} ${only}, missing in ${run} ${[...B.keys()].filter((k) => !A.has(k)).length}`); }
