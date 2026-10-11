#!/usr/bin/env node
// tools/titan/results/wave55-plan/probe-readout.mjs — PLAN.md §6 revert rules 1, 2 and 3 at a device probe, as code (wave 55).
//
// The wave-54 reader (tools/titan/results/wave54-plan/probe-readout.mjs: base-first ORDER guard, --movers 0 guard, rules
// 1 / 2 / 3 and the score side of rule 5 over every cell of the probed sections) with five wave-55 changes:
//   1. --sections a,b (briefs-audit C15): the read is restricted to these sections (default: the run's listed sections —
//      stage1Probe.sections with --stage1, probeRun.sections without). A row or a cell of another section is not read, so
//      a narrowed read never reports another section's gating cell UNMEASURED; a listed section absent from the record is
//      MISSING (exit 3).
//   2. --stage1 reads wave55-pre, ONE run for every lane's §8 device probe (C1): every lane decides (wave 54 decided L1 / L2
//      only at stage 1); --lanes still narrows.
//   3. direction "down-preregistered" (D5): rule 2 fires at Δ <= predictedDelta − 0.002 instead of Δ <= −0.002.
//   4. The non-carrier cells are read in four classes: a MUST-NOT-MOVE line of a lane or of mustNotMoveComplement (C1) —
//      any move is a leak signal (same-host noise is 0); an L6 MAY-MOVE android stem (unionMayMoveCaptures) — a move within
//      0.002 is allowed and printed, beyond it (or a flip) it is a leak signal that names L6 U2 for a LOOK (its brief:
//      revert U2 unless the picture shows a band now under its child); the RING-FENCED test — printed plainly, never fired.
//   5. An f→f row's `floorNA` is printed (no floor is ever read as 0, C11).
// Exit: 0 no rule fired; 1 a rule fired (units named with their revertOrder); 2 usage (round 2, S5: a flag without its value
//       — it crashed with a TypeError, exit 1, which reads "a rule fired" — or an empty --sections entry), wrong order, not a
//       --movers 0 record, or a --lanes entry that matches no lane (round 1, S3); 3 nothing fired but a listed section is missing or a
//       carrier / gating cell went unmeasured (re-run it).
//
// Usage: node tools/titan/results/wave55-plan/probe-readout.mjs <score.json> [--stage1] [--sections a,b] [--lanes L1,L2]
//          [--base wave55-open] [--exp tools/titan/results/wave55-plan/expectations.json]
//   <score.json> = node tools/titan/score-gate.mjs wave55-open <probe> --watch …/watchlist.txt --movers 0 --json <score.json>
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const USAGE = 'usage: probe-readout.mjs <score.json> [--stage1] [--sections a,b] [--lanes L1,L2] [--base wave55-open] [--exp expectations.json]';
// Round 2 (S5): a flag given without its value is a usage error (exit 2), never undefined read as "no option".
const opt = (name, dflt) => {
  const i = args.indexOf(name); if (i < 0) return dflt;
  const v = args[i + 1];
  if (v === undefined || v.startsWith('--')) { console.error(`${USAGE}\n  REFUSED — ${name} given without a value`); process.exit(2); }
  args.splice(i, 2); return v;
};
const flag = (name) => { const i = args.indexOf(name); if (i < 0) return false; args.splice(i, 1); return true; };
const expPath = opt('--exp', path.join(HERE, 'expectations.json'));
const base = opt('--base', 'wave55-open');
const stage1 = flag('--stage1');
const lanesOpt = opt('--lanes', null);
const sectionsOpt = opt('--sections', null);
const [scorePath] = args;
if (!scorePath || args.length !== 1) {
  console.error(USAGE);
  process.exit(2);
}
if (sectionsOpt !== null && sectionsOpt.split(',').some((x) => !x.trim())) {    // round 2 (S5): '' or 'a,' would widen the read
  console.error(`${USAGE}\n  REFUSED — an empty --sections entry (${JSON.stringify(sectionsOpt)}): it would silently read every section`);
  process.exit(2);
}
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const score = JSON.parse(readFileSync(scorePath, 'utf8'));
// Plan-skeptic round 1 (instrument-teeth S3): a --lanes entry that is a prefix of NO lane would read every row as "recorded
// for another lane" and exit 0 — a vacuous pass. Refuse it.
const badLanes = (lanesOpt === null ? [] : lanesOpt.split(',')).filter((x) => !x || !Object.keys(EXP.lanes).some((l) => l.startsWith(x)));
if (badLanes.length) {
  console.error(`UNKNOWN --lanes ${badLanes.map((x) => JSON.stringify(x)).join(', ')}: a prefix of no lane in ${Object.keys(EXP.lanes).join(', ')} — nothing would decide. Refusing.`);
  process.exit(2);
}

