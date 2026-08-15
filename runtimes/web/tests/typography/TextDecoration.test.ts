// TextDecoration.test.ts — decoration, shadow, overflow, emphasis, initial-letter,
// ruby, hyphenation, baselines, SVG typography.
import { describe, it, expect } from 'vitest';

import { extractTextDecorationLine } from '../../src/engine/typography/TextDecorationLineExtractor';
import { applyTextDecorationLine } from '../../src/engine/typography/TextDecorationLineApplier';
import { extractTextDecorationStyle } from '../../src/engine/typography/TextDecorationStyleExtractor';
import { applyTextDecorationStyle } from '../../src/engine/typography/TextDecorationStyleApplier';
import { extractTextDecorationColor } from '../../src/engine/typography/TextDecorationColorExtractor';
import { applyTextDecorationColor } from '../../src/engine/typography/TextDecorationColorApplier';
import { extractTextDecorationThickness } from '../../src/engine/typography/TextDecorationThicknessExtractor';
import { applyTextDecorationThickness } from '../../src/engine/typography/TextDecorationThicknessApplier';
import { extractTextDecorationSkip } from '../../src/engine/typography/TextDecorationSkipExtractor';
import { applyTextDecorationSkip } from '../../src/engine/typography/TextDecorationSkipApplier';
import { extractTextDecorationSkipInk } from '../../src/engine/typography/TextDecorationSkipInkExtractor';
import { applyTextDecorationSkipInk } from '../../src/engine/typography/TextDecorationSkipInkApplier';
import { extractTextUnderlineOffset } from '../../src/engine/typography/TextUnderlineOffsetExtractor';
import { applyTextUnderlineOffset } from '../../src/engine/typography/TextUnderlineOffsetApplier';
import { extractTextUnderlinePosition } from '../../src/engine/typography/TextUnderlinePositionExtractor';
import { applyTextUnderlinePosition } from '../../src/engine/typography/TextUnderlinePositionApplier';
import { extractTextShadow } from '../../src/engine/typography/TextShadowExtractor';
import { applyTextShadow } from '../../src/engine/typography/TextShadowApplier';
import { extractTextOverflow } from '../../src/engine/typography/TextOverflowExtractor';
import { applyTextOverflow } from '../../src/engine/typography/TextOverflowApplier';
import { extractLineClamp } from '../../src/engine/typography/LineClampExtractor';
import { applyLineClamp } from '../../src/engine/typography/LineClampApplier';
import { extractMaxLines } from '../../src/engine/typography/MaxLinesExtractor';
import { applyMaxLines } from '../../src/engine/typography/MaxLinesApplier';
import { extractBlockEllipsis } from '../../src/engine/typography/BlockEllipsisExtractor';
import { applyBlockEllipsis } from '../../src/engine/typography/BlockEllipsisApplier';
import { extractTextEmphasis } from '../../src/engine/typography/TextEmphasisExtractor';
import { applyTextEmphasis } from '../../src/engine/typography/TextEmphasisApplier';
import { extractTextEmphasisStyle } from '../../src/engine/typography/TextEmphasisStyleExtractor';
import { applyTextEmphasisStyle } from '../../src/engine/typography/TextEmphasisStyleApplier';
import { extractTextEmphasisColor } from '../../src/engine/typography/TextEmphasisColorExtractor';
import { applyTextEmphasisColor } from '../../src/engine/typography/TextEmphasisColorApplier';
import { extractTextEmphasisPosition } from '../../src/engine/typography/TextEmphasisPositionExtractor';
import { applyTextEmphasisPosition } from '../../src/engine/typography/TextEmphasisPositionApplier';
import { extractInitialLetter } from '../../src/engine/typography/InitialLetterExtractor';
import { applyInitialLetter } from '../../src/engine/typography/InitialLetterApplier';
import { extractInitialLetterAlign } from '../../src/engine/typography/InitialLetterAlignExtractor';
import { applyInitialLetterAlign } from '../../src/engine/typography/InitialLetterAlignApplier';
import { extractQuotes } from '../../src/engine/typography/QuotesExtractor';
import { applyQuotes } from '../../src/engine/typography/QuotesApplier';
import { extractHangingPunctuation } from '../../src/engine/typography/HangingPunctuationExtractor';
import { applyHangingPunctuation } from '../../src/engine/typography/HangingPunctuationApplier';
import { extractHyphens } from '../../src/engine/typography/HyphensExtractor';
import { applyHyphens } from '../../src/engine/typography/HyphensApplier';
import { extractHyphenateCharacter } from '../../src/engine/typography/HyphenateCharacterExtractor';
import { applyHyphenateCharacter } from '../../src/engine/typography/HyphenateCharacterApplier';
import { extractHyphenateLimitChars } from '../../src/engine/typography/HyphenateLimitCharsExtractor';
import { applyHyphenateLimitChars } from '../../src/engine/typography/HyphenateLimitCharsApplier';
import { extractHyphenateLimitLines } from '../../src/engine/typography/HyphenateLimitLinesExtractor';
import { applyHyphenateLimitLines } from '../../src/engine/typography/HyphenateLimitLinesApplier';
import { extractHyphenateLimitZone } from '../../src/engine/typography/HyphenateLimitZoneExtractor';
import { applyHyphenateLimitZone } from '../../src/engine/typography/HyphenateLimitZoneApplier';
import { extractHyphenateLimitLast } from '../../src/engine/typography/HyphenateLimitLastExtractor';
import { applyHyphenateLimitLast } from '../../src/engine/typography/HyphenateLimitLastApplier';
import { extractOrphans } from '../../src/engine/typography/OrphansExtractor';
import { applyOrphans } from '../../src/engine/typography/OrphansApplier';
import { extractWidows } from '../../src/engine/typography/WidowsExtractor';
import { applyWidows } from '../../src/engine/typography/WidowsApplier';
import { extractTextRendering } from '../../src/engine/typography/TextRenderingExtractor';
import { applyTextRendering } from '../../src/engine/typography/TextRenderingApplier';
import { extractVerticalAlign } from '../../src/engine/typography/VerticalAlignExtractor';
import { applyVerticalAlign } from '../../src/engine/typography/VerticalAlignApplier';
import { extractVerticalAlignLast } from '../../src/engine/typography/VerticalAlignLastExtractor';
import { applyVerticalAlignLast } from '../../src/engine/typography/VerticalAlignLastApplier';
import { extractAlignmentBaseline } from '../../src/engine/typography/AlignmentBaselineExtractor';
import { applyAlignmentBaseline } from '../../src/engine/typography/AlignmentBaselineApplier';
import { extractBaselineShift } from '../../src/engine/typography/BaselineShiftExtractor';
import { applyBaselineShift } from '../../src/engine/typography/BaselineShiftApplier';
import { extractBaselineSource } from '../../src/engine/typography/BaselineSourceExtractor';
import { applyBaselineSource } from '../../src/engine/typography/BaselineSourceApplier';
import { extractDominantBaseline } from '../../src/engine/typography/DominantBaselineExtractor';
import { applyDominantBaseline } from '../../src/engine/typography/DominantBaselineApplier';
import { extractDominantBaselineAdjust } from '../../src/engine/typography/DominantBaselineAdjustExtractor';
import { applyDominantBaselineAdjust } from '../../src/engine/typography/DominantBaselineAdjustApplier';
import { extractGlyphOrientationHorizontal } from '../../src/engine/typography/GlyphOrientationHorizontalExtractor';
import { applyGlyphOrientationHorizontal } from '../../src/engine/typography/GlyphOrientationHorizontalApplier';
import { extractGlyphOrientationVertical } from '../../src/engine/typography/GlyphOrientationVerticalExtractor';
import { applyGlyphOrientationVertical } from '../../src/engine/typography/GlyphOrientationVerticalApplier';
import { extractKerning } from '../../src/engine/typography/KerningExtractor';
import { applyKerning } from '../../src/engine/typography/KerningApplier';
import { extractTextAnchor } from '../../src/engine/typography/TextAnchorExtractor';
import { applyTextAnchor } from '../../src/engine/typography/TextAnchorApplier';
import { extractRubyAlign } from '../../src/engine/typography/RubyAlignExtractor';
import { applyRubyAlign } from '../../src/engine/typography/RubyAlignApplier';
import { extractRubyMerge } from '../../src/engine/typography/RubyMergeExtractor';
import { applyRubyMerge } from '../../src/engine/typography/RubyMergeApplier';
import { extractRubyOverhang } from '../../src/engine/typography/RubyOverhangExtractor';
import { applyRubyOverhang } from '../../src/engine/typography/RubyOverhangApplier';
import { extractRubyPosition } from '../../src/engine/typography/RubyPositionExtractor';
import { applyRubyPosition } from '../../src/engine/typography/RubyPositionApplier';
import { extractLineGrid } from '../../src/engine/typography/LineGridExtractor';
import { applyLineGrid } from '../../src/engine/typography/LineGridApplier';
import { extractLineSnap } from '../../src/engine/typography/LineSnapExtractor';
import { applyLineSnap } from '../../src/engine/typography/LineSnapApplier';
import { extractTextAutospace } from '../../src/engine/typography/TextAutospaceExtractor';
import { applyTextAutospace } from '../../src/engine/typography/TextAutospaceApplier';
import { extractTextSpacing } from '../../src/engine/typography/TextSpacingExtractor';
import { applyTextSpacing } from '../../src/engine/typography/TextSpacingApplier';
import { extractTextSpacingTrim } from '../../src/engine/typography/TextSpacingTrimExtractor';
import { applyTextSpacingTrim } from '../../src/engine/typography/TextSpacingTrimApplier';
import { extractTextSpaceCollapse } from '../../src/engine/typography/TextSpaceCollapseExtractor';
import { applyTextSpaceCollapse } from '../../src/engine/typography/TextSpaceCollapseApplier';
import { extractTextSpaceTrim } from '../../src/engine/typography/TextSpaceTrimExtractor';
import { applyTextSpaceTrim } from '../../src/engine/typography/TextSpaceTrimApplier';
import { extractTextSizeAdjust } from '../../src/engine/typography/TextSizeAdjustExtractor';
import { applyTextSizeAdjust } from '../../src/engine/typography/TextSizeAdjustApplier';
import { extractWordSpaceTransform } from '../../src/engine/typography/WordSpaceTransformExtractor';
import { applyWordSpaceTransform } from '../../src/engine/typography/WordSpaceTransformApplier';
import { extractTextBoxEdge } from '../../src/engine/typography/TextBoxEdgeExtractor';
import { applyTextBoxEdge } from '../../src/engine/typography/TextBoxEdgeApplier';
import { extractTextBoxTrim } from '../../src/engine/typography/TextBoxTrimExtractor';
import { applyTextBoxTrim } from '../../src/engine/typography/TextBoxTrimApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('TextDecoration family', () => {
  it('line underline', () => {
    expect(applyTextDecorationLine(extractTextDecorationLine([p('TextDecorationLine', ['UNDERLINE'])])))
      .toEqual({ textDecorationLine: 'underline' });
  });
  it('line multi', () => {
    expect(applyTextDecorationLine(extractTextDecorationLine([p('TextDecorationLine', ['UNDERLINE', 'OVERLINE'])])))
      .toEqual({ textDecorationLine: 'underline overline' });
  });
  it('style wavy', () => {
    expect(applyTextDecorationStyle(extractTextDecorationStyle([p('TextDecorationStyle', 'WAVY')])))
      .toEqual({ textDecorationStyle: 'wavy' });
  });
  it('color hex', () => {
    expect(applyTextDecorationColor(extractTextDecorationColor([p('TextDecorationColor', {
      original: '#ff3366', srgb: { r: 1.0, g: 0.2, b: 0.4 },
    })])).textDecorationColor).toMatch(/^rgb/);
  });
  it('thickness percentage', () => {
    expect(applyTextDecorationThickness(extractTextDecorationThickness([p('TextDecorationThickness', { type: 'percentage', percentage: 10 })])))
      .toEqual({ textDecorationThickness: '10%' });
  });
  it('thickness px', () => {
    expect(applyTextDecorationThickness(extractTextDecorationThickness([p('TextDecorationThickness', { type: 'length', px: 4 })])))
      .toEqual({ textDecorationThickness: '4px' });
  });
  it('skip list', () => {
    expect(applyTextDecorationSkip(extractTextDecorationSkip([p('TextDecorationSkip', ['EDGES'])])))
      .toEqual({ textDecorationSkip: 'edges' });
  });
  it('skip-ink auto', () => {
    expect(applyTextDecorationSkipInk(extractTextDecorationSkipInk([p('TextDecorationSkipInk', 'AUTO')])))
      .toEqual({ textDecorationSkipInk: 'auto' });
  });
  it('underline-offset em', () => {
    expect(applyTextUnderlineOffset(extractTextUnderlineOffset([p('TextUnderlineOffset', { type: 'length', original: { v: 0.2, u: 'EM' } })])))
      .toEqual({ textUnderlineOffset: '0.2em' });
  });
  it('underline-position list', () => {
    expect(applyTextUnderlinePosition(extractTextUnderlinePosition([p('TextUnderlinePosition', ['FROM_FONT'])])))
      .toEqual({ textUnderlinePosition: 'from-font' });
  });
});

