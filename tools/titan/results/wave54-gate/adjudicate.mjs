#!/usr/bin/env node
// tools/titan/results/wave54-gate/adjudicate.mjs (the wave-53 copy: the expectations path and message strings differ, and fix r3
// added the input guards (exit 2) and R6's degenerateRetirement read; the R1–R5 rule bodies are the wave-53 ones)
//
// Wave 54's closing-gate adjudication, read off the plan's pre-registered
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
//       reported as DEGENERATE, never counted as a gain — UNLESS the plan's
//       `degenerateRetirement` holds for that cell (fix r3, plan-skeptic R3-S2):
//       every unit `degenerateRetirementCheck.units[<platform>]` names is still
//       on the tree (none in probeDecisions.reverted) AND the geometry-gate
//       JSON (--geometry) reads its `hyphenate-character.geometry.py` row PASS,
//       ending "→ GEOMETRY OK". Then it is RETIRED from the list and is a gain.
//       Without --geometry nothing is retired (honest by default). Read-out
//       only: R6 never changes the exit code (R6 picture-correctness is
//       geometry-gate.py's exit, PLAN §6).
// Exit 0 when R1–R5 hold, 1 otherwise, 2 when the input cannot be adjudicated
// (fix r3, plan-skeptic R3-S1 — the two guards probe-readout.mjs already has):
//   ORDER    the record's `prev` is not --base (default wave54-open): Δ = cur − prev must mean final − wave54-open;
//   NOT A --movers 0 RECORD  gained + lost + movers + unmeasured-now ≠ the cells scored on `prev` (or gained + lost +
//            movers + newly-measured ≠ the cells scored on `cur`): a thresholded record — e.g. score-final.movers0005.json,
//            the corpus snapshot's source — drops every same-verdict cell below its threshold, so R4 would read a met
//            P→P row as "scored on neither side" and R5 would not see a must-not-move move in (0.002, threshold);
//   GEOMETRY PAIR  the --geometry JSON was written for another run pair than the score record.
// The read-out names every cell.
//
// Usage: node adjudicate.mjs <score.json> [--geometry <geometry.json>] [--base wave54-open] [--exp <expectations.json>]
//   <score.json>    expectations.json `scoreOfRecord`: score-gate.mjs wave54-open wave54-final --movers 0
//                   --json tools/titan/results/wave54-gate/score-final.json (PLAN §6 "Score of record"; NEVER the
//                   --movers 0.005 score-final.movers0005.json, which is `scoreReadout` / `corpusSnapshotSource`)
//   <geometry.json> expectations.json `geometryGate.closingCmd`: geometry-gate.py wave54-final --base wave54-open
//                   --json tools/titan/results/wave54-gate/geometry-final.json (read for R6's retirement only)
//   --exp           a dry-run expectations.json (plan-build.py --out …) instead of the installed one
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const REPO = path.resolve(HERE, '..', '..', '..', '..');
const args = process.argv.slice(2);
const opt = (name, dflt) => { const i = args.indexOf(name); return i < 0 ? dflt : args.splice(i, 2)[1]; };
const expPath = opt('--exp', path.join(REPO, 'tools/titan/results/wave54-plan/expectations.json'));
const base = opt('--base', 'wave54-open');
const geometryPath = opt('--geometry', null);
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const [scorePath] = args;
if (!scorePath || args.length !== 1) {
  console.error('usage: adjudicate.mjs <score.json> [--geometry <geometry.json>] [--base wave54-open] [--exp <expectations.json>]');
  process.exit(2);
}
const score = JSON.parse(readFileSync(scorePath, 'utf8'));

