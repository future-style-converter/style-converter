#!/usr/bin/env node
// Gaps in tools/visual/cross-platform-gate.mjs that the main suite leaves
// open — the plumbing and keying around the gate rather than the gate's own
// pass/fail expression.
//
// cross-platform-gate.test.mjs pins `pairRegressed` thoroughly and
// `evaluateCrossPlatformGate`'s verdicts thoroughly, but it exercises the
// two through DIFFERENT doors: every threshold test calls `pairRegressed`
// directly, and every gate test uses the default thresholds. Nothing tests
// that the options a caller hands the gate reach the expression underneath.
// One of them does not — see the bug pin below.
//
// Also pinned here: `componentKey`, which has no direct test despite being
// the fix for a measured ledger-wide unbinding; and the ledger collision
// guard, which throws and is never provoked.
//
// Run: node --test tools/visual/cross-platform-gate-thresholds.test.mjs

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  evaluateCrossPlatformGate,
  pairRegressed,
  componentKey,
  formatRecord,
  DEFAULT_DELTA_E_THRESHOLD,
} from './cross-platform-gate.mjs';

const OPTS = { ssimThreshold: 0.95, pixelThreshold: 2, inputLabel: 'fixtures/visual-test.json' };

/** A row carrying one pair on iOS-Android; the other two pairs are absent. */
function row(name, pair) {
  return {
    name,
    platforms: { iOS: {}, Android: {}, web: {} },
    pairs: { 'iOS-Android': pair, 'iOS-web': null, 'Android-web': null },
  };
}

/**
 * A pair that breaches ONLY on colour: SSIM and Δpx are clean, ΔE95 is 20.
 * This is the shape of the rows ΔE was added for (035_Filter_Sepia,
 * 009_Backdrop_Saturate_OverStripes), so it is the shape that exposes
 * whether a ΔE threshold override is honoured.
 */
const colourOnlyBreach = {
  ssim: 0.99, pixelMismatchedPct: 0.1, labDeltaE: { p95: 20 }, divergence: 'color-drift',
};

// ── Threshold plumbing ──────────────────────────────────────────────────────

test('evaluateCrossPlatformGate honours a ΔE threshold override', () => {
  // FIXED 2026-08-28. evaluateCrossPlatformGate neither destructured nor
  // forwarded deltaEThreshold, so the cross-platform gate always applied
  // the 5.0 default while --delta-e-threshold appeared to work — the
  // baseline gate honoured it and this one did not, which is exactly the
  // drift the module header claims routing both through one pairRegressed
  // makes impossible. Found by an independent audit, not by the suite:
  // testing pairRegressed alone could never see it.
  const loose = { ...OPTS, deltaEThreshold: 30 };
  const r = evaluateCrossPlatformGate([row('A.png', colourOnlyBreach)], { expectations: [] }, loose);
  assert.equal(r.unexpected.length, 0, 'ΔE95 20 must pass under a threshold of 30');
});

test('the default ΔE threshold is what the gate applies when none is given', () => {
  // The half of the plumbing that DOES work, pinned so the bug fix above
  // cannot regress it: with no override, a ΔE95 just past the default must
  // fail and one just under must pass, measured through the gate rather
  // than through pairRegressed.
  const over = { ...colourOnlyBreach, labDeltaE: { p95: DEFAULT_DELTA_E_THRESHOLD + 0.01 } };
  const under = { ...colourOnlyBreach, labDeltaE: { p95: DEFAULT_DELTA_E_THRESHOLD } };
  assert.equal(evaluateCrossPlatformGate([row('A.png', over)], { expectations: [] }, OPTS).unexpected.length, 1);
  assert.equal(evaluateCrossPlatformGate([row('A.png', under)], { expectations: [] }, OPTS).unexpected.length, 0);
});

// ── componentKey: the fix for a measured ledger-wide unbinding ──────────────

test('componentKey strips only the capture index, so renumbering cannot unbind the ledger', () => {
  // Captures are `{NNN}_{Name}.png` where NNN is a POSITION. Adding or
  // suppressing any component renumbers everything after it. When the
  // backdrop child-suppression landed and composition-test went 38 → 32
  // captures, every raw-filename-keyed entry past the first suppression was
  // reported orphaned (warning) while the divergence it excused came back
  // unexpected (hard failure) — two wrong verdicts from one rename.
  assert.equal(componentKey('000_A.png'), 'A.png');
  assert.equal(componentKey('117_A.png'), 'A.png', 'the same component at a different position');
  assert.equal(componentKey('A.png'), 'A.png', 'ledger entries are written without an index');
  // Anchored and non-greedy: only the LEADING run of digits goes. A name
  // that itself begins with digits keeps them, so the entry and the row
  // still agree — a `/\d+_/g` here would strip both and silently merge
  // distinct components.
  assert.equal(componentKey('000_2_Col_Layout.png'), '2_Col_Layout.png');
  // The `.png` is deliberately kept: committed entries carry it, and the
  // row names the comparator produces carry it. Dropping it on one side
  // only would unbind all 28 entries at once.
  assert.match(componentKey('000_A.png'), /\.png$/);
});

test('a renumbered capture is still excused by its ledger entry', () => {
  // The end-to-end version of the above: the entry is written bare, the row
  // arrives at position 117, and the divergence must still be excused.
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'r', owner: 'o' }] };
  const bad = { ssim: 0.90, pixelMismatchedPct: 1.2, labDeltaE: { p95: 0 } };
  const r = evaluateCrossPlatformGate([row('117_A.png', bad)], ledger, OPTS);
  assert.equal(r.unexpected.length, 0, 'the position must not matter');
  assert.equal(r.expected.length, 1);
  assert.equal(r.stale.length, 0, 'and the entry must not also be reported orphaned');
});

// ── The collision guard, never provoked by the main suite ───────────────────

test('two entries that flatten to one key throw instead of one silently winning', () => {
  // Component names are unique per PARENT, not per document, so two
  // children called `layer` under different parents flatten to the same
  // key once the index is stripped. A plain Map.set would drop one entry
  // and the divergence it excused would return as an unexpected failure
  // with no hint why — a ledger silently smaller than its file.
  //
  // The committed ledger is checked for duplicates elsewhere; this pins
  // that the guard in the CODE still fires, which is what protects a ledger
  // nobody has re-checked.
  const colliding = { expectations: [
    { component: '000_layer.png', pair: 'iOS-web', reason: 'r', owner: 'o' },
    { component: '004_layer.png', pair: 'iOS-web', reason: 'r', owner: 'o' },
  ] };
  assert.throws(
    () => evaluateCrossPlatformGate([row('000_layer.png', colourOnlyBreach)], colliding, OPTS),
    /collide on "layer\.png" \/ iOS-web/,
    'the error must name the key and the pair, or it is unactionable',
  );
});

// ── Reporting the record type the main suite never formats ─────────────────

test('formatRecord renders an orphaned record without touching absent metrics', () => {
  // Orphans carry `observed: null` — there was no pair to measure. The
  // formatter takes an early branch for them; if that branch were removed,
  // the gate would throw on `observed.ssim` at exactly the moment it has a
  // finding to report, turning a bookkeeping warning into a crash with no
  // report on disk.
  const s = formatRecord({ component: 'GONE.png', pair: 'iOS-web', observed: null, entry: {}, orphaned: true });
  assert.match(s, /GONE\.png/);
  assert.match(s, /iOS-web/);
  assert.match(s, /orphaned/, 'the reader must be told which kind of problem this is');
});
