#!/usr/bin/env node
//
// Unit tests for tools/titan/split-combined-ir.mjs — the combined-IR ->
// per-test-IR splitter that feeds the native inbox feeders.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { splitCombinedIr, testKeyOf, rootTestKey, safe } from './split-combined-ir.mjs';

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
