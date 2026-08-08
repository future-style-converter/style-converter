// FloatRowPacking.test.ts — wave-19 lane FLOAT pins for the web third
// of the float-run twins (engine/layout/FloatRowPacking.ts ↔ Compose
// FloatRowPacking.kt ↔ iOS FloatRowPacking.swift). The segmentation
// pins (P1/P2) mirror FloatRowPackingTest.kt / FloatRowPackingTests.swift
// VERBATIM; the wrapper pins (P4/P6) cover the web-only half — the
// browser does the packing itself inside the flow-root max-content box.

import { describe, expect, it } from 'vitest';
import {
  FLOAT_ROW_STRUT_PX,
  floatChildFacts,
  floatRunWrapperStyle,
  segmentFloatRuns,
  type FloatChildFacts,
} from '../../src/engine/layout/FloatRowPacking';

// IR keyword wire shape — bare string, exactly what the live per-test
// IR carries ({"type":"Float","data":"LEFT"}).
const kw = (type: string, keyword: string) => ({ type, data: keyword });

// Shorthand fact constructors for the segmentation pins.
const F: FloatChildFacts = { floatsLeft: true, clearBreaksLeft: false };
const BR: FloatChildFacts = { floatsLeft: false, clearBreaksLeft: true };
const X: FloatChildFacts = { floatsLeft: false, clearBreaksLeft: false };
// P7 (wave-36 lane M5): a left float that declares shape-outside. It is
// left-floating but UNPACKABLE — the run wrapper is a BFC and a float's
// exclusion only reaches line boxes inside its own BFC.
const FS: FloatChildFacts = { floatsLeft: true, clearBreaksLeft: false, shapesContent: true };

describe('floatChildFacts (pins P1/P2)', () => {
  it('left and inline-start floats pack, right floats do not', () => {
    // P1: the LTR-normalized engine folds inline-start onto left.
    expect(floatChildFacts([kw('Float', 'LEFT')], false).floatsLeft).toBe(true);
    expect(floatChildFacts([kw('Float', 'INLINE_START')], false).floatsLeft).toBe(true);
    // Right-family floats keep today's path (F4).
    expect(floatChildFacts([kw('Float', 'RIGHT')], false).floatsLeft).toBe(false);
    expect(floatChildFacts([kw('Float', 'INLINE_END')], false).floatsLeft).toBe(false);
    // No Float wire at all → plain in-flow content.
    expect(floatChildFacts([], false).floatsLeft).toBe(false);
  });

  it('childless clear:both is a break, right clear and content clears are not', () => {
    // P2: the `<br clear:both>` wire shape — childless, Clear only.
    expect(floatChildFacts([kw('Clear', 'BOTH')], false).clearBreaksLeft).toBe(true);
    // clear:left / inline-start also clear a LEFT run (§9.5.2).
    expect(floatChildFacts([kw('Clear', 'LEFT')], false).clearBreaksLeft).toBe(true);
    expect(floatChildFacts([kw('Clear', 'INLINE_START')], false).clearBreaksLeft).toBe(true);
    // clear:right does NOT clear a left run (§9.5.2 same-side rule).
    expect(floatChildFacts([kw('Clear', 'RIGHT')], false).clearBreaksLeft).toBe(false);
    // A clear on a CONTENT box is real layout, not a break marker.
    expect(floatChildFacts([kw('Clear', 'BOTH')], true).clearBreaksLeft).toBe(false);
    // A floating box never doubles as a break marker.
    expect(
      floatChildFacts([kw('Float', 'LEFT'), kw('Clear', 'BOTH')], false).clearBreaksLeft,
    ).toBe(false);
  });

  it('P7 — a declared shape-outside marks the float unpackable', () => {
    // The live css-shapes wire shapes (shape-box + basic-shape).
    const box = { type: 'ShapeOutside', data: { type: 'content-box' } };
    const circle = { type: 'ShapeOutside', data: { type: 'basic-shape', shape: 'circle(50%)' } };
    expect(floatChildFacts([kw('Float', 'LEFT'), box], false).shapesContent).toBe(true);
    expect(floatChildFacts([kw('Float', 'LEFT'), circle], false).shapesContent).toBe(true);
    // `none` declares NO exclusion — the float stays packable.
    expect(
      floatChildFacts([kw('Float', 'LEFT'), { type: 'ShapeOutside', data: { type: 'none' } }], false)
        .shapesContent,
    ).toBe(false);
    // No ShapeOutside wire at all → the frozen wave-19 behaviour.
    expect(floatChildFacts([kw('Float', 'LEFT')], false).shapesContent).toBe(false);
    // An unknown variant folds to undefined in the style pass, so it
    // must fold to "not shaped" here too (no silent divergence).
    expect(
      floatChildFacts([kw('Float', 'LEFT'), { type: 'ShapeOutside', data: { type: 'made-up' } }], false)
        .shapesContent,
    ).toBe(false);
  });
});

