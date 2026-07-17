// Fonts.test.ts — Phase-6 coverage for font-* property triplets.
// Fixtures mined from examples/properties/typography/font-*.json after
// ./gradlew run conversion.  Each block asserts the emitted CSS matches
// what the browser would accept natively.
import { describe, it, expect } from 'vitest';

import { extractFontFamily } from '../../src/engine/typography/FontFamilyExtractor';
import { applyFontFamily } from '../../src/engine/typography/FontFamilyApplier';
import { extractFontSize } from '../../src/engine/typography/FontSizeExtractor';
import { applyFontSize } from '../../src/engine/typography/FontSizeApplier';
import { extractFontWeight } from '../../src/engine/typography/FontWeightExtractor';
import { applyFontWeight } from '../../src/engine/typography/FontWeightApplier';
import { extractFontStyle } from '../../src/engine/typography/FontStyleExtractor';
import { applyFontStyle } from '../../src/engine/typography/FontStyleApplier';
import { extractFontStretch } from '../../src/engine/typography/FontStretchExtractor';
import { applyFontStretch } from '../../src/engine/typography/FontStretchApplier';
import { extractFontKerning } from '../../src/engine/typography/FontKerningExtractor';
import { applyFontKerning } from '../../src/engine/typography/FontKerningApplier';
import { extractFontOpticalSizing } from '../../src/engine/typography/FontOpticalSizingExtractor';
import { applyFontOpticalSizing } from '../../src/engine/typography/FontOpticalSizingApplier';
import { extractFontFeatureSettings } from '../../src/engine/typography/FontFeatureSettingsExtractor';
import { applyFontFeatureSettings } from '../../src/engine/typography/FontFeatureSettingsApplier';
import { extractFontVariationSettings } from '../../src/engine/typography/FontVariationSettingsExtractor';
import { applyFontVariationSettings } from '../../src/engine/typography/FontVariationSettingsApplier';
import { extractFontSizeAdjust } from '../../src/engine/typography/FontSizeAdjustExtractor';
import { applyFontSizeAdjust } from '../../src/engine/typography/FontSizeAdjustApplier';
import { extractFontPalette } from '../../src/engine/typography/FontPaletteExtractor';
import { applyFontPalette } from '../../src/engine/typography/FontPaletteApplier';
import { extractFontLanguageOverride } from '../../src/engine/typography/FontLanguageOverrideExtractor';
import { applyFontLanguageOverride } from '../../src/engine/typography/FontLanguageOverrideApplier';
import { extractFontDisplay } from '../../src/engine/typography/FontDisplayExtractor';
import { applyFontDisplay } from '../../src/engine/typography/FontDisplayApplier';
import { extractFontNamedInstance } from '../../src/engine/typography/FontNamedInstanceExtractor';
import { applyFontNamedInstance } from '../../src/engine/typography/FontNamedInstanceApplier';
import { extractFontMinSize } from '../../src/engine/typography/FontMinSizeExtractor';
import { applyFontMinSize } from '../../src/engine/typography/FontMinSizeApplier';
import { extractFontMaxSize } from '../../src/engine/typography/FontMaxSizeExtractor';
import { applyFontMaxSize } from '../../src/engine/typography/FontMaxSizeApplier';
import { extractFontVariantCaps } from '../../src/engine/typography/FontVariantCapsExtractor';
import { applyFontVariantCaps } from '../../src/engine/typography/FontVariantCapsApplier';
import { extractFontVariantNumeric } from '../../src/engine/typography/FontVariantNumericExtractor';
import { applyFontVariantNumeric } from '../../src/engine/typography/FontVariantNumericApplier';
import { extractFontVariantLigatures } from '../../src/engine/typography/FontVariantLigaturesExtractor';
import { applyFontVariantLigatures } from '../../src/engine/typography/FontVariantLigaturesApplier';
import { extractFontVariantEastAsian } from '../../src/engine/typography/FontVariantEastAsianExtractor';
import { applyFontVariantEastAsian } from '../../src/engine/typography/FontVariantEastAsianApplier';
import { extractFontVariantPosition } from '../../src/engine/typography/FontVariantPositionExtractor';
import { applyFontVariantPosition } from '../../src/engine/typography/FontVariantPositionApplier';
import { extractFontVariantAlternates } from '../../src/engine/typography/FontVariantAlternatesExtractor';
import { applyFontVariantAlternates } from '../../src/engine/typography/FontVariantAlternatesApplier';
import { extractFontVariantEmoji } from '../../src/engine/typography/FontVariantEmojiExtractor';
import { applyFontVariantEmoji } from '../../src/engine/typography/FontVariantEmojiApplier';
import { extractFontSynthesisWeight } from '../../src/engine/typography/FontSynthesisWeightExtractor';
import { applyFontSynthesisWeight } from '../../src/engine/typography/FontSynthesisWeightApplier';
import { extractFontSmooth } from '../../src/engine/typography/FontSmoothExtractor';
import { applyFontSmooth } from '../../src/engine/typography/FontSmoothApplier';

