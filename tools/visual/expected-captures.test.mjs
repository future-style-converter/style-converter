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

test('composition-test: 42 wire components → 32 captures', () => {
  // The number every device produces (verified on all three platforms),
  // which the old grep-based count read as 42 — sending every run through
  // the stuck-counter branch.
  //
  // Retro R10 (finding A8#3): this used to read `out/tmpOutput.json` and
  // SKIP unless the last convert on this machine happened to be
  // composition-test — which it never was on any recorded sweep, so the
  // pin never executed. It now reads the committed convert of
  // fixtures/composition-test.json vendored beside this test
  // (tools/visual/fixtures/composition-test.ir.json, 42 components, a
  // `:converter:run --from css --to ir` of that fixture), so the mirror is
  // pinned against real converter output on every run, everywhere.
  const wire = JSON.parse(
    readFileSync(resolve(__dirname, 'fixtures/composition-test.ir.json'), 'utf8'),
  );
  // Guard the premise: a different fixture (or a re-vendor that changed the
  // wire) must fail loudly here rather than silently re-baselining the 32.
  assert.equal((wire.components ?? []).length, 42, 'the vendored convert is the 42-component composition-test wire');
  assert.equal(expectedCaptureCount(wire), 32);
});

test('composition-test: a fresh convert in out/ must agree with the vendored wire', (t) => {
  // Drift guard on the vendoring: when the last `test-all.sh` convert WAS
  // composition-test, out/tmpOutput.json must produce the same count. This
  // is the only part that may skip — the pin above always runs.
  let fresh = null;
  try {
    fresh = JSON.parse(readFileSync(resolve(__dirname, '../../out/tmpOutput.json'), 'utf8'));
  } catch { /* absent → skip below */ }
  if (!fresh || (fresh.components ?? []).length !== 42) {
    t.skip('out/tmpOutput.json is not the composition-test convert (vendored pin above still ran)');
    return;
  }
  assert.equal(expectedCaptureCount(fresh), 32);
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
