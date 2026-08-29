#!/usr/bin/env node
// Unit tests for tools/visual/png-color-space.mjs — the colour-space
// tripwire that guards every downstream metric.
//
// The bug class being defended against is invisible by construction: pngjs
// discards `iCCP`/`cICP` without applying them, so a Display-P3 or
// generic-RGB capture is compared as if it were sRGB and produces
// plausible-looking wrong numbers with no error anywhere. These tests pin
// (a) that the chunk walker actually reads the table, (b) that a profile
// chunk throws, and (c) that the 363 committed baselines start green — so
// the assertion can be turned on without a flag day.
//
// Run via `node --test tools/visual/png-color-space.test.mjs`; the smoke.sh
// glob `node --test tools/visual/*.test.mjs` picks it up automatically.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, readdirSync } from 'node:fs';
import { resolve, dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';
import { PNG } from 'pngjs';

import {
  readPngChunkTypes,
  assertSrgbOrUntagged,
  DISQUALIFYING_COLOR_CHUNKS,
} from './png-color-space.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const BASELINE = resolve(__dirname, 'baseline');

// ── Helpers ─────────────────────────────────────────────────────────────────

/** A minimal valid 2×2 PNG, produced by the same encoder the harness uses. */
function tinyPng() {
  const png = new PNG({ width: 2, height: 2 });
  png.data.fill(0x40);
  return PNG.sync.write(png);
}

/**
 * Splice an ancillary chunk of `type` into `buf` immediately after IHDR.
 * Builds a real chunk (length · type · data · CRC placeholder) — the walker
 * never validates CRCs, so a zeroed one is fine and keeps the test honest
 * about what the scanner actually inspects.
 */
function injectChunk(buf, type, payload = Buffer.alloc(0)) {
  const SIG = 8;
  const ihdrLen = buf.readUInt32BE(SIG);          // IHDR payload size (13)
  const insertAt = SIG + 12 + ihdrLen;            // just past IHDR's CRC
  const chunk = Buffer.alloc(12 + payload.length);
  chunk.writeUInt32BE(payload.length, 0);         // length
  chunk.write(type, 4, 4, 'latin1');              // type
  payload.copy(chunk, 8);                         // data
  // CRC left as zeros — not read by the scanner.
  return Buffer.concat([buf.subarray(0, insertAt), chunk, buf.subarray(insertAt)]);
}

// ── readPngChunkTypes ───────────────────────────────────────────────────────

test('readPngChunkTypes: reads a real chunk table in file order', () => {
  const types = readPngChunkTypes(tinyPng());
  assert.equal(types[0], 'IHDR', 'IHDR must come first per PNG spec');
  assert.equal(types.at(-1), 'IEND', 'IEND terminates the stream');
  assert.ok(types.includes('IDAT'), 'a real image carries pixel data');
});

test('readPngChunkTypes: sees an injected ancillary chunk', () => {
  const types = readPngChunkTypes(injectChunk(tinyPng(), 'sRGB', Buffer.from([0])));
  assert.ok(types.includes('sRGB'));
  assert.equal(types.indexOf('sRGB'), 1, 'injected directly after IHDR');
});

test('readPngChunkTypes: non-PNG input returns [] rather than throwing', () => {
  // The decoder raises a better error a moment later; we must not pre-empt it.
  assert.deepEqual(readPngChunkTypes(Buffer.from('not a png at all')), []);
  assert.deepEqual(readPngChunkTypes(Buffer.alloc(0)), []);
});

test('readPngChunkTypes: a corrupt length terminates instead of looping', () => {
  const buf = tinyPng();
  buf.writeUInt32BE(0xFFFFFFF0, 8);               // IHDR length runs past EOF
  const types = readPngChunkTypes(buf);           // must return, not hang
  assert.deepEqual(types, ['IHDR'], 'stops at the chunk it cannot trust');
});

// ── assertSrgbOrUntagged ────────────────────────────────────────────────────

test('assertSrgbOrUntagged: untagged PNG passes', () => {
  assert.doesNotThrow(() => assertSrgbOrUntagged(tinyPng(), 'tiny.png'));
});

test('assertSrgbOrUntagged: an explicit sRGB chunk passes', () => {
  // Android captures carry `sRGB` (rendering intent 0) + `sBIT`. That is the
  // invariant we WANT asserted, not a violation of it.
  const buf = injectChunk(tinyPng(), 'sRGB', Buffer.from([0]));
  assert.doesNotThrow(() => assertSrgbOrUntagged(buf, 'android.png'));
});

test('assertSrgbOrUntagged: pHYs / sBIT are not colour-space claims', () => {
  // web captures carry pHYs; Android carries sBIT. Neither says anything
  // about colour space, and flagging them would be a false positive.
  for (const t of ['pHYs', 'sBIT']) {
    const buf = injectChunk(tinyPng(), t, Buffer.from([0, 0, 0, 1]));
    assert.doesNotThrow(() => assertSrgbOrUntagged(buf, `${t}.png`), `${t} must pass`);
  }
});

test('assertSrgbOrUntagged: gAMA is a colour claim — only the sRGB value passes', () => {
  // The PREVIOUS version of this test asserted that ANY gAMA passes —
  // with a payload of gamma 0.00001, no less — encoding the exact wrong
  // premise the pipeline hunt flagged: gAMA defines the transfer function
  // of the stored bytes, and a linear-gamma capture scored as sRGB is off
  // by ~127/255 at mid-grey. Only the sRGB-compatible 45455 is benign.
  const gama = (v) => {
    const d = Buffer.alloc(4); d.writeUInt32BE(v, 0);
    return injectChunk(tinyPng(), 'gAMA', d);
  };
  assert.doesNotThrow(() => assertSrgbOrUntagged(gama(45455), 'srgb-gamma.png'));
  assert.throws(() => assertSrgbOrUntagged(gama(100000), 'linear.png'), /gAMA\(100000/);
  assert.throws(() => assertSrgbOrUntagged(gama(55556), 'gamma18.png'), /gAMA/);
});

test('assertSrgbOrUntagged: cHRM passes only at the sRGB primaries', () => {
  const chrm = (vals) => {
    const d = Buffer.alloc(32);
    vals.forEach((v, i) => d.writeUInt32BE(v, i * 4));
    return injectChunk(tinyPng(), 'cHRM', d);
  };
  const srgb = [31270, 32900, 64000, 33000, 30000, 60000, 15000, 6000];
  assert.doesNotThrow(() => assertSrgbOrUntagged(chrm(srgb), 'srgb-chrm.png'));
  const p3 = [...srgb]; p3[2] = 68000;              // red x nudged toward P3
  assert.throws(() => assertSrgbOrUntagged(chrm(p3), 'p3-chrm.png'), /cHRM/);
});

test('assertSrgbOrUntagged: an embedded ICC profile throws', () => {
  // This is Chrome's --force-color-profile=generic-rgb signature (a 277-byte
  // iCCP chunk) and half of Apple ImageIO's Display-P3 output.
  const buf = injectChunk(tinyPng(), 'iCCP', Buffer.from('profile\0\0deadbeef', 'latin1'));
  assert.throws(
    () => assertSrgbOrUntagged(buf, 'p3-capture.png'),
    /iCCP/,
    'must name the offending chunk',
  );
});

test('assertSrgbOrUntagged: cICP throws — the chunk most JS decoders miss', () => {
  // cICP is a recent PNG addition Apple emits for wide-gamut contexts and
  // that pngjs has never heard of. Missing it is precisely how a P3 capture
  // becomes silently-wrong sRGB numbers.
  const buf = injectChunk(tinyPng(), 'cICP', Buffer.from([12, 1, 0, 1]));
  assert.throws(() => assertSrgbOrUntagged(buf, 'wide.png'), /cICP/);
});

test('assertSrgbOrUntagged: error explains the fix, not just the fault', () => {
  const buf = injectChunk(tinyPng(), 'iCCP', Buffer.from('p\0\0x', 'latin1'));
  // node:assert's `throws` returns undefined — capture the error ourselves.
  let err;
  try { assertSrgbOrUntagged(buf, 'x.png'); } catch (e) { err = e; }
  assert.ok(err, 'expected a throw');
  // A tripwire nobody knows how to clear gets disabled. Pin the remedy.
  assert.match(err.message, /force-color-profile=srgb/);
  assert.match(err.message, /pngjs/, 'says WHY the profile is dangerous here');
});

test('assertSrgbOrUntagged: returns the chunk list on success', () => {
  const types = assertSrgbOrUntagged(injectChunk(tinyPng(), 'sRGB', Buffer.from([0])), 'ok.png');
  assert.ok(Array.isArray(types) && types.includes('sRGB'));
});

test('DISQUALIFYING_COLOR_CHUNKS is frozen and excludes sRGB', () => {
  assert.ok(Object.isFrozen(DISQUALIFYING_COLOR_CHUNKS));
  assert.ok(!DISQUALIFYING_COLOR_CHUNKS.includes('sRGB'), 'sRGB is the goal, not a fault');
});

// ── The committed corpus must start green ───────────────────────────────────

test('every committed baseline passes the colour-space assertion', () => {
  // If this ever fails, the tripwire is not "too strict" — a capture really
  // did change colour space, and every metric computed from it is suspect.
  const files = readdirSync(BASELINE).filter((f) => f.endsWith('.png'));
  assert.ok(files.length > 300, `expected the full baseline set, saw ${files.length}`);
  const offenders = [];
  for (const f of files) {
    try {
      assertSrgbOrUntagged(readFileSync(join(BASELINE, f)), f);
    } catch (e) {
      offenders.push(`${f}: ${e.message}`);
    }
  }
  assert.deepEqual(offenders, [], 'baselines must be untagged-sRGB');
});