describe('TextShadow', () => {
  it('single shadow with color', () => {
    expect(applyTextShadow(extractTextShadow([p('TextShadow', [
      { x: { px: 1 }, y: { px: 1 }, blur: { px: 0 }, c: { original: '#333', srgb: { r: 0.2, g: 0.2, b: 0.2 } } },
    ])])).textShadow).toMatch(/^1px 1px 0px rgb/);
  });
  it('multi shadow comma-join', () => {
    const out = applyTextShadow(extractTextShadow([p('TextShadow', [
      { x: { px: 2 }, y: { px: 2 }, blur: { px: 4 }, c: { original: '#000', srgb: { r: 0, g: 0, b: 0 } } },
      { x: { px: 0 }, y: { px: 0 }, blur: { px: 20 }, c: { original: '#fff', srgb: { r: 1, g: 1, b: 1 } } },
    ])])).textShadow as string;
    // Count shadow separators: `rgb(r, g, b)` contains commas, so split on
    // `), ` (the boundary after one shadow's color) — always 1 fewer than shadows.
    expect((out.match(/\),/g) ?? []).length).toBe(1);                // 2 shadows -> 1 boundary
  });
  it('empty list -> none', () => {
    expect(applyTextShadow(extractTextShadow([p('TextShadow', [])]))).toEqual({ textShadow: 'none' });
  });
});

