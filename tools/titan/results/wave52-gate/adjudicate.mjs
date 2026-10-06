#!/usr/bin/env node
// tools/titan/results/wave52-gate/adjudicate.mjs
//
// wave-52 closing gate, checked against what PLAN.md §10 PRE-REGISTERED before
// the gate ran. Three score-gate JSONs go in:
//   record   wave51-fix   → wave52-final   (the score of record)
//   calib    wave51-fix   → wave52-calib   (instrument-only: identical captures, re-frozen refs)
//   render   wave52-calib → wave52-final   (what the render lanes did, instrument held fixed)
// and the rules are:
//   R1  every LOST cell of the record is one of the calibration's lost cells
//       (instrument-only, capture-identical). A lost cell outside that list is a
//       REAL regression and stops the ship.
//   R2  render: lost = 0.
//   R3  UNMEASURED NOW of the record = the calibration's absence-only cells plus
//       the two cells L6 adjudicated out (css3-counter-styles-008 ios/android).
//   R4  render: unmeasured-now = exactly those two.
//   R5  no missing section, no short column.
// Exit 0 when all hold, 1 otherwise. The report says which rule broke and names
// every cell — a count alone is not an adjudication.
//
// Usage: node adjudicate.mjs <record.json> <calib.json> <render.json>
import { readFileSync } from 'node:fs';

const [recordPath, calibPath, renderPath] = process.argv.slice(2);
if (!renderPath) { console.error('usage: adjudicate.mjs <record.json> <calib.json> <render.json>'); process.exit(2); }
const load = (p) => JSON.parse(readFileSync(p, 'utf8'));
const record = load(recordPath), calib = load(calibPath), render = load(renderPath);

// One key per cell: the test path and the platform column.
const key = (c) => `${c.test} [${c.platform}]`;
const set = (cells) => new Set(cells.map(key));
const fmt = (c) => `    ${key(c)}  ${c.prev ?? '—'} → ${c.cur ?? '—'}`;
// L6 adjudication A: the armenian -008 natives leave the denominator by decision.
const L6_OUT = (c) => /css3-counter-styles-008\.html$/.test(c.test) && c.platform !== 'web';

let bad = 0;
const rule = (id, ok, text) => { console.log(`${ok ? 'ok  ' : 'FAIL'} ${id}  ${text}`); if (!ok) bad++; };
const totals = (t) => ['web', 'ios', 'android'].map((p) => `${p} ${t[p].passing}/${t[p].measured}`).join('  ');

console.log(`record  ${record.prev} → ${record.cur}`);
console.log(`  prev  ${totals(record.totals.prev)}`);
console.log(`  cur   ${totals(record.totals.cur)}`);
console.log(`  gained ${record.gained.length}  lost ${record.lost.length}  newly-measured ${record.newlyMeasured.length}  unmeasured-now ${record.unmeasuredNow.length}`);
console.log(`render  ${render.prev} → ${render.cur}`);
console.log(`  gained ${render.gained.length}  lost ${render.lost.length}  newly-measured ${render.newlyMeasured.length}  unmeasured-now ${render.unmeasuredNow.length}\n`);

// R1 — lost cells of the record, split by whether the calibration already lost them.
const calibLost = set(calib.lost);
const realLost = record.lost.filter((c) => !calibLost.has(key(c)));
rule('R1', realLost.length === 0, `record lost ${record.lost.length}: ${record.lost.length - realLost.length} instrument-only (pre-registered), ${realLost.length} outside the list`);
realLost.forEach((c) => console.log(fmt(c)));
// Instrument-lost cells a render lane paid back are worth a line (they were not promised).
const recordLost = set(record.lost);
const paidBack = calib.lost.filter((c) => !recordLost.has(key(c)));
if (paidBack.length) { console.log(`     instrument-lost cells NOT lost in the record (${paidBack.length}):`); paidBack.forEach((c) => console.log(fmt(c))); }

// R2 — with the instrument held fixed, the render lanes must lose nothing.
rule('R2', render.lost.length === 0, `render lost ${render.lost.length}`);
render.lost.forEach((c) => console.log(fmt(c)));

// R3 — the denominator: who left it, and was each departure decided beforehand.
const calibOut = set(calib.unmeasuredNow);
const surpriseOut = record.unmeasuredNow.filter((c) => !calibOut.has(key(c)) && !L6_OUT(c));
const recordOut = set(record.unmeasuredNow);
const missingOut = calib.unmeasuredNow.filter((c) => !recordOut.has(key(c)));
rule('R3', surpriseOut.length === 0 && missingOut.length === 0,
  `record unmeasured-now ${record.unmeasuredNow.length}: expected ${calib.unmeasuredNow.length} absence-only + 2 (L6); unexpected ${surpriseOut.length}, expected-but-still-measured ${missingOut.length}`);
surpriseOut.forEach((c) => console.log(fmt(c)));
missingOut.forEach((c) => console.log(`    still measured: ${key(c)}`));

// R4 — the render comparison sees only L6's two departures.
const renderSurprise = render.unmeasuredNow.filter((c) => !L6_OUT(c));
rule('R4', renderSurprise.length === 0 && render.unmeasuredNow.length === 2, `render unmeasured-now ${render.unmeasuredNow.length} (expected the 2 L6 cells; other: ${renderSurprise.length})`);
renderSurprise.forEach((c) => console.log(fmt(c)));

// R5 — a gate with a hole in it is not a gate.
const holes = record.missingSections.length + record.columnShorts.length;
rule('R5', holes === 0, `missing sections ${record.missingSections.length}, short columns ${record.columnShorts.length}`);

// Attribution: the record's gains are the instrument's plus the render lanes'.
const calibGained = set(calib.gained);
const instrumentGained = record.gained.filter((c) => calibGained.has(key(c)));
const renderGained = record.gained.filter((c) => !calibGained.has(key(c)));
const droppedGain = calib.gained.filter((c) => !set(record.gained).has(key(c)));
console.log(`\ngained ${record.gained.length} = ${instrumentGained.length} instrument-only + ${renderGained.length} render`);
if (droppedGain.length) { console.log(`  instrument gains NOT in the record (${droppedGain.length}) — a render lane moved them:`); droppedGain.forEach((c) => console.log(fmt(c))); }
const per = (cells) => ['web', 'ios', 'android'].map((p) => `${p} ${cells.filter((c) => c.platform === p).length}`).join('  ');
console.log(`  render gains by platform: ${per(renderGained)}`);
console.log(`  render newly measured: ${render.newlyMeasured.length}`);
console.log(bad ? `\nADJUDICATION: ${bad} rule(s) broken — do not ship` : '\nADJUDICATION: all five rules hold');
process.exit(bad ? 1 : 0);
