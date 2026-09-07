#!/usr/bin/env node
// Tests for tools/visual/spec-oracle.mjs — the Lane A spec oracle.
//
// The property under test is the one the whole lane exists for: the system
// must be able to FAIL when every platform agrees on the wrong value. So the
// tests here are built from known-value controls (synthetic PNGs whose fill
// and geometry are chosen by hand), not from re-running the implementation —
// a test that restates the code would be worse than none.
//
// Two layers:
//   · unit — parseExpectations / measureCapture / evaluateOracle over
//     pngjs-built images, no disk captures involved.
//   · e2e  — spawn the real comparator and assert exit 6 actually fires,
//     in the style of cross-platform-gate-e2e.test.mjs: a "gate" that
//     classifies perfectly but never reaches process.exit is theatre, and
//     this repo has shipped that exact defect before.
//
// Run via `node --test tools/visual/spec-oracle.test.mjs`.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { spawnSync } from 'node:child_process';
import { mkdtempSync, mkdirSync, writeFileSync, readFileSync } from 'node:fs';
import { tmpdir } from 'node:os';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

import {
  parseExpectations,
  measureCapture,
  evaluateOracle,
  formatViolation,
  EXIT_SPEC_ORACLE_VIOLATION,
  DEFAULT_FILL_TOLERANCE,
  DEFAULT_BOX_TOLERANCE,
} from './spec-oracle.mjs';
// The real capture ground colour — painting the synthetic pages with the
// same constant the oracle excludes keeps the tests honest about what
// "page background" means.
import { CANONICAL_BG } from './compare-screenshots-metrics.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const COMPARATOR = resolve(__dirname, 'compare-screenshots.mjs');
const BG = [CANONICAL_BG.r, CANONICAL_BG.g, CANONICAL_BG.b];

// ── Synthetic-image scaffolding ─────────────────────────────────────────────

/** Blank page: W×H filled with the capture ground colour, opaque. */
function page(w, h) {
  const png = new PNG({ width: w, height: h });
  for (let i = 0; i < w * h * 4; i += 4) {
    png.data[i] = BG[0]; png.data[i + 1] = BG[1]; png.data[i + 2] = BG[2]; png.data[i + 3] = 255;
  }
  return png;
}

/** Paint an axis-aligned rect of a solid colour — the component fill. */
function rect(png, x0, y0, w, h, [r, g, b]) {
  for (let y = y0; y < y0 + h; y++) {
    for (let x = x0; x < x0 + w; x++) {
      const i = (y * png.width + x) * 4;
      png.data[i] = r; png.data[i + 1] = g; png.data[i + 2] = b; png.data[i + 3] = 255;
    }
  }
  return png;
}

/** A capture-like image: page ground + component rect + a white "label"
 *  strip standing in for the component-name text the harness renders. The
 *  label is the pollution the fill/box measurements must ignore. */
function capture(fill, { w = 390, h = 80, rx = 10, ry = 20, rw = 120, rh = 40 } = {}) {
  const png = page(w, h);
  rect(png, rx, ry, rw, rh, fill);                  // the component box
  rect(png, 10, 4, 80, 8, [255, 255, 255]);         // the label text stand-in
  return png;
}

/** Minimal fixture document with one `_expect`-carrying component. */
function fixtureDoc(expect, name = 'Oracle_Case') {
  return { components: { [name]: { properties: { width: '120px' }, _expect: expect } } };
}

/** Comparator-shaped row: `{NNN}_{Name}.png` with three present platforms. */
function rowFor(name, present = { iOS: true, Android: true, web: true }) {
  const platforms = {};
  for (const [p, ok] of Object.entries(present)) platforms[p] = { present: ok };
  return { name: `000_${name}.png`, platforms, pairs: {} };
}

/** evaluateOracle opts serving in-memory PNGs keyed by platform. */
function serving(pngsByPlatform) {
  return { getPng: (platform) => pngsByPlatform[platform] ?? null };
}

// ── The exit-code contract ──────────────────────────────────────────────────

