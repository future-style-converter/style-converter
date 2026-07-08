//
//  TypographyExtractor.swift
//  StyleEngine/typography — Phase 6.
//
//  Facade over every typography triplet extractor. Walks the property
//  list once per triplet, calls the corresponding Applier.contribute to
//  fold the result into the shared TypographyAggregate, and returns the
//  aggregate. `StyleBuilder.applyStyle(_:)` then hands the aggregate
//  exactly once to `TypographyApplier`.
//
//  Keeping all extractors wired here (instead of a dispatch table keyed
//  on property name) lets the Swift compiler type-check every entry and
//  makes the coverage of Phase 6 self-evident at a glance.
//

import Foundation

enum TypographyExtractor {

    /// Runs every typography extractor and folds them into one aggregate.
    /// Returns nil when no typography property was seen — this lets
    /// TypographyApplier short-circuit and leaves the environment defaults
    /// untouched.
    static func extract(from properties: [IRProperty]) -> TypographyAggregate? {
        var agg = TypographyAggregate()

        // MARK: font/ (9 triplets)
        FontSizeApplier.contribute(FontSizeExtractor.extract(from: properties), into: &agg)
        FontFamilyApplier.contribute(FontFamilyExtractor.extract(from: properties), into: &agg)
        FontWeightApplier.contribute(FontWeightExtractor.extract(from: properties), into: &agg)
        FontStyleApplier.contribute(FontStyleExtractor.extract(from: properties), into: &agg)
        FontStretchApplier.contribute(FontStretchExtractor.extract(from: properties), into: &agg)
        FontFeatureSettingsApplier.contribute(FontFeatureSettingsExtractor.extract(from: properties), into: &agg)
        FontVariationSettingsApplier.contribute(FontVariationSettingsExtractor.extract(from: properties), into: &agg)
        FontKerningApplier.contribute(FontKerningExtractor.extract(from: properties), into: &agg)
        FontOpticalSizingApplier.contribute(FontOpticalSizingExtractor.extract(from: properties), into: &agg)

        // MARK: font-variant/ (7 triplets — 6 share FontVariantKeywordListConfig)
        FontVariantCapsApplier.contribute(FontVariantCapsExtractor.extract(from: properties), into: &agg)
        FontVariantNumericApplier.contribute(FontVariantNumericExtractor.extract(from: properties), into: &agg)
        FontVariantLigaturesApplier.contribute(FontVariantLigaturesExtractor.extract(from: properties), into: &agg)
        FontVariantEastAsianApplier.contribute(FontVariantEastAsianExtractor.extract(from: properties), into: &agg)
        FontVariantPositionApplier.contribute(FontVariantPositionExtractor.extract(from: properties), into: &agg)
        FontVariantAlternatesApplier.contribute(FontVariantAlternatesExtractor.extract(from: properties), into: &agg)
        FontVariantEmojiApplier.contribute(FontVariantEmojiExtractor.extract(from: properties), into: &agg)

        // MARK: line/ (3 triplets)
        LineHeightApplier.contribute(LineHeightExtractor.extract(from: properties), into: &agg)
        LineClampApplier.contribute(LineClampExtractor.extract(from: properties), into: &agg)
        MaxLinesApplier.contribute(MaxLinesExtractor.extract(from: properties), into: &agg)

        // MARK: spacing/ (4 triplets)
        LetterSpacingApplier.contribute(LetterSpacingExtractor.extract(from: properties), into: &agg)
        WordSpacingApplier.contribute(WordSpacingExtractor.extract(from: properties), into: &agg)
        TabSizeApplier.contribute(TabSizeExtractor.extract(from: properties), into: &agg)
        TextIndentApplier.contribute(TextIndentExtractor.extract(from: properties), into: &agg)

        // MARK: decoration/ (8 triplets)
        TextDecorationLineApplier.contribute(TextDecorationLineExtractor.extract(from: properties), into: &agg)
        TextDecorationStyleApplier.contribute(TextDecorationStyleExtractor.extract(from: properties), into: &agg)
        TextDecorationColorApplier.contribute(TextDecorationColorExtractor.extract(from: properties), into: &agg)
        TextDecorationThicknessApplier.contribute(TextDecorationThicknessExtractor.extract(from: properties), into: &agg)
        TextUnderlineOffsetApplier.contribute(TextUnderlineOffsetExtractor.extract(from: properties), into: &agg)
        TextUnderlinePositionApplier.contribute(TextUnderlinePositionExtractor.extract(from: properties), into: &agg)
        TextShadowApplier.contribute(TextShadowExtractor.extract(from: properties), into: &agg)
        TextTransformApplier.contribute(TextTransformExtractor.extract(from: properties), into: &agg)

        // MARK: wrapping/ (11 triplets)
        TextAlignApplier.contribute(TextAlignExtractor.extract(from: properties), into: &agg)
        TextAlignLastApplier.contribute(TextAlignLastExtractor.extract(from: properties), into: &agg)
        TextJustifyApplier.contribute(TextJustifyExtractor.extract(from: properties), into: &agg)
        TextWrapApplier.contribute(TextWrapExtractor.extract(from: properties), into: &agg)
        WhiteSpaceApplier.contribute(WhiteSpaceExtractor.extract(from: properties), into: &agg)
        WordBreakApplier.contribute(WordBreakExtractor.extract(from: properties), into: &agg)
        OverflowWrapApplier.contribute(OverflowWrapExtractor.extract(from: properties), into: &agg)
        LineBreakApplier.contribute(LineBreakExtractor.extract(from: properties), into: &agg)
        HyphensApplier.contribute(HyphensExtractor.extract(from: properties), into: &agg)
        HyphenateCharacterApplier.contribute(HyphenateCharacterExtractor.extract(from: properties), into: &agg)
        TextOverflowApplier.contribute(TextOverflowExtractor.extract(from: properties), into: &agg)

        // MARK: writing/ (5 triplets)
        DirectionApplier.contribute(DirectionExtractor.extract(from: properties), into: &agg)
        UnicodeBidiApplier.contribute(UnicodeBidiExtractor.extract(from: properties), into: &agg)
        WritingModeApplier.contribute(WritingModeExtractor.extract(from: properties), into: &agg)
        TextOrientationApplier.contribute(TextOrientationExtractor.extract(from: properties), into: &agg)
        VerticalAlignApplier.contribute(VerticalAlignExtractor.extract(from: properties), into: &agg)

        // MARK: other/ (2 triplets)
        QuotesApplier.contribute(QuotesExtractor.extract(from: properties), into: &agg)
        TextRenderingApplier.contribute(TextRenderingExtractor.extract(from: properties), into: &agg)

        // MARK: unsupported/ (5 grouped triplets — sweep the long tail)
        UnsupportedSvgTypographyApplier.contribute(UnsupportedSvgTypographyExtractor.extract(from: properties), into: &agg)
        UnsupportedPrintTypographyApplier.contribute(UnsupportedPrintTypographyExtractor.extract(from: properties), into: &agg)
        UnsupportedRubyEmphasisApplier.contribute(UnsupportedRubyEmphasisExtractor.extract(from: properties), into: &agg)
        UnsupportedFontMetaApplier.contribute(UnsupportedFontMetaExtractor.extract(from: properties), into: &agg)
        UnsupportedSpacingApplier.contribute(UnsupportedSpacingExtractor.extract(from: properties), into: &agg)

        // Resolve unitless `line-height: <multiplier>` against the
        // (now-known) font-size. CSS uses 16px as the inherited body
        // default when no font-size lands; mirror that here so plain
        // `line-height: 2` still reports a sensible absolute height.
        if agg.lineHeightPx == nil, let mult = agg.lineHeightMultiplier {
            agg.lineHeightPx = mult * (agg.fontSizePx ?? 16)
        }

        // Return nil when no Applier flipped `touched` — lets the caller
        // skip TypographyApplier entirely. Non-touching appliers (stretch,
        // optical-sizing, unsupported groups) intentionally don't set it.
        return agg.touched ? agg : nil
    }
}