describe('TextOverflow / LineClamp / MaxLines / BlockEllipsis', () => {
  it('ellipsis', () => {
    expect(applyTextOverflow(extractTextOverflow([p('TextOverflow', 'ELLIPSIS')]))).toEqual({ textOverflow: 'ellipsis' });
  });
  it('custom string quoted', () => {
    expect(applyTextOverflow(extractTextOverflow([p('TextOverflow', {
      type: 'app.irmodels.properties.typography.TextOverflowProperty.TextOverflowValue.CustomString',
      value: '...',
    })]))).toEqual({ textOverflow: '"..."' });
  });
  it('fade with length', () => {
    expect(applyTextOverflow(extractTextOverflow([p('TextOverflow', {
      type: 'app.irmodels.properties.typography.TextOverflowProperty.TextOverflowValue.Fade',
      length: '20px',
    })]))).toEqual({ textOverflow: 'fade(20px)' });
  });
  it('two-value', () => {
    expect(applyTextOverflow(extractTextOverflow([p('TextOverflow', {
      type: 'app.irmodels.properties.typography.TextOverflowProperty.TextOverflowValue.TwoValue',
      start: 'CLIP', end: 'ELLIPSIS',
    })]))).toEqual({ textOverflow: 'clip ellipsis' });
  });
  it('line-clamp N emits shim trio', () => {
    const out = applyLineClamp(extractLineClamp([p('LineClamp', { type: 'lines', count: 3 })]));
    expect(out.lineClamp).toBe(3);
    expect(out.display).toBe('-webkit-box');
    expect((out as Record<string, unknown>).WebkitLineClamp).toBe(3);
    expect(out.overflow).toBe('hidden');
  });
  it('line-clamp none -> no shim', () => {
    const out = applyLineClamp(extractLineClamp([p('LineClamp', { type: 'none' })]));
    expect(out.lineClamp).toBe('none');
    expect(out.display).toBeUndefined();
  });
  // wave-37 lane W2 — `line-clamp: auto` (css-overflow-4 §5.1).
  it('line-clamp auto resolves the count from a max-height in lh', () => {
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MaxHeight', { type: 'length', original: { v: 4.5, u: 'LH' } }),
    ]));
    // 4.5 line boxes fit four WHOLE lines — the clamp point is the last line
    // that starts before the constraint.
    expect(out.maxHeight).toBe('4lh');
    expect(out.overflow).toBe('hidden');
    // NOT the -webkit-box trio: it re-parents block children (see applier).
    expect(out.display).toBeUndefined();
  });
  it('line-clamp auto divides an absolute max-height by the used line-height', () => {
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MaxHeight', { type: 'length', px: 138 }),
      p('LineHeight', { original: { type: 'length', px: 32 } }),
    ]));
    expect(out.maxHeight).toBe('4lh');                       // floor(138 / 32)
  });
  it('line-clamp auto takes min-height when it overrides a smaller max-height', () => {
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MinHeight', { type: 'length', original: { v: 4, u: 'LH' } }),
      p('MaxHeight', { type: 'length', original: { v: 3, u: 'LH' } }),
    ]));
    expect(out.maxHeight).toBe('4lh');                       // CSS 2.1 §10.7: min wins
  });
  it('line-clamp auto with a zero constraint clips instead of clamping', () => {
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MaxHeight', { type: 'length', px: 0 }),
    ]));
    // 0 is outside -webkit-line-clamp's [1,∞] grammar and `max-height:0lh`
    // would fight the sizing engine's own 0 — hidden overflow is the clamp.
    expect(out.overflow).toBe('hidden');
    expect(out.maxHeight).toBeUndefined();
  });
  it('line-clamp auto with no block-size constraint emits nothing', () => {
    // css-overflow-4: with nothing to clamp against, `auto` never clamps.
    expect(applyLineClamp(extractLineClamp([p('LineClamp', { type: 'auto' })]))).toEqual({});
  });
  it('line-clamp auto leaves an unresolvable constraint alone', () => {
    // A percentage max-height has no statically-knowable line count; guessing
    // one would clamp at the wrong line, so the declaration is a no-op.
    expect(applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MaxHeight', { type: 'percentage', percentage: 50 }),
    ]))).toEqual({});
  });
  // wave-42 lane W7 — the two-value grammar's marker component
  // (css-overflow-4 §5.1 `<integer [1,∞]> || <'block-ellipsis'>`).
  it('line-clamp 4 no-ellipsis clamps by geometry, never the trio', () => {
    // WPT block-ellipsis-023's wire: the marker must not render and must not
    // displace content — the -webkit-box trio's "…" would do both.
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'lines', count: 4, ellipsis: { type: 'no-ellipsis' } }),
    ]));
    expect(out.maxHeight).toBe('4lh');                       // exactly 4 line boxes
    expect(out.overflow).toBe('hidden');                     // discard what follows
    expect(out.display).toBeUndefined();                     // NOT -webkit-box
    expect((out as Record<string, unknown>).WebkitLineClamp).toBeUndefined();
  });
  it('line-clamp 4 with an empty string marker behaves like no-ellipsis', () => {
    // WPT block-ellipsis-024's assert: `""` has the same effect as no-ellipsis.
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'lines', count: 4, ellipsis: { type: 'string', value: '' } }),
    ]));
    expect(out.maxHeight).toBe('4lh');
    expect(out.display).toBeUndefined();
  });
  it('a non-empty string marker keeps the trio (a marker SHOULD render)', () => {
    // `9 " etc., etc. "` wants a marker; the trio's "…" is the closest the
    // platform offers, so the fixed-count path stays untouched.
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'lines', count: 9, ellipsis: { type: 'string', value: ' etc., etc. ' } }),
    ]));
    expect(out.display).toBe('-webkit-box');
    expect((out as Record<string, unknown>).WebkitLineClamp).toBe(9);
  });
  it('a later plain count reinstates the trio after a no-ellipsis one', () => {
    // Last-write-wins must carry the marker fate WITH the winning value.
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'lines', count: 4, ellipsis: { type: 'no-ellipsis' } }),
      p('LineClamp', { type: 'lines', count: 6 }),
    ]));
    expect(out.display).toBe('-webkit-box');                 // default marker again
    expect((out as Record<string, unknown>).WebkitLineClamp).toBe(6);
  });
  it('a later line-clamp:none cancels an earlier auto', () => {
    const out = applyLineClamp(extractLineClamp([
      p('LineClamp', { type: 'auto' }),
      p('MaxHeight', { type: 'length', original: { v: 4, u: 'LH' } }),
      p('LineClamp', { type: 'none' }),
    ]));
    expect(out.lineClamp).toBe('none');
    expect(out.maxHeight).toBeUndefined();
  });
  it('max-lines count', () => {
    const out = applyMaxLines(extractMaxLines([p('MaxLines', { type: 'count', value: 2 })]));
    expect(out.maxLines).toBe(2);
    expect(out.display).toBe('-webkit-box');
  });
  it('block-ellipsis custom', () => {
    expect(applyBlockEllipsis(extractBlockEllipsis([p('BlockEllipsis', { type: 'custom', value: '...' })])))
      .toEqual({ blockEllipsis: '"..."' });
  });
});

