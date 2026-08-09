#!/usr/bin/env node
//
// Unit tests for the pure helpers of tools/titan/feed-ios.mjs.
//
// These cover the two things a refactor is most likely to break silently:
//   1. filename derivation (IR doc → expected per-component PNG names) — a
//      mismatch here makes the feeder poll for files the app never writes,
//      or write host names the compare pipeline can't glob.
//   2. arg parsing + device selection — the feeder must fail LOUDLY, never
//      boot a device, when no Booted simulator is present.
//
// Style mirrors the other tools/titan/*.test.mjs (node:test + strict assert).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { promises as fs } from 'node:fs';
import {
  safeName, deviceSafeName, pad3, parseArgs, pickBootedUdid,
  parentCreatesContext, composeComponents, flattenComponents, expectedCaptures,
  composedTestKey, composedPngName,
} from './feed-ios.mjs';

test('safeName mirrors the compare-pipeline sanitiser (safe() in inject-wpt-block)', () => {
  // Only [A-Za-z0-9._-] survive; everything else → underscore.
  assert.equal(safeName('attachment-local-clipping-color-1__0'),
                        'attachment-local-clipping-color-1__0');
  assert.equal(safeName('Foo Bar:baz/qux'), 'Foo_Bar_baz_qux');
  assert.equal(safeName('keep.dots-and_dashes'), 'keep.dots-and_dashes');
});

test('deviceSafeName mirrors the iOS ScreenshotManager.save rule (only / and space)', () => {
  assert.equal(deviceSafeName('a/b c'), 'a_b_c');
  // A colon is NOT sanitised on device — kept, so the host locates the file.
  assert.equal(deviceSafeName('a:b'), 'a:b');
});

test('pad3 matches the app %03d index prefix', () => {
  assert.equal(pad3(0), '000');
  assert.equal(pad3(7), '007');
  assert.equal(pad3(123), '123');
});

test('parseArgs reads every flag and defaults timeout (SECONDS, like feed-android)', () => {
  // --timeout-per-fixture is now SECONDS (unified with feed-lib/feed-android),
  // so the raw number is taken verbatim.
  const a = parseArgs(['--fixtures', 'ir/', '--out', 'shots/', '--udid', 'ABC', '--timeout-per-fixture', '9000']);
  assert.equal(a.fixtures, 'ir/');
  assert.equal(a.out, 'shots/');
  assert.equal(a.udid, 'ABC');
  assert.equal(a.timeoutPerFixture, 9000); // seconds
  assert.equal(a.noBuild, false);
  const d = parseArgs(['--fixtures', 'x', '--out', 'y', '--no-build']);
  assert.equal(d.timeoutPerFixture, 30); // default seconds — matches feed-android
  assert.equal(d.noBuild, true);
});

test('parseArgs rejects a non-positive / NaN timeout (watchdog must stay armed)', () => {
  // Same guard as feed-lib.parseArgs — a bad value falls back to the 30s default
  // so one malformed flag can never disable the per-fixture watchdog.
  assert.equal(parseArgs(['--fixtures', 'x', '--out', 'y', '--timeout-per-fixture', '0']).timeoutPerFixture, 30);
  assert.equal(parseArgs(['--fixtures', 'x', '--out', 'y', '--timeout-per-fixture', 'abc']).timeoutPerFixture, 30);
  assert.equal(parseArgs(['--fixtures', 'x', '--out', 'y', '--timeout-per-fixture', '-5']).timeoutPerFixture, 30);
});

test('pickBootedUdid returns a Booted device and prefers an iPhone', () => {
  const json = { devices: {
    'iOS 26.0': [
      { udid: 'IPAD-1', state: 'Booted', name: 'iPad Pro' },
      { udid: 'IPHONE-1', state: 'Booted', name: 'iPhone 17' },
      { udid: 'OFF-1', state: 'Shutdown', name: 'iPhone 15' },
    ],
  } };
  assert.equal(pickBootedUdid(json), 'IPHONE-1');
});

test('pickBootedUdid honours a valid requested udid', () => {
  const json = { devices: { rt: [{ udid: 'U1', state: 'Booted', name: 'iPhone 17' }] } };
  assert.equal(pickBootedUdid(json, 'U1'), 'U1');
});

test('pickBootedUdid throws loudly when nothing is Booted (never boots)', () => {
  const json = { devices: { rt: [{ udid: 'U1', state: 'Shutdown', name: 'iPhone 17' }] } };
  assert.throws(() => pickBootedUdid(json), /no Booted simulator/);
});

