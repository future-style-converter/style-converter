// tools/titan/score-gate.test.mjs — pins for the per-cell gate scorer.
//
// The scorer is the number of record for every corpus snapshot, so each rule
// it encodes is pinned on a synthetic pair of run dirs where the expected
// per-cell answer is known by construction:
//   - the idiom (numeric ssim, !scoreExcluded, wptPass === true) — a cell
//     that is scoreExcluded on one side is UNMEASURED there, never "lost";
//   - gained/lost are per-cell, so a section whose totals are unchanged can
//     still carry one gain and one loss (the A1#1 failure mode);
//   - a same-verdict move above the threshold is a mover, below it is not;
//   - composed-capture columns are counted against tests.list (component
//     PNGs with a __N suffix are not columns).
// Each pin was proven able to fail by mutating the scorer (see the
// "mutation" notes) before it was committed.
import { test } from 'node:test';
import assert from 'node:assert/strict';
import fs from 'node:fs';
import os from 'node:os';
import path from 'node:path';
import { loadRun, diffRuns, isScored, isPass, watchCells } from './score-gate.mjs';

// Build a run dir with one section from a compact cell table:
//   cells: { [test]: { web: [ssim, pass, excluded?], ios: ..., android: ... } }
function makeRun(root, name, cells, { tests = Object.keys(cells), pngs = null } = {}) {
  const sdir = path.join(root, name, 'sections', 'css-x');
  fs.mkdirSync(sdir, { recursive: true });
  fs.writeFileSync(path.join(sdir, 'tests.list'), tests.map(t => `css/css-x/${t}.html`).join('\n') + '\n');
  const results = {};
  for (const [test, per] of Object.entries(cells)) {
    const diffs = {};
    for (const [platform, spec] of Object.entries(per)) {
      const [ssim, pass, excluded] = spec;
      const x = { ssim, wptPass: pass };
      if (excluded) x.scoreExcluded = true;
      diffs[`${platform}-ref`] = x;
    }
    results[`css/css-x/${test}.html`] = { browserRef: { diffs } };
  }
  fs.writeFileSync(path.join(sdir, 'manifest.json'), JSON.stringify({ wpt: { results } }));
  const counts = pngs || { screenshots: tests.length, 'android-screenshots': tests.length, 'ios-screenshots': tests.length };
  for (const [dir, n] of Object.entries(counts)) {
    fs.mkdirSync(path.join(sdir, dir), { recursive: true });
    for (let i = 0; i < n; i++) fs.writeFileSync(path.join(sdir, dir, `wpt__css-x__t${i}.png`), '');
    // A component PNG must not count as a column entry.
    fs.writeFileSync(path.join(sdir, dir, `wpt__css-x__t0__1.png`), '');
  }
  return path.join(root, name);
}

const tmp = fs.mkdtempSync(path.join(os.tmpdir(), 'score-gate-'));

test('the idiom: numeric ssim and not excluded is scored; pass is wptPass === true only', () => {
  assert.equal(isScored({ ssim: 0.5 }), true);
  assert.equal(isScored({ ssim: '0.5' }), false, 'a string ssim is not a score');
  assert.equal(isScored({ ssim: 0.99, scoreExcluded: true }), false, 'excluded cells are unmeasured');
  assert.equal(isScored(undefined), false);
  assert.equal(isPass({ wptPass: true }), true);
  assert.equal(isPass({ wptPass: 'true' }), false, 'only the boolean passes'); // mutation: `== true` would accept it
  assert.equal(isPass({ wptPass: 1 }), false);
});

test('gained and lost are per-cell facts even when the totals do not move (A1#1)', () => {
  const prev = makeRun(tmp, 'p1', {
    a: { web: [0.99, true], ios: [0.90, false], android: [0.97, true] },
    b: { web: [0.99, true], ios: [0.97, true], android: [0.90, false] },
  });
  const cur = makeRun(tmp, 'c1', {
    a: { web: [0.99, true], ios: [0.97, true], android: [0.97, true] },   // ios gained
    b: { web: [0.99, true], ios: [0.90, false], android: [0.90, false] }, // ios lost
  });
  const d = diffRuns(loadRun(prev), loadRun(cur));
  assert.equal(d.totals.prev.ios.passing, 1); assert.equal(d.totals.cur.ios.passing, 1); // totals identical…
  assert.deepEqual(d.gained.map(r => `${r.test}|${r.platform}`), ['css/css-x/a.html|ios']); // …one gain
  assert.deepEqual(d.lost.map(r => `${r.test}|${r.platform}`), ['css/css-x/b.html|ios']);   // …one loss
  assert.equal(d.lost[0].prev, 0.97); assert.equal(d.lost[0].cur, 0.90);
});

