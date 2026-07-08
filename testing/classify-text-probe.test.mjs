#!/usr/bin/env node
// Unit tests for classify-text-probe.mjs (B-EXT spec Section 5).
//
// Each test pairs an expected label with the input triplet that should
// produce it. Order mirrors the decision-tree order in the source so a
// future tree refactor keeps tests aligned with the rule that fired.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  classifyTextProbe,
  worstTextProbeLabel,
  textProbeBadgeColor,
  TEXT_PROBE_CLASSIFIER_VERSION,
} from './classify-text-probe.mjs';

// ── classifyTextProbe ─────────────────────────────────────────────────────

test('classifyTextProbe: all probes agree → "agree"', () => {
  // Every metric within its spec-defined sub-pixel agreement band.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0.1, divergentFixtureCount: 0 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 0.1, maxStddevDeltaPx: 0.1 },
  );
  assert.equal(r, 'agree');
});

test('classifyTextProbe: B8 baseline >1.0px → "broken"', () => {
  // Spec Section 4 / B8 row 4: >1 px = "broken" (red).
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 1.5, divergentFixtureCount: 1 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 0, maxStddevDeltaPx: 0 },
  );
  assert.equal(r, 'broken');
});

test('classifyTextProbe: B10 mean+stddev both >1.5 → "broken"', () => {
  // Spec Section 4 / B10 row 4: both axes blown = broken.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 2.0, maxStddevDeltaPx: 2.0 },
  );
  assert.equal(r, 'broken');
});

test('classifyTextProbe: B10 mean drift only → "kerning-drift"', () => {
  // mean 0.5–1.5 = kerning drift, stddev <0.5 keeps it out of "broken".
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 1.0, maxStddevDeltaPx: 0.2 },
  );
  assert.equal(r, 'kerning-drift');
});

test('classifyTextProbe: B10 stddev drift only → "kerning-drift" (hinting variance)', () => {
  // mean stays low (no em scaling drift) but stddev jumps (per-glyph
  // hinting differs). Spec rolls both into "kerning-drift" headline.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 0.1, maxStddevDeltaPx: 1.0 },
  );
  assert.equal(r, 'kerning-drift');
});

test('classifyTextProbe: B8 sub-pixel drift only → "sub-px-drift"', () => {
  // 0.25 < baselineDelta < 1.0 px lands here.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0.5 },
    { agreement: 'agree' },
    { maxMeanDeltaPx: 0, maxStddevDeltaPx: 0 },
  );
  assert.equal(r, 'sub-px-drift');
});

test('classifyTextProbe: B9 platforms differ → "sub-px-drift"', () => {
  // Different AA strategies (e.g. iOS greyscale, web subpixel) is a
  // perceptual difference but NOT a structural bug — sub-pixel drift band.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0 },
    { agreement: 'differ' },
    { maxMeanDeltaPx: 0, maxStddevDeltaPx: 0 },
  );
  assert.equal(r, 'sub-px-drift');
});

test('classifyTextProbe: all-null input → "broken" (no signal = unsafe)', () => {
  // Spec rationale: prefer to flag missing-data over silently passing it.
  const r = classifyTextProbe(null, null, null);
  assert.equal(r, 'broken');
});

test('classifyTextProbe: partial-null input is safe', () => {
  // B9 absent shouldn't push the verdict to broken on its own.
  const r = classifyTextProbe(
    { maxBaselineDeltaPx: 0.1 },
    null,
    { maxMeanDeltaPx: 0.1, maxStddevDeltaPx: 0.1 },
  );
  assert.equal(r, 'agree');
});

// ── worstTextProbeLabel ───────────────────────────────────────────────────

test('worstTextProbeLabel: empty list → "agree"', () => {
  assert.equal(worstTextProbeLabel([]), 'agree');
});

test('worstTextProbeLabel: picks the most severe', () => {
  assert.equal(worstTextProbeLabel(['agree', 'sub-px-drift', 'broken']), 'broken');
  assert.equal(worstTextProbeLabel(['agree', 'kerning-drift', 'sub-px-drift']), 'kerning-drift');
});

test('worstTextProbeLabel: skips null/undefined', () => {
  assert.equal(worstTextProbeLabel([null, 'sub-px-drift', undefined]), 'sub-px-drift');
});

// ── textProbeBadgeColor + version stamp ───────────────────────────────────

test('textProbeBadgeColor: every label → a colour', () => {
  for (const l of ['agree', 'sub-px-drift', 'kerning-drift', 'broken']) {
    assert.match(textProbeBadgeColor(l), /^#[0-9a-fA-F]{3,6}$/);
  }
  // Defensive default for future labels — must still return SOMETHING.
  assert.match(textProbeBadgeColor('mystery-label'), /^#[0-9a-fA-F]{3,6}$/);
});

test('TEXT_PROBE_CLASSIFIER_VERSION is a positive integer', () => {
  // Bumping this requires a deliberate change — this test catches an
  // accidental import-time crash on the constant.
  assert.equal(typeof TEXT_PROBE_CLASSIFIER_VERSION, 'number');
  assert.ok(TEXT_PROBE_CLASSIFIER_VERSION >= 1);
});
