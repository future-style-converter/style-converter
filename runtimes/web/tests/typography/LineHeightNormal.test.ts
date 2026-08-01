// LineHeightNormal.test.ts — wave 22, lane FONT.
//
// Pins the WEB half of the DECLARED-`normal` line-height contract. Web is the
// pixel oracle for this lane, so its behaviour is the reference the two natives
// were built to match — but it was only ever correct BY LUCK: the extractor
// already tested `original === 'normal'` first, and nothing exercised it,
// because the converter never emitted the keyword.
//
// ## What changed upstream
// `font: 92px Arial` RESETS line-height to `normal` (css-fonts-4 §4.3). The
// converter's FontExpander now emits that reset, so the IR for
// css/css-text-decor/text-decoration-dotted-001.html carries
// `{"multiplier":1.2,"original":"normal"}` where before
// (tools/titan/runs/wave21-final/…/per-test-ir/…dotted-001.json) it carried no
// LineHeight at all.
//
// ## Why emitting the CSS keyword is the whole fix on web
// The WPT stage sets `body.wpt-mode, body.wpt-composed-mode { line-height: 1.25 }`
// (apps/web-harness/index.html — the four-surface corpus-v4.1 pin matching
// capture-browser-ref.mjs REF_LINE_HEIGHT). Descendants INHERIT that 1.25, so
// a 92px div got a 115px line box. An IR-declared line-height arrives as an
// INLINE style, and a directly-matching declaration always beats an inherited
// value regardless of the source rule's specificity — exactly the cascade the
// Chromium browser-ref itself runs, where the div ends up on Arial's own
// metrics (hhea asc 1854 + desc 434 + gap 67 over a 2048 upem = 1.1499em ≈
// 105.8px at 92px). So `lineHeight: 'normal'` on the element is both the CSS
// keyword and the pixel answer; no calibration override is needed here.
//
// ## The invariant that must never break
// ABSENT LineHeight must still emit NOTHING, so the stage's 1.25 keeps
// applying — the whole WPT corpus and all 327 committed baselines ride that.
// Twins: Compose LineHeightNormalTest.kt, SwiftUI LineHeightNormalTests.swift.

import { describe, it, expect } from 'vitest';

import { extractLineHeight } from '../../src/engine/typography/LineHeightExtractor';
import { applyLineHeight } from '../../src/engine/typography/LineHeightApplier';

// The exact payload the converter now emits for `font: 92px Arial, sans-serif`
// (verified by running :converter:run on
// fixtures/wpt/css-text-decor/text-decoration-dotted-001.json).
const NORMAL_WIRE = { multiplier: 1.2, original: 'normal' };

describe('line-height: normal (the `font` shorthand reset)', () => {
  it('emits the CSS keyword, not the wire’s legacy 1.2 multiplier', () => {
    const cfg = extractLineHeight([{ type: 'LineHeight', data: NORMAL_WIRE }]);
    // 'normal' — the browser then resolves the face's own metrics. Emitting
    // 1.2 instead would pin a face-independent 110.4px box at 92px.
    expect(cfg.value).toBe('normal');
    expect(applyLineHeight(cfg)).toEqual({ lineHeight: 'normal' });
  });

  it('absent LineHeight emits nothing so the stage’s 1.25 still inherits', () => {
    // THE protected invariant — this is the branch the whole corpus and every
    // committed baseline ride. An empty object means no inline declaration,
    // so body.wpt-mode's line-height: 1.25 keeps winning by inheritance.
    const cfg = extractLineHeight([
      { type: 'FontSize', data: { px: 92 } },
      { type: 'FontFamily', data: ['Arial', 'sans-serif'] },
    ]);
    expect(cfg.value).toBeUndefined();
    expect(applyLineHeight(cfg)).toEqual({});
  });

  it('a real unitless number is still emitted as a number', () => {
    // Regression guard: `line-height: 1.2` shares the multiplier field with
    // the reset but rides original:{type:'number'} — it must NOT collapse to
    // the keyword, or every unitless line-height in the corpus would change.
    const cfg = extractLineHeight([{
      type: 'LineHeight',
      data: { multiplier: 1.2, original: { type: 'number', value: 1.2 } },
    }]);
    expect(cfg.value).toBe(1.2);
  });

  it('lengths and percentages are untouched', () => {
    expect(extractLineHeight([{
      type: 'LineHeight',
      data: { original: { type: 'percentage', value: 150 } },
    }]).value).toBe('150%');
    // Live converter wire for `line-height: 40px` (verified via :converter:run).
    expect(extractLineHeight([{
      type: 'LineHeight', data: { original: { type: 'length', px: 40 } },
    }]).value).toBe('40px');
  });

  it('last declaration wins, in both directions (CSS cascade)', () => {
    // Mirrors the natives' last-wins gate so the three runtimes can never
    // disagree about WHICH declaration is in force.
    const numberWire = { multiplier: 2, original: { type: 'number', value: 2 } };
    expect(extractLineHeight([
      { type: 'LineHeight', data: numberWire },
      { type: 'LineHeight', data: NORMAL_WIRE },
    ]).value).toBe('normal');
    expect(extractLineHeight([
      { type: 'LineHeight', data: NORMAL_WIRE },
      { type: 'LineHeight', data: numberWire },
    ]).value).toBe(2);
  });
});
