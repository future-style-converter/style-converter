#!/usr/bin/env node
//
// Unit tests for tools/titan/split-combined-ir.mjs — the combined-IR ->
// per-test-IR splitter that feeds the native inbox feeders.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync, existsSync } from 'node:fs';
import { dirname, join } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  splitCombinedIr, testKeyOf, rootTestKey, safe,
  familyId, referencedFamilies, scopedFontFaces,
} from './split-combined-ir.mjs';

// A combined doc mirroring the real shape: roots carry the wpt__ prefix,
// flattened children keep RAW names and link to the root via slot.parent.
const combined = {
  irVersion: 2,
  minReaderVersion: 2,
  components: [
    // Test 1: root __0 with a nested child chain (raw child names).
    { id: 'wpt__css-color__clip-1__0-1', name: 'wpt__css-color__clip-1__0', properties: [] },
    { id: 'clip-1__0__0-2', name: 'clip-1__0__0', slot: { parent: 'wpt__css-color__clip-1__0-1' }, properties: [] },
    { id: 'clip-1__0__0__0-3', name: 'clip-1__0__0__0', slot: { parent: 'clip-1__0__0-2' }, properties: [] },
    // Test 1: a second root __1.
    { id: 'wpt__css-color__clip-1__1-4', name: 'wpt__css-color__clip-1__1', properties: [] },
    // Test 2: single root.
    { id: 'wpt__css-backgrounds__attach-1__0-5', name: 'wpt__css-backgrounds__attach-1__0', properties: [] },
  ],
};

test('rootTestKey strips exactly the trailing __<idx> segment of a root name', () => {
  assert.equal(rootTestKey('wpt__css-color__a98rgb-001__0'), 'wpt__css-color__a98rgb-001');
  assert.equal(rootTestKey('wpt__css-color__a98rgb-001__12'), 'wpt__css-color__a98rgb-001');
  assert.equal(rootTestKey('plain-component'), 'plain-component');
});

test('rootTestKey keeps subdir-encoded stems intact (wave-21 collision fix)', () => {
  // fixtureStem() encodes a nested test's subdir chain with the `__` slot
  // separator (`flexbox__monolithic-overflow-001.tentative`), so stems now
  // legitimately CONTAIN `__`. The pre-fix "first three segments" rule would
  // truncate this root to `wpt__css-break__flexbox`, merging every test in
  // that subdir into one bogus group; stripping only the trailing child
  // index preserves the full test identity.
  assert.equal(
    rootTestKey('wpt__css-break__flexbox__monolithic-overflow-001.tentative__0'),
    'wpt__css-break__flexbox__monolithic-overflow-001.tentative',
  );
  // The colliding sibling from the other subdir resolves to a DIFFERENT key.
  assert.equal(
    rootTestKey('wpt__css-break__grid__monolithic-overflow-001.tentative__0'),
    'wpt__css-break__grid__monolithic-overflow-001.tentative',
  );
  // Two-level subdir chains strip only the one trailing index too.
  assert.equal(
    rootTestKey('wpt__CSS2__floats-clear__deep__adjoining-float-001__3'),
    'wpt__CSS2__floats-clear__deep__adjoining-float-001',
  );
});

test('testKeyOf walks the slot chain so children group with their root test', () => {
  const byId = new Map(combined.components.map((c) => [c.id, c]));
  // Root -> its own test.
  assert.equal(testKeyOf(combined.components[0], byId), 'wpt__css-color__clip-1');
  // Deeply nested child (raw name, 3 links up) -> parent's test, NOT its own name.
  assert.equal(testKeyOf(combined.components[2], byId), 'wpt__css-color__clip-1');
});

test('splitCombinedIr groups a test root WITH its slot descendants', () => {
  const docs = splitCombinedIr(combined);
  assert.equal(docs.length, 2, 'two distinct test groups (not one-per-component)');
  assert.equal(docs[0].key, 'wpt__css-color__clip-1');
  assert.equal(docs[1].key, 'wpt__css-backgrounds__attach-1');
  // Test 1 collects both roots + the 2 descendants = 4 components.
  assert.equal(docs[0].doc.components.length, 4);
  assert.equal(docs[1].doc.components.length, 1);
  // Slot refs preserved so composition still resolves inside the split doc.
  const child = docs[0].doc.components.find((c) => c.id === 'clip-1__0__0-2');
  assert.equal(child.slot.parent, 'wpt__css-color__clip-1__0-1');
  // Names preserved verbatim (so PNG names still match inject's glob).
  assert.equal(docs[0].doc.components[0].name, 'wpt__css-color__clip-1__0');
  for (const { doc } of docs) {
    assert.equal(doc.irVersion, 2);
    assert.equal(doc.minReaderVersion, 2);
  }
});

