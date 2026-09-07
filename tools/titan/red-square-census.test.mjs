#!/usr/bin/env node
//
// Pins for tools/titan/red-square-census.mjs — the committed red-square census
// (retro A1#3: the wave-49 numbers came from an uncommitted predicate and do
// not reproduce). Three layers: (1) the PREDICATE constants, exactly — a silent
// widening/narrowing of "red" would move every number this census produced;
// (2) a SYNTHETIC run dir with hand-built PNGs where every count is known by
// construction — the mutation test; (3) the VERBATIM wave49-final artifact
// (local-only, skipped visibly when absent): the canonical
// hit-test-unrelated-element cell, and under TITAN_CENSUS_FULL=1 the full-run
// totals the module banner quotes.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { mkdtempSync, mkdirSync, writeFileSync, existsSync, rmSync } from 'node:fs';
import { join, resolve, dirname } from 'node:path';
import { tmpdir } from 'node:os';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';
import {
  RED_SQUARE_PREDICATE, redPct, isScored, captureName, censusRun, summarize,
} from './red-square-census.mjs';

const here = dirname(fileURLToPath(import.meta.url));

// ── 1. the predicate, pinned ────────────────────────────────────────────────

test('predicate: strict red is r>200 && g<80 && b<80 — boundaries exact', () => {
  const { isRed, capMinPct, refMaxPct } = RED_SQUARE_PREDICATE;
  assert.equal(isRed(255, 0, 0), true, 'WPT canonical red');
  assert.equal(isRed(201, 79, 79), true, 'just inside every bound');
  assert.equal(isRed(200, 79, 79), false, 'r must EXCEED 200');
  assert.equal(isRed(201, 80, 79), false, 'g must be BELOW 80');
  assert.equal(isRed(201, 79, 80), false, 'b must be BELOW 80');
  assert.equal(isRed(255, 165, 0), false, 'orange is not red');
  assert.equal(isRed(0, 128, 0), false, 'the PASS green is not red');
  assert.equal(capMinPct, 0.5, 'the wave-49 comment\'s own bar: capRedPct > 0.5');
  assert.equal(refMaxPct, 0, 'the reference must carry NO red');
  assert.ok(Object.isFrozen(RED_SQUARE_PREDICATE), 'the predicate is frozen — change it in source, with a new census');
});

test('isScored is the scorer idiom verbatim: numeric ssim and not scoreExcluded', () => {
  assert.equal(isScored({ ssim: 0.99 }), true);
  assert.equal(isScored({ ssim: 0.99, scoreExcluded: true }), false);
  assert.equal(isScored({ ssim: 0.99, scoreExcluded: 'native-font-parity' }), false, 'the string stamp is truthy too');
  assert.equal(isScored({ ssim: null }), false);
  assert.equal(isScored({ error: 'decode' }), false);
  assert.equal(isScored(null), false);
});

test('captureName derives the composed PNG name the feeders wrote', () => {
  assert.equal(captureName('css/css-color/background-color-hsl-001.html'), 'wpt__css-color__background-color-hsl-001.png');
  // Nested tests use the subdir-encoded fixtureStem (wave-21 collision fix).
  assert.equal(captureName('css/css-flexbox/abspos/abspos-autopos-htb-ltr.html'), 'wpt__css-flexbox__abspos__abspos-autopos-htb-ltr.png');
});

// ── 2. synthetic run — counts known by construction (the mutation test) ────

/** A W×H opaque PNG filled with `bg`, with an optional `sq`-pixel square of
 *  colour `fg` painted top-left. Returns the encoded bytes. */
function png(w, h, bg, fg = null, sq = 0) {
  const p = new PNG({ width: w, height: h });
  for (let y = 0; y < h; y++) for (let x = 0; x < w; x++) {
    const i = (y * w + x) * 4;
    const c = fg && x < sq && y < sq ? fg : bg;
    p.data[i] = c[0]; p.data[i + 1] = c[1]; p.data[i + 2] = c[2]; p.data[i + 3] = 255;
  }
  return PNG.sync.write(p);
}

test('redPct measures the exact red fraction', () => {
  // 100×100 canvas, 10×10 red square = 1.00 %.
  assert.equal(redPct(PNG.sync.read(png(100, 100, [255, 255, 255], [255, 0, 0], 10))), 1);
  // 7×7 = 0.49 % — under the 0.5 bar by construction.
  assert.equal(redPct(PNG.sync.read(png(100, 100, [255, 255, 255], [255, 0, 0], 7))), 0.49);
  // Green square: zero red.
  assert.equal(redPct(PNG.sync.read(png(100, 100, [255, 255, 255], [0, 128, 0], 50))), 0);
});