const p = (type: string, data: unknown) => ({ type, data });

describe('FontFamily', () => {
  it('emits a generic family unquoted', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', ['serif'])])))
      .toEqual({ fontFamily: 'serif' });
  });
  it('emits a fallback list comma-separated', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', ['Helvetica Neue', 'Helvetica', 'Arial', 'sans-serif'])])))
      .toEqual({ fontFamily: '"Helvetica Neue", Helvetica, Arial, sans-serif' });
  });
  it('quotes family names containing whitespace', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', ['Times New Roman'])])))
      .toEqual({ fontFamily: '"Times New Roman"' });
  });
  it('leaves single-token custom names unquoted', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', ['Arial'])])))
      .toEqual({ fontFamily: 'Arial' });
  });
  it('accepts cursive/fantasy/monospace as generic', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', ['cursive'])])))
      .toEqual({ fontFamily: 'cursive' });
  });
  it('empty list -> empty styles', () => {
    expect(applyFontFamily(extractFontFamily([p('FontFamily', [])]))).toEqual({});
  });
  it('ignores unrelated properties', () => {
    expect(extractFontFamily([p('FontWeight', 700)])).toEqual({});
  });
});

describe('FontSize', () => {
  it('emits px for numeric', () => {
    expect(applyFontSize(extractFontSize([p('FontSize', { px: 20, original: { type: 'length', px: 20 } })])))
      .toEqual({ fontSize: '20px' });
  });
  it('emits absolute keyword (large)', () => {
    expect(applyFontSize(extractFontSize([p('FontSize', { px: 18, original: { keyword: 'large', type: 'absolute' } })])))
      .toEqual({ fontSize: 'large' });
  });
  it('emits relative keyword (larger)', () => {
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { keyword: 'larger', type: 'relative' } })])))
      .toEqual({ fontSize: 'larger' });
  });
  it('emits calc expression verbatim', () => {
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { expr: 'calc(16px + 2px)', type: 'expression' } })])))
      .toEqual({ fontSize: 'calc(16px + 2px)' });
  });
  it('emits rem length from the LIVE deep-flattened IRLength wire (no top-level px)', () => {
    // font-size: 2rem — the LIVE converter deep-flattens: it emits
    // {original:{type:'length', original:{v:2,u:'REM'}}} with NO px and NO
    // `value` key (shape captured verbatim from ./gradlew :converter:run on a
    // font-size probe fixture). Previously the extractor read `orig.value`
    // and DROPPED this shape entirely.
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { type: 'length', original: { v: 2, u: 'REM' } } })])))
      .toEqual({ fontSize: '2rem' });
  });
  it('emits em length from the LIVE deep-flattened IRLength wire', () => {
    // font-size: 1.5em — same live relative-length wire, em flavour (captured
    // from real converter output); Chromium resolves it natively against the
    // inherited size (css-fonts-4 §3.1).
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { type: 'length', original: { v: 1.5, u: 'EM' } } })])))
      .toEqual({ fontSize: '1.5em' });
  });
  it('still emits rem from the LEGACY nested `value` wire (pre-flatten IR)', () => {
    // Older IR nested the IRLength wire under `value`; the extractor keeps a
    // fallback (`orig.original ?? orig.value`) so archived IR still renders.
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { type: 'length', value: { original: { v: 1.5, u: 'REM' } } } })])))
      .toEqual({ fontSize: '1.5rem' });
  });
  it('emits percentage of parent size (live wire shape)', () => {
    // font-size: 120% — the LIVE converter emits {original:{type:'percentage',
    // value:120}} (captured from real converter output); only the browser can
    // resolve the parent reference, so we pass '120%' through.
    expect(applyFontSize(extractFontSize([p('FontSize', { original: { type: 'percentage', value: 120 } })])))
      .toEqual({ fontSize: '120%' });
  });
  it('empty on unset', () => { expect(applyFontSize({})).toEqual({}); });
});