test('splitCombinedIr carries document-level keyframes only when present', () => {
  const withKf = { ...combined, keyframes: { spin: [{ offset: '0%', declarations: {} }] } };
  const docs = splitCombinedIr(withKf);
  for (const { doc } of docs) assert.deepEqual(doc.keyframes, withKf.keyframes);
  // Absent in the plain case.
  for (const { doc } of splitCombinedIr(combined)) assert.equal(doc.keyframes, undefined);
});

test('splitCombinedIr rejects a non-IR input', () => {
  assert.throws(() => splitCombinedIr({}), /components array/);
  assert.throws(() => splitCombinedIr(null), /components array/);
});

test('safe sanitises path segments like the compare pipeline', () => {
  assert.equal(safe('wpt__css-color__a98rgb-001'), 'wpt__css-color__a98rgb-001');
  assert.equal(safe('a/b c'), 'a_b_c');
});

// ── Wave 43 lane V5: per-test fontFaces scoping ─────────────────────────────
//
// Wave 35 copied the combined document's WHOLE face database onto every split
// doc; these tests pin the narrowed carry — a split doc keeps exactly the
// faces its own components' FontFamily properties name (css-fonts-4 §4.2
// case-insensitive matching), fails OPEN on unreadable shapes, and omits the
// key entirely when nothing is referenced.

test('familyId matches css-fonts-4 §4.2: case-insensitive, quote-agnostic', () => {
  assert.equal(familyId('Test'), 'test');                 // ASCII case folds
  assert.equal(familyId('  test  '), 'test');             // whitespace trimmed
  assert.equal(familyId('"Lin Libertine"'), 'lin libertine'); // <string> spelling
  assert.equal(familyId("'test'"), 'test');               // single-quoted too
  assert.equal(familyId(null), '');                       // defensive: no crash
});

test('referencedFamilies collects FontFamily names across a whole group', () => {
  const comps = [
    // Root declares the face-backed family via the expanded `font` shorthand.
    { id: 'r', properties: [{ type: 'FontFamily', data: ['Test', 'sans-serif'] }] },
    // Child has unrelated properties — must not confuse the scan.
    { id: 'c', properties: [{ type: 'FontSize', data: { px: 36 } }] },
    // A style-less component (no properties key) is legal and contributes nothing.
    { id: 'd' },
  ];
  assert.deepEqual([...referencedFamilies(comps)].sort(), ['sans-serif', 'test']);
});

test('referencedFamilies fails OPEN (null) on shapes it cannot read', () => {
  // FontFamily with null data (the wire's "runtime-dependent" marker, e.g.
  // var()) — the group cannot be proven face-free, so the caller keeps all.
  assert.equal(referencedFamilies([{ id: 'a', properties: [{ type: 'FontFamily', data: null }] }]), null);
  // Non-string entry inside the family list — same conservatism.
  assert.equal(referencedFamilies([{ id: 'a', properties: [{ type: 'FontFamily', data: [42] }] }]), null);
  // A properties container that is not an array (unknown wire) — same.
  assert.equal(referencedFamilies([{ id: 'a', properties: { FontFamily: ['x'] } }]), null);
});

// The shared face database for the scoping tests: one referenced face, one
// unreferenced face, in document order.
const faces = [
  { family: 'test', src: 'css/css-text/x/LinLibertine.woff' },
  { family: 'other', src: 'css/css-text/x/Other.woff' },
];

test('scopedFontFaces keeps only referenced faces, preserving document order', () => {
  const comps = [{ id: 'r', properties: [{ type: 'FontFamily', data: ['TEST'] }] }];
  // Case-insensitive: component says TEST, face says test — §4.2 match.
  assert.deepEqual(scopedFontFaces(faces, comps), [faces[0]]);
  // Nothing referenced → empty list (the caller then omits the key).
  const bare = [{ id: 'r', properties: [{ type: 'FontSize', data: { px: 12 } }] }];
  assert.deepEqual(scopedFontFaces(faces, bare), []);
  // Unscannable group → ALL faces verbatim (fail open, wave-35 behavior).
  const varred = [{ id: 'r', properties: [{ type: 'FontFamily', data: null }] }];
  assert.deepEqual(scopedFontFaces(faces, varred), faces);
});

