#!/usr/bin/env node
// Unit tests for tools/visual/a11y-audit.mjs.
//
// Built round 72. Following the round 56 pattern (css-to-ir.test.mjs)
// — pin the contract for the testing-side script's pure logic so a future
// regression doesn't have to be discovered by failing in production.
//
// Coverage focus: shapeAxeResult (extracted from auditWebFixture in
// round 72), BLOCKED_CRITERIA structure, and the iOS/Android stub
// return shape.
//
// Run via `node --test tools/visual/a11y-audit.test.mjs` standalone or as
// part of `node --test tools/visual/*.test.mjs` (smoke.sh wires this in).

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  shapeAxeResult,
  BLOCKED_CRITERIA,
  iOSStub,
  androidStub,
  AUDITED_RULE,
  AXE_CDN,
} from './a11y-audit.mjs';

// ── Constants ──────────────────────────────────────────────────────────────

test('AUDITED_RULE is color-contrast', () => {
  // Pin so a future "I'll add violation-X to the audit" change doesn't
  // accidentally widen scope and produce noisy reports without an
  // explicit decision.
  assert.equal(AUDITED_RULE, 'color-contrast');
});

test('AXE_CDN points to a pinned axe-core version', () => {
  // Pin axe-core version. CDN URL drift would mean upgrading axe — needs
  // explicit acknowledgment because new axe versions emit different
  // violation shapes.
  assert.match(AXE_CDN, /axe-core\/4\.\d+\.\d+\/axe\.min\.js/);
});

// ── BLOCKED_CRITERIA ────────────────────────────────────────────────────────

test('BLOCKED_CRITERIA has exactly 7 entries (the WCAG criteria the IR cant support today)', () => {
  // Count is meaningful: TIER11_A11Y.md numbers rows 1-8 (criterion 3 is
  // color-contrast = the one we DO audit). Rows 9-10 are iOS/Android
  // harnesses, not WCAG criteria. So 7 blocked criteria here matches
  // the doc's "blocked-IR" count.
  assert.equal(BLOCKED_CRITERIA.length, 7);
});

test('every BLOCKED_CRITERIA entry has id + aXeRule + reason', () => {
  for (const entry of BLOCKED_CRITERIA) {
    assert.ok(typeof entry.id === 'string' && entry.id.length > 0,
      `entry missing id: ${JSON.stringify(entry)}`);
    assert.ok(typeof entry.aXeRule === 'string' && entry.aXeRule.length > 0,
      `${entry.id}: missing aXeRule`);
    assert.ok(typeof entry.reason === 'string' && entry.reason.length > 10,
      `${entry.id}: reason too short or missing`);
  }
});

test('BLOCKED_CRITERIA includes screen-reader-output and focus-order', () => {
  // Spot-check that the most-commonly-audited WCAG criteria are present
  // (per TIER11_A11Y.md table rows 1-8).
  const ids = BLOCKED_CRITERIA.map((e) => e.id);
  assert.ok(ids.includes('screen-reader-output'));
  assert.ok(ids.includes('focus-order'));
  assert.ok(ids.includes('keyboard-nav'));
  assert.ok(ids.includes('aria-labels'));
});

// ── iOS / Android stubs ─────────────────────────────────────────────────────

test('iOSStub returns { skipped: true } with TIER11 row 9 reason', () => {
  const r = iOSStub();
  assert.equal(r.skipped, true);
  assert.match(r.reason, /XCUIAccessibility/);
  assert.match(r.reason, /TIER11 row 9/);
});

test('androidStub returns { skipped: true } with TIER11 row 10 reason', () => {
  const r = androidStub();
  assert.equal(r.skipped, true);
  assert.match(r.reason, /Espresso AccessibilityChecks/);
  assert.match(r.reason, /TIER11 row 10/);
});

// ── shapeAxeResult ──────────────────────────────────────────────────────────