describe('FontWeight', () => {
  it('passes numeric weight through', () => {
    expect(applyFontWeight(extractFontWeight([p('FontWeight', 700)]))).toEqual({ fontWeight: 700 });
  });
  it('handles odd numeric weight like 350', () => {
    expect(applyFontWeight(extractFontWeight([p('FontWeight', 350)]))).toEqual({ fontWeight: 350 });
  });
  it('decodes keyword-form object {weight, original} to the resolved numeric (bold)', () => {
    // font-weight:bold — the converter emits { weight: 700, original: "bold" }
    // (FontWeightProperty.kt serialize()); web must render 700 like the natives.
    expect(applyFontWeight(extractFontWeight([p('FontWeight', { weight: 700, original: 'bold' })])))
      .toEqual({ fontWeight: 700 });
  });
  it('decodes keyword-form object for normal', () => {
    // font-weight:normal — same object shape, resolved to 400 per css-fonts-4 §2.2.
    expect(applyFontWeight(extractFontWeight([p('FontWeight', { weight: 400, original: 'normal' })])))
      .toEqual({ fontWeight: 400 });
  });
  it('falls back to original keyword when object lacks a numeric weight', () => {
    // Defensive: if a future wire drops the numeric, the source keyword still applies.
    expect(applyFontWeight(extractFontWeight([p('FontWeight', { original: 'bolder' })])))
      .toEqual({ fontWeight: 'bolder' });
  });
  it('drops an unrecognised object shape without clobbering the cascade', () => {
    // Unknown payloads must not throw and must not overwrite an earlier valid weight.
    expect(applyFontWeight(extractFontWeight([p('FontWeight', 700), p('FontWeight', { bogus: true })])))
      .toEqual({ fontWeight: 700 });
  });
  it('passes bolder relative keyword', () => {
    expect(applyFontWeight(extractFontWeight([p('FontWeight', 'bolder')]))).toEqual({ fontWeight: 'bolder' });
  });
  it('passes lighter relative keyword', () => {
    expect(applyFontWeight(extractFontWeight([p('FontWeight', 'lighter')]))).toEqual({ fontWeight: 'lighter' });
  });
  it('empty on missing input', () => { expect(applyFontWeight({})).toEqual({}); });
});

describe('FontStyle', () => {
  it('emits italic', () => {
    expect(applyFontStyle(extractFontStyle([p('FontStyle', 'italic')]))).toEqual({ fontStyle: 'italic' });
  });
  it('emits normal', () => {
    expect(applyFontStyle(extractFontStyle([p('FontStyle', 'normal')]))).toEqual({ fontStyle: 'normal' });
  });
  it('emits oblique (bare)', () => {
    expect(applyFontStyle(extractFontStyle([p('FontStyle', 'oblique')]))).toEqual({ fontStyle: 'oblique' });
  });
  it('emits oblique with angle', () => {
    expect(applyFontStyle(extractFontStyle([p('FontStyle', { oblique: { deg: -10 } })])))
      .toEqual({ fontStyle: 'oblique -10deg' });
  });
});

describe('FontStretch', () => {
  // Decision record (2026-07): these tests used to assert that the applier
  // emitted `{ fontStretch: 'condensed' }` / `{ fontStretch: '110%' }`.
  // That encoded a pre-parity product decision. The applier is now a
  // DELIBERATE no-op (see FontStretchApplier.ts kdoc): iOS SwiftUI and
  // Android Compose have no width-axis rendering path, so emitting the CSS
  // on web alone made web the only platform with visible stretch and sank
  // Typography_FontUltraCondensed/_FontUltraExpanded to SSIM ~0.46. The
  // EXTRACTOR is unchanged and still correct per CSS Fonts Level 4
  // §font-stretch (keywords AND <percentage> values are both valid), so we
  // assert extraction fidelity directly and pin the applier's no-op.
  it('extracts keyword when the IR preserves author intent', () => {
    expect(extractFontStretch([p('FontStretch', {
      percentage: 75, original: { keyword: 'condensed', type: 'keyword' },
    })])).toEqual({ value: 'condensed' });
  });
  it('extracts percentage in the numeric branch (valid <percentage> per CSS Fonts 4)', () => {
    expect(extractFontStretch([p('FontStretch', {
      percentage: 110, original: { type: 'percent' },
    })])).toEqual({ value: '110%' });
  });
  it('applier is a deliberate no-op for cross-platform SSIM parity', () => {
    // Keyword and percentage configs alike must emit NO css — if this
    // starts failing because someone re-enabled emission, re-check that
    // iOS/Android grew real width-axis support first (FontStretchApplier.ts).
    expect(applyFontStretch({ value: 'condensed' })).toEqual({});
    expect(applyFontStretch({ value: '110%' })).toEqual({});
  });
});

