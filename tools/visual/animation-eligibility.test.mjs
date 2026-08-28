#!/usr/bin/env node
// Unit tests for animation-eligibility.mjs — the sweep's expected-motion rule.
//
// Both directions matter and both have a measured failure behind them, so
// each is pinned with the fixture that produced it.

import { test } from 'node:test';
import assert from 'node:assert/strict';
import { readFileSync } from 'node:fs';
import { resolve, dirname } from 'node:path';
import { fileURLToPath } from 'node:url';

import {
  eligibleToMove,
  declaresBucketFor,
  declaresKeyframeAnimation,
  componentNameOf,
} from './animation-eligibility.mjs';

const __dirname = dirname(fileURLToPath(import.meta.url));
const fixture = (p) => JSON.parse(readFileSync(resolve(__dirname, '../../fixtures', p), 'utf8'));

test('an unforced run places no restriction', () => {
  // The fixture alone cannot say which components move when nothing is
  // forced, so the sweep must expect all of them.
  assert.equal(eligibleToMove({ components: { A: {} } }, ''), null);
});

test('only components with a bucket for the forced state are eligible', () => {
  // The measured wrong-red: transitions.json under `active` has exactly one
  // eligible component. A state-blind rule flags 9 of 12 series.
  const set = eligibleToMove(fixture('fidelity/motion/transitions.json'), 'active');
  assert.deepEqual([...set], ['MT_WidthGrow']);
});

test('each forced state selects its own component in transitions.json', () => {
  const doc = fixture('fidelity/motion/transitions.json');
  assert.deepEqual([...eligibleToMove(doc, 'hover')], ['MT_BgFade']);
  assert.deepEqual([...eligibleToMove(doc, 'focus')], ['MT_AllChannels']);
  assert.deepEqual([...eligibleToMove(doc, 'checked')], ['MT_Delayed']);
});

test('a state no component declares yields an empty set, not a crash', () => {
  const set = eligibleToMove(fixture('fidelity/motion/transitions.json'), 'disabled');
  assert.equal(set.size, 0);
});

test('keyframe components stay eligible under ANY forced state', () => {
  // keyframes-basic.json declares no selectors at all. A state-only rule
  // would call all 8 ineligible while all 8 correctly animate — 24 false
  // "leak" rows, the exact wrong-red this rule exists to prevent.
  const doc = fixture('fidelity/motion/keyframes-basic.json');
  const set = eligibleToMove(doc, 'hover');
  assert.equal(set.size, Object.keys(doc.components).length);
  assert.ok(set.has('MK_FillBoth'));
});

test('the two eligibility rules are independent', () => {
  const bucketOnly = { selectors: [{ selector: ':hover', properties: {} }], properties: {} };
  const animOnly = { properties: { 'animation-name': 'x' } };
  const neither = { properties: { width: '10px' } };
  assert.equal(declaresBucketFor(bucketOnly, 'hover'), true);
  assert.equal(declaresKeyframeAnimation(bucketOnly), false);
  assert.equal(declaresBucketFor(animOnly, 'hover'), false);
  assert.equal(declaresKeyframeAnimation(animOnly), true);
  assert.equal(declaresBucketFor(neither, 'hover'), false);
  assert.equal(declaresKeyframeAnimation(neither), false);
});

test('both selector spellings are accepted', () => {
  // A mismatch here would silently empty the eligible set and turn every
  // real transition into a reported failure.
  assert.equal(declaresBucketFor({ selectors: [{ selector: ':hover' }] }, 'hover'), true);
  assert.equal(declaresBucketFor({ selectors: [{ condition: 'hover' }] }, 'hover'), true);
  assert.equal(declaresBucketFor({ selectors: [{ selector: ':hover' }] }, 'active'), false);
});

test('the animation matcher does not catch unrelated properties', () => {
  // `animation-*` and the shorthand only. A prefix-blind match would make
  // every component eligible and silently disable the leak assertion.
  assert.equal(declaresKeyframeAnimation({ properties: { animation: 'x 1s' } }), true);
  assert.equal(declaresKeyframeAnimation({ properties: { 'animation-delay': '1s' } }), true);
  assert.equal(declaresKeyframeAnimation({ properties: { 'transition-property': 'all' } }), false);
  assert.equal(declaresKeyframeAnimation({ properties: { 'animate-me': '1' } }), false);
});

test('componentNameOf strips the capture index and extension', () => {
  assert.equal(componentNameOf('003_MT_Delayed.png'), 'MT_Delayed');
  assert.equal(componentNameOf('000_MK_Fade.png'), 'MK_Fade');
  // Names containing digits must survive.
  assert.equal(componentNameOf('012_Grid_2x2.png'), 'Grid_2x2');
});