test('pickBootedUdid throws when the requested udid is not Booted', () => {
  const json = { devices: { rt: [{ udid: 'U1', state: 'Shutdown', name: 'iPhone 17' }] } };
  assert.throws(() => pickBootedUdid(json, 'U1'), /not Booted/);
  assert.throws(() => pickBootedUdid(json, 'MISSING'), /not in simctl/);
});

test('parentCreatesContext fires on clip/overflow/opacity/transform', () => {
  assert.equal(parentCreatesContext({ properties: [{ type: 'OverflowX', data: 'HIDDEN' }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Overflow', data: { value: 'clip' } }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Opacity', data: { alpha: 0.5 } }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Transform', data: [{ fn: 'scale' }] }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Filter', data: {} }] }), true);
  // Opacity 1 and no properties do NOT create a context.
  assert.equal(parentCreatesContext({ properties: [{ type: 'Opacity', data: 1 }] }), false);
  assert.equal(parentCreatesContext({ properties: [] }), false);
});

test('flattenComponents is identity for a flat v2 doc (children absent)', () => {
  const comps = [{ name: 'a', properties: [] }, { name: 'b', properties: [] }];
  assert.deepEqual(flattenComponents(comps).map((c) => c.name), ['a', 'b']);
});

test('flattenComponents suppresses children under a paint-context parent', () => {
  // Legacy-shape safety: a clipping parent hides its standalone child capture.
  const comps = [{
    name: 'root', properties: [{ type: 'Overflow', data: 'HIDDEN' }],
    children: [{ name: 'child', properties: [] }],
  }];
  assert.deepEqual(flattenComponents(comps).map((c) => c.name), ['root']);
  // Non-context parent keeps the child.
  const comps2 = [{
    name: 'root', properties: [],
    children: [{ name: 'child', properties: [] }],
  }];
  assert.deepEqual(flattenComponents(comps2).map((c) => c.name), ['root', 'child']);
});

test('composeComponents nests slot-children under their parent id', () => {
  // Mirrors IRComposer.compose: slot.parent references the parent's ID.
  const flat = [
    { id: 'r-1', name: 'root', properties: [], slot: null },
    { id: 'c-2', name: 'child', properties: [], slot: { parent: 'r-1' } },
  ];
  const roots = composeComponents(flat);
  assert.equal(roots.length, 1);
  assert.equal(roots[0].name, 'root');
  assert.equal(roots[0].children.length, 1);
  assert.equal(roots[0].children[0].name, 'child');
});

test('composeComponents: no-slot entries are all roots (identity forest)', () => {
  const flat = [
    { id: 'a-1', name: 'a', properties: [], slot: null },
    { id: 'b-2', name: 'b', properties: [], slot: null },
  ];
  assert.deepEqual(composeComponents(flat).map((c) => c.name), ['a', 'b']);
});

test('expectedCaptures composes THEN flattens (slot child under overflow parent → 1 capture)', () => {
  // The attachment-local-clipping-color regression: 3 wire entries, but the
  // root clips (overflow:hidden) so its slot-children are suppressed — the
  // app writes exactly ONE PNG, and the feeder must expect exactly one.
  const doc = { components: [
    { id: 'r-1', name: 'clip__0', properties: [{ type: 'OverflowX', data: 'HIDDEN' }], slot: null },
    { id: 'c-2', name: 'clip__0__0', properties: [], slot: { parent: 'r-1' } },
    { id: 'c-3', name: 'clip__0__0__0', properties: [], slot: { parent: 'c-2' } },
  ] };
  const exp = expectedCaptures(doc);
  assert.equal(exp.length, 1);
  assert.equal(exp[0].deviceFile, '000_clip__0.png');
});

test('expectedCaptures derives device + host filenames per component', () => {
  const doc = { components: [
    { name: 'attachment-local-clipping-color-1__0', properties: [] },
    { name: 'has space', properties: [] },
  ] };
  const exp = expectedCaptures(doc);
  assert.equal(exp.length, 2);
  assert.deepEqual(exp[0], {
    index: 0,
    name: 'attachment-local-clipping-color-1__0',
    deviceFile: '000_attachment-local-clipping-color-1__0.png',
    hostFile: '000_attachment-local-clipping-color-1__0.png',
  });
  // Space → underscore on BOTH device and host names; index advances.
  assert.equal(exp[1].deviceFile, '001_has_space.png');
  assert.equal(exp[1].hostFile, '001_has_space.png');
});

test('expectedCaptures host name always matches the compare glob suffix', () => {
  // inject-wpt-block globs endsWith(`_${safe(key)}.png`) — assert our host
  // filename ends exactly that way for a key with a compare-unsafe char.
  const doc = { components: [{ name: 'a:b', properties: [] }] };
  const [e] = expectedCaptures(doc);
  assert.ok(e.hostFile.endsWith(`_${safeName('a:b')}.png`));
});

