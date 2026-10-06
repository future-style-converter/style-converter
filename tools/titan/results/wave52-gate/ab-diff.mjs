#!/usr/bin/env node
// tools/titan/results/wave52-gate/ab-diff.mjs
//
// One device A/B, read out per cell. The EXCLUDE arm is a short run (a few
// sections) of the integrated tree with ONE mechanism taken out; the INCLUDE
// arm is the closing gate itself. Every cell of the excluded run's sections is
// compared — a flip, or a move of at least <threshold> SSIM, is what the
// mechanism did on device; everything else in those sections is what it did
// NOT do (the control, and the reason whole sections are run).
//
// Usage: node ab-diff.mjs <excludeRun> <includeRun> [--threshold 0.002] [--platforms ios,android]
// Uses the scorer's own loader, so "scored" and "pass" mean what the gate means.
import { loadRun, resolveRunDir } from '../../score-gate.mjs';

const args = process.argv.slice(2);
const opt = (name, dflt) => { const i = args.indexOf(name); return i < 0 ? dflt : args.splice(i, 2)[1]; };
const threshold = Number(opt('--threshold', '0.002'));
const platforms = opt('--platforms', 'web,ios,android').split(',');
const [excludeId, includeId] = args;
if (!includeId) { console.error('usage: ab-diff.mjs <excludeRun> <includeRun> [--threshold N] [--platforms a,b]'); process.exit(2); }
const load = (id) => { const dir = resolveRunDir(id); if (!dir) { console.error(`no run dir for ${id}`); process.exit(2); } return loadRun(dir); };
const exclude = load(excludeId), include = load(includeId);

const rows = [];
let compared = 0, identical = 0;
for (const [sec, section] of Object.entries(exclude.sections)) {
  const other = include.sections[sec];
  if (!other) { console.log(`section ${sec}: absent from ${includeId} — not compared`); continue; }
  // The union of both arms' scored cells: a cell only one arm scored is a finding too.
  for (const k of new Set([...section.cells.keys(), ...other.cells.keys()])) {
    const [test, platform] = k.split('|');
    if (!platforms.includes(platform)) continue;
    const a = section.cells.get(k), b = other.cells.get(k);
    compared++;
    if (!a || !b) { rows.push({ sec, test, platform, kind: a ? 'only-exclude' : 'only-include', a, b, delta: 0 }); continue; }
    const delta = +(b.ssim - a.ssim).toFixed(4);
    if (a.pass !== b.pass) rows.push({ sec, test, platform, kind: b.pass ? 'flip f→P' : 'flip P→f', a, b, delta });
    else if (Math.abs(delta) >= threshold) rows.push({ sec, test, platform, kind: 'mover', a, b, delta });
    else identical++;
  }
}
const show = (x) => (x ? `${x.pass ? 'P' : 'f'} ${x.ssim}` : '—');
console.log(`A/B  exclude=${excludeId}  include=${includeId}  sections=${Object.keys(exclude.sections).join(',')}  platforms=${platforms.join(',')}`);
console.log(`compared ${compared} cells: ${rows.length} differ (flip, or |Δ| ≥ ${threshold}), ${identical} within threshold`);
// Flips first, then movers by size: the read-out order of an A/B.
rows.sort((p, q) => (p.kind.startsWith('flip') ? 0 : 1) - (q.kind.startsWith('flip') ? 0 : 1) || Math.abs(q.delta) - Math.abs(p.delta));
for (const r of rows) console.log(`  ${r.kind.padEnd(12)} ${r.platform.padEnd(8)} ${show(r.a).padEnd(9)} → ${show(r.b).padEnd(9)} Δ${r.delta >= 0 ? '+' : ''}${r.delta}  ${r.test}`);
