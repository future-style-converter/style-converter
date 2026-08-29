#!/usr/bin/env node
// Pins the host-side mirror of the device flatten rules against the
// real converter output for both nested corpora. If a rule here drifts
// from the device rule, these counts mismatch on the next device run —
// but this test catches the cheaper failure first: the mirror drifting
// from ITSELF (a refactor changing the walk).

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import { expectedCaptureCount, parentCreatesContext, dependsOnBackdrop } from './expected-captures.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));

test('composition-test: 42 wire components → 32 captures', (t) => {
  // The number every device produces (verified on all three platforms),
  // which the old grep-based count read as 42 — sending every run through
  // the stuck-counter branch. Reads the converter's real output when the
  // last convert was composition-test; SKIPS (visibly, never passes
  // vacuously) otherwise — out/ is gitignored scratch, so its content
  // depends on what ran last.
  let wire = null;
  try {
    wire = JSON.parse(readFileSync(resolve(__dirname, '../../out/tmpOutput.json'), 'utf8'));
  } catch { /* absent → skip below */ }
  if (!wire || (wire.components ?? []).length !== 42) {
    t.skip('out/tmpOutput.json is not the composition-test convert — run test-all.sh on it first');
    return;
  }
  assert.equal(expectedCaptureCount(wire), 32);
});

test('a flat document counts every component', () => {
  const wire = { components: [
    { id: 'a', properties: [] },
    { id: 'b', properties: [] },
  ] };
  assert.equal(expectedCaptureCount(wire), 2);
});

test('a context-creating parent swallows its children', () => {
  const wire = { components: [
    { id: 'p', properties: [{ type: 'Opacity', data: { alpha: 0.5 } }] },
    { id: 'c', properties: [], slot: { parent: 'p' } },
  ] };
  assert.equal(expectedCaptureCount(wire), 1, 'child renders inside the parent capture only');
});

test('a backdrop-dependent child is suppressed standalone', () => {
  const wire = { components: [
    { id: 'p', properties: [] },
    { id: 'c', properties: [{ type: 'MixBlendMode', data: 'multiply' }], slot: { parent: 'p' } },
    { id: 'd', properties: [], slot: { parent: 'p' } },
  ] };
  assert.equal(expectedCaptureCount(wire), 2, 'blend child suppressed; plain child counted');
});

test('the predicates mirror the device spellings', () => {
  // Kotlin's serializer uppercases enum values; the parser lowercases.
  assert.equal(parentCreatesContext({ properties: [{ type: 'Overflow', data: 'HIDDEN' }] }), true);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Overflow', data: 'visible' }] }), false);
  assert.equal(dependsOnBackdrop({ properties: [{ type: 'MixBlendMode', data: { value: 'Screen' } }] }), true);
  assert.equal(dependsOnBackdrop({ properties: [{ type: 'MixBlendMode', data: 'normal' }] }), false);
  assert.equal(parentCreatesContext({ properties: [{ type: 'Transform', data: [] }] }), false, 'empty transform list is identity');
  assert.equal(parentCreatesContext({ properties: [{ type: 'Opacity', data: 1.0 }] }), false, 'opacity 1 creates no context');
});
