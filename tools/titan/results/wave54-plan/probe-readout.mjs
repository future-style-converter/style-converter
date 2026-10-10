#!/usr/bin/env node
// tools/titan/results/wave54-plan/probe-readout.mjs — PLAN.md §6 revert rules 1, 2 and 3 at a device probe, as code.
//
// Why (fix r2, plan-skeptic round 2, R2-M2): the plan pre-registered `ab-diff.mjs wave54-probe wave54-open` as the
// stage-2 read-out. ab-diff is `<excludeRun> <includeRun>` and prints Δ = include − exclude, so that order printed
// Δ = open − probe and labelled every probe GAIN "flip P→f" (wave 53's own pair: background-attachment-margin-root-002
// web printed `P 1 → f 0.339 Δ-0.661`). Rules 1 and 2 read literally off that output revert the commits that fixed
// cells and keep the ones that broke them. And rule 3 (floors) had no reader at all: a gating cell that did not move
// is absent from ab-diff's list (the closing gate's M1 hole, again at the probe). This reader closes both:
//   - it reads a `score-gate.mjs <base> <probe> --movers 0 --json` record, so the sign is fixed by the scorer's own
//     prev → cur (Δ = probe − base), and it REFUSES a record whose `prev` is not the base (exit 2);
//   - with --movers 0 every cell measured on both sides is in exactly one of gained / lost / movers, so a gating cell
//     that did not move is READ (and fails its floor), never skipped; a record written with a mover threshold is
//     refused (exit 2) because it would read an unlisted cell as "unchanged".
// Rules (expectations.json `revertRule`, restricted to the sections this probe ran and, with --lanes, to those lanes):
//   1  lost: a cell P on the base and f on the probe. A carrier names the unit(s) that carry it; a non-carrier is a
//      leak (rule 5: bisect, never guessed).
//   2  moved down: Δ <= −0.002 on an "up-or-stay" prediction row names its units; an "undirected" row's fall is READ
//      (looked at against the ref and named in the read-out), never a revert by itself.
//   3  below floor: a gating prediction whose probe cell is not the predicted verdict or is below its floor.
//   +  leak signals (the score side of rule 5): a cell of the read sections that no kept unit carries and that flipped,
//      went (un)measured or moved AT ALL (same-host noise is 0: BACKLOG "Wave 53 lessons", 0 movers at |Δ| >= 0.00005 on
//      the opening gate) — must-not-move lines are labelled as such. Bisect; never guessed. control-check.mjs remains
//      the pixel-level reader of rule 5 (it also sees a moved capture whose score did not change).
// Withdrawn predictions (expectations.json probeDecisions) are held to must-not-move, as adjudicate.mjs does.
// The read-out is per cell, then the units named, each with its lane's revertOrder (rule 6: latest first).
//
// Usage: node tools/titan/results/wave54-plan/probe-readout.mjs <score.json> [--stage1] [--lanes L1,L2]
//          [--base wave54-open] [--exp tools/titan/results/wave54-plan/expectations.json]
//   <score.json> = node tools/titan/score-gate.mjs wave54-open <probe> --watch …/watchlist.txt --movers 0 --json <score.json>
//   --stage1   read the stage-1 sections (expectations.json stage1Probe) and decide lanes L1, L2 only (others: recorded)
//   --lanes    the lanes whose rows may fire (default: every lane; with --stage1, L1 and L2)
// Exit: 0 no rule fired; 1 a rule fired (units named); 2 usage, wrong order, or not a --movers 0 record;
//       3 nothing fired but a probed section is missing or a carrier / prediction cell went unmeasured (re-run it).
import { readFileSync } from 'node:fs';
import path from 'node:path';
import { fileURLToPath } from 'node:url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const args = process.argv.slice(2);
const opt = (name, dflt) => { const i = args.indexOf(name); return i < 0 ? dflt : args.splice(i, 2)[1]; };
const flag = (name) => { const i = args.indexOf(name); if (i < 0) return false; args.splice(i, 1); return true; };
const expPath = opt('--exp', path.join(HERE, 'expectations.json'));
const base = opt('--base', 'wave54-open');
const stage1 = flag('--stage1');
const lanesOpt = opt('--lanes', stage1 ? 'L1,L2' : null);
const [scorePath] = args;
if (!scorePath || args.length !== 1) {
  console.error('usage: probe-readout.mjs <score.json> [--stage1] [--lanes L1,L2] [--base wave54-open] [--exp expectations.json]');
  process.exit(2);
}
const EXP = JSON.parse(readFileSync(expPath, 'utf8'));
const score = JSON.parse(readFileSync(scorePath, 'utf8'));

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
const listedSections = stage1 ? EXP.stage1Probe.sections : EXP.probeRun.sections;
const missing = listedSections.filter((s) => score.missingSections.includes(s) || !(s in score.sections));
const read = new Set(listedSections.filter((s) => !missing.includes(s)));
const laneOn = (laneId) => !lanesOpt || lanesOpt.split(',').some((x) => laneId.startsWith(x));
const show = (c) => (c ? `${c.prevPass == null ? '—' : c.prevPass ? 'P' : 'f'} ${c.prev ?? '—'} → ${c.curPass == null ? '—' : c.curPass ? 'P' : 'f'} ${c.cur ?? '—'}` : 'not scored on either side');
const delta = (c) => (typeof c.cur === 'number' && typeof c.prev === 'number' ? +(c.cur - c.prev).toFixed(4) : null);

