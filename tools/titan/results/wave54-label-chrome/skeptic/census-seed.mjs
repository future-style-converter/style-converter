// skeptic/census-seed.mjs — wave 54 L7 SKEPTIC census of U2-seed's radius (independent of the lane's census.mjs):
// (1) which tracked fixtures (git ls-files fixtures/*.json, any depth of `children`) declare a component whose name
//     equals one of the six seeded names — test-all.sh `_gate_fixture_has_baselines` and compare-screenshots
//     --baseline match PNGs by `<platform>__NNN_<name>.png`, so such a fixture would switch / compare against the seed;
// (2) for each gate fixture (tools/visual/gate-fixtures.txt): has-baselines today vs after the 18 PNGs (the shell rule
//     re-typed), so a gate-only → BASELINE flip by the seed is visible;
// (3) the per-test IR of a run: component names equal to a seeded name (WPT mode never runs --baseline; reported only).
import { execSync } from 'node:child_process';                      // git ls-files
import { readFileSync, readdirSync, existsSync } from 'node:fs';    // fixtures, baselines
import path from 'node:path';                                       // joins
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), '../../../../..'); // repo root
const SEEDED = ['ATC_AllThenProps', 'ATC_PropsThenAll_InGreenParent', 'reset', 'ATC_InitialUnderRedParent', 'span', 'ATC_DirectionSurvives'];
const names = (doc) => { const s = []; const w = (m) => { for (const [k, v] of Object.entries(m ?? {})) { s.push(k); if (v && v.children) w(v.children); } }; w(doc?.components); return s; };
const tracked = execSync('git ls-files fixtures', { cwd: ROOT }).toString().trim().split('\n').filter((f) => f.endsWith('.json'));
let parsed = 0; const hits = [];
for (const f of tracked) { let d; try { d = JSON.parse(readFileSync(path.join(ROOT, f), 'utf8')); } catch { continue; } if (!d?.components) continue; parsed++;
  const ns = names(d); const hit = ns.filter((n) => SEEDED.includes(n) || /^atc_/i.test(n) || /^(reset|span)$/i.test(n)); if (hit.length) hits.push(`${f}: ${[...new Set(hit)].join(', ')}`); }
console.log(`(1) tracked fixture JSONs ${tracked.length}, with components ${parsed}; declaring a seeded name (or ATC_* / reset / span, any case):`); hits.forEach((h) => console.log('    ' + h));
const base = readdirSync(path.join(ROOT, 'tools/visual/baseline')).filter((f) => f.endsWith('.png'));
const seed = readdirSync(path.join(ROOT, 'tools/titan/results/wave54-plan/label-chrome-all-reset.seeded-77fe41e8')).filter((f) => f.endsWith('.png'));
const has = (doc, files) => { const ns = new Set(names(doc)); return files.some((f) => ns.has(/^(?:iOS|Android|web)__\d+_(.+)\.png$/.exec(f)?.[1])); };
console.log(`(2) gate fixtures — has committed baselines (test-all rule): today (${base.length} PNGs) → after seed (${base.length + seed.length})`);
for (const line of readFileSync(path.join(ROOT, 'tools/visual/gate-fixtures.txt'), 'utf8').split('\n')) { const f = line.trim().split(/\s+/)[0]; if (!f || f.startsWith('#')) continue;
  const d = JSON.parse(readFileSync(path.join(ROOT, f), 'utf8')); console.log(`    ${f.padEnd(52)} ${has(d, base)} → ${has(d, [...base, ...seed])}`); }
const run = process.argv[2]; if (run) { const S = path.join(ROOT, 'tools/titan/runs', run, 'sections'); let n = 0; const wpt = [];
  for (const s of readdirSync(S)) { const d = path.join(S, s, 'per-test-ir'); if (!existsSync(d)) continue; for (const f of readdirSync(d)) { n++; const ir = JSON.parse(readFileSync(path.join(d, f), 'utf8'));
    for (const c of ir.components ?? []) if (SEEDED.includes(c.name)) wpt.push(`${s}/${f}:${c.name}`); } }
  console.log(`(3) ${run}: ${n} per-test IR docs; components named exactly a seeded name: ${wpt.length}`); wpt.slice(0, 10).forEach((x) => console.log('    ' + x)); }