describe('FontKerning + FontOpticalSizing', () => {
  it('kerning -> auto', () => {
    expect(applyFontKerning(extractFontKerning([p('FontKerning', 'AUTO')]))).toEqual({ fontKerning: 'auto' });
  });
  it('kerning -> none', () => {
    expect(applyFontKerning(extractFontKerning([p('FontKerning', 'NONE')]))).toEqual({ fontKerning: 'none' });
  });
  it('optical sizing -> auto', () => {
    expect(applyFontOpticalSizing(extractFontOpticalSizing([p('FontOpticalSizing', 'AUTO')])))
      .toEqual({ fontOpticalSizing: 'auto' });
  });
});

describe('FontFeatureSettings', () => {
  it('emits normal', () => {
    expect(applyFontFeatureSettings(extractFontFeatureSettings([p('FontFeatureSettings', { type: 'normal' })])))
      .toEqual({ fontFeatureSettings: 'normal' });
  });
  it('quotes tag and emits value', () => {
    expect(applyFontFeatureSettings(extractFontFeatureSettings([p('FontFeatureSettings', {
      type: 'features', features: [{ tag: 'kern', value: 1 }],
    })]))).toEqual({ fontFeatureSettings: '"kern" 1' });
  });
  it('joins multiple features with comma', () => {
    expect(applyFontFeatureSettings(extractFontFeatureSettings([p('FontFeatureSettings', {
      type: 'features', features: [{ tag: 'liga', value: 1 }, { tag: 'dlig', value: 0 }, { tag: 'smcp', value: 1 }],
    })]))).toEqual({ fontFeatureSettings: '"liga" 1, "dlig" 0, "smcp" 1' });
  });
  it('omits absent value', () => {
    expect(applyFontFeatureSettings(extractFontFeatureSettings([p('FontFeatureSettings', {
      type: 'features', features: [{ tag: 'liga' }],
    })]))).toEqual({ fontFeatureSettings: '"liga"' });
  });
});

describe('FontVariationSettings', () => {
  it('emits normal', () => {
    expect(applyFontVariationSettings(extractFontVariationSettings([p('FontVariationSettings', { type: 'normal' })])))
      .toEqual({ fontVariationSettings: 'normal' });
  });
  it('quotes axis and emits value', () => {
    expect(applyFontVariationSettings(extractFontVariationSettings([p('FontVariationSettings', {
      type: 'variations', variations: [{ axis: 'wdth', value: 100 }],
    })]))).toEqual({ fontVariationSettings: '"wdth" 100' });
  });
});

describe('FontSizeAdjust', () => {
  it('none', () => {
    expect(applyFontSizeAdjust(extractFontSizeAdjust([p('FontSizeAdjust', { type: 'none' })])))
      .toEqual({ fontSizeAdjust: 'none' });
  });
  it('from-font', () => {
    expect(applyFontSizeAdjust(extractFontSizeAdjust([p('FontSizeAdjust', { type: 'from-font' })])))
      .toEqual({ fontSizeAdjust: 'from-font' });
  });
  it('numeric', () => {
    expect(applyFontSizeAdjust(extractFontSizeAdjust([p('FontSizeAdjust', { type: 'number', value: 0.5 })])))
      .toEqual({ fontSizeAdjust: 0.5 });
  });
  it('metric-value pair', () => {
    expect(applyFontSizeAdjust(extractFontSizeAdjust([p('FontSizeAdjust', { type: 'metric-value', metric: 'cap-height', value: 0.7 })])))
      .toEqual({ fontSizeAdjust: 'cap-height 0.7' });
  });
});

describe('FontPalette', () => {
  it('light', () => { expect(applyFontPalette(extractFontPalette([p('FontPalette', { type: 'light' })]))).toEqual({ fontPalette: 'light' }); });
  it('dark', () => { expect(applyFontPalette(extractFontPalette([p('FontPalette', { type: 'dark' })]))).toEqual({ fontPalette: 'dark' }); });
  it('custom --ident', () => {
    expect(applyFontPalette(extractFontPalette([p('FontPalette', { type: 'custom', name: '--accent' })])))
      .toEqual({ fontPalette: '--accent' });
  });
});

