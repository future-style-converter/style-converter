// TextLayout.test.ts — alignment, transforms, spacing, wrapping.
import { describe, it, expect } from 'vitest';

import { extractTextAlign } from '../../src/engine/typography/TextAlignExtractor';
import { applyTextAlign } from '../../src/engine/typography/TextAlignApplier';
import { extractTextAlignLast } from '../../src/engine/typography/TextAlignLastExtractor';
import { applyTextAlignLast } from '../../src/engine/typography/TextAlignLastApplier';
import { extractTextAlignAll } from '../../src/engine/typography/TextAlignAllExtractor';
import { applyTextAlignAll } from '../../src/engine/typography/TextAlignAllApplier';
import { extractTextJustify } from '../../src/engine/typography/TextJustifyExtractor';
import { applyTextJustify } from '../../src/engine/typography/TextJustifyApplier';
import { extractTextIndent } from '../../src/engine/typography/TextIndentExtractor';
import { applyTextIndent } from '../../src/engine/typography/TextIndentApplier';
import { extractTextTransform } from '../../src/engine/typography/TextTransformExtractor';
import { applyTextTransform } from '../../src/engine/typography/TextTransformApplier';
import { extractLetterSpacing } from '../../src/engine/typography/LetterSpacingExtractor';
import { applyLetterSpacing } from '../../src/engine/typography/LetterSpacingApplier';
import { extractWordSpacing } from '../../src/engine/typography/WordSpacingExtractor';
import { applyWordSpacing } from '../../src/engine/typography/WordSpacingApplier';
import { extractLineHeight } from '../../src/engine/typography/LineHeightExtractor';
import { applyLineHeight } from '../../src/engine/typography/LineHeightApplier';
import { extractLineHeightStep } from '../../src/engine/typography/LineHeightStepExtractor';
import { applyLineHeightStep } from '../../src/engine/typography/LineHeightStepApplier';
import { extractTabSize } from '../../src/engine/typography/TabSizeExtractor';
import { applyTabSize } from '../../src/engine/typography/TabSizeApplier';
import { extractWhiteSpace } from '../../src/engine/typography/WhiteSpaceExtractor';
import { applyWhiteSpace } from '../../src/engine/typography/WhiteSpaceApplier';
import { extractWhiteSpaceCollapse } from '../../src/engine/typography/WhiteSpaceCollapseExtractor';
import { applyWhiteSpaceCollapse } from '../../src/engine/typography/WhiteSpaceCollapseApplier';
import { extractWordBreak } from '../../src/engine/typography/WordBreakExtractor';
import { applyWordBreak } from '../../src/engine/typography/WordBreakApplier';
import { extractWordWrap } from '../../src/engine/typography/WordWrapExtractor';
import { applyWordWrap } from '../../src/engine/typography/WordWrapApplier';
import { extractOverflowWrap } from '../../src/engine/typography/OverflowWrapExtractor';
import { applyOverflowWrap } from '../../src/engine/typography/OverflowWrapApplier';
import { extractLineBreak } from '../../src/engine/typography/LineBreakExtractor';
import { applyLineBreak } from '../../src/engine/typography/LineBreakApplier';
import { extractTextWrap } from '../../src/engine/typography/TextWrapExtractor';
import { applyTextWrap } from '../../src/engine/typography/TextWrapApplier';
import { extractTextWrapMode } from '../../src/engine/typography/TextWrapModeExtractor';
import { applyTextWrapMode } from '../../src/engine/typography/TextWrapModeApplier';
import { extractTextWrapStyle } from '../../src/engine/typography/TextWrapStyleExtractor';
import { applyTextWrapStyle } from '../../src/engine/typography/TextWrapStyleApplier';
import { extractTextOrientation } from '../../src/engine/typography/TextOrientationExtractor';
import { applyTextOrientation } from '../../src/engine/typography/TextOrientationApplier';
import { extractWritingMode } from '../../src/engine/typography/WritingModeExtractor';
import { applyWritingMode } from '../../src/engine/typography/WritingModeApplier';
import { extractDirection } from '../../src/engine/typography/DirectionExtractor';
import { applyDirection } from '../../src/engine/typography/DirectionApplier';
import { extractUnicodeBidi } from '../../src/engine/typography/UnicodeBidiExtractor';
import { applyUnicodeBidi } from '../../src/engine/typography/UnicodeBidiApplier';
import { extractTextCombineUpright } from '../../src/engine/typography/TextCombineUprightExtractor';
import { applyTextCombineUpright } from '../../src/engine/typography/TextCombineUprightApplier';
import { extractTextGroupAlign } from '../../src/engine/typography/TextGroupAlignExtractor';
import { applyTextGroupAlign } from '../../src/engine/typography/TextGroupAlignApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('TextAlign / TextAlignLast / TextAlignAll', () => {
  it.each([['LEFT','left'],['RIGHT','right'],['CENTER','center'],['JUSTIFY','justify'],['START','start'],['END','end'],['MATCH_PARENT','match-parent']])(
    'text-align %s -> %s', (kw, out) => {
      expect(applyTextAlign(extractTextAlign([p('TextAlign', kw)]))).toEqual({ textAlign: out });
  });
  it('text-align-last auto', () => {
    expect(applyTextAlignLast(extractTextAlignLast([p('TextAlignLast', 'AUTO')]))).toEqual({ textAlignLast: 'auto' });
  });
  it('text-align-all center', () => {
    expect(applyTextAlignAll(extractTextAlignAll([p('TextAlignAll', 'CENTER')]))).toEqual({ textAlignAll: 'center' });
  });
});