describe('TextEmphasis family', () => {
  it('style dot', () => {
    expect(applyTextEmphasisStyle(extractTextEmphasisStyle([p('TextEmphasisStyle', { type: 'dot' })])))
      .toEqual({ textEmphasisStyle: 'dot' });
  });
  it('style custom char', () => {
    expect(applyTextEmphasisStyle(extractTextEmphasisStyle([p('TextEmphasisStyle', { type: 'custom', character: '*' })])))
      .toEqual({ textEmphasisStyle: '"*"' });
  });
  it('position vertical+horizontal', () => {
    expect(applyTextEmphasisPosition(extractTextEmphasisPosition([p('TextEmphasisPosition', { vertical: 'OVER', horizontal: 'LEFT' })])))
      .toEqual({ textEmphasisPosition: 'over left' });
  });
  it('position single OVER', () => {
    expect(applyTextEmphasisPosition(extractTextEmphasisPosition([p('TextEmphasisPosition', 'OVER')])))
      .toEqual({ textEmphasisPosition: 'over' });
  });
  it('shorthand style+color', () => {
    expect(applyTextEmphasis(extractTextEmphasis([p('TextEmphasis', {
      style: { type: 'dot' },
      color: { original: '#3498db', srgb: { r: 0.2, g: 0.6, b: 0.86 } },
    })])).textEmphasis).toMatch(/^dot rgb/);
  });
  it('color standalone', () => {
    expect(applyTextEmphasisColor(extractTextEmphasisColor([p('TextEmphasisColor', {
      original: '#e74c3c', srgb: { r: 0.9, g: 0.3, b: 0.24 },
    })])).textEmphasisColor).toMatch(/^rgb/);
  });
});