describe('segmentFloatRuns (pins P1/P2)', () => {
  it('segments the justify-self-001 sibling pattern into 3/2/5/4 strutted runs', () => {
    // The live wire order: 14 floats + 4 br markers (18 siblings).
    const segs = segmentFloatRuns([F, F, F, BR, F, F, BR, F, F, F, F, F, BR, F, F, F, F, BR]);
    // 4 runs + 4 br singles = 8 segments, sibling order preserved.
    expect(segs).toHaveLength(8);
    // Run 1: indices 0-2, terminated by the br at 3 → strutted.
    expect(segs[0]).toEqual({ indices: [0, 1, 2], isRun: true, strutted: true });
    // The br itself is a single (renders as a 0-height clear div).
    expect(segs[1]).toEqual({ indices: [3], isRun: false, strutted: false });
    // Runs 2-4 mirror the 2/5/4 ref rows, all strutted.
    expect(segs[2]).toEqual({ indices: [4, 5], isRun: true, strutted: true });
    expect(segs[4]).toEqual({ indices: [7, 8, 9, 10, 11], isRun: true, strutted: true });
    expect(segs[6]).toEqual({ indices: [13, 14, 15, 16], isRun: true, strutted: true });
  });

  it('keeps the grid float pair as one un-strutted run', () => {
    // descendant-static-position-001: green+grey, no br after — the run
    // reports its bare height (no line box exists to strut against).
    expect(segmentFloatRuns([F, F])).toEqual([
      { indices: [0, 1], isRun: true, strutted: false },
    ]);
  });

  it('keeps lone floats and non-floats as singles', () => {
    // A lone float keeps today's block path (F4 conservatism).
    const segs = segmentFloatRuns([F, X, F]);
    expect(segs).toHaveLength(3);
    expect(segs.some((s) => s.isRun)).toBe(false);
  });

  it('P7 — shaped floats never pack, and break a run they sit in', () => {
    // The css-shapes shape-box wire: TWO consecutive `float:left;
    // shape-outside:content-box` siblings followed by the in-flow boxes
    // that must wrap around them. Packing them into a flow-root wrapper
    // made shape-outside inert (measured: content-box-001 0.65 ssim).
    expect(segmentFloatRuns([FS, FS])).toEqual([
      { indices: [0], isRun: false, strutted: false },
      { indices: [1], isRun: false, strutted: false },
    ]);
    // A shaped float in the middle of a plain run splits it, and the
    // surviving halves are lone floats → singles as well.
    const split = segmentFloatRuns([F, FS, F]);
    expect(split).toHaveLength(3);
    expect(split.some((s) => s.isRun)).toBe(false);
    // Plain runs on either side of a shaped float still pack.
    const mixed = segmentFloatRuns([F, F, FS, F, F]);
    expect(mixed[0]).toEqual({ indices: [0, 1], isRun: true, strutted: false });
    expect(mixed[1]).toEqual({ indices: [2], isRun: false, strutted: false });
    expect(mixed[2]).toEqual({ indices: [3, 4], isRun: true, strutted: false });
  });
});

describe('floatRunWrapperStyle (pins P4/P6)', () => {
  it('wraps runs in a max-content BFC so rows break only at clear markers', () => {
    // P4's ∞ twin: flow-root contains the floats (§10.6.7) and
    // max-content sizes the containing block to the run itself.
    expect(floatRunWrapperStyle(false)).toEqual({
      display: 'flow-root',
      width: 'max-content',
    });
  });

  it('adds the 20px br line-box strut to strutted runs only', () => {
    // P6: the ref's `<br clear:both>` line box floors the row advance
    // at 20px (16px ref font × 1.25 REF line-height).
    expect(FLOAT_ROW_STRUT_PX).toBe(20);
    expect(floatRunWrapperStyle(true)).toEqual({
      display: 'flow-root',
      width: 'max-content',
      minHeight: '20px',
    });
  });
});