/** Build a fake run dir: one section, five tests, refs + captures laid out
 *  exactly as section-runner.sh writes them, manifest shaped like inject's. */
function buildSyntheticRun() {
  const root = mkdtempSync(join(tmpdir(), 'r8b-census-'));     // stands in for the repo root
  const runDir = join(root, 'runs', 'fake');
  const sec = join(runDir, 'sections', 'css-fake');
  const refs = join(root, 'refs', 'css-fake');
  for (const d of ['screenshots', 'ios-screenshots', 'android-screenshots']) mkdirSync(join(sec, d), { recursive: true });
  mkdirSync(refs, { recursive: true });
  const white = [255, 255, 255], red = [255, 0, 0], green = [0, 128, 0];
  const refGreen = png(100, 100, white, green, 10);              // a green-square ref (no red)
  const refRed = png(100, 100, white, red, 10);                  // a ref that legitimately paints red
  const capRed1 = png(100, 100, white, red, 10);                 // 1.00 % red → flagged
  const capRedSmall = png(100, 100, white, red, 7);              // 0.49 % red → under the bar
  const capGreen = png(100, 100, white, green, 10);              // correct render
  const capAlmostRed = png(100, 100, white, [200, 79, 79], 10);  // r=200: NOT strict red
  const tests = {
    // test key                     ref        web         ios         android      wptPass per platform
    't-pass-red':      { ref: refGreen, caps: [capRed1, capRed1, capGreen], pass: [true, true, true] },
    't-fail-red':      { ref: refGreen, caps: [capRed1, capGreen, capRed1], pass: [false, true, false] },
    't-small-red':     { ref: refGreen, caps: [capRedSmall, capRedSmall, capRedSmall], pass: [true, true, true] },
    't-ref-is-red':    { ref: refRed, caps: [capRed1, capRed1, capRed1], pass: [true, true, true] },
    't-almost-red':    { ref: refGreen, caps: [capAlmostRed, capAlmostRed, capAlmostRed], pass: [true, true, true] },
  };
  const results = {};
  for (const [stem, t] of Object.entries(tests)) {
    const testRel = `css/css-fake/${stem}.html`;
    writeFileSync(join(refs, `${stem}.png`), t.ref);
    const diffs = {};
    ['web', 'ios', 'android'].forEach((plat, i) => {
      writeFileSync(join(sec, `${plat}-screenshots`.replace('web-', ''), captureName(testRel)), t.caps[i]);
      diffs[`${plat}-ref`] = { ssim: 0.99, wptPass: t.pass[i] };
    });
    results[testRel] = { browserRef: { available: true, path: `refs/css-fake/${stem}.png`, diffs } };
  }
  // One extra test with a score-EXCLUDED red cell and an error-shaped cell: neither may count.
  const exclRel = 'css/css-fake/t-excluded.html';
  writeFileSync(join(refs, 't-excluded.png'), refGreen);
  writeFileSync(join(sec, 'screenshots', captureName(exclRel)), capRed1);
  writeFileSync(join(sec, 'ios-screenshots', captureName(exclRel)), capRed1);
  results[exclRel] = { browserRef: { available: true, path: 'refs/css-fake/t-excluded.png',
    diffs: { 'web-ref': { ssim: 0.99, wptPass: true, scoreExcluded: true }, 'ios-ref': { error: 'decode' } } } };
  writeFileSync(join(sec, 'manifest.json'), JSON.stringify({ manifestVersion: 4, wpt: { results } }));
  return { root, runDir };
}

