#!/usr/bin/env node
// tools/titan/results/wave54-plan/snapshot-cells.mjs — freeze every SCORED cell of a run into one JSON file, so
// plan-build.py can (a) verify each "from" value it writes, (b) enumerate family must-not-move lines (every css-gaps
// cell, every web hyphens cell, …) instead of typing them, and (c) refuse a watch line that matches no scored cell.
//
// Why a node step: "scored" and "P" must mean what the gate means, so this imports the scorer's own loader
// (tools/titan/score-gate.mjs loadRun) — the same call cells.mjs and watchlist-check.mjs make; nothing is
// re-implemented. Read-only over tools/titan/runs/<run>/sections/*/manifest.json.
//
// Usage: node tools/titan/results/wave54-plan/snapshot-cells.mjs [run=wave53-final]
//   → tools/titan/results/wave54-plan/cells-<run>.json  {run, totals:{web:[pass,scored],…}, cells:{"<sec>/<path>.html <platform>": "P 0.9818"}}
import { writeFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const runId = process.argv[2] || 'wave53-final';
const dir = resolveRunDir(runId);
if (!dir) { console.error(`snapshot-cells: no run dir for ${runId}`); process.exit(2); }
const run = loadRun(dir);
const cells = {}, totals = { web: [0, 0], ios: [0, 0], android: [0, 0] };
for (const s of Object.values(run.sections)) {
  for (const [key, c] of s.cells) {
    // key is "css/<section>/<path>.html|<platform>" — the watch-line form is "<section>/<path>.html <platform>".
    const [test, platform] = key.split('|');
    cells[`${test.replace(/^css\//, '')} ${platform}`] = `${c.pass ? 'P' : 'f'} ${c.ssim}`;
    totals[platform][1]++; if (c.pass) totals[platform][0]++;
  }
}
const out = path.join(HERE, `cells-${runId}.json`);
writeFileSync(out, JSON.stringify({ run: runId, totals, cells }, null, 0) + '\n');
console.log(`${runId}: ${Object.keys(cells).length} scored cells — ${Object.entries(totals).map(([p, [a, b]]) => `${p} ${a}/${b}`).join(' · ')} → ${path.relative(process.cwd(), out)}`);
