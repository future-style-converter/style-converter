#!/usr/bin/env node
//
// Unit tests for tools/titan/mono-pin.mjs (wave-46 lane Y7).
//
// The property that matters most is BYTE-DISCIPLINE: with TITAN_MONO_PIN
// unset every export must collapse to identity / empty so the default native
// feed cannot move. The flag-on shape is pinned second: which documents take
// the pin, the exact `fontFaces` entries appended, the face order the iOS
// single-name registry depends on, and the two places the pin must yield to
// the document's own declaration.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  monoPinEnabled,
  monoPinFontsDir,
  MONO_PIN_FAMILY,
  MONO_PIN_GENERICS,
  MONO_PIN_SANDBOX_DIR,
  MONO_PIN_FACES,
  monoPinFontFiles,
  documentNamesMonospace,
  monoPinFontFaces,
  monoPinDocument,
  monoPinMissingWarnings,
} from './mono-pin.mjs';

const OFF = {};                                                   // the default pipeline
const ON  = { TITAN_MONO_PIN: '1', TITAN_MONO_PIN_FONTS: '/staged' };

/** A minimal flat-v2 document whose one component declares `families`. */
const docWith = (families, extra = {}) => ({
  irVersion: 2, minReaderVersion: 2,
  components: [{ id: 'c-1', name: 'c', properties: [{ type: 'FontFamily', data: families }], text: 'x' }],
  ...extra,
});
// The live wire shape of hyphens-manual-inline-010's bordered box.
const MONO_DOC = docWith(['monospace']);

// ── flag OFF: every export is an identity ───────────────────────────────────

test('flag off: enabled=false for unset/other values, dir=null', () => {
  assert.equal(monoPinEnabled(OFF), false);
  assert.equal(monoPinEnabled({ TITAN_MONO_PIN: '0' }), false);
  assert.equal(monoPinEnabled({ TITAN_MONO_PIN: 'true' }), false);   // '1' only
  assert.equal(monoPinFontsDir(OFF), null);
});

test('flag off: monoPinDocument returns the SAME object (identity, not a copy)', () => {
  assert.equal(monoPinDocument(MONO_DOC, OFF), MONO_DOC);
  assert.equal(monoPinDocument(MONO_DOC, { TITAN_MONO_PIN: '0' }), MONO_DOC);
});

test('flag off: no faces on the wire, no files to push, no warnings', () => {
  assert.deepEqual(monoPinFontFaces(OFF), []);
  const io = { existsSync: () => { throw new Error('must not probe disk with the flag off'); } };
  assert.deepEqual(monoPinFontFiles(OFF, io), { present: [], missing: [] });
  assert.deepEqual(monoPinMissingWarnings(OFF, io), []);
});

// ── the pin shape ───────────────────────────────────────────────────────────

test('the pin registers under the generic\'s OWN name and covers both quirk keywords', () => {
  assert.equal(MONO_PIN_FAMILY, 'monospace');
  assert.deepEqual([...MONO_PIN_GENERICS], ['monospace', 'ui-monospace']);
  assert.equal(MONO_PIN_SANDBOX_DIR, '_mono-pin');
});

test('face order: Bold first, Regular LAST (iOS keeps one name per family, last wins)', () => {
  assert.equal(MONO_PIN_FACES.length, 2);
  assert.equal(MONO_PIN_FACES[0].weight, '700');
  assert.equal(MONO_PIN_FACES[MONO_PIN_FACES.length - 1].weight, '400');
  assert.equal(MONO_PIN_FACES[MONO_PIN_FACES.length - 1].file, 'DejaVuSansMono.ttf');
  assert.ok(Object.isFrozen(MONO_PIN_FACES));
});

test('flag on: fontFaces entries are sandbox-relative under _mono-pin/', () => {
  assert.deepEqual(monoPinFontFaces(ON), [
    { family: 'monospace', src: '_mono-pin/DejaVuSansMono-Bold.ttf', weight: '700', style: 'normal' },
    { family: 'monospace', src: '_mono-pin/DejaVuSansMono.ttf',      weight: '400', style: 'normal' },
  ]);
});

// ── which documents take the pin ────────────────────────────────────────────