test('EXIT_SPEC_ORACLE_VIOLATION is literally 6 — the number IS the contract', () => {
  // The adversarial review mutated this constant to 0 — every spec
  // violation exiting as SUCCESS, the exact catastrophe this module exists
  // to prevent — and all 26 tests stayed green, because the e2e assertions
  // compared `status` against the imported constant: the test restated the
  // implementation. The e2e assertions now pin the LITERAL 6, and this test
  // pins the constant, so a renumbering breaks loudly in two places (and
  // test-all.sh's `case 6)` message stops dead-lettering silently).
  assert.equal(EXIT_SPEC_ORACLE_VIOLATION, 6);
});

// ── parseExpectations ───────────────────────────────────────────────────────

test('a fixture without _expect yields an empty map (the inert case)', () => {
  const m = parseExpectations({ components: { A: { properties: {} }, B: { properties: {} } } });
  assert.equal(m.size, 0);
});

test('defaults are applied and explicit values are honoured', () => {
  const m = parseExpectations(fixtureDoc({ fill: [200, 100, 50] }));
  const e = m.get('Oracle_Case');
  assert.equal(e.fillTolerance, DEFAULT_FILL_TOLERANCE);
  assert.equal(e.boxTolerance, DEFAULT_BOX_TOLERANCE);
  assert.equal(e.box, null);                        // box is optional
  const m2 = parseExpectations(fixtureDoc({ fill: [200, 100, 50], fillTolerance: 0, box: [30, 10], boxTolerance: 3, note: 'n' }));
  assert.deepEqual(m2.get('Oracle_Case'), { fill: [200, 100, 50], fillTolerance: 0, box: [30, 10], boxTolerance: 3, note: 'n', waive: null });
});

test('waivers parse, validate their platforms, and demand a reason', () => {
  // The happy path: a per-platform excuse travels with the expectation.
  const m = parseExpectations(fixtureDoc({ fill: [200, 100, 50], waive: { iOS: 'ledgered src-over alpha trade-off' } }));
  assert.deepEqual(m.get('Oracle_Case').waive, { iOS: 'ledgered src-over alpha trade-off' });
  // Unknown platform and empty reason are both authoring errors — a waiver
  // that binds to nothing, or explains nothing, is worse than none.
  assert.throws(() => parseExpectations(fixtureDoc({ fill: [1, 2, 3], waive: { ios: 'x' } })), /unknown platform/);
  assert.throws(() => parseExpectations(fixtureDoc({ fill: [1, 2, 3], waive: { iOS: '' } })), /non-empty reason/);
  assert.throws(() => parseExpectations(fixtureDoc({ fill: [1, 2, 3], waive: ['iOS'] })), /waive must be/);
});

test('nested child expectations are collected; duplicate names throw', () => {
  const doc = {
    components: {
      Parent: { properties: {}, children: { Child: { properties: {}, _expect: { fill: [200, 0, 0] } } } },
    },
  };
  assert.ok(parseExpectations(doc).has('Child'));
  // Same name twice (child of two parents) → one capture key → refuse.
  const dup = {
    components: {
      P1: { children: { Kid: { _expect: { fill: [200, 0, 0] } } } },
      P2: { children: { Kid: { _expect: { fill: [0, 200, 0] } } } },
    },
  };
  assert.throws(() => parseExpectations(dup), /duplicate component name/);
});

test('a fill at (or indistinguishably near) the page background is rejected loudly', () => {
  // Exact page ground — the measurement excludes these pixels by
  // construction, so the check could never fire. Must be an authoring error.
  assert.throws(() => parseExpectations(fixtureDoc({ fill: BG })), /page background/);
  // Inside the ±8 exclusion band (30,30,50 vs 26,26,46) — same trap.
  assert.throws(() => parseExpectations(fixtureDoc({ fill: [30, 30, 50] })), /page background/);
  // Just OUTSIDE the band on one channel — measurable, must parse.
  assert.ok(parseExpectations(fixtureDoc({ fill: [26, 26, 60] })).has('Oracle_Case'));
});

