#!/usr/bin/env node
// tools/titan/results/wave53-gate/adjudicate.mjs
//
// Wave 53's closing-gate adjudication, read off the plan's pre-registered
// expectations (tools/titan/results/wave54-plan/expectations.json, written
// before any lane landed) instead of hand-written rules:
//   R1  lost cells ⊆ expected.lost (EMPTY this wave — no instrument change);
//       a lost cell stops the ship until its cause is found.
//   R2  unmeasured-now ⊆ expected.unmeasuredNow (empty), newly measured ⊆
//       expected.newlyMeasured (empty): the denominator does not move.
//   R3  no missing section, no short column.
//   R4  every GATING prediction with a floor meets it (verdict and score);
//       the others are read out, never enforced.
//   R5  every must-not-move cell of every lane is unchanged in verdict and
//       within 0.002 in score.
//   R6  a flip onto a cell the plan marks degenerate-by-construction is
//       reported as DEGENERATE, never counted as a gain.
// Exit 0 when R1–R5 hold, 1 otherwise. The read-out names every cell.
//
// Usage: node adjudicate.mjs <score.json>   (score-gate.mjs wave53-open → <final> --json)
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const EXP = JSON.parse(readFileSync(process.argv[3], 'utf8'));
const [scorePath] = process.argv.slice(2);
if (!scorePath) { console.error('usage: adjudicate.mjs <score.json>'); process.exit(2); }
const score = JSON.parse(readFileSync(scorePath, 'utf8'));

