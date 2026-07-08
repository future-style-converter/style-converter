#!/usr/bin/env node
// Unit tests for testing/css-to-ir.mjs.
//
// Built round 56. Until this round css-to-ir.mjs had no test coverage despite
// being the critical Tier 9 silent-corruption surface (rounds 43-51 added
// extractRootVars + resolveVars + isVisible — each with subtle edge cases
// that took a real-world bug to surface). This file pins the contract:
//
//   - extractRootVars: handles :root / html / body / * scope, including
//     minified CSS where the selector follows `}`.
//   - resolveVars: recursive substitution with fallback, depth-bail.
//   - isVisible: rejects no-render-effect declarations (opacity:1 is the
//     archetype false-positive caught in round 51).
//   - extractRules: runs the full pipeline (extract → var-resolve →
//     visible-filter) on a small CSS input and returns a known shape.
//
// Run via `node --test testing/css-to-ir.test.mjs`. No vitest / jest
// dependency; uses Node's built-in test runner.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import {
  stripComments,
  extractRootVars,
  resolveVars,
  isVisible,
  extractRules,
  VISIBLE_PROPS,
} from './css-to-ir.mjs';

// ── stripComments ───────────────────────────────────────────────────────────

test('stripComments removes /* … */ blocks', () => {
  assert.equal(stripComments('a /* x */ b'), 'a  b');
});

test('stripComments handles multi-line comments', () => {
  const input = `a /* line1\nline2\n*/ b`;
  assert.equal(stripComments(input), 'a  b');
});

test('stripComments leaves CSS without comments untouched', () => {
  const input = '.foo { color: red; }';
  assert.equal(stripComments(input), input);
});

// ── extractRootVars ─────────────────────────────────────────────────────────

test('extractRootVars finds :root vars', () => {
  const vars = extractRootVars(':root { --primary: #3b82f6; --space: 16px }');
  assert.equal(vars['--primary'], '#3b82f6');
  assert.equal(vars['--space'], '16px');
});

test('extractRootVars finds html and body vars too', () => {
  const css = 'html { --h: 1 } body { --b: 2 }';
  const vars = extractRootVars(css);
  assert.equal(vars['--h'], '1');
  assert.equal(vars['--b'], '2');
});

test('extractRootVars handles :root, html selector list', () => {
  const vars = extractRootVars(':root, html { --shared: ok }');
  assert.equal(vars['--shared'], 'ok');
});

test('extractRootVars handles minified CSS where :root follows }', () => {
  // Round 44 fix: real Stripe minified CSS had `}@media{...}}:root{`
  // — the :root selector starts directly after a brace with no whitespace.
  // The previous anchored regex missed it. This pins the lookbehind fix.
  const css = '.foo{color:red}@media (min-width:1px){.bar{margin:0}}:root{--late:found}';
  const vars = extractRootVars(css);
  assert.equal(vars['--late'], 'found');
});

test('extractRootVars ignores non-custom properties on :root', () => {
  const vars = extractRootVars(':root { color: red; --x: 1 }');
  assert.equal(vars['--x'], '1');
  assert.equal(vars.color, undefined);
});

test('extractRootVars strips !important', () => {
  const vars = extractRootVars(':root { --p: red !important }');
  assert.equal(vars['--p'], 'red');
});

test('extractRootVars: last-write-wins for same var name', () => {
  // Mirrors CSS cascade for same-specificity rules.
  const vars = extractRootVars(':root { --x: 1 } :root { --x: 2 }');
  assert.equal(vars['--x'], '2');
});

test('extractRootVars does NOT match .NotRoot { … }', () => {
  // The lookbehind `(?<![\w-:])` should reject mid-identifier matches.
  const vars = extractRootVars('.NotRoot { --x: nope }');
  assert.equal(vars['--x'], undefined);
});

// ── resolveVars ─────────────────────────────────────────────────────────────

test('resolveVars substitutes a defined var', () => {
  assert.equal(resolveVars('color: var(--p)', { '--p': 'red' }), 'color: red');
});

test('resolveVars uses fallback when var undefined', () => {
  assert.equal(resolveVars('var(--missing, blue)', {}), 'blue');
});