// ── the order guard: Δ = cur − prev must mean probe − base ──
if (score.prev !== base) {
  console.error(`ORDER: this record is score-gate.mjs ${score.prev} → ${score.cur}; the probe read needs ${base} FIRST ` +
    `(score-gate.mjs ${base} <probe> --movers 0 --json …), so that Δ = probe − ${base}. Refusing.`);
  process.exit(2);
}
// ── the --movers 0 guard: every cell scored on both sides must be in gained / lost / movers ──
const sum = (t) => ['web', 'ios', 'android'].reduce((n, p) => n + t[p].measured, 0);
const listed = score.gained.length + score.lost.length + score.movers.length + score.unmeasuredNow.length;
if (listed !== sum(score.totals.prev)) {
  console.error(`NOT A --movers 0 RECORD: ${listed} cells listed (gained + lost + movers + unmeasured-now) vs ${sum(score.totals.prev)} ` +
    `scored on ${score.prev}; a cell absent from every list would read as "unchanged". Re-run score-gate.mjs with --movers 0.`);
  process.exit(2);
}

const key = (c) => `${c.test.replace(/^css\//, '')} ${c.platform}`;
const byKey = new Map();
for (const list of ['gained', 'lost', 'movers', 'newlyMeasured', 'unmeasuredNow']) for (const c of score[list]) byKey.set(key(c), { ...c, list });
const sectionOf = (cell) => cell.split('/')[0];
const runSections = stage1 ? EXP.stage1Probe.sections : EXP.probeRun.sections;
const listedSections = sectionsOpt ? sectionsOpt.split(',') : runSections;
const strays = listedSections.filter((s) => !runSections.includes(s));
if (strays.length) console.log(`  NOTE --sections names ${strays.join(',')} outside this run's listed sections (${stage1 ? 'stage1Probe' : 'probeRun'}); read anyway`);
const missing = listedSections.filter((s) => score.missingSections.includes(s) || !(s in score.sections));
const read = new Set(listedSections.filter((s) => !missing.includes(s)));
const laneOn = (laneId) => !lanesOpt || lanesOpt.split(',').some((x) => laneId.startsWith(x));
const show = (c) => (c ? `${c.prevPass == null ? '—' : c.prevPass ? 'P' : 'f'} ${c.prev ?? '—'} → ${c.curPass == null ? '—' : c.curPass ? 'P' : 'f'} ${c.cur ?? '—'}` : 'not scored on either side');
const delta = (c) => (typeof c.cur === 'number' && typeof c.prev === 'number' ? +(c.cur - c.prev).toFixed(4) : null);
const stemOf = (cell) => 'wpt__' + cell.replace(/ (web|ios|android)$/, '').replace(/\.html$/, '').split('/').join('__');

console.log(`probe-readout  ${score.prev} → ${score.cur}   (Δ = ${score.cur} − ${score.prev}; a gain prints +, a fall −)`);
console.log(`  sections read (${read.size}${stage1 ? ', stage 1' : ''}${sectionsOpt ? ', --sections' : ''}): ${[...read].join(',')}`);
if (missing.length) console.log(`  MISSING listed sections (${missing.length}): ${missing.join(',')} — re-run them; nothing in them is decided`);
console.log(`  lanes that decide: ${lanesOpt || 'all'}`);

