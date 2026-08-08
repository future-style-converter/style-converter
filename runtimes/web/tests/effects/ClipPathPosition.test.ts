// ClipPathPosition.test.ts — pins the `at <position>` clause, the
// deep-flatten unwrapping and the shape()/rect() paths through the web
// extractor. Mirrors the Kotlin suite at
// src/test/kotlin/.../ClipPathPositionTest.kt so the invariant trips on
// both sides if either regresses.
//
// Wave-37 lane W3 bugs pinned here:
//  1. The Kotlin `parsePosition` accepted only bare lengths + a literal
//     `center`, so `circle(50% at left bottom)` reached the runtimes with
//     NO `at` clause at all. It now emits the full css-values-4 grammar,
//     with `xEdge`/`yEdge` for the right/bottom-anchored offset arm.
//  2. The IR property serializer deep-flattens a `{type, <one object
//     field>}` node, so `circle(50%)` arrives as
//     `{type:'circle', original:{…}}` and `circle(at 200px 150px)` as
//     `{type:'circle', x:…, y:…}`. Web handled neither: the first fell
//     through to the `closest-side` default (right only on a square box),
//     the second lost its centre.
//  3. `rect()` reached web as `rect(auto auto auto auto)` because of an
//     elvis-on-`put` bug in the Kotlin serializer; the round clause was
//     dropped here even when present.
//  4. `shape()` had no parser branch at all and fell out of the IR.

import { describe, it, expect } from 'vitest';
import { extractClipPath } from '../../src/engine/effects/clip/ClipPathExtractor';

const p = (type: string, data: unknown) => ({ type, data });
const css = (data: unknown) => extractClipPath([p('ClipPath', data)]).value;

// Percent / px IRLength wire shapes, spelled once.
const pct = (v: number) => ({ original: { v, u: 'PERCENT' } });
const px = (v: number) => ({ px: v });

describe('extractClipPath — at <position>', () => {

  it('keyword-only position arrives normalised to the default origin', () => {
    // `circle(50% at left bottom)` — the parser resolves the keywords to
    // 0% from left / 100% from top, so no edge field is needed and a
    // consumer that ignores edges is still correct.
    expect(css({ type: 'circle', r: pct(50), pos: { x: pct(0), y: pct(100) } }))
      .toBe('circle(50% at 0% 100%)');
  });

  it('right/bottom-anchored offsets keep the two-token CSS spelling', () => {
    // `circle(50% at right 40px bottom 40px)`. 40px in from the RIGHT
    // edge cannot be rewritten as a left-origin length without the box
    // width, so the IR carries the edge and we re-emit the author's
    // grammar — which CSS accepts verbatim inside `at`.
    expect(css({
      type: 'circle',
      r: pct(50),
      pos: { x: px(40), y: px(40), xEdge: 'right', yEdge: 'bottom' },
    })).toBe('circle(50% at right 40px bottom 40px)');
  });

  it('mixed anchoring: left-origin x, bottom-anchored y', () => {
    // `circle(50% at left 40px bottom 40px)` — only the y axis needs an
    // edge; x is already measured from the default origin.
    expect(css({
      type: 'circle',
      r: pct(50),
      pos: { x: px(40), y: px(40), yEdge: 'bottom' },
    })).toBe('circle(50% at 40px bottom 40px)');
  });

  it('ellipse carries the same at-clause treatment', () => {
    // `ellipse(farthest-side closest-side at top 40px right 60px)`:
    // keyword radii on both axes plus a right-anchored x.
    expect(css({
      type: 'ellipse',
      rx: 'farthest-side',
      ry: 'closest-side',
      pos: { x: px(60), y: px(40), xEdge: 'right' },
    })).toBe('ellipse(farthest-side closest-side at right 60px 40px)');
  });

  it('at center still serialises byte-identically to the pre-fix wire', () => {
    // The one position form the old parser DID understand. Its wire
    // shape must not shift, or every committed baseline moves.
    expect(css({ type: 'circle', r: pct(50), pos: { x: pct(50), y: pct(50) } }))
      .toBe('circle(50% at 50% 50%)');
  });
});