test('resolveVars leaves unresolved vars intact', () => {
  // Defensive: don't silently drop unresolved refs — let the renderer
  // see them and report.
  assert.equal(resolveVars('var(--missing)', {}), 'var(--missing)');
});

test('resolveVars recursively resolves chained vars', () => {
  const vars = { '--a': 'var(--b)', '--b': 'final' };
  assert.equal(resolveVars('var(--a)', vars), 'final');
});

test('resolveVars bails at depth 10 on self-reference', () => {
  // No infinite loop on `--a: var(--a)`. Should return SOMETHING (the
  // partially-substituted string) rather than hang or stack-overflow.
  const vars = { '--a': 'var(--a)' };
  const result = resolveVars('var(--a)', vars);
  // Just verify it returns and contains var( (depth bail leaves the
  // unresolved ref intact at the bottom of recursion).
  assert.match(result, /var\(/);
});

// ── isVisible ───────────────────────────────────────────────────────────────

test('isVisible: background-color counts as visible', () => {
  assert.equal(isVisible({ 'background-color': 'red' }), true);
});

test('isVisible: opacity:1 is NOT visible (round-51 fix)', () => {
  // Round 51 caught CNN's `header__navigation-separator` having
  // `opacity: 1; border: none; height: 2px` and being mistakenly
  // counted as visible. opacity:1 is the default no-op; transition
  // setups commonly declare it. Pin the fix.
  assert.equal(isVisible({ opacity: '1' }), false);
});

test('isVisible: opacity is NOT in VISIBLE_PROPS', () => {
  assert.equal(VISIBLE_PROPS.has('opacity'), false);
});

test('isVisible: border:none is NOT visible', () => {
  // Explicit "none" values count as invisible.
  assert.equal(isVisible({ border: 'none' }), false);
});

test('isVisible: background:none + color:#fff IS visible (color counts)', () => {
  // First match short-circuits, so order in iteration matters less than
  // SET membership.
  assert.equal(isVisible({ background: 'none', color: '#fff' }), true);
});

test('isVisible: width/height/padding/margin alone are NOT visible', () => {
  assert.equal(isVisible({ width: '100%', height: '2px', margin: '0' }), false);
});

test('isVisible: transition alone is NOT visible', () => {
  assert.equal(isVisible({ transition: 'opacity .25s' }), false);
});

// ── extractRules (integration) ──────────────────────────────────────────────

test('extractRules end-to-end: var resolution + visibility filter', () => {
  const css = `
    :root { --bg: #c00 }
    .visible-button { background-color: var(--bg); padding: 8px }
    .invisible-only-margin { margin: 0; transition: opacity .25s }
    .a-multi-class.combo { color: red }
  `;
  const rootVars = extractRootVars(css);
  const rules = extractRules(css, rootVars);

  // .visible-button should be present, with var resolved.
  const button = rules.find((r) => r.name === 'visible-button');
  assert.ok(button, 'visible-button should be extracted');
  assert.equal(button.props['background-color'], '#c00');
  assert.equal(button.props.padding, '8px');

  // .invisible-only-margin should NOT be filtered by extractRules
  // (it filters by class-name shape only); the visibility filter runs
  // in the main script body. Confirm it's still in the rules list,
  // just to verify extractRules's scope.
  const margin = rules.find((r) => r.name === 'invisible-only-margin');
  assert.ok(margin, 'invisible-only-margin extracted by extractRules (visibility filter is downstream)');

  // Multi-class selectors should be skipped.
  assert.equal(rules.find((r) => r.name === 'a-multi-class.combo'), undefined);
});

test('extractRules drops --custom-property declarations on .class scopes', () => {
  // Round 43 design: theme-scoped vars (`.dark { --bg: #000 }`) are
  // out of scope for the global-vars-only adapter. Verify they're not
  // treated as regular declarations.
  const css = '.themed { --theme-bg: #000; color: red }';
  const rules = extractRules(css, {});
  const themed = rules.find((r) => r.name === 'themed');
  assert.ok(themed);
  assert.equal(themed.props.color, 'red');
  assert.equal(themed.props['--theme-bg'], undefined);
});
