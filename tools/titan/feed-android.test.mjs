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

import { existsSync, statSync } from 'node:fs';
import { join, resolve } from 'node:path';
import { tmpdir } from 'node:os';

import {
  parseArgs, safe, deviceSafeName, expectedPngNames, composedPngName,
  parentCreatesContext, composeRoots, flattenComponents, pngIsValid,
  // wave-35 lane B2 — the @font-face file hop shared by both feeders.
  documentFontSrcs, resolveFontFile,
} from './feed-lib.mjs';

/** The fs probes resolveFontFile takes by injection (so the pure resolution
 *  rules can be pinned without stubbing the module's imports). */
const fsProbes = { resolve, existsSync, statSync };

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

// ── sanitisers: device rule (drop dot) vs compare rule (keep dot) ─────────────

test('deviceSafeName matches the Kotlin per-component char class [^a-zA-Z0-9_-] (dot DROPPED)', () => {
  assert.equal(deviceSafeName('attachment-local__0'), 'attachment-local__0'); // - and _ kept
  // A dot becomes `_` on device (ScreenshotManager.saveScreenshot rule).
  assert.equal(deviceSafeName('a b/c.d:e'), 'a_b_c_d_e');
});

test('safe (compare rule) keeps the dot — diverges from deviceSafeName on a dotted name', () => {
  // The shared compare-pipeline sanitiser keeps `.`; only the dot differs.
  assert.equal(safe('a b/c.d:e'), 'a_b_c.d_e');
  assert.notEqual(safe('x.y'), deviceSafeName('x.y')); // 'x.y' vs 'x_y'
});

// ── expectedPngNames: flat v2 (no slots), like css-color output ───────────────
// Returns { index, name, deviceFile (poll), hostFile (compare-glob output) }.
// For a dot-free name deviceFile === hostFile.

test('flat v2 document → one capture per component in order (device == host, no dots)', () => {
  const doc = { irVersion: 2, components: [
    { id: 'x-1', name: 'bg__0', properties: [] },
    { id: 'x-2', name: 'bg__1', properties: [] },
  ] };
  assert.deepEqual(expectedPngNames(doc).map((e) => e.deviceFile), ['000_bg__0.png', '001_bg__1.png']);
  assert.deepEqual(expectedPngNames(doc).map((e) => e.hostFile), ['000_bg__0.png', '001_bg__1.png']);
});

test('expectedPngNames decouples device (drop-dot) from host (keep-dot) for a dotted name', () => {
  // A component name with a "." is the exact footgun: the device WROTE the
  // drop-dot name (poll target) but inject globs the keep-dot name — so the
  // feeder must pull deviceFile and rename to hostFile.
  const doc = { irVersion: 2, components: [{ id: 'x', name: 'a.b', properties: [] }] };
  const [e] = expectedPngNames(doc);
  assert.equal(e.deviceFile, '000_a_b.png');   // device rule → dot dropped
  assert.equal(e.hostFile,   '000_a.b.png');   // compare rule → dot kept
  // hostFile must end exactly with the compare glob suffix inject looks for.
  assert.ok(e.hostFile.endsWith(`_${safe('a.b')}.png`));
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
  assert.deepEqual(expectedPngNames(doc).map((e) => e.deviceFile), ['000_clip__0.png']);
});

test('slot-composed tree WITHOUT a paint context keeps parent + children', () => {
  const doc = { irVersion: 2, components: [
    { id: 'r-1', name: 'box__0', properties: [] },
    { id: 'c-2', name: 'box__0__0', properties: [], slot: { parent: 'r-1' } },
  ] };
  // Pre-order: parent (000) then child (001).
  assert.deepEqual(expectedPngNames(doc).map((e) => e.hostFile), ['000_box__0.png', '001_box__0__0.png']);
});

