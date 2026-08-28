#!/usr/bin/env node
// Unit tests for tools/visual/cross-platform-gate.mjs — the Wave 1 gate that
// finally makes the three-way comparison mean something.
//
// The properties worth pinning are the ones that decide whether this gate is
// trustworthy or theatre:
//   · an unexpected divergence FAILS (that is the whole point)
//   · a listed divergence is excused, and only the listed one
//   · a listed pair that now passes is reported as stale, not silently kept
//   · an expectation for a component that no longer exists is reported
//   · a single-platform run skips entirely (this is what protects CI)
//   · the pass/fail expression matches the baseline gate exactly
//
// Run via `node --test tools/visual/cross-platform-gate.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  evaluateCrossPlatformGate,
  pairRegressed,
  formatRecord,
  PAIR_KEYS,
  EXIT_UNEXPECTED_DIVERGENCE,
} from './cross-platform-gate.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const OPTS = { ssimThreshold: 0.95, pixelThreshold: 2, inputLabel: 'fixtures/visual-test.json' };

/** Build a row whose named pairs carry the given metrics. */
function row(name, pairs, platforms = { iOS: {}, Android: {}, web: {} }) {
  const full = {};
  for (const k of PAIR_KEYS) full[k] = pairs[k] ?? null;
  return { name, platforms, pairs: full };
}

/** A passing pair. */
const ok = (ssim = 0.999) => ({ ssim, pixelMismatchedPct: 0.1, labDeltaE: { p95: 0 }, divergence: 'identical' });
/** A failing pair. */
const bad = (ssim = 0.90) => ({ ssim, pixelMismatchedPct: 1.2, labDeltaE: { p95: 0 }, divergence: 'structural-divergence' });

// ── The core expression ─────────────────────────────────────────────────────

test('pairRegressed mirrors the baseline gate expression', () => {
  // If these two gates ever disagree about what "failing" means, one of them
  // is lying about the same pixels. Same metrics, same comparisons.
  assert.equal(pairRegressed({ ssim: 0.96, pixelMismatchedPct: 1 }, OPTS), false);
  assert.equal(pairRegressed({ ssim: 0.94, pixelMismatchedPct: 1 }, OPTS), true, 'ssim below threshold');
  assert.equal(pairRegressed({ ssim: 0.99, pixelMismatchedPct: 2.1 }, OPTS), true, 'pixel above threshold');
  assert.equal(pairRegressed({ ssim: null, pixelMismatchedPct: 0 }, OPTS), false, 'null ssim never fails alone');
  assert.equal(pairRegressed(null, OPTS), false, 'absent pair is not a failure');
});

test('the SSIM threshold is exclusive at the boundary', () => {
  // 0.95 exactly must PASS — the ledger was seeded with that reading, so an
  // off-by-one here would silently invalidate every seeded entry.
  assert.equal(pairRegressed({ ssim: 0.95, pixelMismatchedPct: 0 }, OPTS), false);
  assert.equal(pairRegressed({ ssim: 0.9499, pixelMismatchedPct: 0 }, OPTS), true);
});

// ── Unexpected divergence: the reason this gate exists ──────────────────────

test('an unexpected divergence is reported as unexpected', () => {
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': bad() })], { expectations: [] }, OPTS);
  assert.equal(r.skipped, false);
  assert.equal(r.unexpected.length, 1);
  assert.equal(r.unexpected[0].component, 'A.png');
  assert.equal(r.unexpected[0].pair, 'iOS-Android');
  assert.equal(r.expected.length, 0);
});

test('a listed divergence is excused', () => {
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'known', owner: 'x' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': bad() })], ledger, OPTS);
  assert.equal(r.unexpected.length, 0);
  assert.equal(r.expected.length, 1);
});

test('an expectation excuses ONLY its own pair', () => {
  // The failure mode this guards: a blanket entry quietly covering the other
  // two pairs of the same component.
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'r', owner: 'x' }] };
  const r = evaluateCrossPlatformGate(
    [row('A.png', { 'iOS-Android': bad(), 'iOS-web': bad(), 'Android-web': ok() })], ledger, OPTS);
  assert.equal(r.expected.length, 1);
  assert.equal(r.unexpected.length, 1);
  assert.equal(r.unexpected[0].pair, 'iOS-web');
});