// ── the order guard (fix r3, R3-S1): Δ = cur − prev must mean <closing run> − <base> ──
if (score.prev !== base) {
  console.error(`ORDER: this record is score-gate.mjs ${score.prev} → ${score.cur}; adjudication needs ${base} FIRST ` +
    `(${EXP.scoreOfRecord || `score-gate.mjs ${base} <run> --movers 0 --json …`}). Refusing.`);
  process.exit(2);
}
// ── the --movers 0 guard (fix r3, R3-S1): every cell scored on both sides must be in gained / lost / movers ──
const measuredOn = (t) => ['web', 'ios', 'android'].reduce((n, p) => n + t[p].measured, 0);
const listedPrev = score.gained.length + score.lost.length + score.movers.length + score.unmeasuredNow.length;
const listedCur = score.gained.length + score.lost.length + score.movers.length + score.newlyMeasured.length;
if (listedPrev !== measuredOn(score.totals.prev) || listedCur !== measuredOn(score.totals.cur)) {
  console.error(`NOT A --movers 0 RECORD: ${listedPrev} cells listed against ${measuredOn(score.totals.prev)} scored on ${score.prev} ` +
    `(${listedCur} against ${measuredOn(score.totals.cur)} on ${score.cur}); a same-verdict cell below the record's mover threshold is ` +
    `absent from every list, so R4 would read a met P→P row as a miss and R5 would not see a must-not-move move. ` +
    `Adjudicate the --movers 0 record (${EXP.scoreOfRecord ? 'expectations.json scoreOfRecord' : 'score-gate.mjs … --movers 0'}); ` +
    `the --movers 0.005 JSON is the PR read-out and the corpus snapshot's source only (scoreReadout / corpusSnapshotSource). Refusing.`);
  process.exit(2);
}
// ── the geometry JSON R6 reads must be for the same pair (fix r3, R3-S2) ──
const geometry = geometryPath ? JSON.parse(readFileSync(geometryPath, 'utf8')) : null;
if (geometry && (geometry.run !== score.cur || geometry.base !== score.prev)) {
  console.error(`GEOMETRY PAIR: ${geometryPath} is geometry-gate.py ${geometry.run} --base ${geometry.base}; the score record is ` +
    `${score.prev} → ${score.cur}. Refusing.`);
  process.exit(2);
}

