#!/usr/bin/env node
//
// Unit tests for tools/titan/capture-browser-ref.mjs.
//
// We test the pure helpers (path resolution + cache key) here. The
// puppeteer-driven render path is exercised by the orchestrator's smoke
// run rather than per-PR unit tests so node --test stays headless.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { sep } from 'node:path';

import {
  cachePathFor,
  resolveRefPath,
  CANVAS_BG,
  CANVAS_REV,
} from './capture-browser-ref.mjs';

// ── the corpus-v4 white-canvas contract ─────────────────────────────────────
//
// The ref canvas flipped from the dark #1A1A2E stage to WHITE at the
// corpus-v4 boundary (white WPT ink must vanish into the canvas on BOTH
// sides of the diff — see the module header). These pins hold the contract
// so a canvas change can never ship without (a) being deliberate and (b)
// bumping the cache revision segment that keeps stale refs out of diffs.

test('CANVAS_BG is the corpus-v4 white canvas', () => {
  assert.equal(CANVAS_BG, '#FFFFFF');
});

test('CANVAS_REV names the corpus-v4.1 black-ink+font+line-height cache revision', () => {
  // run-titan.sh + section-runner.sh hardcode
  // ".../refs/$WPT_REF/white-black-ink-font-lh" in their --refs-root; this
  // pin keeps the .mjs segment in lock-step. 'white-black-ink-font-lh' is
  // the full corpus-v4.1 sub-boundary (white canvas + spec-BLACK injected
  // default ink + the harness Inter font stack + the deterministic
  // REF_LINE_HEIGHT line-height pin on the ref); the line-height-less
  // black-ink scratch refs stay behind at refs/<sha>/white-black-ink-font/
  // and the v4.0 white-ink refs at refs/<sha>/white/.
  assert.equal(CANVAS_REV, 'white-black-ink-font-lh');
});

// ── cachePathFor ────────────────────────────────────────────────────────────

test('cachePathFor produces canvas-rev/section/stem layout under WPT SHA', () => {
  const p = cachePathFor('abc123', 'css/css-color/a98rgb-001.html');
  // Use sep so the assertion holds on Windows too even though dev is macOS.
  // The CANVAS_REV segment sits between the SHA and the section so the
  // pre-v4 dark refs (refs/<sha>/<section>/) are never picked up.
  assert.match(p, new RegExp(`tools\\${sep}wpt\\${sep}refs\\${sep}abc123\\${sep}white-black-ink-font-lh\\${sep}css-color\\${sep}a98rgb-001\\.png$`));
});

test('cachePathFor preserves SHA verbatim (no truncation)', () => {
  const sha = '9b5435e55e0b54a6cd09c1c563861eb3c999cef1';
  const p = cachePathFor(sha, 'css/css-grid/grid-001.html');
  assert.ok(p.includes(sha), `expected SHA in path; got ${p}`);
});

test('cachePathFor encodes subdirs into the stem for nested test paths', () => {
  // Some WPT tests live one level deeper, e.g. css/css-flexbox/abspos/foo.html
  // — section is still the second segment ("css-flexbox"), and the stem is
  // the SUBDIR-ENCODED fixtureStem (`abspos__foo`), NOT the bare basename.
  // wave-21 collision fix: with a bare basename, two nested tests with equal
  // basenames (css-break/flexbox vs css-break/grid monolithic-overflow
  // family) shared ONE cache slot — the first render's PNG silently served
  // as the second test's reference.
  const p = cachePathFor('sha', 'css/css-flexbox/abspos/abspos-autopos-htb-ltr.html');
  assert.match(p, new RegExp(`refs\\${sep}sha\\${sep}white-black-ink-font-lh\\${sep}css-flexbox\\${sep}abspos__abspos-autopos-htb-ltr\\.png$`));
});