// ── TITAN WPT Round 3 composed-mode helpers ──────────────────────────────────

test('composedTestKey strips .json from a per-test-ir fixture path', () => {
  // per-test-ir docs are named exactly `wpt__<section>__<stem>.json`; the
  // testKey is the basename minus the extension.
  assert.equal(
    composedTestKey('/runs/x/per-test-ir/wpt__css-color__background-color-hsl-001.json'),
    'wpt__css-color__background-color-hsl-001');
  // A bare filename works too (no directory).
  assert.equal(composedTestKey('wpt__css-borders__border-radius-greater-than-width.json'),
               'wpt__css-borders__border-radius-greater-than-width');
});

test('composedPngName is `<safe(testKey)>.png` — the diffComposedVsRef glob', () => {
  // A clean WPT key is already all-safe: identity + .png.
  assert.equal(composedPngName('wpt__css-color__background-color-hsl-001'),
               'wpt__css-color__background-color-hsl-001.png');
  // A key with compare-unsafe chars is sanitised the same way safe() /
  // ScreenshotManager.safeCaptureName do (parity across the three sites).
  assert.equal(composedPngName('wpt__x__a b:c'), 'wpt__x__a_b_c.png');
});

test('parseArgs reads the --composed flag (default false)', () => {
  assert.equal(parseArgs(['--fixtures', 'x', '--out', 'y']).composed, false);
  assert.equal(parseArgs(['--fixtures', 'x', '--out', 'y', '--composed']).composed, true);
});

test('a wedged capture restarts the app so it cannot cascade-timeout the batch', async () => {
  // Symmetric with feed-android: a fixture whose composed capture WEDGES hangs
  // the inbox poll loop, so without a terminate+relaunch every fixture behind
  // it cascade-timeouts on the still-hung app.
  const src = await fs.readFile(new URL('./feed-ios.mjs', import.meta.url), 'utf8');
  // The restart log + the relaunch must both live in the recovery block (the
  // relaunch follows the restart message — pinning the ordering makes this
  // recovery-specific, not a match against the identical startup launch above).
  assert.match(src, /restarting app to clear the wedge[\s\S]{0,400}'simctl', 'launch'/,
    'timeout recovery must relaunch the app after the restart log');
  assert.match(src, /n < fixtures\.length - 1/, 'last-fixture guard on the restart dropped');
});

// ── wave-39 lane A2: the replaced-element image hop (iOS half) ───────────────
//
// The PURE resolution rules are pinned once, in feed-android.test.mjs, because
// both feeders import the SAME feed-lib functions. What is iOS-specific — and
// therefore pinned here — is the sandbox's identity and the copy's position in
// the per-fixture sequence.

test('feed-ios copies images into a THIRD sibling sandbox, wiped on entry', async () => {
  const src = await fs.readFile(new URL('./feed-ios.mjs', import.meta.url), 'utf8');
  // Its own directory, not `fonts/` with a wider table: the two asset channels
  // have different lifetimes and different decline semantics in the runtimes'
  // heads, and must stay separately auditable (feed-lib.mjs's hop banner).
  assert.match(src, /const imagesDir = join\(container, 'Documents', 'images'\)/,
    'the images sandbox must be Documents/images');
  assert.notEqual(src.indexOf("join(container, 'Documents', 'fonts')"), -1,
    'the fonts sandbox must stay where it was');
  // Wiped on entry for the fonts dir's reason: a support image left from a
  // previous run lets a fixture whose own delivery failed paint a plausible
  // raster from the wrong file, with nothing in any log.
  assert.match(src, /imagesDir[\s\S]{0,120}fs\.rm\(imagesDir, \{ recursive: true, force: true \}\)/,
    'the images sandbox must be wiped on entry');
});

test('feed-ios copies images BEFORE the IR reaches the inbox', async () => {
  const src = await fs.readFile(new URL('./feed-ios.mjs', import.meta.url), 'utf8');
  const imagesAt = src.indexOf('for (const src of documentReplacedSrcs(doc))');
  const renameAt = src.indexOf('await fs.rename(tmp, dest)');
  assert.ok(imagesAt > 0 && renameAt > 0, 'both sites must exist');
  // A race guard rather than a correctness contract (images decode at paint
  // time, fonts at decode time) — but a capture that SOMETIMES shows the image
  // is worse to debug than one that never does.
  assert.ok(imagesAt < renameAt, 'images must be copied before the IR lands in the inbox');
  // Verbatim relative path under the sandbox root — the runtime's registry
  // resolves exactly this join, so no escaping rule can drift host↔device.
  assert.match(src, /const dest = join\(imagesDir, src\)/,
    'the corpus-relative path must be preserved verbatim under imagesDir');
});