test('splitCombinedIr scopes fontFaces per test and omits when unreferenced', () => {
  const withFaces = {
    ...combined,
    fontFaces: faces,
    components: [
      // Test 1's ROOT does not name any face…
      { id: 'wpt__css-text__t1__0-1', name: 'wpt__css-text__t1__0', properties: [] },
      // …but its CHILD does (inheritance goes the other way in CSS, yet the
      // scan is group-wide on purpose: ANY member naming the family keeps it).
      { id: 't1__0__0-2', name: 't1__0__0', slot: { parent: 'wpt__css-text__t1__0-1' },
        properties: [{ type: 'FontFamily', data: ['test'] }] },
      // Test 2 never mentions any declared family.
      { id: 'wpt__css-text__t2__0-3', name: 'wpt__css-text__t2__0',
        properties: [{ type: 'FontFamily', data: ['sans-serif'] }] },
    ],
  };
  const docs = splitCombinedIr(withFaces);
  assert.equal(docs.length, 2);
  // Test 1 keeps EXACTLY its referenced face (not the whole database).
  assert.deepEqual(docs[0].doc.fontFaces, [faces[0]]);
  // Test 2 references nothing declared → the key is OMITTED, not [].
  assert.equal(docs[1].doc.fontFaces, undefined);
  // The keyframes carry (document-scoped, deliberately unscoped) is untouched.
  assert.equal(docs[0].doc.keyframes, undefined);
});

test('splitCombinedIr keeps every face for every doc when a group is unscannable', () => {
  const withVar = {
    ...combined,
    fontFaces: faces,
    components: [
      // One test whose FontFamily is the wire's null (var()/calc()) marker —
      // it cannot be proven face-free, so it must keep the WHOLE database.
      { id: 'wpt__css-text__t1__0-1', name: 'wpt__css-text__t1__0',
        properties: [{ type: 'FontFamily', data: null }] },
    ],
  };
  const docs = splitCombinedIr(withVar);
  assert.deepEqual(docs[0].doc.fontFaces, faces);
});

// ── The wave42-final proving set (skip-guarded: runs/ is a gitignored run
// artifact, so CI and fresh checkouts skip; any machine with the wave-42
// css-text run proves the measured claim verbatim). The smell this lane
// fixes: the combined css-text document registers ONE LinLibertine face and
// the wave-35 carry put it on all 48 per-test docs, though only the 8
// boundary-shaping tests (001..008) reference family "test".
const RUNS_COMBINED = join(
  dirname(fileURLToPath(import.meta.url)),
  'runs', 'wave42-final', 'sections', 'css-text', 'out', 'tmpOutput.json',
);

test('wave42-final css-text: 40 undeclared carriers drop to the true 8-doc set',
  { skip: !existsSync(RUNS_COMBINED) }, () => {
    const combinedIr = JSON.parse(readFileSync(RUNS_COMBINED, 'utf8'));
    // Precondition of the measurement: one document-level LinLibertine face.
    assert.equal(combinedIr.fontFaces.length, 1);
    assert.match(combinedIr.fontFaces[0].src, /LinLibertine/);
    const docs = splitCombinedIr(combinedIr);
    assert.equal(docs.length, 48, 'the wave-42 css-text section is 48 tests');
    const carriers = docs.filter(({ doc }) => doc.fontFaces?.length).map(({ key }) => key);
    // Exactly the 8 tests whose WPT sources declare AND use family "test".
    assert.deepEqual(carriers.sort(), [1, 2, 3, 4, 5, 6, 7, 8].map(
      (n) => `wpt__css-text__boundary-shaping__boundary-shaping-00${n}`,
    ));
    // And each carrier keeps the face VERBATIM (same object shape, §4.1 order).
    for (const { doc } of docs) {
      if (doc.fontFaces) assert.deepEqual(doc.fontFaces, combinedIr.fontFaces);
    }
  });
