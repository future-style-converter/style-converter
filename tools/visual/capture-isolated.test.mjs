// tools/visual/capture-isolated.test.mjs
//
// Unit tests for apps/web-harness/capture-isolated.mjs — the B-RC3
// (wave 21) isolated composed-capture path. Real Chromium is impractical
// under `node --test` (the point of splitting the module out of the
// top-level-await driver), so a MOCK puppeteer browser/page records the
// exact protocol the module drives and scripts each page response. What
// these tests pin:
//
//   1. The isolated page replicates the batch page's environment pins
//      (viewport width, explicit prefers-color-scheme emulation) and
//      navigates to the composed URL + `&only=<encoded key>`.
//   2. The PNG lands at `<safe(testKey)>.png` in outDir — the exact slot
//      the batch path would have written, so inject-wpt-block.mjs globs it
//      with zero knowledge of which path produced it.
//   3. Every no-silent-fallthrough contract THROWS: sentinel/canvas count
//      ≠ 1 (`?only=` ignored or matched nothing), wrong canvas identity,
//      missing __seizeAnimations hook under a pinned animation clock.
//   4. The page is closed on success AND on every failure path (no page
//      leak across a section's many flagged tests).
//
// The one REAL end-to-end isolated capture (live vite + headless Chromium)
// is exercised by the driver run documented in the wave-21 lane report —
// this file covers the branches that run can't reach (failure modes).
//
// Lives in tools/visual/ so CI's `node --test tools/visual/*.test.mjs`
// glob picks it up (same placement rationale as capture-url.test.mjs).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { resolve } from 'node:path';

import { captureIsolatedTest } from '../../apps/web-harness/capture-isolated.mjs';

/**
 * Build a mock puppeteer page + browser. `script` customises the page's
 * responses; `calls` records everything the module drove so assertions can
 * replay the protocol.
 *
 * evaluate() is queue-scripted (returns script.evaluateReturns in call
 * order, then undefined) because the module calls it for several distinct
 * purposes — sentinel read, fonts.ready, settle, optional seize — and the
 * mock must not execute the passed page-functions (they reference
 * browser-only globals like `document`).
 */
function makeMock(script = {}) {
  const calls = { viewport: null, media: null, goto: [], screenshots: [], closed: 0 };
  const evalQueue = [...(script.evaluateReturns ?? [[1, 1]])];
  const page = {
    // Environment pins — recorded for the parity assertions.
    setViewport: async (v) => { calls.viewport = v; },
    emulateMediaFeatures: async (m) => { calls.media = m; },
    // pageerror listener registration — inert in the mock.
    on: () => {},
    goto: async (url) => { calls.goto.push(url); },
    waitForSelector: async () => {},
    evaluate: async () => (evalQueue.length ? evalQueue.shift() : undefined),
    // Marker check for the seize contract (true unless a test overrides).
    $$eval: async () => script.marked ?? true,
    // Canvas identity read — defaults to echoing the requested key.
    $eval: async () => script.canvasName ?? script.testKey,
    // The single canvas handle whose screenshot() writes the PNG.
    $: async () => ({ screenshot: async ({ path }) => { calls.screenshots.push(path); } }),
    close: async () => { calls.closed += 1; },
  };
  const browser = { newPage: async () => page };
  return { browser, calls };
}

// Shared happy-path options — the shape the driver passes.
const KEY = 'wpt__css-multicol__abspos-containing-block-outside-spanner';
const baseOpts = {
  baseUrl: 'http://localhost:3257', outDir: '/tmp/shots', testKey: KEY,
  wptMode: true, captureWidth: 390, forceState: undefined,
  animationTime: undefined, captureDark: false,
};

test('isolated capture: drives the &only= URL and writes <safe(key)>.png', async () => {
  const { browser, calls } = makeMock({ testKey: KEY });
  const file = await captureIsolatedTest({ browser, ...baseOpts });
  // One navigation, to the composed capture URL with the key appended LAST.
  assert.equal(calls.goto.length, 1);
  assert.equal(
    calls.goto[0],
    `http://localhost:3257/?mode=capture&wpt=1&wptComposed=1&only=${KEY}`
  );
  // The PNG overwrites the exact batch-path slot (safe() is identity for
  // this key — it is already [a-z0-9_-]).
  assert.equal(file, resolve('/tmp/shots', `${KEY}.png`));
  assert.deepEqual(calls.screenshots, [file]);
  // Environment parity with the batch page: 1x viewport at captureWidth,
  // light scheme pinned explicitly (never inherited from the host OS).
  assert.deepEqual(calls.viewport, { width: 390, height: 844, deviceScaleFactor: 1 });
  assert.deepEqual(calls.media, [{ name: 'prefers-color-scheme', value: 'light' }]);
  // Page released.
  assert.equal(calls.closed, 1);
});