test('shapeAxeResult: clean run reports passed=true, no violations', () => {
  const axeResult = {
    violations: [],
    passes: [{ id: 'color-contrast' }, { id: 'other-rule' }],
    inapplicable: [],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, 'http://example.test/?fixture=Foo');
  assert.equal(r.passed, true);
  assert.equal(r.violationCount, 0);
  assert.equal(r.failures.length, 0);
  assert.equal(r.url, 'http://example.test/?fixture=Foo');
  assert.equal(r.rule, 'color-contrast');
});

test('shapeAxeResult: single contrast violation extracts data correctly', () => {
  // Mirrors the real aXe color-contrast violation shape we observed
  // in round 41 (NavHeader 2.28 contrast).
  const axeResult = {
    violations: [{
      id: 'color-contrast',
      nodes: [{
        target: ['span'],
        impact: 'serious',
        failureSummary: 'Element has insufficient color contrast',
        any: [{
          data: {
            contrastRatio: 2.28,
            expectedContrastRatio: '4.5:1',
            fgColor: '#b8cdf0',
            bgColor: '#3b82f6',
            fontSize: '12.0pt (16px)',
            fontWeight: 'normal',
          },
        }],
      }],
    }],
    passes: [],
    inapplicable: [],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, 'http://example.test/?fixture=NavHeader');
  assert.equal(r.passed, false);
  assert.equal(r.violationCount, 1);
  assert.equal(r.failures.length, 1);
  const f = r.failures[0];
  assert.equal(f.selector, 'span');
  assert.equal(f.contrastRatio, 2.28);
  assert.equal(f.expectedContrastRatio, '4.5:1');
  assert.equal(f.fgColor, '#b8cdf0');
  assert.equal(f.bgColor, '#3b82f6');
  assert.equal(f.impact, 'serious');
});

test('shapeAxeResult: multiple violations × multiple nodes flatten correctly', () => {
  // violationCount counts NODES, not violation entries — defensive
  // against axe grouping multiple offenders under one violation.
  const axeResult = {
    violations: [
      { id: 'color-contrast', nodes: [{ target: ['span'] }, { target: ['div'] }] },
      { id: 'color-contrast', nodes: [{ target: ['p'] }] },
    ],
    passes: [],
    inapplicable: [],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, '/');
  assert.equal(r.violationCount, 3);
  assert.equal(r.failures.length, 3);
});

test('shapeAxeResult: inapplicable rule reported correctly', () => {
  // "no text in fixture" → axe puts color-contrast in inapplicable[].
  const axeResult = {
    violations: [],
    passes: [],
    inapplicable: [{ id: 'color-contrast' }],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, '/');
  assert.equal(r.inapplicable, true);
  assert.equal(r.passed, false);
  assert.equal(r.violationCount, 0);
});

test('shapeAxeResult: defensive against missing fields', () => {
  // Defensive: if axe gives back a malformed result (missing arrays),
  // we should not crash. Used to seed the report with a sane shape
  // even when axe is broken.
  const r = shapeAxeResult({}, '/');
  assert.equal(r.passed, false);
  assert.equal(r.violationCount, 0);
  assert.equal(r.failures.length, 0);
  assert.equal(r.inapplicable, false);
});

test('shapeAxeResult: falls back to .all[] data when .any[] is absent', () => {
  // Some aXe rules attach data under `all` instead of `any`. The
  // shaper checks both with `??` fallback.
  const axeResult = {
    violations: [{
      id: 'color-contrast',
      nodes: [{
        target: ['span'],
        impact: 'serious',
        all: [{ data: { contrastRatio: 3.5, fgColor: '#ccc' } }],
      }],
    }],
    passes: [],
    inapplicable: [],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, '/');
  assert.equal(r.failures[0].contrastRatio, 3.5);
  assert.equal(r.failures[0].fgColor, '#ccc');
});

test('shapeAxeResult: custom rule param overrides default', () => {
  // Lets a future "audit a different rule" caller reuse the shape
  // without monkey-patching the constant.
  const axeResult = {
    violations: [],
    passes: [{ id: 'image-alt' }],
    inapplicable: [],
    incomplete: [],
  };
  const r = shapeAxeResult(axeResult, '/', 'image-alt');
  assert.equal(r.rule, 'image-alt');
  assert.equal(r.passed, true);
});