test('malformed declarations throw with the component named', () => {
  for (const bad of [
    { fill: [1, 2] },                               // wrong arity
    { fill: [0, 0, 256] },                          // out of range
    { fill: [0, 0.5, 0] },                          // non-integer channel
    { fill: 'red' },                                // not an array
    {},                                             // fill missing entirely
    { fill: [200, 0, 0], box: [120] },              // box wrong arity
    { fill: [200, 0, 0], box: [120, 0] },           // zero-size box
    { fill: [200, 0, 0], fillTolerance: -1 },       // negative tolerance
    'nope',                                         // _expect not an object
  ]) {
    assert.throws(() => parseExpectations(fixtureDoc(bad)), /Oracle_Case/, JSON.stringify(bad));
  }
});

// ── measureCapture ──────────────────────────────────────────────────────────

test('fill is the dominant non-ground colour — the label does not pollute it', () => {
  const expect = { fill: [200, 100, 50], fillTolerance: 2 };
  const m = measureCapture(capture([200, 100, 50]), expect);
  // 120×40 = 4800 fill pixels vs 640 white label pixels → mode is the fill.
  assert.deepEqual(m.fill, [200, 100, 50]);
  assert.deepEqual(m.box, [120, 40]);               // bbox matches the painted rect
});

test('box is measured against the DECLARED fill, within tolerance, two-sided', () => {
  // Core rect of the exact declared fill plus a 1px ring at fill+2 (inside
  // the default ±2 band) → the ring joins the bbox: 32×12, not 30×10.
  const png = page(200, 60);
  rect(png, 9, 19, 32, 12, [202, 102, 52]);         // ring colour, +2 per channel
  rect(png, 10, 20, 30, 10, [200, 100, 50]);        // exact core over-paints the middle
  const near = measureCapture(png, { fill: [200, 100, 50], fillTolerance: 2 });
  assert.deepEqual(near.box, [32, 12]);
  // Same construction with the ring at fill+5 — OUTSIDE the band → excluded.
  const png2 = page(200, 60);
  rect(png2, 9, 19, 32, 12, [205, 105, 55]);
  rect(png2, 10, 20, 30, 10, [200, 100, 50]);
  const far = measureCapture(png2, { fill: [200, 100, 50], fillTolerance: 2 });
  assert.deepEqual(far.box, [30, 10]);
});

test('an all-background image measures fill null and box [0,0]', () => {
  const m = measureCapture(page(100, 50), { fill: [200, 100, 50], fillTolerance: 2 });
  assert.equal(m.fill, null);
  assert.deepEqual(m.box, [0, 0]);
});

test('a wrong-colour render reports box [0,0] rather than measuring garbage', () => {
  // The whole box is present but painted the WRONG colour: fill reports the
  // wrong colour, and the declared-fill bbox is empty — two independent
  // signals, matching the "box is vs the DECLARED fill" contract.
  const m = measureCapture(capture([90, 90, 90]), { fill: [200, 100, 50], fillTolerance: 2 });
  assert.deepEqual(m.fill, [90, 90, 90]);
  assert.deepEqual(m.box, [0, 0]);
});

test('an exact histogram tie breaks to the smaller packed RGB, deterministically', () => {
  const png = page(100, 40);
  rect(png, 0, 0, 10, 10, [100, 0, 0]);             // packed 0x640000
  rect(png, 20, 0, 10, 10, [50, 200, 10]);          // packed 0x32C80A — smaller
  const m = measureCapture(png, { fill: [100, 0, 0], fillTolerance: 2 });
  assert.deepEqual(m.fill, [50, 200, 10]);          // tie → smaller packed wins, every run
});

// ── evaluateOracle ──────────────────────────────────────────────────────────

const EXPECT_OK = parseExpectations(fixtureDoc({ fill: [200, 100, 50], box: [120, 40], note: 'ctl' }));

test('a spec-exact render on all three platforms passes with checked=3', () => {
  const good = capture([200, 100, 50]);
  const r = evaluateOracle([rowFor('Oracle_Case')], EXPECT_OK, serving({ iOS: good, Android: good, web: good }));
  assert.equal(r.checked, 3);
  assert.deepEqual(r.violations, []);
  assert.deepEqual(r.missing, []);
});