describe('InitialLetter', () => {
  it('size only', () => {
    expect(applyInitialLetter(extractInitialLetter([p('InitialLetter', { type: 'size', size: 2 })])))
      .toEqual({ initialLetter: '2' });
  });
  it('size + sink', () => {
    expect(applyInitialLetter(extractInitialLetter([p('InitialLetter', { type: 'size', size: 3, sink: 2 })])))
      .toEqual({ initialLetter: '3 2' });
  });
  it('align hanging', () => {
    expect(applyInitialLetterAlign(extractInitialLetterAlign([p('InitialLetterAlign', { type: 'hanging' })])))
      .toEqual({ initialLetterAlign: 'hanging' });
  });
});

describe('Quotes / HangingPunctuation', () => {
  it('quotes auto', () => {
    expect(applyQuotes(extractQuotes([p('Quotes', { type: 'auto' })]))).toEqual({ quotes: 'auto' });
  });
  it('quotes pair list', () => {
    expect(applyQuotes(extractQuotes([p('Quotes', { type: 'pairs', pairs: [{ open: '\u201c', close: '\u201d' }] })])))
      .toEqual({ quotes: '"\u201c" "\u201d"' });
  });
  it('hanging-punctuation combo', () => {
    expect(applyHangingPunctuation(extractHangingPunctuation([p('HangingPunctuation', ['FIRST', 'ALLOW_END', 'LAST'])])))
      .toEqual({ hangingPunctuation: 'first allow-end last' });
  });
});