describe('FontLanguageOverride / FontDisplay / FontNamedInstance / FontSmooth', () => {
  it('language-override normal', () => {
    expect(applyFontLanguageOverride(extractFontLanguageOverride([p('FontLanguageOverride', { type: 'normal' })])))
      .toEqual({ fontLanguageOverride: 'normal' });
  });
  it('language-override tag is quoted', () => {
    expect(applyFontLanguageOverride(extractFontLanguageOverride([p('FontLanguageOverride', { type: 'language-tag', tag: 'ENG' })])))
      .toEqual({ fontLanguageOverride: '"ENG"' });
  });
  it('display swap', () => {
    expect(applyFontDisplay(extractFontDisplay([p('FontDisplay', 'SWAP')]))).toEqual({ fontDisplay: 'swap' });
  });
  it('named instance auto', () => {
    expect(applyFontNamedInstance(extractFontNamedInstance([p('FontNamedInstance', { type: 'auto' })])))
      .toEqual({ fontNamedInstance: 'auto' });
  });
  it('named instance named', () => {
    expect(applyFontNamedInstance(extractFontNamedInstance([p('FontNamedInstance', { type: 'named', name: 'Bold' })])))
      .toEqual({ fontNamedInstance: '"Bold"' });
  });
  it('font-smooth passthrough', () => {
    expect(applyFontSmooth(extractFontSmooth([p('FontSmooth', 'ALWAYS')]))).toEqual({ fontSmooth: 'always' });
  });
});

describe('FontMinSize / FontMaxSize', () => {
  it('min-size px', () => {
    expect(applyFontMinSize(extractFontMinSize([p('FontMinSize', { type: 'length', px: 10 })])))
      .toEqual({ fontMinSize: '10px' });
  });
  it('min-size none', () => {
    expect(applyFontMinSize(extractFontMinSize([p('FontMinSize', { type: 'none' })])))
      .toEqual({ fontMinSize: 'none' });
  });
  it('max-size infinity', () => {
    expect(applyFontMaxSize(extractFontMaxSize([p('FontMaxSize', { type: 'infinity' })])))
      .toEqual({ fontMaxSize: 'infinity' });
  });
  it('max-size em retains relative unit', () => {
    expect(applyFontMaxSize(extractFontMaxSize([p('FontMaxSize', { type: 'length', original: { v: 3.0, u: 'EM' } })])))
      .toEqual({ fontMaxSize: '3em' });
  });
});

describe('FontVariant family', () => {
  it('variant-caps small-caps', () => {
    expect(applyFontVariantCaps(extractFontVariantCaps([p('FontVariantCaps', 'SMALL_CAPS')])))
      .toEqual({ fontVariantCaps: 'small-caps' });
  });
  it('variant-numeric list', () => {
    expect(applyFontVariantNumeric(extractFontVariantNumeric([p('FontVariantNumeric', ['OLDSTYLE_NUMS', 'DIAGONAL_FRACTIONS'])])))
      .toEqual({ fontVariantNumeric: 'oldstyle-nums diagonal-fractions' });
  });
  it('variant-ligatures list', () => {
    expect(applyFontVariantLigatures(extractFontVariantLigatures([p('FontVariantLigatures', ['COMMON_LIGATURES', 'DISCRETIONARY_LIGATURES', 'CONTEXTUAL'])])))
      .toEqual({ fontVariantLigatures: 'common-ligatures discretionary-ligatures contextual' });
  });
  it('variant-east-asian list', () => {
    expect(applyFontVariantEastAsian(extractFontVariantEastAsian([p('FontVariantEastAsian', ['JIS04', 'FULL_WIDTH'])])))
      .toEqual({ fontVariantEastAsian: 'jis04 full-width' });
  });
  it('variant-position super', () => {
    expect(applyFontVariantPosition(extractFontVariantPosition([p('FontVariantPosition', 'SUPER')])))
      .toEqual({ fontVariantPosition: 'super' });
  });
  it('variant-alternates historical-forms', () => {
    expect(applyFontVariantAlternates(extractFontVariantAlternates([p('FontVariantAlternates', ['HISTORICAL_FORMS'])])))
      .toEqual({ fontVariantAlternates: 'historical-forms' });
  });
  it('variant-emoji emoji', () => {
    expect(applyFontVariantEmoji(extractFontVariantEmoji([p('FontVariantEmoji', 'EMOJI')])))
      .toEqual({ fontVariantEmoji: 'emoji' });
  });
  it('synthesis-weight none', () => {
    expect(applyFontSynthesisWeight(extractFontSynthesisWeight([p('FontSynthesisWeight', 'NONE')])))
      .toEqual({ fontSynthesisWeight: 'none' });
  });
});
