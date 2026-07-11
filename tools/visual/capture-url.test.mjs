// tools/visual/capture-url.test.mjs
//
// Unit tests for apps/web-harness/capture-url.mjs — the URL builder used by
// capture-screenshots.mjs to wire the `WPT_MODE=1` env var through to the
// React app's `?wpt=1` query parameter. Pins the contract that:
//
//   1. Legacy flow (no WPT_MODE) gets the byte-identical legacy URL — any
//      drift here invalidates every committed visual-test baseline PNG.
//   2. WPT flow (WPT_MODE=1) gets `&wpt=1` appended in the right shape so
//      ComponentRenderer's `WPT_MODE` constant resolves to true and the
//      placeholder text overlay is suppressed.
//
// Why a separate test file (and a separate helper module)? The main
// capture-screenshots.mjs script does top-level `await puppeteer.launch()`
// — importing it from a unit test would launch Chromium and start
// screenshotting whatever vite happens to be on :3000. Splitting the URL
// builder into capture-url.mjs lets us pin its contract for ~3 ms instead
// of ~30 s + a Chromium dependency.
//
// Lives in tools/visual/ root (not apps/web-harness/) so smoke.sh's
// `node --test tools/visual/*.test.mjs tools/titan/*.test.mjs` glob picks it
// up alongside the other adapter tests. doc-staleness-check.sh's "live
// test count" extracted from the same glob will include these tests.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { buildCaptureUrl } from '../../apps/web-harness/capture-url.mjs';

// ── Legacy contract — unchanged from pre-fix ───────────────────────────────

test('buildCaptureUrl: legacy flow (wptMode=false) produces ?mode=capture only', () => {
  // Exact string match — this is what every committed baseline PNG was
  // captured against. A regression here means "next test-all.sh run will
  // miss the cache and rebuild every baseline".
  assert.equal(
    buildCaptureUrl('http://localhost:3000', false),
    'http://localhost:3000/?mode=capture'
  );
});

test('buildCaptureUrl: legacy flow strips trailing slash from baseUrl', () => {
  // Mirrors the existing `baseUrl.replace(/\/$/, '')` behaviour — capture
  // log line stays canonical regardless of how the caller formatted the
  // vite origin (with or without trailing slash).
  assert.equal(
    buildCaptureUrl('http://localhost:3000/', false),
    'http://localhost:3000/?mode=capture'
  );
});

test('buildCaptureUrl: legacy flow tolerates IPv4-style port-suffixed origin', () => {
  // section-runner.sh deterministic-port path can produce ports outside the
  // default 3000 range (3100..3299). The builder must not be coupled to a
  // single hard-coded port.
  assert.equal(
    buildCaptureUrl('http://localhost:3257', false),
    'http://localhost:3257/?mode=capture'
  );
});

// ── WPT contract — the new behaviour ───────────────────────────────────────

test('buildCaptureUrl: WPT mode appends &wpt=1 after mode=capture', () => {
  // Order matters for byte-identity with future log greps, even though
  // URLSearchParams parses either order. Keep mode=capture first.
  assert.equal(
    buildCaptureUrl('http://localhost:3257', true),
    'http://localhost:3257/?mode=capture&wpt=1'
  );
});

test('buildCaptureUrl: WPT mode also strips trailing slash', () => {
  // Same trailing-slash hygiene as the legacy path — section-runner.sh
  // builds the URL via `http://localhost:$PORT` so this never fires in
  // practice, but the helper should be predictable for any caller.
  assert.equal(
    buildCaptureUrl('http://localhost:3257/', true),
    'http://localhost:3257/?mode=capture&wpt=1'
  );
});

test('buildCaptureUrl: WPT mode toggle is the ONLY difference vs legacy', () => {
  // Cross-check: legacy + appending `&wpt=1` literally equals WPT mode.
  // Catches a future "let's also tack on &foo=bar in WPT mode" drift that
  // would diverge from what ComponentRenderer's URLSearchParams check
  // looks for.
  const base = 'http://localhost:3000';
  assert.equal(
    buildCaptureUrl(base, false) + '&wpt=1',
    buildCaptureUrl(base, true)
  );
});

// ── Dynamic-styling hooks (wave 7 — docs/DYNAMIC_CAPTURE.md) ───────────────

test('buildCaptureUrl: default-width opts leave the legacy URL byte-identical', () => {
  // CAPTURE_WIDTH defaults to 390 and capture-screenshots.mjs always passes
  // it through opts — the 390 guard must keep the default run's URL exactly
  // the committed-baseline string.
  assert.equal(
    buildCaptureUrl('http://localhost:3000', false, { width: 390 }),
    'http://localhost:3000/?mode=capture'
  );
});

test('buildCaptureUrl: width override appends &width=<px>', () => {
  // The 250px second run of the two-width media recipe.
  assert.equal(
    buildCaptureUrl('http://localhost:3000', false, { width: 250 }),
    'http://localhost:3000/?mode=capture&width=250'
  );
});

test('buildCaptureUrl: forceState appends &forceState=<state>', () => {
  // One forced state per capture run (spec 06 §6).
  assert.equal(
    buildCaptureUrl('http://localhost:3000', false, { forceState: 'active' }),
    'http://localhost:3000/?mode=capture&forceState=active'
  );
});

test('buildCaptureUrl: hooks compose in fixed order (wpt, width, forceState)', () => {
  // Deterministic param order keeps log greps and artifact matching stable.
  assert.equal(
    buildCaptureUrl('http://localhost:3000', true, { width: 250, forceState: 'hover' }),
    'http://localhost:3000/?mode=capture&wpt=1&width=250&forceState=hover'
  );
});