test('synthetic run: flagged counts are exactly the constructed ones (and the predicate can fail)', () => {
  const { root, runDir } = buildSyntheticRun();
  try {
    const { cells, summary } = censusRun(runDir, { repoRoot: root });
    // 5 tests × 3 platforms scored; the excluded/error cells are not cells at all.
    assert.equal(summary.scoredCells, 15, 'scored denominator = the scorer idiom');
    // PASSING red: t-pass-red web+ios (2). FAILING red: t-fail-red web+android (2).
    assert.deepEqual(summary.passing, { total: 2, web: 1, ios: 1, android: 0, tests: 1 });
    assert.deepEqual(summary.failing, { total: 2, web: 1, ios: 0, android: 1, tests: 1 });
    // Negatives, each by name: under the bar; ref itself red; r=200 not strict red.
    for (const stem of ['t-small-red', 't-ref-is-red', 't-almost-red']) {
      const flagged = cells.filter((c) => c.test.includes(stem) && c.flagged);
      assert.equal(flagged.length, 0, `${stem} must not be flagged`);
    }
    assert.equal(cells.some((c) => c.test.includes('t-excluded')), false, 'excluded/error cells never enter the census');
    // MUTATION PROOFS — each alternative predicate moves a count, so a silent
    // edit to RED_SQUARE_PREDICATE cannot pass this test:
    //   (a) loosen the bar to 0.4 % → t-small-red's three cells join PASSING;
    const loose = censusRun(runDir, { repoRoot: root, predicate: { ...RED_SQUARE_PREDICATE, capMinPct: 0.4 } });
    assert.equal(loose.summary.passing.total, 5, 'capMinPct 0.4 must admit the 0.49 % cells');
    //   (b) loosen red to r>=200 → t-almost-red's three cells join PASSING;
    const loosered = censusRun(runDir, { repoRoot: root, predicate: { ...RED_SQUARE_PREDICATE, isRed: (r, g, b) => r >= 200 && g < 80 && b < 80 } });
    assert.equal(loosered.summary.passing.total, 5, 'r>=200 must admit the (200,79,79) cells');
    //   (c) allow a red ref → t-ref-is-red's three cells join PASSING.
    const refok = censusRun(runDir, { repoRoot: root, predicate: { ...RED_SQUARE_PREDICATE, refMaxPct: 100 } });
    assert.equal(refok.summary.passing.total, 5, 'refMaxPct 100 must admit the red-ref cells');
  } finally {
    rmSync(root, { recursive: true, force: true });
  }
});

test('summarize counts distinct tests, not cells', () => {
  const cells = [
    { test: 'a', platform: 'web', wptPass: true, flagged: true },
    { test: 'a', platform: 'ios', wptPass: true, flagged: true },
    { test: 'b', platform: 'android', wptPass: false, flagged: true },
    { test: 'c', platform: 'web', wptPass: true, flagged: false },
  ];
  assert.deepEqual(summarize(cells), {
    scoredCells: 4,
    passing: { total: 2, web: 1, ios: 1, android: 0, tests: 1 },
    failing: { total: 1, web: 0, ios: 0, android: 1, tests: 1 },
  });
});

// ── 3. the verbatim wave49-final artifact (local-only) ──────────────────────

const WAVE49 = resolve(here, 'runs', 'wave49-final');

test('wave49-final: the canonical hit-test-unrelated-element cell is red-passing on all three platforms', (t) => {
  if (!existsSync(join(WAVE49, 'sections', 'css-view-transitions', 'manifest.json'))) {
    t.skip('wave49-final run artifacts not present on this machine'); return;
  }
  // One section only (fast); the full census is behind TITAN_CENSUS_FULL below.
  const { cells } = censusRun(WAVE49, { sections: ['css-view-transitions'] });
  const canon = cells.filter((c) => c.test === 'css/css-view-transitions/hit-test-unrelated-element.html');
  assert.equal(canon.length, 3, 'three scored platforms');
  for (const c of canon) {
    assert.equal(c.flagged, true, `${c.platform} paints red where the ref has none`);
    assert.equal(c.wptPass, true, `${c.platform} is recorded as a PASS — the wave-49 headline`);
    assert.equal(c.refRedPct, 0);
    assert.ok(c.capRedPct > 4, `capture red ${c.capRedPct}% (measured 4.27 %)`);
  }
});

test('wave49-final FULL census reproduces the banner: 179 passing (23/66/90, 105 tests), 87 failing (15/35/37, 51 tests)', (t) => {
  // 15–50 s of PNG decoding over 4111 cells (measured 14 s on an idle host
  // 2026-09-04, 52 s alongside concurrent lanes 2026-09-05) — opt-in so the
  // tooling sweep stays fast. Run with
  // TITAN_CENSUS_FULL=1 node --test tools/titan/red-square-census.test.mjs
  if (process.env.TITAN_CENSUS_FULL !== '1') { t.skip('set TITAN_CENSUS_FULL=1 to run the full-corpus census pin'); return; }
  if (!existsSync(join(WAVE49, 'sections'))) { t.skip('wave49-final run artifacts not present on this machine'); return; }
  const { summary } = censusRun(WAVE49);
  assert.equal(summary.scoredCells, 4111, 'the wave-49 scored denominator');
  assert.deepEqual(summary.passing, { total: 179, web: 23, ios: 66, android: 90, tests: 105 });
  assert.deepEqual(summary.failing, { total: 87, web: 15, ios: 35, android: 37, tests: 51 });
});