console.log(`probe-readout  ${score.prev} → ${score.cur}   (Δ = ${score.cur} − ${score.prev}; a gain prints +, a fall −)`);
console.log(`  sections read (${read.size}${stage1 ? ', stage 1' : ''}): ${[...read].join(',')}`);
if (missing.length) console.log(`  MISSING probed sections (${missing.length}): ${missing.join(',')} — re-run them; nothing in them is decided`);
console.log(`  lanes that decide: ${lanesOpt || 'all'}`);

// Carrier cells → [lane, unit] (lanes.*.revertUnits.*.captures: '<platform>' -> stems 'wpt__<sec>__<a>__<b>'). A unit
// reverted at a probe (probeDecisions) carries nothing any more: its cells must be back at the base.
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
  if (!deciding.length) { recorded++; console.log(`  recorded  ${cell}  ${show(c)}  [carrier of ${by.map((x) => x.join(' ')).join(', ')}: decided at stage 2]`); continue; }
  fired[1]++; for (const [l, u] of deciding) name(l, [u], `rule 1 ${cell}`);
  console.log(`  FIRES     ${cell}  ${show(c)}  [carrier of ${deciding.map((x) => x.join(' ')).join(', ')}]`);
}
if (!lostCarriers) console.log('  none');

// ── rules 2 and 3: every prediction row of the probed sections ──
console.log('\nrules 2 / 3 — prediction rows:');
for (const [laneId, lane] of Object.entries(EXP.lanes)) {
  for (const p of lane.predictions || []) {
    if (!/ (web|ios|android)$/.test(p.cell) || !read.has(sectionOf(p.cell))) continue;
    if (withdrawn.has(p.cell)) continue;   // held to must-not-move below
    const c = byKey.get(p.cell);
    const d = c ? delta(c) : null;
    const tag = `${p.confidence}${p.gating ? `, gating floor ${p.floor}` : ''}${p.direction === 'undirected' ? ', undirected' : ''}`;
    const line = `${laneId}  ${p.cell}  ${show(c)}${d == null ? '' : ` Δ${d >= 0 ? '+' : ''}${d}`}  (predicted ${p.to}; ${tag})`;
    if (!laneOn(laneId)) { recorded++; console.log(`  recorded  ${line}`); continue; }
    let why = null;
    if (!c || c.cur == null) { unmeasured++; console.log(`  UNMEASURED ${line}`); if (p.gating && p.floor != null) { fired[3]++; why = 'rule 3 (no probe score)'; } }
    else if (p.gating && p.floor != null && (c.curPass !== /^P/.test(p.to) || c.cur < p.floor)) { fired[3]++; why = `rule 3 below floor ${p.floor}`; }
    if (c && d != null && d <= -0.002) {
      if (p.direction === 'undirected') { undirectedFalls++; console.log(`  READ      ${line}  — undirected fall: look at it against the ref and name its cause (exempt from rule 2)`); }
      else { fired[2]++; why = (why ? `${why}; ` : '') + `rule 2 Δ${d}`; }
    }
    if (why) { name(laneId, p.units, `${why} ${p.cell}`); console.log(`  FIRES     ${line}  — ${why}`); }
    else if (c && c.cur != null && (p.gating || (d != null && Math.abs(d) >= 0.002))) console.log(`  ok        ${line}`);
  }
}

// ── leak signals (the score side of rule 5): every non-carrier cell of the read sections ──
console.log('\nleak signals (score side of rule 5) — non-carrier cells that moved at all:');
const mnmOf = new Map([...Object.entries(EXP.lanes).flatMap(([l, lane]) => (lane.mustNotMove || []).map((c) => [c, l])),
  ...decisions.flatMap((d) => (d.mustNotMoveAfter || []).map((c) => [c, `probe-revert:${d.unit}`]))]);
let nonCarrierRead = 0, mnmRead = 0;
for (const [cell, c] of byKey) {
  if (!read.has(c.sec) || carriedBy.has(cell)) continue;
  nonCarrierRead++; if (mnmOf.has(cell)) mnmRead++;
  const moved = c.list !== 'movers' || (delta(c) ?? 0) !== 0;
  if (moved) {
    fired.leak++;
    console.log(`  LEAK      ${cell}  ${show(c)} [${c.list}]${mnmOf.has(cell) ? ` must-not-move of ${mnmOf.get(cell)}` : ''} — bisect by commit (rule 5)`);
  }
}
console.log(`  ${nonCarrierRead} non-carrier cells read (${mnmRead} of them must-not-move lines); ${fired.leak} leak signal(s)`);

// ── the units, in each lane's revertOrder ──
console.log(`\nsummary: rule 1 fired ${fired[1]} · rule 2 fired ${fired[2]} · rule 3 fired ${fired[3]} · leak signals ${fired.leak} · ` +
  `undirected falls read ${undirectedFalls} · unmeasured ${unmeasured} · recorded for another stage ${recorded}`);
if (named.size) {
  console.log('units named (revert rule 6: each unit with its revertOrder, latest first; re-probe between steps):');
  for (const [k, whys] of named) {
    const [laneId, unit] = k.split(' ');
    const order = EXP.lanes[laneId].revertUnits[unit]?.revertOrder || [unit];
    console.log(`  ${laneId} ${unit}  (revertOrder ${order.join(' → ')}): ${whys.length} reason(s) — ${whys.slice(0, 4).join('; ')}${whys.length > 4 ? '; …' : ''}`);
  }
}
const anyFired = fired[1] + fired[2] + fired[3] + fired.leak;
console.log(anyFired ? '\nPROBE: revert rules fired — revert the named units (leaks: bisect)' : missing.length || unmeasured ? '\nPROBE: no rule fired, but the read is INCOMPLETE — re-run the missing sections' : '\nPROBE: rules 1, 2, 3 and the must-not-move read hold');
process.exit(anyFired ? 1 : missing.length || unmeasured ? 3 : 0);