// Carrier cells → [lane, unit]. A unit reverted at a probe (probeDecisions) carries nothing any more.
const decisions = (EXP.probeDecisions && EXP.probeDecisions.reverted) || [];
const revertedUnits = new Set(decisions.map((d) => `${d.lane} ${d.unit}`));
const carriedBy = new Map();
for (const [laneId, lane] of Object.entries(EXP.lanes)) {
  for (const [unit, u] of Object.entries(lane.revertUnits || {})) {
    if (revertedUnits.has(`${laneId} ${unit}`)) continue;
    for (const [plat, stems] of Object.entries(u.captures || {})) {
      for (const st of stems) {
        const cell = `${st.split('__').slice(1).join('/')}.html ${plat}`;
        if (!carriedBy.has(cell)) carriedBy.set(cell, []);
        carriedBy.get(cell).push([laneId, unit]);
      }
    }
  }
}
const mayMove = new Map(Object.entries(EXP.unionMayMoveCaptures || {}).map(([p, stems]) => [p, new Set(stems)]));
const ring = EXP.ringFenced && typeof EXP.ringFenced === 'object' ? new Set(EXP.ringFenced.cells || []) : new Set();
const withdrawn = new Set(decisions.flatMap((d) => d.withdrawnPredictions || []));
const named = new Map();     // `${lane} ${unit}` -> [reasons]
const name = (laneId, units, why) => { for (const u of units) { const k = `${laneId} ${u}`; if (!named.has(k)) named.set(k, []); named.get(k).push(why); } };
const fired = { 1: 0, 2: 0, 3: 0, leak: 0 }; let unmeasured = 0, undirectedFalls = 0, recorded = 0;

// ── rule 1: a carrier lost at the probe (a lost NON-carrier is a leak signal, below) ──
console.log('\nrule 1 — lost carriers (P on the base, f on the probe):');
let lostCarriers = 0;
for (const c of score.lost) {
  const cell = key(c);
  const by = carriedBy.get(cell) || [];
  if (!read.has(c.sec) || !by.length) continue;
  lostCarriers++;
  const deciding = by.filter(([l]) => laneOn(l));
  if (!deciding.length) { recorded++; console.log(`  recorded  ${cell}  ${show(c)}  [carrier of ${by.map((x) => x.join(' ')).join(', ')}: not a deciding lane]`); continue; }
  fired[1]++; for (const [l, u] of deciding) name(l, [u], `rule 1 ${cell}`);
  console.log(`  FIRES     ${cell}  ${show(c)}  [carrier of ${deciding.map((x) => x.join(' ')).join(', ')}]`);
}
if (!lostCarriers) console.log('  none');

// ── rules 2 and 3: every prediction row of the read sections ──
console.log('\nrules 2 / 3 — prediction rows:');
for (const [laneId, lane] of Object.entries(EXP.lanes)) {
  for (const p of lane.predictions || []) {
    if (!/ (web|ios|android)$/.test(p.cell) || !read.has(sectionOf(p.cell))) continue;
    if (withdrawn.has(p.cell)) continue;   // held to must-not-move below
    const c = byKey.get(p.cell);
    const d = c ? delta(c) : null;
    const tag = `${p.confidence}${p.gating ? `, gating floor ${p.floor}` : ''}${p.floorNA ? ', floor n/a (stays f)' : ''}` +
      `${p.direction === 'undirected' ? ', undirected' : ''}${p.direction === 'down-preregistered' ? `, pre-registered Δ${p.predictedDelta}` : ''}`;
    const line = `${laneId}  ${p.cell}  ${show(c)}${d == null ? '' : ` Δ${d >= 0 ? '+' : ''}${d}`}  (predicted ${p.to}; ${tag})`;
    if (!laneOn(laneId)) { recorded++; console.log(`  recorded  ${line}`); continue; }
    let why = null;
    if (!c || c.cur == null) { unmeasured++; console.log(`  UNMEASURED ${line}`); if (p.gating && p.floor != null) { fired[3]++; why = 'rule 3 (no probe score)'; } }
    else if (p.gating && p.floor != null && (c.curPass !== /^P/.test(p.to) || c.cur < p.floor)) { fired[3]++; why = `rule 3 below floor ${p.floor}`; }
    // rule 2: the 0.002 band below 0 — or below the pre-registered fall (D5).
    const bar = p.direction === 'down-preregistered' ? +(p.predictedDelta - 0.002).toFixed(4) : -0.002;
    if (c && d != null && d <= bar) {
      if (p.direction === 'undirected') { undirectedFalls++; console.log(`  READ      ${line}  — undirected fall: look at it against the ref and name its cause (exempt from rule 2)`); }
      else { fired[2]++; why = (why ? `${why}; ` : '') + `rule 2 Δ${d} <= ${bar}`; }
    }
    if (why) { name(laneId, p.units, `${why} ${p.cell}`); console.log(`  FIRES     ${line}  — ${why}`); }
    else if (c && c.cur != null && (p.gating || (d != null && Math.abs(d) >= 0.002))) console.log(`  ok        ${line}`);
  }
}