describe('TextJustify + TextIndent', () => {
  it('text-justify inter-word', () => {
    expect(applyTextJustify(extractTextJustify([p('TextJustify', 'INTER_WORD')]))).toEqual({ textJustify: 'inter-word' });
  });
  it('text-indent px', () => {
    expect(applyTextIndent(extractTextIndent([p('TextIndent', { type: 'length', px: 8 })])))
      .toEqual({ textIndent: '8px' });
  });
  it('text-indent percentage', () => {
    expect(applyTextIndent(extractTextIndent([p('TextIndent', { type: 'percentage', percentage: 10 })])))
      .toEqual({ textIndent: '10%' });
  });
  it('text-indent em', () => {
    expect(applyTextIndent(extractTextIndent([p('TextIndent', { type: 'length', original: { v: 2.0, u: 'EM' } })])))
      .toEqual({ textIndent: '2em' });
  });
});

describe('TextTransform', () => {
  it.each([['UPPERCASE','uppercase'],['LOWERCASE','lowercase'],['CAPITALIZE','capitalize'],['FULL_WIDTH','full-width'],['FULL_SIZE_KANA','full-size-kana']])(
    'text-transform %s -> %s', (kw, out) => {
      expect(applyTextTransform(extractTextTransform([p('TextTransform', kw)]))).toEqual({ textTransform: out });
  });
});

describe('LetterSpacing / WordSpacing', () => {
  it('letter-spacing px', () => {
    expect(applyLetterSpacing(extractLetterSpacing([p('LetterSpacing', { px: 2, original: { px: 2, type: 'length' } })])))
      .toEqual({ letterSpacing: '2px' });
  });
  it('letter-spacing em via nested original', () => {
    expect(applyLetterSpacing(extractLetterSpacing([p('LetterSpacing', { original: { original: { v: 0.1, u: 'EM' }, type: 'length' }, px: 0 })])))
      .toEqual({ letterSpacing: '0.1em' });
  });
  it('word-spacing px', () => {
    expect(applyWordSpacing(extractWordSpacing([p('WordSpacing', { original: { px: -2, type: 'length' }, px: -2 })])))
      .toEqual({ wordSpacing: '-2px' });
  });
});

describe('LineHeight / LineHeightStep', () => {
  it('line-height unitless', () => {
    expect(applyLineHeight(extractLineHeight([p('LineHeight', { multiplier: 1.5, original: { type: 'number', value: 1.5 } })])))
      .toEqual({ lineHeight: 1.5 });
  });
  it('line-height normal', () => {
    expect(applyLineHeight(extractLineHeight([p('LineHeight', { multiplier: 1.2, original: 'normal' })])))
      .toEqual({ lineHeight: 'normal' });
  });
  it('line-height percentage', () => {
    expect(applyLineHeight(extractLineHeight([p('LineHeight', { multiplier: 1.5, original: { type: 'percentage', value: 150 } })])))
      .toEqual({ lineHeight: '150%' });
  });
  it('line-height-step px', () => {
    expect(applyLineHeightStep(extractLineHeightStep([p('LineHeightStep', { type: 'length', px: 24 })])))
      .toEqual({ lineHeightStep: '24px' });
  });
});