test('a cell excluded (or absent) on one side is UNMEASURED there, never lost or gained', () => {
  const prev = makeRun(tmp, 'p2', { a: { web: [0.99, true], ios: [0.99, true], android: [0.99, true] } });
  const cur = makeRun(tmp, 'c2', { a: { web: [0.99, true], ios: [0.99, true, /*excluded*/ true] } }); // android absent
  const d = diffRuns(loadRun(prev), loadRun(cur));
  assert.equal(d.lost.length, 0); // mutation: dropping the `!b` early-continue makes this a loss
  assert.deepEqual(d.unmeasuredNow.map(r => r.platform).sort(), ['android', 'ios']);
  assert.equal(d.totals.cur.ios.measured, 0, 'an excluded cell is not measured');
  assert.equal(d.totals.cur.web.measured, 1);
});

test('newly measured cells are listed apart from gains', () => {
  const prev = makeRun(tmp, 'p3', { a: { web: [0.99, true] } });
  const cur = makeRun(tmp, 'c3', { a: { web: [0.99, true], ios: [0.99, true] } });
  const d = diffRuns(loadRun(prev), loadRun(cur));
  assert.equal(d.gained.length, 0);
  assert.deepEqual(d.newlyMeasured.map(r => r.platform), ['ios']);
});

test('movers: same verdict, |Δssim| at or above the threshold only', () => {
  const prev = makeRun(tmp, 'p4', { a: { web: [0.990, true], ios: [0.980, true], android: [0.800, false] } });
  const cur = makeRun(tmp, 'c4', { a: { web: [0.995, true], ios: [0.960, true], android: [0.850, false] } });
  const d = diffRuns(loadRun(prev), loadRun(cur), { moverThreshold: 0.01 });
  assert.deepEqual(d.movers.map(r => `${r.platform}:${r.delta}`).sort(), ['android:0.05', 'ios:-0.02']); // web +0.005 is below
  assert.equal(d.gained.length + d.lost.length, 0);
});

test('column counts: composed PNGs vs tests.list, component PNGs excluded, shorts reported', () => {
  const prev = makeRun(tmp, 'p5', { a: { web: [0.99, true] }, b: { web: [0.99, true] } });
  const cur = makeRun(tmp, 'c5', { a: { web: [0.99, true] }, b: { web: [0.99, true] } },
    { pngs: { screenshots: 2, 'android-screenshots': 1, 'ios-screenshots': 2 } });
  const run = loadRun(cur);
  assert.deepEqual(run.sections['css-x'].columns, { web: 2, android: 1, ios: 2 }); // mutation: counting __1.png gives 3/2/3
  assert.equal(run.sections['css-x'].testsListed, 2);
  const d = diffRuns(loadRun(prev), run);
  assert.deepEqual(d.columnShorts, [{ sec: 'css-x', platform: 'android', have: 1, want: 2 }]);
});

test('a section missing from the current run is named, and its cells are unmeasured-now', () => {
  const prev = makeRun(tmp, 'p6', { a: { web: [0.99, true] } });
  const curRoot = path.join(tmp, 'c6'); fs.mkdirSync(path.join(curRoot, 'sections'), { recursive: true });
  const d = diffRuns(loadRun(prev), loadRun(curRoot));
  assert.deepEqual(d.missingSections, ['css-x']);
  assert.equal(d.unmeasuredNow.length, 1);
  assert.equal(d.lost.length, 0);
});

test('watch cells resolve by substring and optional platform', () => {
  const prev = makeRun(tmp, 'p7', { 'angle-units-001': { web: [0.9990, false], ios: [0.9974, false] } });
  const cur = makeRun(tmp, 'c7', { 'angle-units-001': { web: [0.9990, true], ios: [0.9974, true] } });
  const rows = watchCells(loadRun(prev), loadRun(cur), ['angle-units-001 web', '# a comment', 'angle-units']);
  assert.equal(rows.filter(r => r.watch === 'angle-units-001 web').length, 1);
  assert.equal(rows.filter(r => r.watch === 'angle-units').length, 2);
  assert.equal(rows[0].prev, 'f 0.999'); assert.equal(rows[0].cur, 'P 0.999');
});