test('fill tolerance is two-sided: 3 off at ±2 fails, 2 off passes — both directions', () => {
  // Deliberately NO box in this expectation: a wrong fill also empties the
  // declared-fill bbox (that coupling is pinned by the "wrong-colour render
  // reports box [0,0]" test above), and here the fill band must be measured
  // in isolation.
  const FILL_ONLY = parseExpectations(fixtureDoc({ fill: [200, 100, 50], note: 'ctl' }));
  const run = (fill) => evaluateOracle([rowFor('Oracle_Case')], FILL_ONLY,
    serving({ iOS: capture(fill), Android: capture([200, 100, 50]), web: capture([200, 100, 50]) }));
  assert.equal(run([203, 100, 50]).violations.length, 1, '+3 must fail');
  assert.equal(run([197, 100, 50]).violations.length, 1, '−3 must fail');
  assert.equal(run([202, 100, 50]).violations.length, 0, '+2 must pass');
  assert.equal(run([198, 100, 50]).violations.length, 0, '−2 must pass');
  // The violation names everything a diagnosis needs, from the log alone.
  const v = run([203, 100, 50]).violations[0];
  assert.equal(v.platform, 'iOS');
  assert.equal(v.field, 'fill');
  assert.equal(v.delta, 3);
  assert.match(formatViolation(v), /expected rgb\(200,100,50\)/);
  assert.match(formatViolation(v), /measured rgb\(203,100,50\)/);
  assert.match(formatViolation(v), /ctl/);          // the note rides along
});

test('box tolerance is two-sided: 2px off at ±1 fails, 1px passes', () => {
  const run = (rw, rh) => evaluateOracle([rowFor('Oracle_Case')], EXPECT_OK,
    serving({ iOS: capture([200, 100, 50], { rw, rh }), Android: capture([200, 100, 50]), web: capture([200, 100, 50]) }));
  assert.equal(run(122, 40).violations.length, 1, '+2px width must fail');
  assert.equal(run(118, 40).violations.length, 1, '−2px width must fail');
  assert.equal(run(121, 40).violations.length, 0, '+1px passes (AA slack)');
  const v = run(122, 40).violations[0];
  assert.equal(v.field, 'box');
  assert.deepEqual(v.actual, [122, 40]);
  assert.deepEqual(v.delta, [2, 0]);
});

test('THE POINT: all three platforms wrong the same way → three violations', () => {
  // A pairwise gate scores this run perfect — identical renders everywhere.
  // The oracle must fail every platform individually.
  const wrong = capture([74, 110, 113]);            // the 2026-08-28 iOS sepia value
  const r = evaluateOracle([rowFor('Oracle_Case')], EXPECT_OK, serving({ iOS: wrong, Android: wrong, web: wrong }));
  const fills = r.violations.filter((v) => v.field === 'fill');
  assert.equal(fills.length, 3);
  assert.deepEqual(fills.map((v) => v.platform).sort(), ['Android', 'iOS', 'web']);
});

test('a missing platform is reported, not crashed on — and others still judged', () => {
  const r = evaluateOracle(
    [rowFor('Oracle_Case', { iOS: false, Android: true, web: true })],
    EXPECT_OK,
    serving({ Android: capture([200, 100, 50]), web: capture([200, 100, 50]) }),
  );
  assert.equal(r.checked, 2);                       // the two present platforms
  assert.equal(r.violations.length, 0);
  assert.deepEqual(r.missing, [{ component: 'Oracle_Case', platform: 'iOS', reason: 'no capture' }]);
});

test('getPng returning null (or throwing) lands in missing, never a crash', () => {
  const r = evaluateOracle([rowFor('Oracle_Case')], EXPECT_OK, {
    getPng: (p) => { if (p === 'web') throw new Error('boom'); return p === 'iOS' ? null : capture([200, 100, 50]); },
  });
  assert.equal(r.checked, 1);                       // only Android measured
  assert.equal(r.missing.length, 2);
  assert.match(r.missing.find((m) => m.platform === 'web').reason, /boom/);
});

