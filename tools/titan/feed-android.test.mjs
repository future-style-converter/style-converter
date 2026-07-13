#!/usr/bin/env node
//
// Unit tests for the Android TITAN feeder's pure helpers (tools/titan/feed-lib.mjs)
// plus source-scan pins on feed-android.mjs. Run with:
//   node --test tools/titan/feed-android.test.mjs
//
// The expected-PNG-name derivation is the highest-risk piece: if it drifts from
// the on-device flatten+naming the feeder waits for filenames the app never
// writes and every fixture times out. These tests lock it to concrete IR docs
// shaped exactly like the converter's `--to ir` output.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import { PNG } from 'pngjs';

import {
  parseArgs, safeName, expectedPngNames, composedPngName,
  parentCreatesContext, composeRoots, flattenComponents, pngIsValid,
} from './feed-lib.mjs';

// ── parseArgs ────────────────────────────────────────────────────────────────

test('parseArgs reads all flags', () => {
  const o = parseArgs(['--fixtures', 'a.json,b.json', '--out', '/tmp/out',
    '--timeout-per-fixture', '12', '--udid', 'emulator-5554', '--skip-install']);
  assert.equal(o.fixtures, 'a.json,b.json');
  assert.equal(o.out, '/tmp/out');
  assert.equal(o.timeoutPerFixture, 12);
  assert.equal(o.udid, 'emulator-5554');
  assert.equal(o.skipInstall, true);
});

test('parseArgs defaults timeout to 30 and skipInstall false', () => {
  const o = parseArgs(['--fixtures', 'a.json', '--out', 'x']);
  assert.equal(o.timeoutPerFixture, 30);
  assert.equal(o.skipInstall, false);
  assert.equal(o.udid, null);
  assert.equal(o.composed, false); // per-component by default
});

test('parseArgs reads the --composed sub-flag', () => {
  assert.equal(parseArgs(['--fixtures', 'a.json', '--out', 'x', '--composed']).composed, true);
});

// ── composedPngName: one PNG per test named for the WPT key ───────────────────

test('composedPngName strips the .json (and any feeder index prefix) → <key>.png', () => {
  // Host-side basename (no prefix) → key + .png.
  assert.equal(
    composedPngName('wpt__css-color__background-color-hsl-001.json'),
    'wpt__css-color__background-color-hsl-001.png',
  );
  // The feeder's on-device `<NNNN>-` prefix (should the caller pass it) is dropped.
  assert.equal(
    composedPngName('0007-wpt__css-backgrounds__background-334.json'),
    'wpt__css-backgrounds__background-334.png',
  );
  // Matches inject's safe() class exactly (dot kept; slash/space/colon → _).
  assert.equal(composedPngName('a.b/c d:e.json'), 'a.b_c_d_e.png');
});

test('parseArgs rejects a non-positive / NaN timeout (watchdog must stay armed)', () => {
  assert.equal(parseArgs(['--timeout-per-fixture', '0']).timeoutPerFixture, 30);
  assert.equal(parseArgs(['--timeout-per-fixture', 'abc']).timeoutPerFixture, 30);
  assert.equal(parseArgs(['--timeout-per-fixture', '-5']).timeoutPerFixture, 30);
});

// ── safeName ─────────────────────────────────────────────────────────────────

test('safeName matches the Kotlin char class [^a-zA-Z0-9_-]', () => {
  assert.equal(safeName('attachment-local__0'), 'attachment-local__0'); // - and _ kept
  assert.equal(safeName('a b/c.d:e'), 'a_b_c_d_e');
});

// ── expectedPngNames: flat v2 (no slots), like css-color output ───────────────

test('flat v2 document → one PNG per component in order', () => {
  const doc = { irVersion: 2, components: [
    { id: 'x-1', name: 'bg__0', properties: [] },
    { id: 'x-2', name: 'bg__1', properties: [] },
  ] };
  assert.deepEqual(expectedPngNames(doc), ['000_bg__0.png', '001_bg__1.png']);
});

// ── expectedPngNames: v2 slots with a clipping parent (suppression) ───────────