describe('Hyphenation', () => {
  it('hyphens auto', () => {
    expect(applyHyphens(extractHyphens([p('Hyphens', 'AUTO')]))).toEqual({ hyphens: 'auto' });
  });
  it('hyphenate-character string', () => {
    expect(applyHyphenateCharacter(extractHyphenateCharacter([p('HyphenateCharacter', { type: 'string', value: '-' })])))
      .toEqual({ hyphenateCharacter: '"-"' });
  });
  it('hyphenate-character auto', () => {
    expect(applyHyphenateCharacter(extractHyphenateCharacter([p('HyphenateCharacter', { type: 'auto' })])))
      .toEqual({ hyphenateCharacter: 'auto' });
  });
  it('hyphenate-limit-chars 3 values', () => {
    expect(applyHyphenateLimitChars(extractHyphenateLimitChars([p('HyphenateLimitChars', {
      type: 'values', wordMin: 6, charsBefore: 2, charsAfter: 3,
    })]))).toEqual({ hyphenateLimitChars: '6 2 3' });
  });
  it('hyphenate-limit-chars trims trailing auto', () => {
    expect(applyHyphenateLimitChars(extractHyphenateLimitChars([p('HyphenateLimitChars', {
      type: 'values', wordMin: 6,
    })]))).toEqual({ hyphenateLimitChars: '6' });
  });
  it('hyphenate-limit-lines number', () => {
    expect(applyHyphenateLimitLines(extractHyphenateLimitLines([p('HyphenateLimitLines', { type: 'number', value: 2 })])))
      .toEqual({ hyphenateLimitLines: 2 });
  });
  it('hyphenate-limit-lines no-limit', () => {
    expect(applyHyphenateLimitLines(extractHyphenateLimitLines([p('HyphenateLimitLines', { type: 'no-limit' })])))
      .toEqual({ hyphenateLimitLines: 'no-limit' });
  });
  it('hyphenate-limit-zone px', () => {
    expect(applyHyphenateLimitZone(extractHyphenateLimitZone([p('HyphenateLimitZone', { type: 'length', px: 8 })])))
      .toEqual({ hyphenateLimitZone: '8px' });
  });
  it('hyphenate-limit-last column', () => {
    expect(applyHyphenateLimitLast(extractHyphenateLimitLast([p('HyphenateLimitLast', 'COLUMN')])))
      .toEqual({ hyphenateLimitLast: 'column' });
  });
});