test('an expectation matching no row is reported missing (renamed component)', () => {
  const r = evaluateOracle([rowFor('SomethingElse')], EXPECT_OK, serving({}));
  assert.equal(r.checked, 0);
  assert.deepEqual(r.missing[0].component, 'Oracle_Case');
  assert.equal(r.missing[0].platform, null);
});

test('an empty render (all page ground) is a fill VIOLATION, not a skip', () => {
  const r = evaluateOracle([rowFor('Oracle_Case')], EXPECT_OK,
    serving({ iOS: page(390, 80), Android: capture([200, 100, 50]), web: capture([200, 100, 50]) }));
  const v = r.violations.find((x) => x.platform === 'iOS' && x.field === 'fill');
  assert.equal(v.actual, null);
  assert.match(formatViolation(v), /NOTHING/);
});

// ── End-to-end: the wiring actually exits 6 ─────────────────────────────────

/** Scaffold a full comparator run: three platform dirs each holding one
 *  synthetic capture, a fixture carrying `_expect`, and an EMPTY ledger so
 *  the repo's real expectation file cannot interfere. */
function scaffold({ ios, android, web, expect, ledger = [] }) {
  const dir = mkdtempSync(join(tmpdir(), 'spec-oracle-e2e-'));
  const dirs = {};
  const NAME = '000_Oracle_Case.png';
  for (const [p, fill] of Object.entries({ iOS: ios, Android: android, web })) {
    dirs[p] = join(dir, p);
    mkdirSync(dirs[p]);
    writeFileSync(join(dirs[p], NAME), PNG.sync.write(capture(fill)));
  }
  const fixture = join(dir, 'oracle-fixture.json');
  writeFileSync(fixture, JSON.stringify(fixtureDoc(expect), null, 2));
  const ledgerPath = join(dir, 'ledger.json');
  writeFileSync(ledgerPath, JSON.stringify({ expectations: ledger }, null, 2));
  return { dir, dirs, fixture, ledgerPath };
}

/** Spawn the real comparator over a scaffold; return {status, out, manifest}. */
function run(s, extraArgs = []) {
  const manifestOut = join(s.dir, 'manifest.json');
  const r = spawnSync(process.execPath, [COMPARATOR, '--input', s.fixture, ...extraArgs], {
    encoding: 'utf8',
    env: {
      ...process.env,
      IOS_SCREENSHOTS_DIR: s.dirs.iOS,
      ANDROID_SCREENSHOTS_DIR: s.dirs.Android,
      WEB_SCREENSHOTS_DIR: s.dirs.web,
      REPORT_DIR: join(s.dir, 'report'),
      MANIFEST_OUT: manifestOut,
      CROSS_PLATFORM_EXPECTATIONS: s.ledgerPath,
    },
  });
  let manifest = null;
  try { manifest = JSON.parse(readFileSync(manifestOut, 'utf8')); } catch { /* run may exit before writing */ }
  return { status: r.status, out: `${r.stdout}\n${r.stderr}`, manifest };
}

const SPEC = [200, 100, 50];                        // the "spec-derived" control value
const OK_EXPECT = { fill: SPEC, box: [120, 40], note: 'e2e control expectation' };

test('e2e control: spec-exact renders on all three platforms exit 0', () => {
  // If this failed, every exit-6 assertion below would be measuring a broken
  // scaffold rather than the oracle.
  const { status, out, manifest } = run(scaffold({ ios: SPEC, android: SPEC, web: SPEC, expect: OK_EXPECT }));
  assert.equal(status, 0, out);
  assert.match(out, /spec oracle: 3 platform-component check\(s\) · 0 violation\(s\)/);
  assert.equal(manifest.specOracle.checked, 3);     // the manifest records the verdict
});

