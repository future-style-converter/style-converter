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
// UIKit only for UIFont metric introspection in the font-size-adjust
// resolution below (capHeight/xHeight of the bundled reference face).
import UIKit

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

        // Lane IOS-TEXT fix 3 — resolve em/rem letter/word-spacing now
        // that the element's COMPUTED font size is known (css-values-4
        // §6.1: em on a non-font-size property refers to the element's
        // own font size; rem to the 16px harness root — the same bases
        // Compose's extractLetterSpacing workaround uses). Runs BEFORE
        // the font-size-adjust scale below because em tracks the
        // computed size, not the adjust-scaled USED size (§4.6).
        if let rel = agg.letterSpacingRelative {
            agg.letterSpacingPx = rel.value * (rel.isRem ? 16 : (agg.fontSizePx ?? 16))
        }
        if let rel = agg.wordSpacingRelative {
            agg.wordSpacingPx = rel.value * (rel.isRem ? 16 : (agg.fontSizePx ?? 16))
        }

        // Fidelity wave 2 — `font-size-adjust` (css-fonts-4 §4.6): the
        // used font size is scaled so the chosen metric hits the
        // requested ratio: used = size × value / metricRatio(font).
        // The web reference applies this natively (Typography_C04's
        // `cap-height 0.7` renders 28px Inter at ≈26.9px); iOS ignored
        // it and drew visibly larger glyphs.
        if let factor = fontSizeAdjustFactor(from: properties) {
            // No explicit font-size still adjusts the 16px default.
            agg.fontSizePx = (agg.fontSizePx ?? 16) * factor
            agg.touched = true
        }

        // Return nil when no Applier flipped `touched` — lets the caller
        // skip TypographyApplier entirely. Non-touching appliers (stretch,
        // optical-sizing, unsupported groups) intentionally don't set it.
        return agg.touched ? agg : nil
    }

    // MARK: - font-size-adjust resolution (fidelity wave 2)

    /// Scale factor for `font-size-adjust: [<metric>] <number>`, or nil
    /// when absent / `none` / an unsupported metric. IR shape:
    /// `{"type":"metric-value","metric":"cap-height","value":0.7}` (the
    /// one-value ex-height form ships without the metric key).
    static func fontSizeAdjustFactor(from properties: [IRProperty]) -> CGFloat? {
        for prop in properties where prop.type == "FontSizeAdjust" {
            // Keyword forms (`none`, `from-font`) have no numeric value.
            guard case .object(let o) = prop.data,
                  let v = o["value"]?.doubleValue, v > 0 else { continue }
            // Default metric per spec grammar is ex-height.
            let metric = o["metric"]?.stringValue?.lowercased() ?? "ex-height"
            let ratio: CGFloat?
            switch metric {
            case "cap-height": ratio = referenceMetricRatio(\.capHeight, fallback: 0.7275)
            case "ex-height":  ratio = referenceMetricRatio(\.xHeight,  fallback: 0.5459)
            default:           ratio = nil  // ch-width / ic-* unsupported
            }
            guard let r = ratio, r > 0 else { continue }
            return CGFloat(v) / r
        }
        return nil
    }

    /// Metric-to-em ratio of the harness reference face (bundled Inter),
    /// probed live via UIFont so the value tracks the exact font tables
    /// the render uses. Falls back to Inter 4.0's OS/2 constants
    /// (capHeight 1490/2048, xHeight 1118/2048) when the face isn't
    /// registered — e.g. in the unit-test bundle — keeping tests
    /// deterministic.
    private static func referenceMetricRatio(_ metric: KeyPath<UIFont, CGFloat>,
                                             fallback: CGFloat) -> CGFloat {
        if let f = UIFont(name: "Inter", size: 100) {
            return f[keyPath: metric] / 100
        }
        return fallback
    }
}