test('isolated capture: CAPTURE_DARK pins the dark scheme on the fresh page', async () => {
  // A dark-run isolated capture must not silently revert to light — that
  // would diff a light frame against dark batch neighbors.
  const { browser, calls } = makeMock({ testKey: KEY });
  await captureIsolatedTest({ browser, ...baseOpts, captureDark: true });
  assert.deepEqual(calls.media, [{ name: 'prefers-color-scheme', value: 'dark' }]);
});

test('isolated capture: sanitises the filename through safe()', async () => {
  // A hypothetical key with a filesystem-hostile character must land at the
  // same sanitised name the batch path (and inject's glob) uses.
  const odd = 'wpt__x__a b';
  const { browser, calls } = makeMock({ testKey: odd, canvasName: odd });
  const file = await captureIsolatedTest({ browser, ...baseOpts, testKey: odd });
  assert.equal(file, resolve('/tmp/shots', 'wpt__x__a_b.png'));
  assert.deepEqual(calls.screenshots, [file]);
});

test('isolated capture: throws when the page ignored ?only= (N canvases)', async () => {
  // Sentinel/live count of 2 = the app mounted the FULL gallery (stale
  // bundle without the filter) — re-capturing it would reproduce the very
  // divergent frame this path exists to avoid. Must throw, and must still
  // close the page.
  const { browser, calls } = makeMock({ evaluateReturns: [[2, 2]] });
  await assert.rejects(
    () => captureIsolatedTest({ browser, ...baseOpts }),
    /only= filter not honored/
  );
  assert.equal(calls.closed, 1);
  assert.deepEqual(calls.screenshots, []);
});

test('isolated capture: throws when the key matched no test (0 canvases)', async () => {
  // Sentinel 0 = driver/page test-key drift (should be impossible while
  // both sides run splitCombinedIr's algorithm) — loud, never a blank PNG.
  const { browser, calls } = makeMock({ evaluateReturns: [[0, 0]] });
  await assert.rejects(
    () => captureIsolatedTest({ browser, ...baseOpts }),
    /expected exactly 1 canvas/
  );
  assert.equal(calls.closed, 1);
});

test('isolated capture: throws when the mounted canvas is a DIFFERENT test', async () => {
  // A filter bug mounting the wrong single test would overwrite this
  // test's PNG with another test's pixels — undetectable downstream, so it
  // must die here.
  const { browser, calls } = makeMock({ canvasName: 'wpt__css-multicol__some-other-test' });
  await assert.rejects(
    () => captureIsolatedTest({ browser, ...baseOpts }),
    /mounted canvas "wpt__css-multicol__some-other-test"/
  );
  assert.equal(calls.closed, 1);
  assert.deepEqual(calls.screenshots, []);
});

test('isolated capture: pinned animation clock without a seize hook throws', async () => {
  // Same contract as the batch page: CAPTURE_ANIMATION_TIME set but the
  // page exposes no __seizeAnimations ⇒ the frame would be a live-motion
  // frame masquerading as the t-state. evaluate order: sentinel, fonts,
  // settle, then seize (-1 = hook missing).
  const { browser, calls } = makeMock({
    testKey: KEY,
    evaluateReturns: [[1, 1], undefined, undefined, -1],
  });
  await assert.rejects(
    () => captureIsolatedTest({ browser, ...baseOpts, animationTime: 0.5 }),
    /__seizeAnimations/
  );
  assert.equal(calls.closed, 1);
});

test('isolated capture: pinned animation clock with a live hook captures', async () => {
  // Seize returns a pause count and the marker check passes ⇒ the capture
  // proceeds exactly like the un-animated happy path.
  const { browser, calls } = makeMock({
    testKey: KEY,
    evaluateReturns: [[1, 1], undefined, undefined, 3],
    marked: true,
  });
  const file = await captureIsolatedTest({ browser, ...baseOpts, animationTime: 0.5 });
  assert.deepEqual(calls.screenshots, [file]);
  // The URL carries the animationTime BEFORE only (fixed suffix order).
  assert.match(calls.goto[0], /&animationTime=0\.5&only=/);
});