test('e2e: all three platforms agreeing on the WRONG value exits 6', () => {
  // The correlated-failure mode this lane exists for: every pairwise gate is
  // perfect (identical captures), and the build must still go red.
  const wrong = [74, 110, 113];                     // the shipped iOS sepia bug value
  const { status, out } = run(scaffold({ ios: wrong, android: wrong, web: wrong, expect: OK_EXPECT }));
  assert.equal(status, 6, out);   // LITERAL, not the imported constant — see the contract test
  // A failure must be diagnosable from the log alone.
  assert.match(out, /expected rgb\(200,100,50\)/);
  assert.match(out, /measured rgb\(74,110,113\)/);
  assert.match(out, /Δmax 126/);                    // worst channel is R: |200−74| = 126, hand-derived
  assert.match(out, /e2e control expectation/);     // the note prints
  // ALL THREE platforms are named — each judged alone.
  for (const p of ['iOS', 'Android', 'web']) assert.match(out, new RegExp(`${p} · Oracle_Case · fill`));
});

test('e2e: --no-spec-oracle is a working escape hatch, and says so', () => {
  const wrong = [74, 110, 113];
  const s = scaffold({ ios: wrong, android: wrong, web: wrong, expect: OK_EXPECT });
  const { status, out, manifest } = run(s, ['--no-spec-oracle']);
  assert.equal(status, 0, out);
  assert.match(out, /spec oracle skipped — disabled via --no-spec-oracle/);
  assert.equal(manifest.specOracle.skipped, true);  // the skip is on the record
});

test('e2e ordering: unexpected cross-platform divergence (4) outranks the oracle (6)', () => {
  // iOS renders red, the others blue: pairs diverge hard AND everyone
  // violates the green expectation. "The runtimes disagree" names which
  // platform moved and must win the exit code.
  const s = scaffold({ ios: [255, 0, 0], android: [0, 0, 255], web: [0, 0, 255], expect: { fill: [0, 255, 0], note: 'ordering' } });
  const { status, out } = run(s);
  assert.equal(status, 4, out);                     // EXIT_UNEXPECTED_DIVERGENCE
});

test('e2e ordering: the oracle (6) outranks a stale ledger entry (5)', () => {
  // All platforms agree on the wrong value, and the ledger holds a now-
  // passing entry. Spec wrongness is a product defect; the stale line is
  // bookkeeping — the exit code must name the defect.
  const wrong = [74, 110, 113];
  const s = scaffold({
    ios: wrong, android: wrong, web: wrong, expect: OK_EXPECT,
    // `expires` is part of the ledger contract (validateLedger) — a line
    // without one is exit 2 before any verdict, which would mask the ordering
    // this test is about.
    ledger: [{ component: 'Oracle_Case.png', pair: 'iOS-Android', reason: 'stale by construction', owner: 'test', expires: '2099-01-01' }],
  });
  const { status, out } = run(s);
  assert.equal(status, 6, out);   // LITERAL, not the imported constant — see the contract test
});

test('e2e (retro A11#3): the oracle is REPORTED even when the gate exits 4 — a waived fixture reaches it', () => {
  // iOS renders red, Android/web the spec value: the cross-platform gate has
  // two unexpected pairs (exit 4) AND the fixture waives iOS. Before the
  // report/enforce split the comparator process.exit(4)'d inside the gate
  // block, so the oracle never printed and every `_expect.waive` in
  // nested-transforms / radius-overflow-transform / blend-isolation was
  // unreachable under ./test-all.sh. The exit code is unchanged (4 still
  // outranks 6); what changes is that the oracle's verdict is on the record.
  const s = scaffold({
    ios: [255, 0, 0], android: SPEC, web: SPEC,
    expect: { fill: SPEC, note: 'reachability', waive: { iOS: 'known iOS divergence, ledgered' } },
  });
  const { status, out, manifest } = run(s);
  assert.equal(status, 4, out);
  assert.match(out, /unexpected cross-platform divergence/);           // the gate still reports
  assert.match(out, /spec oracle: 3 platform-component check\(s\) · 0 violation\(s\) · 1 waived/);
  assert.match(out, /waived: iOS · Oracle_Case · fill/);
  assert.match(out, /waiver: known iOS divergence, ledgered/);
  assert.equal(manifest.specOracle.waived.length, 1, 'the manifest records the waiver');
});

