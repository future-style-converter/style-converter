#!/usr/bin/env node
//
// Unit tests for the wave-50 lane B7 root-scope conflict resolution in
// propsForBodyRoot (tools/titan/extract-fixture.mjs; BACKLOG queue 5(g)).
//
// Own file, same reason as extract-fixture-ua-hr.test.mjs: the bake lands as
// a self-contained patch against a file another lane is editing in the same
// wave, and `node --test tools/titan/*.test.mjs` picks it up with no runner
// change.
//
// Every stylesheet below is VERBATIM from the gate corpus — the three
// documents that are the complete html-vs-body conflict census over the 1435
// wave49-final scored tests.

import { test } from 'node:test';
import assert from 'node:assert/strict';

import { parseCss, propsForBodyRoot } from './extract-fixture.mjs';

/** Merge a stylesheet through the root-scope collector. */
const root = (css) => propsForBodyRoot(parseCss(css)).props;

test('propsForBodyRoot: <body> wins a geometry conflict with <html>', () => {
  // css-contain/contain-html-overflow-002, VERBATIM. `html { height: 400px }`
  // came LAST in source order and used to delete body's 200px, which made the
  // synthetic root a 200×400 clipper that clipped nothing — so the red <div>
  // the ref forbids got painted (wave49-final web f 0.8709 · iOS f 0.8705 ·
  // Android f 0.8698).
  const props = root(`
html, body, p, div {
    margin: 0;
    width: 200px;
    height: 200px;
}
div { background: red; }
body {
    overflow: hidden;
}
html {
    height: 400px;
    contain: paint;
}`);
  assert.equal(props.height, '200px');     // the body box's own height
  assert.equal(props.width, '200px');      // no conflict — unchanged
  assert.equal(props.overflow, 'hidden');  // body-only — unchanged
  assert.equal(props.contain, 'paint');    // html-only — unchanged
});

test('propsForBodyRoot: <html> wins the canvas background', () => {
  // CSS2/css21-errata/s-11-1-1b-005, VERBATIM. body's black used to win by
  // source order and flooded the canvas — the wave49-final web capture is
  // 234000/234000 pixels black against a white ref (all three f 0.0001).
  // css-backgrounds-3 §2.11.2 puts the root element's background on the
  // canvas; body's propagates only when the root's is transparent.
  const props = root(`
 html { overflow:hidden; display:table; border-spacing:0; background:white; margin:40px 8px 8px }
 head { display:caption; margin-bottom:10px }
 body { display:table-cell; width:20px; height:20px; margin-top:-15px; background:black }
 p { position:absolute; top:0 }`);
  assert.equal(props.background, 'white');       // canvas → root element
  assert.equal(props.display, 'table-cell');     // geometry → body box
  assert.equal(props.overflow, 'hidden');        // html-only — unchanged
});

test('propsForBodyRoot: a conflict body already won stays byte-identical', () => {
  // css-writing-modes/inline-box-border-vlr-001, VERBATIM — body's `margin`
  // already came last, so this document must not move at all.
  const props = root(`
html {
  margin: 0;
  font-size: 20px;
}
body {
  margin: 1em;
  border: 1px solid blue;
}
body > div {
  margin-bottom: 2em;
  border: 1px solid black;
}`);
  assert.equal(props.margin, '1em');
  assert.equal(props['font-size'], '20px');      // html-only — still folded
  assert.equal(props.border, '1px solid blue');  // body-only — still folded
});

test('propsForBodyRoot: no conflict ⇒ source order, and key order, untouched', () => {
  // contain-html-overflow-001 is byte-identical to -002 except its <html>
  // rule declares only `contain:` — no conflict, so nothing may change. The
  // KEY ORDER matters as much as the values: it becomes the emitted IR
  // property order, so the repair must overwrite in place, never re-merge.
  const props = root(`
html, body, p, div {
    margin: 0;
    width: 200px;
    height: 200px;
}
div { background: red; }
body {
    overflow: hidden;
}
html {
    contain: layout;
}`);
  assert.equal(props.height, '200px');
  assert.deepEqual(Object.keys(props),
    ['margin', 'width', 'height', 'overflow', 'contain']);
});

test('propsForBodyRoot: `:root` counts as the html scope', () => {
  // Selectors-4 §8.1 — `:root` IS the document root element, so a geometry
  // conflict against `body` resolves the same way a bare `html` one does.
  const props = root(':root { height: 400px } body { height: 200px }');
  assert.equal(props.height, '200px');
  // …and the background family reverses, for `:root` exactly as for `html`.
  const bg = root(':root { background: white } body { background: black }');
  assert.equal(bg.background, 'white');
});

test('propsForBodyRoot: a `*` rule is HTML scope, and IS re-decided', () => {
  // wave-50 fix lane F3 (skeptic S2 defect 3). The lane-B7 banner used to
  // carry a "named residual" saying a `*` root-scope rule conflicting with an
  // html/body one was "still decided by source order". That was FALSE, and
  // the `rootScopeOf` branch that implied it (`needTag === '*'` → a third
  // `star` bucket) was unreachable: `parseCompound` consumes `*` as
  // "universal — no constraint" and never writes `needTag`, so a `*` rule
  // arrives with `needTag === null` and buckets as HTML scope, exactly like
  // `:root`. Instrumented over the synthetic cases and all 1435 corpus tests
  // the `'*'` arm fired 0 times; the no-tag fallthrough took 41.
  //
  // These two lines pin the behaviour that is actually there, in BOTH
  // directions of the family split, and each one FLIPS against HEAD
  // (747b28e4), so neither is a tautology:
  //   canvas background → the root element's wins (css-backgrounds-3
  //   §2.11.2), so `*`'s red survives even though body's black came later;
  assert.equal(root('*{background:red} body{background:black}').background, 'red');
  //   every other property describes the body box, so body's 200px wins even
  //   though `*`'s 300px came later.
  assert.equal(root('body{height:200px} *{height:300px}').height, '200px');
  // The HONEST residual that replaces the false one: this is a re-decision by
  // FAMILY, not by Selectors-4 §17 specificity — `*` has specificity 0 and
  // would lose to both `html` and `body` in a real cascade, which this pass
  // does not model. Corpus exposure is nil: of the 1435 wave49-final tests,
  // 17 carry a bare `*` root-scope rule (24 rules) and ZERO declare a
  // property an `html`/`body` root-scope rule in the same document also
  // declares — a complete census, because zero corpus tests link an external
  // stylesheet.
  assert.equal(root('*{height:300px}').height, '300px');       // no conflict ⇒ untouched
  assert.equal(root('*{background:red}').background, 'red');   // …either family
});