describe('extractClipPath — deep-flattened wire shapes', () => {

  it('flattened percentage radius is no longer read as closest-side', () => {
    // `circle(50%)` has ONE object-valued field, so the serializer inlines
    // the IRLength: {type:'circle', original:{v:50,u:'PERCENT'}}. Web had
    // a branch for the `px` spelling but not this one, and silently fell
    // back to `closest-side` — indistinguishable on a square box, wrong on
    // every other.
    expect(css({ type: 'circle', ...pct(50) })).toBe('circle(50%)');
  });

  it('flattened px radius keeps working (pre-existing branch)', () => {
    expect(css({ type: 'circle', ...px(40) })).toBe('circle(40px)');
  });

  it('flattened position (radius absent) is recovered', () => {
    // `circle(farthest-corner at 200px 150px)`: the corner keyword is not
    // in the css-shapes `<shape-radius>` grammar so the radius is dropped,
    // which leaves `pos` as the single object field and flattens it up.
    // The centre must still reach CSS.
    expect(css({ type: 'circle', x: px(200), y: px(150) }))
      .toBe('circle(closest-side at 200px 150px)');
  });

  it('a genuinely radius-less, position-less circle keeps the spec default', () => {
    expect(css({ type: 'circle' })).toBe('circle(closest-side)');
  });
});

describe('extractClipPath — rect() and shape()', () => {

  it('rect() emits its four sides, not four autos', () => {
    expect(css({ type: 'rect', t: px(50), r: px(200), b: px(150), l: px(50) }))
      .toBe('rect(50px 200px 150px 50px)');
  });

  it('rect() percentages survive', () => {
    expect(css({ type: 'rect', t: pct(25), r: pct(50), b: pct(75), l: pct(12.5) }))
      .toBe('rect(25% 50% 75% 12.5%)');
  });

  it('rect() auto sides still serialise as auto', () => {
    expect(css({ type: 'rect', t: 'auto', r: px(200), b: 'auto', l: px(50) }))
      .toBe('rect(auto 200px auto 50px)');
  });

  it('rect() round clause is emitted when the IR carries one', () => {
    expect(css({ type: 'rect', t: px(50), r: px(200), b: px(150), l: px(50), round: px(20) }))
      .toBe('rect(50px 200px 150px 50px round 20px)');
  });

  it('shape() passes through verbatim', () => {
    const fn = 'shape(evenodd from 10px 10px, hline by 80px, vline by 80%, close)';
    expect(css({ type: 'shape', fn })).toBe(fn);
  });

  it('shape() with a geometry box keeps the box suffix', () => {
    const fn = 'shape(from center left, curve to center right with center top / center top, close)';
    expect(css({ 'geometry-box': 'content-box', shape: { type: 'shape', fn } }))
      .toBe(`${fn} content-box`);
  });

  it('ellipse() with no radii defaults to closest-side, not a zero clip', () => {
    // WPT clip-path-ellipse-006 writes bare `ellipse()`. The absent axes
    // used to fall through to '0', i.e. `ellipse(0 0)` — an empty region
    // that erases the element.
    expect(css({ type: 'ellipse' })).toBe('ellipse(closest-side closest-side)');
  });

  it('path() keeps its fill-rule as a separate argument', () => {
    expect(css({ type: 'path', rule: 'nonzero', d: 'M0,0 L100,0 L0,100 L0,0' }))
      .toBe('path(nonzero, "M0,0 L100,0 L0,100 L0,0")');
  });

  it('path() without a fill-rule is unchanged', () => {
    expect(css({ type: 'path', d: 'M 0 0 L 10 10 Z' })).toBe('path("M 0 0 L 10 10 Z")');
  });

  it('an empty shape() payload is dropped rather than emitted', () => {
    // Defensive: an empty `fn` would produce the declaration `clip-path:`
    // which the browser discards anyway — better to emit nothing.
    expect(css({ type: 'shape', fn: '' })).toBeUndefined();
  });
});