test('slot-composed tree with overflow:hidden parent suppresses child captures', () => {
  // Shaped like the converted attachment-local-clipping-color-1 IR: a root with
  // OverflowX/Y=HIDDEN and two nested descendants via slot.parent. The paint
  // context suppresses the standalone child captures → only the root PNG.
  const doc = { irVersion: 2, components: [
    { id: 'r-1', name: 'clip__0', properties: [
      { type: 'OverflowX', data: 'HIDDEN' }, { type: 'OverflowY', data: 'HIDDEN' } ] },
    { id: 'c-2', name: 'clip__0__0', properties: [], slot: { parent: 'r-1' } },
    { id: 'g-3', name: 'clip__0__0__0', properties: [], slot: { parent: 'c-2' } },
  ] };
  assert.deepEqual(expectedPngNames(doc), ['000_clip__0.png']);
});

test('slot-composed tree WITHOUT a paint context keeps parent + children', () => {
  const doc = { irVersion: 2, components: [
    { id: 'r-1', name: 'box__0', properties: [] },
    { id: 'c-2', name: 'box__0__0', properties: [], slot: { parent: 'r-1' } },
  ] };
  // Pre-order: parent (000) then child (001).
  assert.deepEqual(expectedPngNames(doc), ['000_box__0.png', '001_box__0__0.png']);
});

test('v1 nested document (children arrays, no slots) flattens pre-order', () => {
  const doc = { components: [
    { id: 'r', name: 'root', properties: [], children: [
      { id: 'k', name: 'kid', properties: [] } ] },
  ] };
  assert.deepEqual(expectedPngNames(doc), ['000_root.png', '001_kid.png']);
});

// ── parentCreatesContext ──────────────────────────────────────────────────────

test('parentCreatesContext fires on the documented property set', () => {
  assert.equal(parentCreatesContext({ properties: [{ type: 'OverflowX', data: 'HIDDEN' }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Overflow', data: { value: 'clip' } }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Transform', data: [{ fn: 'scale' }] }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Opacity', data: 0.5 }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Filter', data: {} }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'MixBlendMode', data: 'multiply' }] }), true);
});

test('parentCreatesContext stays false for inert values', () => {
  assert.equal(parentCreatesContext({ properties: [] }), false);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Overflow', data: 'VISIBLE' }] }), false);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Transform', data: [] }] }), false);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Opacity', data: 1.0 }] }), false);
  assert.equal(parentCreatesContext({ properties: [{ type: 'MixBlendMode', data: 'normal' }] }), false);
});

// ── composeRoots / flattenComponents wiring ───────────────────────────────────

test('composeRoots promotes a dangling slot.parent to a root', () => {
  const doc = { components: [
    { id: 'a', name: 'A', properties: [] },
    { id: 'b', name: 'B', properties: [], slot: { parent: 'ghost' } },
  ] };
  const roots = composeRoots(doc);
  assert.equal(roots.length, 2); // both are roots (b's parent doesn't exist)
  assert.equal(flattenComponents(roots).length, 2);
});

// ── pngIsValid ────────────────────────────────────────────────────────────────

test('pngIsValid accepts a real PNG and rejects truncated/garbage buffers', () => {
  const png = new PNG({ width: 2, height: 2 });
  png.data.fill(255);
  const buf = PNG.sync.write(png);
  assert.equal(pngIsValid(buf), true);            // full PNG
  assert.equal(pngIsValid(buf.subarray(0, 20)), false); // truncated (sig ok, body cut)
  assert.equal(pngIsValid(Buffer.from('not a png')), false);
  assert.equal(pngIsValid(Buffer.alloc(0)), false);
});

// ── Source-scan pins on feed-android.mjs (the platform driver) ────────────────

test('feed-android.mjs is wired to the shared lib and device contract', async () => {
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  assert.match(src, /from '\.\/feed-lib\.mjs'/, 'must reuse the tested pure helpers');
  assert.match(src, /--ez', 'titanInbox', 'true'/, 'must launch in inbox mode');
  assert.match(src, /files\/inbox/, 'must push to the on-device inbox path');
  assert.match(src, /no Android device\/emulator attached/, 'must fail loudly with no device');
});
