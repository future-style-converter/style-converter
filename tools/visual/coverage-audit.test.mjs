//
// coverage-audit.test.mjs
//
// Locks the two-signal contract of coverage-audit.mjs: the historical
// REGISTERED (string-presence) count AND the added REAL (dedicated-applier-
// file) count must both be emitted, and REAL must behave as an honest lower
// bound on REGISTERED. We drive the script as a subprocess in `--json` mode
// (the script runs top-level side effects + process.exit on import, so a
// subprocess is the clean seam) and assert on its machine-readable output.
//
// Run: node --test tools/visual/coverage-audit.test.mjs
//

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { execFileSync } from 'node:child_process';
import { dirname, join, resolve } from 'node:path';
import { fileURLToPath } from 'node:url';

const __dirname = dirname(fileURLToPath(import.meta.url));
const SCRIPT = join(__dirname, 'coverage-audit.mjs');
const REPO = resolve(__dirname, '..', '..');

// Run coverage-audit.mjs --json once and parse it; reused by every test.
function runJson() {
  const stdout = execFileSync('node', [SCRIPT, '--json'], {
    cwd: REPO,
    encoding: 'utf8',
  });
  return JSON.parse(stdout);
}

test('emits both REGISTERED totals and REAL totals + applierFiles', () => {
  const j = runJson();
  // Registered (string-presence) block — the historical, backward-compatible
  // shape other consumers may read.
  assert.equal(typeof j.totals, 'object');
  assert.equal(j.totals.ir, 558, 'IR catalogue is 558 properties');
  for (const p of ['android', 'ios', 'web']) {
    assert.equal(typeof j.totals[p], 'number', `totals.${p} present`);
  }
  // Real (dedicated-applier-file) block — the honesty signal added here.
  assert.equal(typeof j.realTotals, 'object', 'realTotals present');
  assert.equal(j.realTotals.ir, 558);
  for (const p of ['android', 'ios', 'web']) {
    assert.equal(typeof j.realTotals[p], 'number', `realTotals.${p} present`);
  }
  // Raw dedicated-applier file count (incl. grouped appliers).
  assert.equal(typeof j.applierFiles, 'object', 'applierFiles present');
  for (const p of ['android', 'ios', 'web']) {
    assert.equal(typeof j.applierFiles[p], 'number', `applierFiles.${p} present`);
  }
});

test('every platform is fully REGISTERED (the 558/558 claim)', () => {
  const j = runJson();
  // The registration facade is complete on all three platforms — that is the
  // exact over-count the REAL signal exists to expose.
  for (const p of ['android', 'ios', 'web']) {
    assert.equal(j.totals[p], 558, `${p} registers all 558`);
  }
  assert.equal(j.passed, true, 'gate passes on the registered signal');
});

test('REAL is a strict, honest lower bound below REGISTERED', () => {
  const j = runJson();
  for (const p of ['android', 'ios', 'web']) {
    // Real can never exceed registered — a dedicated applier file for a
    // property that isn't even registered would be a bug.
    assert.ok(
      j.realTotals[p] <= j.totals[p],
      `${p}: real (${j.realTotals[p]}) <= registered (${j.totals[p]})`,
    );
    // And real must be strictly below the 558 facade — the whole point of the
    // honesty fix is that string-presence over-counts real rendering.
    assert.ok(
      j.realTotals[p] < 558,
      `${p}: real (${j.realTotals[p]}) is below the 558 facade`,
    );
    // Some real appliers exist on every platform.
    assert.ok(j.realTotals[p] > 0, `${p}: has at least one real applier`);
  }
});

test('applierFiles >= REAL (grouped appliers inflate the raw file count)', () => {
  const j = runJson();
  // The raw dedicated-applier file count includes grouped files whose basename
  // matches at most one IR name, so it is >= the per-property real total.
  for (const p of ['android', 'ios', 'web']) {
    assert.ok(
      j.applierFiles[p] >= j.realTotals[p],
      `${p}: applierFiles (${j.applierFiles[p]}) >= real (${j.realTotals[p]})`,
    );
  }
});

test('per-category rows carry both reg and real, and real ⊆ reg', () => {
  const j = runJson();
  assert.ok(Array.isArray(j.byCategory) && j.byCategory.length === 33);
  for (const r of j.byCategory) {
    for (const [reg, real] of [
      ['android', 'androidReal'],
      ['ios', 'iosReal'],
      ['web', 'webReal'],
    ]) {
      assert.equal(typeof r[reg].ok, 'number', `${r.category}.${reg}.ok`);
      assert.equal(typeof r[real].ok, 'number', `${r.category}.${real}.ok`);
      // Per category, real-covered properties are a subset of registered ones.
      assert.ok(
        r[real].ok <= r[reg].ok,
        `${r.category}: ${real} (${r[real].ok}) <= ${reg} (${r[reg].ok})`,
      );
      // Both share the same category total.
      assert.equal(r[real].total, r[reg].total);
    }
  }
});
