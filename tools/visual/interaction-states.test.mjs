#!/usr/bin/env node
// Unit tests for tools/visual/interaction-states.mjs.
//
// Built round 74. Last of the 4 testing-side scripts to get unit-test
// coverage. Following the round 56/72/73 pattern.
//
// interaction-states.mjs is mostly puppeteer orchestration — there's
// not a lot of pure logic to test. But the exported constants
// (STATES, COMPONENTS, BASE_URL) and the iOS/Android stub return
// shapes ARE testable, and pinning them prevents the Tier 5 harness
// from accidentally drifting (e.g. someone removes "Badge" from
// COMPONENTS without realising the report shape changes).

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  STATES,
  COMPONENTS,
  WEB_PORT,
  BASE_URL,
  OUT_DIR,
  snapshotiOS,
  snapshotAndroid,
} from './interaction-states.mjs';

// ── Constants ──────────────────────────────────────────────────────────────

test('STATES has the 6 WCAG-relevant interaction states', () => {
  // Tier 5's contract: 6 states × 15 components = 90 captures.
  // If anyone changes this list, the report shape changes and the
  // 90/90 invariant in TIER5_INTERACTIONS.md becomes wrong.
  assert.equal(STATES.length, 6);
  assert.deepEqual(STATES, ['hover', 'focus', 'active', 'checked', 'disabled', 'visited']);
});

test('COMPONENTS has the 15 Tier-3 component fixtures', () => {
  // Same: 90/90 invariant requires exactly 15 components.
  // The list is the union of fixtures/components/*.json
  // (except scaffolding files); any addition or removal needs an
  // explicit decision.
  assert.equal(COMPONENTS.length, 15);
  // Spot-check the components most likely to need re-verification
  // after an IR change (they're in the auditor's run-this-when-modified
  // list per CAMPAIGN_SUMMARY round 41).
  for (const c of ['Alert', 'Avatar', 'NavHeader', 'PrimaryButton', 'FormInput']) {
    assert.ok(COMPONENTS.includes(c), `COMPONENTS missing ${c}`);
  }
});

test('STATES × COMPONENTS = 90 expected captures', () => {
  // The 90/90 number quoted everywhere (TIER5_INTERACTIONS.md,
  // smoke.sh expected output, audit.yml CI workflow) must equal
  // STATES.length * COMPONENTS.length.
  assert.equal(STATES.length * COMPONENTS.length, 90);
});

test('WEB_PORT defaults to 3000 (vite default)', () => {
  // smoke.sh and the CI workflow both assume vite on :3000.
  // If WEB_PORT default drifts, vite + harness will fight over ports.
  // (Override is still possible via env var — this just pins the default.)
  // Note: if test runs with WEB_PORT env set, this'll still pass; we're
  // pinning the FALLBACK only.
  assert.ok(WEB_PORT === '3000' || WEB_PORT === process.env.WEB_PORT);
});

test('BASE_URL points at vite on the configured port', () => {
  assert.equal(BASE_URL, `http://localhost:${WEB_PORT}`);
});

test('OUT_DIR is tools/visual/interaction-snapshots/ (gitignored)', () => {
  // Must be under tools/visual/ so .gitignore catches it; must match the
  // path TIER5_INTERACTIONS.md and smoke.sh both reference.
  assert.equal(OUT_DIR, 'tools/visual/interaction-snapshots');
});

// ── iOS / Android stubs ─────────────────────────────────────────────────────

test('snapshotiOS returns { skipped: true } with TIER5 task #2 reason', () => {
  // Per Tier 5 plan, iOS XCUITest harness is Phase 5b (8-12h, deferred).
  // Pin the stub so we don't accidentally claim iOS coverage by
  // returning { path: '...' } from a half-implemented stub.
  const r = snapshotiOS('PrimaryButton', 'hover');
  assert.equal(r.skipped, true);
  assert.match(r.reason, /XCUITest/);
  assert.match(r.reason, /TIER5 task #2/);
});

test('snapshotAndroid returns { skipped: true } with TIER5 task #3 reason', () => {
  // Same for Android Espresso harness (Phase 5c).
  const r = snapshotAndroid('PrimaryButton', 'focus');
  assert.equal(r.skipped, true);
  assert.match(r.reason, /Espresso/);
  assert.match(r.reason, /TIER5 task #3/);
});

test('snapshot stubs ignore their args', () => {
  // The stubs return the same shape regardless of inputs (they're
  // pure no-ops that don't peek at component/state). Confirm they
  // don't accidentally record the args in a way that would change
  // the report shape across calls.
  const r1 = snapshotiOS('Alert', 'hover');
  const r2 = snapshotiOS('Toast', 'visited');
  assert.deepEqual(r1, r2);
});