test('documentNamesMonospace: bare generic, ui-monospace, trailing generic, quoted/cased', () => {
  assert.equal(documentNamesMonospace(docWith(['monospace'])), true);
  assert.equal(documentNamesMonospace(docWith(['ui-monospace'])), true);
  // hyphens-auto-control's live payload: the generic is LAST — still named.
  assert.equal(documentNamesMonospace(docWith(['Courier New', 'Courier', 'monospace'])), true);
  assert.equal(documentNamesMonospace(docWith([' "Monospace" '])), true);
  // The documented object shape and the legacy single string.
  assert.equal(documentNamesMonospace(docWith({ families: ['monospace'] })), true);
  assert.equal(documentNamesMonospace(docWith('monospace')), true);
});

test('documentNamesMonospace: sans-serif / Inter / face-free / malformed documents do NOT', () => {
  assert.equal(documentNamesMonospace(docWith(['Inter', 'sans-serif'])), false);
  // "Courier Mono" is a CONCRETE name; the pin is for the generic only.
  assert.equal(documentNamesMonospace(docWith(['Courier Mono'])), false);
  assert.equal(documentNamesMonospace({ components: [{ properties: [] }] }), false);
  assert.equal(documentNamesMonospace({ components: [{ properties: [{ type: 'FontFamily', data: 7 }] }] }), false);
  assert.equal(documentNamesMonospace(null), false);
  assert.equal(documentNamesMonospace({}), false);
});

test('flag on: a monospace document gets the pin faces APPENDED, everything else shared', () => {
  const out = monoPinDocument(MONO_DOC, ON);
  assert.notEqual(out, MONO_DOC);
  assert.equal(out.components, MONO_DOC.components);           // shared, not copied
  assert.equal(out.irVersion, 2);
  assert.deepEqual(out.fontFaces, monoPinFontFaces(ON));
  assert.equal('fontFaces' in MONO_DOC, false);               // input untouched
});

test('flag on: a document with its own faces keeps them FIRST (document order), pin after', () => {
  const own = { family: 'test', src: 'css/res/Lin.woff', weight: '400' };
  const out = monoPinDocument(docWith(['monospace'], { fontFaces: [own] }), ON);
  assert.equal(out.fontFaces.length, 3);
  assert.equal(out.fontFaces[0], own);
  assert.equal(out.fontFaces[1].src, '_mono-pin/DejaVuSansMono-Bold.ttf');
});

test('flag on: a document that names no monospace family is returned UNCHANGED', () => {
  const sans = docWith(['Inter', 'sans-serif']);
  assert.equal(monoPinDocument(sans, ON), sans);
});

test('flag on: a document declaring its OWN face named monospace is left alone', () => {
  // css-fonts-4 §4.1 — the document's declaration wins; the pilot must not
  // pin over it (it would be measuring itself).
  const own = docWith(['monospace'], { fontFaces: [{ family: '"Monospace"', src: 'css/res/Mono.ttf' }] });
  assert.equal(monoPinDocument(own, ON), own);
});

test('flag on: non-object inputs pass through', () => {
  assert.equal(monoPinDocument(null, ON), null);
  assert.equal(monoPinDocument(undefined, ON), undefined);
});

// ── staged files ────────────────────────────────────────────────────────────

test('flag on: present/missing split follows existsSync, missing warns LOUDLY per face', () => {
  const io = { existsSync: (p) => p.endsWith('/DejaVuSansMono.ttf') };
  const r = monoPinFontFiles(ON, io);
  assert.deepEqual(r.present.map((f) => f.abs), ['/staged/DejaVuSansMono.ttf']);
  assert.deepEqual(r.missing.map((f) => f.file), ['DejaVuSansMono-Bold.ttf']);
  const w = monoPinMissingWarnings(ON, io, 'feed-x');
  assert.equal(w.length, 1);
  assert.match(w[0], /\[feed-x\] WARN: staged pin font MISSING \(\/staged\/DejaVuSansMono-Bold\.ttf\)/);
  assert.match(w[0], /weight 700/);
});

test('flag on with NO fonts dir: every face is missing (a mis-set run cannot pass quietly)', () => {
  const r = monoPinFontFiles({ TITAN_MONO_PIN: '1' }, { existsSync: () => true });
  assert.equal(r.present.length, 0);
  assert.equal(r.missing.length, MONO_PIN_FACES.length);
  assert.match(monoPinMissingWarnings({ TITAN_MONO_PIN: '1' }, { existsSync: () => true })[0],
               /<TITAN_MONO_PIN_FONTS unset>/);
});