test('an expectation scoped to another fixture does not excuse this one', () => {
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', fixture: 'other-suite.json', reason: 'r' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': bad() })], ledger, OPTS);
  assert.equal(r.unexpected.length, 1, 'wrong-fixture entry must not apply');
});

// ── Two-sided: the ledger must not rot ──────────────────────────────────────

test('a listed pair that now PASSES is reported stale', () => {
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'r', owner: 'x' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': ok() })], ledger, OPTS);
  assert.equal(r.stale.length, 1, 'a fix must surface as a stale line to delete');
  assert.equal(r.unexpected.length, 0);
});

test('an expectation for a component that no longer exists is reported orphaned', () => {
  // Renaming a fixture component is how ledgers accumulate dead weight.
  const ledger = { expectations: [{ component: 'GONE.png', pair: 'iOS-web', reason: 'r', owner: 'x' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-web': ok() })], ledger, OPTS);
  assert.equal(r.stale.length, 1);
  assert.equal(r.stale[0].orphaned, true);
  assert.equal(r.stale[0].component, 'GONE.png');
});

test('an expired expectation still excuses, but is flagged', () => {
  // Expiry is a review prompt, not a booby trap that reddens the build on a
  // date rollover — an expired entry that still fails is still known.
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'r', expires: '2020-01-01' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': bad() })], ledger, OPTS);
  assert.equal(r.unexpected.length, 0, 'expiry must not manufacture a failure');
  assert.equal(r.expected.length, 1);
  assert.equal(r.expired.length, 1);
});

test('a far-future expiry is not flagged', () => {
  const ledger = { expectations: [{ component: 'A.png', pair: 'iOS-Android', reason: 'r', expires: '2999-01-01' }] };
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': bad() })], ledger, OPTS);
  assert.equal(r.expired.length, 0);
});

// ── The CI-protection property ──────────────────────────────────────────────

test('a single-platform run skips the gate entirely', () => {
  // CI runs one platform per job (SKIP_IOS=1 …). If this branch broke, every
  // visual CI job would start failing on pairs that cannot exist.
  const r = evaluateCrossPlatformGate(
    [{ name: 'A.png', platforms: { Android: {}, iOS: { present: false }, web: { present: false } }, pairs: {} }],
    { expectations: [] }, OPTS);
  assert.equal(r.skipped, true);
  assert.match(r.reason, /1 platform/);
  assert.equal(r.checked, 0);
});

test('two platforms is enough to gate', () => {
  const r = evaluateCrossPlatformGate(
    [row('A.png', { 'Android-web': bad() }, { iOS: { present: false }, Android: {}, web: {} })],
    { expectations: [] }, OPTS);
  assert.equal(r.skipped, false);
  assert.equal(r.unexpected.length, 1);
});

test('absent pairs are not counted as checked', () => {
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-Android': ok() })], { expectations: [] }, OPTS);
  assert.equal(r.checked, 1, 'only the one present pair');
});

// ── The committed ledger must be valid and must match the seeded run ────────

test('the committed ledger parses and every entry is well-formed', () => {
  const led = JSON.parse(readFileSync(resolve(__dirname, 'cross-platform-expectations.json'), 'utf8'));
  assert.ok(Array.isArray(led.expectations));
  // Hardcoded deliberately: the count is the thing that must not drift
  // unnoticed. Changing it should require editing this line, which is a
  // review prompt.
  assert.equal(led.expectations.length, 24, '22 on visual-test + 2 on composition-test; opacity-group entries went stale when the iOS compositingGroup fix landed');
  for (const e of led.expectations) {
    // Every field a reviewer needs to judge the line without opening the report.
    assert.ok(e.component && e.component.endsWith('.png'), `bad component: ${e.component}`);
    assert.ok(PAIR_KEYS.includes(e.pair), `bad pair: ${e.pair}`);
    assert.ok(e.reason && e.reason.length > 40, `reason too thin for ${e.component}: ${e.reason}`);
    assert.ok(e.owner, `missing owner for ${e.component}`);
    assert.ok(Date.parse(e.expires), `unparseable expiry for ${e.component}`);
    assert.ok(e.observed && typeof e.observed.ssim === 'number', 'must record what was observed');
  }
});

test('the ledger separates real size bugs from rasterisation noise', () => {
  // Three Android components render SHORTER than their iOS/web peers
  // (091_Button_Outline 58 vs 62, 094_Input_Field 60 vs 62,
  // 105_Edge_DeepNesting 58 vs 62). Every one was invisible until the pad
  // sentinel landed, because the old #1A1A2E fill matched the capture
  // background. They are bugs to fix, not divergences to excuse — so they
  // must be labelled as such and carry a shorter expiry than the AA lines.
  const led = JSON.parse(readFileSync(resolve(__dirname, 'cross-platform-expectations.json'), 'utf8'));
  const sizeBugs = led.expectations.filter((e) => e.observed?.sizes);
  assert.equal(sizeBugs.length, 6, 'three components × two affected pairs each');
  const components = [...new Set(sizeBugs.map((e) => e.component))].sort();
  assert.deepEqual(components,
    ['Button_Outline.png', 'Edge_DeepNesting.png', 'Input_Field.png']);
  for (const e of sizeBugs) {
    assert.match(e.reason, /REAL BUG/, `${e.component} must not be filed as benign`);
    // Android is the outlier in all three; iOS and web agree.
    assert.notEqual(e.observed.sizes.Android, e.observed.sizes.web);
    assert.equal(e.observed.sizes.iOS, e.observed.sizes.web, 'iOS and web agree — Android is the outlier');
    assert.ok(Date.parse(e.expires) < Date.parse('2026-10-31'), 'size bugs get a short expiry');
  }
});

test('the committed ledger has no duplicate (component, pair) keys', () => {
  // A duplicate means one line is dead and nobody would notice.
  const led = JSON.parse(readFileSync(resolve(__dirname, 'cross-platform-expectations.json'), 'utf8'));
  const keys = led.expectations.map((e) => `${e.component} ${e.pair}`);
  assert.equal(new Set(keys).size, keys.length);
});

test('every seeded entry actually breaches the threshold it claims', () => {
  // Guards against a seed script that listed passing rows — which would make
  // the ledger excuse things that were never failing.
  const led = JSON.parse(readFileSync(resolve(__dirname, 'cross-platform-expectations.json'), 'utf8'));
  for (const e of led.expectations) {
    assert.equal(
      pairRegressed({ ssim: e.observed.ssim, pixelMismatchedPct: e.observed.pixelPct }, OPTS),
      true,
      `${e.component} ${e.pair} was listed but its recorded metrics pass`,
    );
  }
});

// ── Reporting ───────────────────────────────────────────────────────────────

test('formatRecord names the metric that decided it', () => {
  const r = evaluateCrossPlatformGate([row('A.png', { 'iOS-web': bad(0.88) })], { expectations: [] }, OPTS);
  const s = formatRecord(r.unexpected[0]);
  assert.match(s, /A\.png/);
  assert.match(s, /iOS-web/);
  assert.match(s, /0\.8800/);
  assert.match(s, /ΔE95/, 'ΔE is the discriminator between colour bugs and AA');
});

test('EXIT_UNEXPECTED_DIVERGENCE is distinct from the other exit codes', () => {
  // 1 = baseline regression · 2 = IO/empty · 3 = colour space · 4 = this.
  assert.equal(EXIT_UNEXPECTED_DIVERGENCE, 4);
});

test('a skipped platform does not orphan its expectations', () => {
  // SKIP_ANDROID=1 means no iOS-Android and no Android-web pair exists at
  // all. Reporting every Android expectation as "orphaned — delete the line"
  // is noise dressed as a finding, and it trains people to ignore the
  // warning that matters. Only iOS-web is evaluable here.
  const ledger = { expectations: [
    { component: 'A.png', pair: 'iOS-Android', reason: 'r', owner: 'x' },
    { component: 'A.png', pair: 'Android-web', reason: 'r', owner: 'x' },
    { component: 'GONE.png', pair: 'iOS-web', reason: 'r', owner: 'x' },
  ] };
  const rows = [row('A.png', { 'iOS-web': ok() }, { iOS: {}, Android: { present: false }, web: {} })];
  const r = evaluateCrossPlatformGate(rows, ledger, OPTS);
  assert.equal(r.skipped, false, 'iOS + web is still two platforms');
  // The genuinely orphaned iOS-web entry IS reported; the two Android ones are not.
  const orphans = r.stale.filter((s) => s.orphaned).map((s) => `${s.component} ${s.pair}`);
  assert.deepEqual(orphans, ['GONE.png iOS-web']);
});