test('e2e (retro A11#3): a STALE waiver is reported under an exit-4 run instead of being hidden', () => {
  // The two-sided rule needs the oracle to be reached: web passes but carries
  // a waiver (stale), while iOS diverges (exit 4). The stale line must print
  // even though exit 4 wins, or the waiver rots exactly as before.
  const s = scaffold({
    ios: [255, 0, 0], android: SPEC, web: SPEC,
    expect: { fill: SPEC, note: 'stale under 4', waive: { web: 'was broken once' } },
  });
  const { status, out } = run(s);
  assert.equal(status, 4, out);
  assert.match(out, /stale oracle waiver\(s\) — the platform now passes; delete the waiver/);
  assert.match(out, /web · Oracle_Case — waived as: was broken once/);
});

test('e2e: an oracle whose expectations bind to nothing fails instead of passing', () => {
  // Rename the fixture component out from under the captures: zero checks
  // run. A declared oracle that measures nothing must not read as green.
  const s = scaffold({ ios: SPEC, android: SPEC, web: SPEC, expect: OK_EXPECT });
  writeFileSync(s.fixture, JSON.stringify(fixtureDoc(OK_EXPECT, 'Renamed_Component'), null, 2));
  const { status, out } = run(s);
  assert.equal(status, 6, out);   // LITERAL, not the imported constant — see the contract test
  assert.match(out, /ZERO checks ran/);
});

test('e2e: a page-background fill is an authoring error → exit 2, loudly', () => {
  const s = scaffold({ ios: SPEC, android: SPEC, web: SPEC, expect: { fill: BG } });
  const { status, out } = run(s);
  assert.equal(status, 2, out);
  assert.match(out, /page background/);
});

test('e2e inertness: a fixture with no _expect leaves run and manifest untouched', () => {
  // Non-negotiable: the 327-pair corpus must not notice this lane exists.
  const s = scaffold({ ios: SPEC, android: SPEC, web: SPEC, expect: OK_EXPECT });
  writeFileSync(s.fixture, JSON.stringify({ components: { Oracle_Case: { properties: {} } } }, null, 2));
  const { status, out, manifest } = run(s);
  assert.equal(status, 0, out);
  assert.ok(!out.includes('spec oracle'), 'no oracle output for an oracle-free fixture');
  assert.ok(!('specOracle' in manifest), 'no manifest key either — absence means "none declared"');
});

// ── waivers (evaluateOracle) ────────────────────────────────────────────────

test('a waived platform is excused with its reason; others still fail', () => {
  // iOS diverges (waived), Android diverges identically (NOT waived) — the
  // waiver must excuse exactly its own platform, nothing more.
  const exp = parseExpectations(fixtureDoc({
    fill: [200, 100, 50], waive: { iOS: 'documented trade-off' },
  }));
  const wrong = capture([230, 100, 50]);            // 30 off on red
  const r = evaluateOracle(
    [rowFor('Oracle_Case')],
    exp,
    serving({ iOS: wrong, Android: wrong, web: wrong }),
  );
  assert.equal(r.waived.length, 1, 'iOS excused');
  assert.equal(r.waived[0].platform, 'iOS');
  assert.equal(r.waived[0].waiveReason, 'documented trade-off');
  const failed = r.violations.map((v) => v.platform).sort();
  assert.deepEqual([...new Set(failed)], ['Android', 'web'], 'the unwaived platforms still fail');
});

test('a waiver whose platform PASSES is reported stale', () => {
  // Two-sided: the excuse must die with the divergence, exactly like the
  // cross-platform ledger after the noise-floor study.
  const exp = parseExpectations(fixtureDoc({
    fill: [200, 100, 50], waive: { iOS: 'was broken once' },
  }));
  const right = capture([200, 100, 50]);
  const r = evaluateOracle([rowFor('Oracle_Case')], exp,
    serving({ iOS: right, Android: right, web: right }));
  assert.equal(r.violations.length, 0);
  assert.equal(r.waived.length, 0);
  assert.equal(r.stale.length, 1, 'the passing waiver is stale');
  assert.equal(r.stale[0].platform, 'iOS');
  assert.match(r.stale[0].reason, /was broken once/);
});