test('v1 nested document (children arrays, no slots) flattens pre-order', () => {
  const doc = { components: [
    { id: 'r', name: 'root', properties: [], children: [
      { id: 'k', name: 'kid', properties: [] } ] },
  ] };
  assert.deepEqual(expectedPngNames(doc).map((e) => e.hostFile), ['000_root.png', '001_kid.png']);
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

test('a wedged capture restarts the app so it cannot cascade-timeout the batch', async () => {
  // A fixture whose on-device composed capture WEDGES (e.g. css-transforms
  // z-ordering-003) hangs the inbox poll loop; without a restart every fixture
  // behind it cascade-timeouts on the still-hung app (observed: 5/12 → the
  // recovery makes it 11/12, only the one genuinely-wedging fixture failing).
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  assert.match(src, /async function resetAndLaunch\(/, 'reset+launch helper dropped');
  // Called at startup AND in the timeout branch — at least two call-sites.
  const calls = src.match(/await resetAndLaunch\(adbx, opts\)/g) ?? [];
  assert.ok(calls.length >= 2, `resetAndLaunch must run at startup AND on timeout, found ${calls.length}`);
  assert.match(src, /restarting app to clear the wedge/, 'timeout-branch restart log/behaviour dropped');
  assert.match(src, /i < fixtures\.length - 1/, 'last-fixture guard on the restart dropped');
});

test('timed-out fixtures get ONE warm tail-retry (cold-start holes)', async () => {
  // On a pooled device the FIRST fixture after boot regularly blows the
  // timeout (cold JIT + first render) while everything after runs in ~1s —
  // without a warm retry, every section landing on a cold slot ships a hole
  // at its first fixture. The retry pass is skipped when ALL fixtures timed
  // out (dead device — don't double the wall-time), and a fixture that
  // times out twice stays failed (genuine wedge, e.g. z-ordering-003).
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  assert.match(src, /tail-retry/, 'tail-retry pass dropped');
  assert.match(src, /timedOutRows\.length < fixtures\.length/, 'all-timed-out (dead device) guard dropped');
  assert.match(src, /row\.retried = true/, 'retried rows must be marked in the summary');
});

// ── wave-35 lane B2: the @font-face file hop ────────────────────────────────
//
// The hop writes files into an app sandbox from a path that arrived out of a
// third-party corpus, so the DECLINE rules matter more than the happy path:
// every one of them is the difference between "the runtime falls back to its
// bundled face" and "the harness copied an arbitrary file onto a device".

test('documentFontSrcs returns each distinct src in document order', () => {
  const doc = { fontFaces: [
    { family: 'a', src: 'x/A.woff' },
    { family: 'a', src: 'x/A.woff', weight: '700' },  // same FILE, second face
    { family: 'b', src: 'x/B.ttf' },
  ] };
  // Deduped: pushing the same 261 KB file once per weight would multiply the
  // slowest step of a native section run for no gain.
  assert.deepEqual(documentFontSrcs(doc), ['x/A.woff', 'x/B.ttf']);
});

test('documentFontSrcs is empty for a document with no faces', () => {
  assert.deepEqual(documentFontSrcs({}), []);
  assert.deepEqual(documentFontSrcs({ fontFaces: null }), []);
  assert.deepEqual(documentFontSrcs(null), []);
  // A malformed entry names no file — dropping it here keeps the feeder from
  // logging a decline for something that was never a candidate.
  assert.deepEqual(documentFontSrcs({ fontFaces: [{ family: 'a' }] }), []);
});

test('resolveFontFile resolves a real corpus-relative font file', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w35b2-fonts-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.mkdir(join(root, 'css', 'res'), { recursive: true });
  const target = join(root, 'css', 'res', 'Lin.woff');
  await fs.writeFile(target, 'not really a font, but a real file');
  assert.equal(resolveFontFile(root, 'css/res/Lin.woff', fsProbes), target);
});

test('resolveFontFile DECLINES traversal, absolute paths and URLs', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w35b2-fonts-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  // Containment is checked on the RESOLVED path — a `..` chain that escapes
  // must never become a file this hop writes into an app sandbox.
  assert.equal(resolveFontFile(root, '../../../etc/passwd.woff', fsProbes), null);
  assert.equal(resolveFontFile(root, '/etc/passwd.woff', fsProbes), null);
  assert.equal(resolveFontFile(root, 'https://evil.example/x.woff', fsProbes), null);
  assert.equal(resolveFontFile(null, 'css/res/Lin.woff', fsProbes), null);
});

test('resolveFontFile DECLINES a non-font extension even when the file exists', async (t) => {
  const root = await fs.mkdtemp(join(tmpdir(), 'w35b2-fonts-'));
  t.after(() => fs.rm(root, { recursive: true, force: true }));
  await fs.writeFile(join(root, 'payload.sh'), '#!/bin/sh\necho hi');
  // The extension table is CLOSED so this route can never become a general
  // file copier for whatever a corpus path happens to point at.
  assert.equal(resolveFontFile(root, 'payload.sh', fsProbes), null);
  // …and an admitted extension that is ABSENT is still a decline, not a guess.
  assert.equal(resolveFontFile(root, 'missing.woff2', fsProbes), null);
});

test('feed-android pushes fonts BEFORE the IR reaches the inbox', async () => {
  // Source-scan pin on the ordering CONTRACT: the app registers faces at
  // decode time, so a font pushed after its document registers too late to
  // shape the capture and the screenshot silently records the fallback.
  const src = await fs.readFile(new URL('./feed-android.mjs', import.meta.url), 'utf8');
  const fontsAt = src.indexOf('const fonts = pushFontFaces(');
  const inboxAt = src.indexOf("adbx(['push', fx, `${INBOX_DIR}");
  assert.ok(fontsAt > 0 && inboxAt > 0, 'both push sites must exist');
  assert.ok(fontsAt < inboxAt, 'fonts must be pushed before the IR');
  // The fonts sandbox must be wiped with the inbox: a face left from a
  // previous run is the worst stale state here (it LOOKS like text).
  assert.match(src, /rm', '-rf', INBOX_DIR, SHOT_DIR, FONTS_DIR/,
    'the fonts dir must join the reset wipe');
});