// The plan writes a cell as "css-lists/counter-reset-reversed-nested.html web"; the scorer as test "css/css-lists/….html" + platform.
const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const fmt = (c) => `    ${key(c)}  ${c.prev ?? '—'} → ${c.cur ?? '—'}`;
const byKey = new Map();
for (const list of ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow']) for (const c of score[list]) byKey.set(key(c), { ...c, list });
// The score of record is written with --movers 0 (PLAN §6) and the guard above
// refuses any other, so every cell scored on BOTH sides is in gained / lost /
// movers and every one-sided cell in newlyMeasured / unmeasuredNow: an absent key
// is a cell scored on neither side. (Under a thresholded record an absent key
// would mean "moved less than the threshold" — the wave-54 plan-skeptic M1 /
// R3-S1 hole — which is why such a record is refused, and R4 still counts an
// absent gating cell as a miss.)

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
// their cells are must-not-move for R5 — the closing gate must show them back at wave54-open.
const decisions = (EXP.probeDecisions && EXP.probeDecisions.reverted) || [];
const withdrawn = new Set(decisions.flatMap((d) => d.withdrawnPredictions || []));
const withdrawnAt = new Map(decisions.flatMap((d) => (d.withdrawnPredictions || []).map((c) => [c, `${d.run} (unit ${d.unit})`])));
const heldCells = decisions.flatMap((d) => (d.mustNotMoveAfter || []).map((c) => [`probe-revert:${d.unit}`, c]));
// R4 — gating predictions with a floor.
let gatingFail = 0, gatingTotal = 0;
console.log('\npredictions (gating ones enforced):');
for (const [laneId, lane] of Object.entries(EXP.lanes)) {
  for (const p of lane.predictions || []) {
    if (!/ (web|ios|android)$/.test(p.cell)) { console.log(`  ${laneId}  ${p.cell}: ${p.from} → ${p.to} (not a corpus cell — read elsewhere)`); continue; }
    if (withdrawn.has(p.cell)) { console.log(`  ~ ${laneId}  ${p.cell}: WITHDRAWN at ${withdrawnAt.get(p.cell)} (its units were reverted — probeDecisions); held to must-not-move instead`); continue; }
    const cell = byKey.get(p.cell);
    const wantPass = /^P/.test(p.to);
    const measured = cell ? `${cell.curPass ? 'P' : 'f'} ${cell.cur}` : 'not in any list (scored on neither side)';
    let ok = true;
    if (p.gating && p.floor != null) {
      gatingTotal++;
      if (!cell) ok = false;   // a gating prediction that did not move at all did not happen
      else ok = (cell.curPass === wantPass) && (typeof cell.cur === 'number' && cell.cur >= p.floor);
      if (!ok) gatingFail++;
    }
    console.log(`  ${ok ? ' ' : '!'} ${laneId}  ${p.cell}: predicted ${p.from} → ${p.to} (${p.confidence}${p.gating ? ', gating' : ''}${p.floor != null ? `, floor ${p.floor}` : ''}${p.demotedFrom ? `, demoted at ${p.demotedFrom.run}: unit ${p.demotedFrom.unit} reverted` : ''}) — measured ${measured}`);
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
    if (!cell) continue;   // scored on neither side (the --movers 0 record lists every two-sided cell)
    const moved = cell.list === 'lost' || cell.list === 'gained' || cell.list === 'unmeasuredNow' || cell.list === 'newlyMeasured' || Math.abs((cell.cur ?? 0) - (cell.prev ?? 0)) > 0.002;
    if (moved) { mnmBroken++; console.log(`    must-not-move MOVED (${laneId}): ${cellName}  ${cell.prev} → ${cell.cur} [${cell.list}]`); }
  }
}
rule('R5', mnmBroken === 0, `must-not-move cells ${mnmTotal}, moved ${mnmBroken}`);

// R6 — degenerate-by-construction flips are named, not celebrated — unless degenerateRetirement holds (fix r3, R3-S2).
const degenerate = new Set((EXP.degenerateByConstruction || []).map((t) => t.replace(/^css\//, '')));
const degenerateGains = score.gained.filter((c) => degenerate.has(c.test.replace(/^css\//, '').replace(/ .*$/, '')) || [...degenerate].some((t) => c.test.endsWith(t)));
const RC = EXP.degenerateRetirementCheck;   // plan-build.py: the machine form of EXP.degenerateRetirement
// Why a degenerate gain is NOT retired (null = retired): units first (a reverted unit cannot be repaired by a picture),
// then the geometry row of THIS run pair.
function notRetired(c) {
  if (!RC) return 'expectations.json has no degenerateRetirementCheck';
  const test = c.test.replace(/^css\//, '');
  const gone = (RC.units[c.platform] || []).filter((u) => decisions.some((d) => d.lane === RC.lane && d.unit === u));
  if (gone.length) return `unit ${gone.join(', ')} reverted (probeDecisions)`;
  if (!geometry) return 'degenerateRetirement not read: no --geometry JSON (geometry-gate.py <run> --base <base> --json …)';
  const geoKey = `${RC.keys[test]} ${c.platform}`;
  const row = geometry.rows.find((r) => r.script === RC.script && r.key === geoKey);
  if (!row) return `no ${RC.script} "${geoKey}" row in ${geometryPath}`;
  if (row.verdict !== 'PASS' || !row.line.endsWith(RC.lineEndsWith)) return `${RC.script} "${geoKey}" ${row.verdict}: …${row.line.slice(-120)}`;
  return null;
}
const judged = degenerateGains.map((c) => ({ c, why: notRetired(c) }));
const stillDegenerate = judged.filter((j) => j.why), retired = judged.filter((j) => !j.why);
if (stillDegenerate.length) {
  console.log(`\nDEGENERATE-BY-CONSTRUCTION gains (not fixes): ${stillDegenerate.length} — degenerateRetirement does not hold for them`);
  stillDegenerate.forEach(({ c, why }) => console.log(`${fmt(c)}  — ${why}`));
}
if (retired.length) {
  console.log(`\nRETIRED from degenerateByConstruction (degenerateRetirement holds — gains): ${retired.length}`);
  retired.forEach(({ c }) => console.log(`${fmt(c)}  — units ${RC.units[c.platform].join(', ')} on the tree; ${RC.script} "${RC.keys[c.test.replace(/^css\//, '')]} ${c.platform}" PASS (${RC.lineEndsWith})`));
}

console.log(`\ngained ${score.gained.length}: ${['web', 'ios', 'android'].map((p) => `${p} ${score.gained.filter((c) => c.platform === p).length}`).join('  ')}` +
  (stillDegenerate.length ? `  (of which DEGENERATE-BY-CONSTRUCTION, not fixes: ${stillDegenerate.length})` : ''));
console.log(bad ? `\nADJUDICATION: ${bad} rule(s) broken — do not ship` : '\nADJUDICATION: R1–R5 hold');
process.exit(bad ? 1 : 0);