// ── the non-carrier cells of the read sections (the score side of rule 5) ──
console.log('\nnon-carrier cells — must-not-move (any move = leak signal), L6 may-move (0.002 band), ring-fenced (report only):');
const mnmOf = new Map([...Object.entries(EXP.lanes).flatMap(([l, lane]) => (lane.mustNotMove || []).map((c) => [c, l])),
  ...((EXP.mustNotMoveComplement && EXP.mustNotMoveComplement.cells) || []).map((c) => [c, 'complement']),
  ...decisions.flatMap((d) => (d.mustNotMoveAfter || []).map((c) => [c, `probe-revert:${d.unit}`]))]);
let nonCarrierRead = 0, mnmRead = 0, mayRead = 0, mayWithin = 0;
for (const [cell, c] of byKey) {
  if (!read.has(c.sec) || carriedBy.has(cell)) continue;
  nonCarrierRead++;
  const plat = cell.split(' ').pop();
  const moved = c.list !== 'movers' || (delta(c) ?? 0) !== 0;
  if (ring.has(cell)) { if (moved) console.log(`  RING-FENCED ${cell}  ${show(c)} [${c.list}] — report plainly in the PR; never a target, never a revert`); continue; }
  if ((mayMove.get(plat) || new Set()).has(stemOf(cell))) {
    mayRead++;
    if (!moved) continue;
    const d = delta(c);
    if (c.list === 'movers' && d != null && Math.abs(d) <= 0.002) { mayWithin++; console.log(`  may-move  ${cell}  ${show(c)} Δ${d} — within L6's 0.002 band`); continue; }
    fired.leak++; name('L6-android-paint-order', ['U2'], `may-move beyond 0.002 ${cell}`);
    console.log(`  MAY-MOVE  ${cell}  ${show(c)} [${c.list}] — beyond L6's 0.002 band: LOOK — a band now under its child → keep U2 and record it; anything else → revert L6 U2`);
    continue;
  }
  if (mnmOf.has(cell)) mnmRead++;
  if (moved) {
    fired.leak++;
    console.log(`  LEAK      ${cell}  ${show(c)} [${c.list}]${mnmOf.has(cell) ? ` must-not-move of ${mnmOf.get(cell)}` : ' (scored on one side only)'} — bisect by commit (rule 5)`);
  }
}
console.log(`  ${nonCarrierRead} non-carrier cells read (${mnmRead} must-not-move lines, ${mayRead} L6 may-move of which ${mayWithin} moved within the band); ${fired.leak} leak signal(s)`);

// ── the units, in each lane's revertOrder ──
console.log(`\nsummary: rule 1 fired ${fired[1]} · rule 2 fired ${fired[2]} · rule 3 fired ${fired[3]} · leak signals ${fired.leak} · ` +
  `undirected falls read ${undirectedFalls} · unmeasured ${unmeasured} · recorded for another lane ${recorded}`);
if (named.size) {
  console.log('units named (revert rule 6: each unit with its revertOrder, latest first; re-probe between steps):');
  for (const [k, whys] of named) {
    const [laneId, unit] = k.split(' ');
    const order = EXP.lanes[laneId].revertUnits[unit]?.revertOrder || [unit];
    console.log(`  ${laneId} ${unit}  (revertOrder ${order.join(' → ')}): ${whys.length} reason(s) — ${whys.slice(0, 4).join('; ')}${whys.length > 4 ? '; …' : ''}`);
  }
}
const anyFired = fired[1] + fired[2] + fired[3] + fired.leak;
console.log(anyFired ? '\nPROBE: revert rules fired — revert the named units (leaks: bisect; may-move: look first)' : missing.length || unmeasured ? '\nPROBE: no rule fired, but the read is INCOMPLETE — re-run the missing sections' : '\nPROBE: rules 1, 2, 3 and the must-not-move read hold');
process.exit(anyFired ? 1 : missing.length || unmeasured ? 3 : 0);
