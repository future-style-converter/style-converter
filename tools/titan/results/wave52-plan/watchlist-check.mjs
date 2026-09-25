#!/usr/bin/env node
// tools/titan/results/wave52-plan/watchlist-check.mjs — does every line of the
// wave-52 watchlist match at least one SCORED cell of the run of record?
//
// Why: plan-skeptic-1 C7 found 11 of 176 watchlist lines that matched zero
// cells under score-gate.mjs's own substring rule (the L7 headline flip among
// them) — a watch that matches nothing attributes nothing at the gate. This
// script replays the SAME matching, by importing `loadRun` and `watchCells`
// from tools/titan/score-gate.mjs (no re-implementation, so it cannot drift):
//   pat is a substring of `${sectionDir}/${manifestKey}` where manifestKey is
//   `css/<section>/<path>.html`; an optional second token restricts platform.
// It prints every unmatched line and exits 1 if there is one, 0 otherwise.
//
// Usage: node tools/titan/results/wave52-plan/watchlist-check.mjs
//        [RUN=wave51-fix] [WATCH=<file>]   (env overrides; read-only)
import fs from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const TITAN = path.resolve(HERE, '..', '..');
const { loadRun, watchCells, resolveRunDir } = await import(path.join(TITAN, 'score-gate.mjs'));

const runId = process.env.RUN || 'wave51-fix';
const watchPath = process.env.WATCH || path.join(HERE, 'watchlist.txt');
const runDir = resolveRunDir(runId);
if (!runDir) { console.error(`watchlist-check: run dir not found for ${runId}`); process.exit(2); }

// One run on both sides — the matching in watchCells is over the union of the
// two runs' scored cell keys, so prev == cur replays it against the run alone.
const run = loadRun(runDir);
const lines = fs.readFileSync(watchPath, 'utf8').split('\n');
const watches = lines.map(l => l.trim()).filter(l => l && !l.startsWith('#'));
const rows = watchCells(run, run, lines);

// Count matched cells per watch line (watchCells tags each row with the line).
const hits = new Map(watches.map(w => [w, 0]));
for (const r of rows) hits.set(r.watch, (hits.get(r.watch) || 0) + 1);

let scored = 0;
for (const s of Object.values(run.sections)) scored += s.cells.size;
const distinct = new Set(rows.map(r => `${r.sec}/${r.test}|${r.platform}`)).size;
const unmatched = watches.filter(w => !hits.get(w));

console.log(`watchlist-check  run=${path.basename(runDir)}  scored-cells=${scored}  watch-lines=${watches.length}  matched-cells(distinct)=${distinct}`);
for (const w of watches) if (hits.get(w)) console.log(`  ok   ${String(hits.get(w)).padStart(4)}  ${w}`);
if (unmatched.length) {
  console.log(`  UNMATCHED (${unmatched.length}) — these attribute nothing at the gate:`);
  for (const w of unmatched) console.log(`    ${w}`);
} else {
  console.log(`  unmatched 0`);
}
process.exit(unmatched.length ? 1 : 0);