test('cachePathFor gives colliding-basename nested tests DISTINCT cache slots', () => {
  // The exact wave-21 skeptic pair: same section, same basename, different
  // subdir — these used to flatten to one identical cache path.
  const a = cachePathFor('sha', 'css/css-break/flexbox/monolithic-overflow-001.tentative.html');
  const b = cachePathFor('sha', 'css/css-break/grid/monolithic-overflow-001.tentative.html');
  assert.notEqual(a, b, 'nested tests with equal basenames must not share a ref cache slot');
  assert.match(a, /flexbox__monolithic-overflow-001\.tentative\.png$/);
  assert.match(b, /grid__monolithic-overflow-001\.tentative\.png$/);
});

test('cachePathFor for tests directly under /css/ falls back to "css" section', () => {
  // Defensive: very few tests live directly under /css/, but the spec
  // section helper in the bucketer hands them "css".
  const p = cachePathFor('sha', 'css/orphan.html');
  assert.match(p, /refs.sha.white-black-ink-font-lh.css.orphan\.png$/);
});

// ── resolveRefPath ──────────────────────────────────────────────────────────
//
// Hits the filesystem (reads tools/wpt/css/css-color/a98rgb-001.html). We
// only run it when the corpus is present; in a fresh worktree with no WPT
// checkout we silently skip rather than failing.

import { existsSync } from 'node:fs';
import { fileURLToPath } from 'node:url';
import { dirname, join, resolve } from 'node:path';

const __dirname  = dirname(fileURLToPath(import.meta.url));
const REPO_ROOT  = resolve(__dirname, '..', '..');
const SAMPLE_TEST = 'css/css-color/a98rgb-001.html';
const SAMPLE_TEST_ABS = join(REPO_ROOT, 'tools', 'wpt', SAMPLE_TEST);

test('resolveRefPath returns the absolute ref path for a real test', { skip: !existsSync(SAMPLE_TEST_ABS) }, async () => {
  const ref = await resolveRefPath(SAMPLE_TEST);
  assert.ok(ref.endsWith('greensquare-ref.html'), `unexpected ref path: ${ref}`);
  assert.ok(ref.startsWith('/'), 'ref path should be absolute');
});

// ── wave-21 6b: mismatch-only reftests are an explicit skip ─────────────────
//
// css/css-text-decor/text-combine-emphasis.html carries ONLY a
// rel="mismatch" link (pass = "does NOT equal the ref"), which a
// match-asserting capture cannot represent. resolveRefPath must throw an
// error carrying the machine-readable `skipReason` marker so captureRefs
// reports SKIP (ok:true, skipped:true) instead of FAIL — the wave-21 gate
// logged this exact test as `FAIL … no rel="match" link`, which then read
// as capture breakage rather than a structural skip.

const MISMATCH_ONLY_TEST = 'css/css-text-decor/text-combine-emphasis.html';
const MISMATCH_ONLY_ABS = join(REPO_ROOT, 'tools', 'wpt', MISMATCH_ONLY_TEST);

test('resolveRefPath marks mismatch-only reftests with skipReason', { skip: !existsSync(MISMATCH_ONLY_ABS) }, async () => {
  await assert.rejects(
    resolveRefPath(MISMATCH_ONLY_TEST),
    (err) => {
      // The marker drives the SKIP branch in captureRefs.
      assert.equal(err.skipReason, 'mismatch-only-reftest');
      // Message must say WHY, for the humans reading browser-ref.log.
      assert.match(err.message, /mismatch-only reftest/);
      return true;
    },
  );
});

test('resolveRefPath keeps the plain error for tests with NO ref link at all', { skip: !existsSync(SAMPLE_TEST_ABS) }, async () => {
  // A ref page itself has neither rel="match" nor rel="mismatch" — the
  // error must NOT carry skipReason (that would silently skip real input
  // mistakes). a98rgb-001's own ref file is a handy no-link fixture.
  const refAbs = await resolveRefPath(SAMPLE_TEST);
  const refRel = refAbs.slice(join(REPO_ROOT, 'tools', 'wpt').length + 1);
  await assert.rejects(
    resolveRefPath(refRel),
    (err) => {
      assert.equal(err.skipReason, undefined);
      assert.match(err.message, /no rel="match" link/);
      return true;
    },
  );
});