describe('TabSize', () => {
  it('tab-size numeric', () => {
    expect(applyTabSize(extractTabSize([p('TabSize', { type: 'number', value: 4 })]))).toEqual({ tabSize: 4 });
  });
  it('tab-size length', () => {
    expect(applyTabSize(extractTabSize([p('TabSize', { type: 'length', px: 16 })]))).toEqual({ tabSize: '16px' });
  });
});

describe('Wrap / WhiteSpace / WordBreak / LineBreak', () => {
  it.each([['NOWRAP','nowrap'],['PRE','pre'],['PRE_LINE','pre-line'],['PRE_WRAP','pre-wrap'],['NORMAL','normal'],['BREAK_SPACES','break-spaces']])(
    'white-space %s -> %s', (kw, out) => {
      expect(applyWhiteSpace(extractWhiteSpace([p('WhiteSpace', kw)]))).toEqual({ whiteSpace: out });
  });
  it('white-space-collapse preserve', () => {
    expect(applyWhiteSpaceCollapse(extractWhiteSpaceCollapse([p('WhiteSpaceCollapse', 'PRESERVE')])))
      .toEqual({ whiteSpaceCollapse: 'preserve' });
  });
  it('word-break break-all', () => {
    expect(applyWordBreak(extractWordBreak([p('WordBreak', 'BREAK_ALL')]))).toEqual({ wordBreak: 'break-all' });
  });
  it('word-wrap legacy', () => {
    expect(applyWordWrap(extractWordWrap([p('WordWrap', 'BREAK_WORD')]))).toEqual({ wordWrap: 'break-word' });
  });
  it('overflow-wrap anywhere', () => {
    expect(applyOverflowWrap(extractOverflowWrap([p('OverflowWrap', 'ANYWHERE')]))).toEqual({ overflowWrap: 'anywhere' });
  });
  it('line-break anywhere', () => {
    expect(applyLineBreak(extractLineBreak([p('LineBreak', 'ANYWHERE')]))).toEqual({ lineBreak: 'anywhere' });
  });
  it('text-wrap balance', () => {
    expect(applyTextWrap(extractTextWrap([p('TextWrap', 'BALANCE')]))).toEqual({ textWrap: 'balance' });
  });
  it('text-wrap-mode wrap', () => {
    expect(applyTextWrapMode(extractTextWrapMode([p('TextWrapMode', 'WRAP')]))).toEqual({ textWrapMode: 'wrap' });
  });
  it('text-wrap-style pretty', () => {
    expect(applyTextWrapStyle(extractTextWrapStyle([p('TextWrapStyle', 'PRETTY')]))).toEqual({ textWrapStyle: 'pretty' });
  });
});

describe('Writing mode / direction / bidi', () => {
  it('writing-mode vertical-rl', () => {
    expect(applyWritingMode(extractWritingMode([p('WritingMode', 'VERTICAL_RL')]))).toEqual({ writingMode: 'vertical-rl' });
  });
  it('direction rtl', () => {
    expect(applyDirection(extractDirection([p('Direction', 'RTL')]))).toEqual({ direction: 'rtl' });
  });
  it('unicode-bidi isolate-override', () => {
    expect(applyUnicodeBidi(extractUnicodeBidi([p('UnicodeBidi', 'ISOLATE_OVERRIDE')])))
      .toEqual({ unicodeBidi: 'isolate-override' });
  });
  it('text-orientation upright', () => {
    expect(applyTextOrientation(extractTextOrientation([p('TextOrientation', 'UPRIGHT')]))).toEqual({ textOrientation: 'upright' });
  });
  it('text-combine-upright all', () => {
    expect(applyTextCombineUpright(extractTextCombineUpright([p('TextCombineUpright', { type: 'all' })])))
      .toEqual({ textCombineUpright: 'all' });
  });
  it('text-combine-upright digits N', () => {
    expect(applyTextCombineUpright(extractTextCombineUpright([p('TextCombineUpright', { type: 'digits', count: 3 })])))
      .toEqual({ textCombineUpright: 'digits 3' });
  });
  it('text-group-align start', () => {
    expect(applyTextGroupAlign(extractTextGroupAlign([p('TextGroupAlign', 'START')]))).toEqual({ textGroupAlign: 'start' });
  });
});