describe('Orphans / Widows / TextRendering', () => {
  it('orphans numeric', () => {
    expect(applyOrphans(extractOrphans([p('Orphans', 3)]))).toEqual({ orphans: 3 });
  });
  it('widows numeric', () => {
    expect(applyWidows(extractWidows([p('Widows', 5)]))).toEqual({ widows: 5 });
  });
  it('text-rendering geometricPrecision', () => {
    expect(applyTextRendering(extractTextRendering([p('TextRendering', 'GEOMETRIC_PRECISION')])))
      .toEqual({ textRendering: 'geometric-precision' });
  });
});

describe('Vertical alignment & baselines', () => {
  it('vertical-align px', () => {
    expect(applyVerticalAlign(extractVerticalAlign([p('VerticalAlign', { type: 'length', px: 8 })])))
      .toEqual({ verticalAlign: '8px' });
  });
  it('vertical-align keyword', () => {
    expect(applyVerticalAlign(extractVerticalAlign([p('VerticalAlign', { type: 'keyword', value: 'BASELINE' })])))
      .toEqual({ verticalAlign: 'baseline' });
  });
  it('vertical-align percentage', () => {
    expect(applyVerticalAlign(extractVerticalAlign([p('VerticalAlign', { type: 'percentage', percentage: 50 })])))
      .toEqual({ verticalAlign: '50%' });
  });
  it('vertical-align-last auto', () => {
    expect(applyVerticalAlignLast(extractVerticalAlignLast([p('VerticalAlignLast', { type: 'auto' })])))
      .toEqual({ verticalAlignLast: 'auto' });
  });
  it('alignment-baseline central', () => {
    expect(applyAlignmentBaseline(extractAlignmentBaseline([p('AlignmentBaseline', 'CENTRAL')])))
      .toEqual({ alignmentBaseline: 'central' });
  });
  it('baseline-shift baseline', () => {
    expect(applyBaselineShift(extractBaselineShift([p('BaselineShift', { type: 'baseline' })])))
      .toEqual({ baselineShift: 'baseline' });
  });
  it('baseline-shift percentage', () => {
    expect(applyBaselineShift(extractBaselineShift([p('BaselineShift', { type: 'percentage', value: 50 })])))
      .toEqual({ baselineShift: '50%' });
  });
  it('baseline-shift length', () => {
    expect(applyBaselineShift(extractBaselineShift([p('BaselineShift', { type: 'length', px: 6 })])))
      .toEqual({ baselineShift: '6px' });
  });
  it('baseline-source first', () => {
    expect(applyBaselineSource(extractBaselineSource([p('BaselineSource', 'FIRST')])))
      .toEqual({ baselineSource: 'first' });
  });
  it('dominant-baseline hanging', () => {
    expect(applyDominantBaseline(extractDominantBaseline([p('DominantBaseline', 'HANGING')])))
      .toEqual({ dominantBaseline: 'hanging' });
  });
  it('dominant-baseline-adjust percentage', () => {
    expect(applyDominantBaselineAdjust(extractDominantBaselineAdjust([p('DominantBaselineAdjust', { type: 'percentage', value: 10 })])))
      .toEqual({ dominantBaselineAdjust: '10%' });
  });
});

describe('SVG-ish typography', () => {
  it('glyph-orientation-horizontal deg', () => {
    expect(applyGlyphOrientationHorizontal(extractGlyphOrientationHorizontal([p('GlyphOrientationHorizontal', { type: 'angle', deg: 90 })])))
      .toEqual({ glyphOrientationHorizontal: '90deg' });
  });
  it('glyph-orientation-vertical auto', () => {
    expect(applyGlyphOrientationVertical(extractGlyphOrientationVertical([p('GlyphOrientationVertical', { type: 'auto' })])))
      .toEqual({ glyphOrientationVertical: 'auto' });
  });
  it('kerning auto', () => {
    expect(applyKerning(extractKerning([p('Kerning', { type: 'auto' })]))).toEqual({ kerning: 'auto' });
  });
  it('kerning length', () => {
    expect(applyKerning(extractKerning([p('Kerning', { type: 'length', px: 2 })]))).toEqual({ kerning: '2px' });
  });
  it('text-anchor middle', () => {
    expect(applyTextAnchor(extractTextAnchor([p('TextAnchor', 'MIDDLE')]))).toEqual({ textAnchor: 'middle' });
  });
});