// The plan writes a cell as "css-lists/counter-reset-reversed-nested.html web"; the scorer as test "css/css-lists/….html" + platform.
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const fmt = (c) => `    ${key(c)}  ${c.prev ?? '—'} → ${c.cur ?? '—'}`;
const byKey = new Map();
for (const list of ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow']) for (const c of score[list]) byKey.set(key(c), { ...c, list });
// Per-section tables carry every scored cell's totals only; for must-not-move
// reads we need the cell itself — the scorer's lists hold only the cells that
// moved, so an absent key means "unchanged within the mover threshold".

let bad = 0;
const rule = (id, ok, text) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${id}  ${text}`); if (!ok) bad++; };
const totals = (t) => ['web', 'ios', 'android'].map((p) => `${p} ${t[p].passing}/${t[p].measured}`).join('  ');
console.log(`record  ${score.prev} → ${score.cur}\n  prev  ${totals(score.totals.prev)}\n  cur   ${totals(score.totals.cur)}`);
console.log(`  gained ${score.gained.length}  lost ${score.lost.length}  newly-measured ${score.newlyMeasured.length}  unmeasured-now ${score.unmeasuredNow.length}  movers ${score.movers.length}\n`);

// R1 — the empty lost list.
const lostAllowed = new Set(EXP.expected.lost);
const realLost = score.lost.filter((c) => !lostAllowed.has(key(c)));
rule('R1', realLost.length === 0, `lost ${score.lost.length}; pre-registered ${EXP.expected.lost.length}; outside the list ${realLost.length}`);
realLost.forEach((c) => console.log(fmt(c)));

// R2 — the denominator.
const outAllowed = new Set(EXP.expected.unmeasuredNow), inAllowed = new Set(EXP.expected.newlyMeasured);
const surpriseOut = score.unmeasuredNow.filter((c) => !outAllowed.has(key(c))), surpriseIn = score.newlyMeasured.filter((c) => !inAllowed.has(key(c)));
rule('R2', surpriseOut.length === 0 && surpriseIn.length === 0, `unmeasured-now ${score.unmeasuredNow.length} (unexpected ${surpriseOut.length}), newly measured ${score.newlyMeasured.length} (unexpected ${surpriseIn.length})`);
[...surpriseOut, ...surpriseIn].forEach((c) => console.log(fmt(c)));

// R3 — a gate with a hole in it is not a gate.
rule('R3', score.missingSections.length === 0 && score.columnShorts.length === 0, `missing sections ${score.missingSections.length}, short columns ${score.columnShorts.length}`);

// Units the probe reverted (expectations.probeDecisions): their predictions are withdrawn from R4 and
// their cells are must-not-move for R5 — the closing gate must show them back at wave53-open.
const decisions = (EXP.probeDecisions && EXP.probeDecisions.reverted) || [];
const withdrawn = new Set(decisions.flatMap((d) => d.withdrawnPredictions || []));
const heldCells = decisions.flatMap((d) => (d.mustNotMoveAfter || []).map((c) => [`probe-revert:${d.unit}`, c]));
// R4 — gating predictions with a floor.
let gatingFail = 0, gatingTotal = 0;
console.log('\npredictions (gating ones enforced):');
for (const [laneId, lane] of Object.entries(EXP.lanes)) {
  for (const p of lane.predictions || []) {
    if (!/ (web|ios|android)$/.test(p.cell)) { console.log(`  ${laneId}  ${p.cell}: ${p.from} → ${p.to} (not a corpus cell — read elsewhere)`); continue; }
    if (withdrawn.has(p.cell)) { console.log(`  ~ ${laneId}  ${p.cell}: WITHDRAWN at wave53-probe (its unit was reverted — probeDecisions); held to must-not-move instead`); continue; }
    const cell = byKey.get(p.cell);
    const wantPass = /^P/.test(p.to);
    const measured = cell ? `${cell.curPass ? 'P' : 'f'} ${cell.cur}` : 'unchanged (not in any list)';
    let ok = true;
    if (p.gating && p.floor != null) {
      gatingTotal++;
      if (!cell) ok = false;   // a gating prediction that did not move at all did not happen
      else ok = (cell.curPass === wantPass) && (typeof cell.cur === 'number' && cell.cur >= p.floor);
      if (!ok) gatingFail++;
    }
    console.log(`  ${ok ? ' ' : '!'} ${laneId}  ${p.cell}: predicted ${p.from} → ${p.to} (${p.confidence}${p.gating ? ', gating' : ''}${p.floor != null ? `, floor ${p.floor}` : ''}) — measured ${measured}`);
  }
}
rule('R4', gatingFail === 0, `gating predictions ${gatingTotal}, missed ${gatingFail}`);

// R5 — must-not-move cells: not in lost/gained, and movers within 0.002.
let mnmBroken = 0, mnmTotal = 0;
const mnmPairs = [...Object.entries(EXP.lanes).flatMap(([laneId, lane]) => (lane.mustNotMove || []).map((c) => [laneId, c])), ...heldCells];
for (const [laneId, cellName] of mnmPairs) {
  {
    mnmTotal++;
    const cell = byKey.get(cellName);
    if (!cell) continue;   // unchanged within the scorer's mover threshold
    const moved = cell.list === 'lost' || cell.list === 'gained' || cell.list === 'unmeasuredNow' || cell.list === 'newlyMeasured' || Math.abs((cell.cur ?? 0) - (cell.prev ?? 0)) > 0.002;
    if (moved) { mnmBroken++; console.log(`    must-not-move MOVED (${laneId}): ${cellName}  ${cell.prev} → ${cell.cur} [${cell.list}]`); }
  }
}
rule('R5', mnmBroken === 0, `must-not-move cells ${mnmTotal}, moved ${mnmBroken}`);

// R6 — degenerate-by-construction flips are named, not celebrated.
const degenerate = new Set((EXP.degenerateByConstruction || []).map((t) => t.replace(/^css\//, '')));
const degenerateGains = score.gained.filter((c) => degenerate.has(c.test.replace(/^css\//, '').replace(/ .*$/, '')) || [...degenerate].some((t) => c.test.endsWith(t)));
if (degenerateGains.length) { console.log(`\nDEGENERATE-BY-CONSTRUCTION gains (not fixes): ${degenerateGains.length}`); degenerateGains.forEach((c) => console.log(fmt(c))); }

console.log(`\ngained ${score.gained.length}: ${['web', 'ios', 'android'].map((p) => `${p} ${score.gained.filter((c) => c.platform === p).length}`).join('  ')}`);
console.log(bad ? `\nADJUDICATION: ${bad} rule(s) broken — do not ship` : '\nADJUDICATION: R1–R5 hold');
process.exit(bad ? 1 : 0);
