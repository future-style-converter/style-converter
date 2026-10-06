#!/usr/bin/env node
// tools/titan/results/wave52-gate/cells.mjs
//
// Look a cell up by name across runs — the read-out behind every "f 0.94 → P 0.99"
// sentence in the wave-52 record. One line per scored cell whose "test|platform"
// key matches the regex; one column per run, in the order given ("—" = not scored
// in that run). Uses the scorer's own loader, so "scored" and "P" mean what the
// gate means.
//
// Usage: node cells.mjs '<regex over "css/<section>/<test>.html|<platform>">' <run> [<run> …]
//   e.g. node cells.mjs 'hyphens-manual-01[12].*android' wave51-fix wave52-final wave52-ship
import { loadRun, resolveRunDir } from '../../score-gate.mjs';
const [pat, ...runs] = process.argv.slice(2);
const re = new RegExp(pat);
const loaded = runs.map((r) => [r, loadRun(resolveRunDir(r))]);
const keys = new Set();
for (const [, run] of loaded) for (const s of Object.values(run.sections)) for (const k of s.cells.keys()) if (re.test(k)) keys.add(k);
for (const k of [...keys].sort()) {
  const row = loaded.map(([, run]) => { for (const s of Object.values(run.sections)) { const c = s.cells.get(k); if (c) return `${c.pass ? 'P' : 'f'} ${c.ssim}`; } return '—'; });
  console.log(k.replace('css/', '').padEnd(78), row.map((x) => x.padEnd(9)).join(' → '));
}