describe('Ruby', () => {
  it('ruby-align center', () => {
    expect(applyRubyAlign(extractRubyAlign([p('RubyAlign', 'CENTER')]))).toEqual({ rubyAlign: 'center' });
  });
  it('ruby-merge collapse', () => {
    expect(applyRubyMerge(extractRubyMerge([p('RubyMerge', 'COLLAPSE')]))).toEqual({ rubyMerge: 'collapse' });
  });
  it('ruby-overhang (any keyword)', () => {
    expect(applyRubyOverhang(extractRubyOverhang([p('RubyOverhang', 'AUTO')]))).toEqual({ rubyOverhang: 'auto' });
  });
  it('ruby-position alternate', () => {
    expect(applyRubyPosition(extractRubyPosition([p('RubyPosition', { type: 'alternate' })])))
      .toEqual({ rubyPosition: 'alternate' });
  });
  it('ruby-position over-left', () => {
    expect(applyRubyPosition(extractRubyPosition([p('RubyPosition', { type: 'over-left' })])))
      .toEqual({ rubyPosition: 'over-left' });
  });
});

describe('Line grid / snap / text-spacing / text-size-adjust / word-space-transform / text-box', () => {
  it('line-grid named', () => {
    expect(applyLineGrid(extractLineGrid([p('LineGrid', { type: 'named', name: 'my-grid' })])))
      .toEqual({ lineGrid: 'my-grid' });
  });
  it('line-grid create', () => {
    expect(applyLineGrid(extractLineGrid([p('LineGrid', { type: 'create' })]))).toEqual({ lineGrid: 'create' });
  });
  it('line-snap baseline', () => {
    expect(applyLineSnap(extractLineSnap([p('LineSnap', 'BASELINE')]))).toEqual({ lineSnap: 'baseline' });
  });
  it('text-autospace list', () => {
    expect(applyTextAutospace(extractTextAutospace([p('TextAutospace', ['IDEOGRAPH_ALPHA'])])))
      .toEqual({ textAutospace: 'ideograph-alpha' });
  });
  it('text-spacing raw', () => {
    expect(applyTextSpacing(extractTextSpacing([p('TextSpacing', { type: 'raw', value: 'trim-start' })])))
      .toEqual({ textSpacing: 'trim-start' });
  });
  it('text-spacing-trim normal', () => {
    expect(applyTextSpacingTrim(extractTextSpacingTrim([p('TextSpacingTrim', 'NORMAL')])))
      .toEqual({ textSpacingTrim: 'normal' });
  });
  it('text-space-collapse preserve-breaks', () => {
    expect(applyTextSpaceCollapse(extractTextSpaceCollapse([p('TextSpaceCollapse', 'PRESERVE_BREAKS')])))
      .toEqual({ textSpaceCollapse: 'preserve-breaks' });
  });
  it('text-space-trim list', () => {
    expect(applyTextSpaceTrim(extractTextSpaceTrim([p('TextSpaceTrim', ['TRIM_END'])])))
      .toEqual({ textSpaceTrim: 'trim-end' });
  });
  it('text-size-adjust percentage', () => {
    expect(applyTextSizeAdjust(extractTextSizeAdjust([p('TextSizeAdjust', { type: 'percentage', value: 150 })])))
      .toEqual({ textSizeAdjust: '150%' });
  });
  it('text-size-adjust none', () => {
    expect(applyTextSizeAdjust(extractTextSizeAdjust([p('TextSizeAdjust', { type: 'none' })])))
      .toEqual({ textSizeAdjust: 'none' });
  });
  it('word-space-transform space', () => {
    expect(applyWordSpaceTransform(extractWordSpaceTransform([p('WordSpaceTransform', 'SPACE')])))
      .toEqual({ wordSpaceTransform: 'space' });
  });
  it('text-box-edge mixed', () => {
    expect(applyTextBoxEdge(extractTextBoxEdge([p('TextBoxEdge', { over: 'CAP', under: 'ALPHABETIC' })])))
      .toEqual({ textBoxEdge: 'cap alphabetic' });
  });
  it('text-box-trim trim-both', () => {
    expect(applyTextBoxTrim(extractTextBoxTrim([p('TextBoxTrim', 'TRIM_BOTH')])))
      .toEqual({ textBoxTrim: 'trim-both' });
  });
});
