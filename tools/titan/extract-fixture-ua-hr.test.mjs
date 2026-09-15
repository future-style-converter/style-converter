#!/usr/bin/env node
//
// Unit tests for the wave-50 lane B7 UA <hr> separator bake in
// tools/titan/extract-fixture.mjs (BACKLOG ranked-queue item 5(a)).
//
// Lives in its OWN file rather than in extract-fixture.test.mjs so the bake
// can land as a self-contained patch against a file another lane is editing
// in the same wave; `node --test tools/titan/*.test.mjs` picks it up with no
// runner change. Same discipline as that suite: pure helpers only, no file
// IO, <100 ms.
//
// Every payload below is VERBATIM from the gate corpus — the four tests that
// are the complete `<hr>` census over the 1435 wave49-final scored tests
// (css-images/gradient/gradient-hue-direction, css-pseudo/
// active-selection-057, css-writing-modes/direction-upright-001 and -002).

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { uaHrProps, parseCss, buildComponents } from './extract-fixture.mjs';

// The twelve declarations the bake stands for. Kept as one literal so a
// change to UA_HR_PROPS has to be restated here deliberately, not absorbed.
const UA_HR_BAKE = {
  display: 'block',
  height: '0px',
  'box-sizing': 'content-box',
  'border-top-width': '1px',
  'border-right-width': '1px',
  'border-bottom-width': '1px',
  'border-left-width': '1px',
  'border-style': 'inset',
  'border-color': '#eeeeee',
  'margin-top': '8px',
  'margin-bottom': '8px',
  overflow: 'hidden',
};

test('uaHrProps: only <hr> carries the UA separator rule', () => {
  // HTML Rendering §15.3.11 scopes the rule to hr and nothing else — a div or
  // a br must come back null so the caller's bag is untouched.
  assert.equal(uaHrProps('div', {}), null);
  assert.equal(uaHrProps('br', {}), null);
  assert.equal(uaHrProps('p', {}), null);
});

test('uaHrProps: a rule-less <hr> takes the full UA box', () => {
  // gradient-hue-direction's three <hr>s match no author rule at all — the
  // exact case that used to fall through to the 100×100 empty-node
  // placeholder (capture 390×894 vs the 390×600 frozen ref).
  const out = uaHrProps('hr', {});
  assert.deepEqual(out.props, UA_HR_BAKE);
  assert.equal(out.declined, undefined);
});

test('uaHrProps: an author declaration outside the guard set still bakes', () => {
  // direction-upright-001/-002 declare `hr { clear: both }`. `clear` names no
  // part of the separator box, so the UA rule applies underneath it exactly
  // as the browser applies it.
  const out = uaHrProps('hr', { clear: 'both' });
  assert.deepEqual(out.props, UA_HR_BAKE);
});

test('uaHrProps: an author-redefined <hr> declines the whole bake', () => {
  // active-selection-057, VERBATIM author bag: `background-color: transparent;
  // height: 100px` plus `border: none 0px`. Two guards fire (height, border),
  // and the bake is atomic, so NOTHING is emitted — this test passes on all
  // three platforms today (wave49-final web P 1.0000 · ios P 0.9994 ·
  // android P 0.9986) and must stay byte-identical.
  const out = uaHrProps('hr', {
    'background-color': 'transparent', height: '100px', border: 'none 0px',
  });
  assert.equal(out.declined, true);
  assert.equal(out.props, undefined);
});

test('uaHrProps: each guard family alone is enough to decline', () => {
  // One representative per guard family, so a guard list that loses a member
  // fails here rather than silently half-baking a box in a future wave.
  for (const bag of [
    { display: 'none' },                    // display
    { 'block-size': '4px' },                // height family (logical)
    { 'box-sizing': 'border-box' },         // box-sizing
    { 'border-width': '3px' },              // all four side widths
    { 'border-block': '2px solid red' },    // logical border shorthand
    { 'border-top-style': 'dashed' },       // style family
    { 'border-left-color': 'red' },         // colour family
    { 'margin-block-start': '0' },          // margin-top family
    { margin: '0' },                        // both margin families
    { 'overflow-y': 'visible' },            // overflow family
  ]) {
    assert.equal(uaHrProps('hr', bag).declined, true,
      `expected decline for ${JSON.stringify(bag)}`);
  }
});

test('buildComponents: a rule-less <hr> emits the UA box, not the 100×100 placeholder', () => {
  // End-to-end through the component builder, on the shape
  // gradient-hue-direction has: styled divs around bare <hr>s.
  const css = 'div { width: 200px; height: 50px; margin: 10px }';
  const html = '<body><div></div><hr><div></div></body>';
  const { components } = buildComponents(html, parseCss(css), 'hue');
  const hr = Object.values(components).find((c) => c._tag === 'hr');
  assert.ok(hr, 'the <hr> must survive as its own component');
  // The defect signature: a 100×100 white square where the ref paints 2px.
  assert.notEqual(hr.properties.width, '100px');
  assert.notEqual(hr.properties.height, '100px');
  // The repair: the UA box, plus the LOUD reason so the geometry is never
  // mistaken for an author declaration.
  assert.equal(hr.properties.height, '0px');
  assert.equal(hr.properties['border-style'], 'inset');
  assert.equal(hr.properties['border-color'], '#eeeeee');
  assert.equal(hr.properties['box-sizing'], 'content-box');
  assert.ok(hr._lossyReasons.includes('ua-hr-separator-baked'));
});

test('buildComponents: an author-sized <hr> keeps its own bag and says so', () => {
  // active-selection-057's shape. The author bag survives untouched and the
  // decline is announced — no silent fallthrough.
  const css = 'hr#subtest2 { background-color: transparent; height: 100px }'
    + ' hr#subtest2 { border: none 0px }';
  const html = '<body><hr id="subtest2"></body>';
  const { components } = buildComponents(html, parseCss(css), 'sel-057');
  const hr = Object.values(components).find((c) => c._tag === 'hr');
  assert.equal(hr.properties.height, '100px');
  assert.equal(hr.properties.border, 'none 0px');
  assert.equal(hr.properties['border-style'], undefined);
  assert.equal(hr.properties['margin-top'], undefined);
  assert.ok(hr._lossyReasons.includes('ua-hr-separator-declined'));
});
