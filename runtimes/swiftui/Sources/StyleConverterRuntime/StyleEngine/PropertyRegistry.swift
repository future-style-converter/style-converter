// PropertyRegistry.swift
// iOS StyleEngine — Phase 0 scaffolding.
//
// Purpose: a dispatch shell that maps `IRProperty.type` strings to dedicated
// extractor functions once they are migrated out of the monolithic
// `StyleBuilder.build(from:)` path.
//
// In Phase 0 this registry is intentionally empty: every property still flows
// through the legacy `StyleBuilder`, and `isLegacy(_:)` returns `true` for
// every input. Future phases will add entries to `migrated` and wire real
// extractors here, without forking the renderer dispatch.
//
// See `CLAUDE.md` → *Per-property contract* for the migration rules.

// Foundation gives us `Set<String>`, the only type we need at this stage.
import Foundation

/// Maps `IRProperty.type` → a typed piece of the `ComponentStyle` output.
///
/// Phase 0 scaffold: all properties still flow through the legacy
/// `StyleBuilder.build(from:)` monolith; this registry exists so future
/// phases can migrate properties one at a time without forking the
/// dispatch path.
///
/// See `CLAUDE.md` → *Per-property contract* for the migration contract.
enum PropertyRegistry {

    // MARK: - Migration ledger

    /// Property-type names that have been migrated out of `StyleBuilder`
    /// and into dedicated `{Property}Extractor.swift` files under
    /// `StyleEngine/{category}/`. Empty in Phase 0; filled by later phases.
    ///
    /// When a property is added here, the renderer will route its IR through
    /// the corresponding extractor instead of the legacy `StyleBuilder` path.
    /// Until then, `isLegacy(_:)` returns `true` for every property type.
    static let migrated: Set<String> = Set<String>([
        // Phase 2 — spacing family. Padding/Margin physical+logical, Gap
        // longhands, MarginTrim. See runtimes/swiftui/.../StyleEngine/spacing/.
        "PaddingTop", "PaddingRight", "PaddingBottom", "PaddingLeft",
        "PaddingBlockStart", "PaddingBlockEnd",
        "PaddingInlineStart", "PaddingInlineEnd",
        "MarginTop", "MarginRight", "MarginBottom", "MarginLeft",
        "MarginBlockStart", "MarginBlockEnd",
        "MarginInlineStart", "MarginInlineEnd",
        "Gap", "RowGap", "ColumnGap",
        "MarginTrim",
        // Phase 3 — sizing family. Physical + logical sizing + aspect-ratio.
        // See runtimes/swiftui/.../StyleEngine/sizing/.
        "Width", "Height",
        "MinWidth", "MaxWidth", "MinHeight", "MaxHeight",
        "AspectRatio",
        "BlockSize", "InlineSize",
        "MinBlockSize", "MaxBlockSize",
        "MinInlineSize", "MaxInlineSize",
        // Phase 4 — colour + background + blend + isolation families.
        // See runtimes/swiftui/.../StyleEngine/{color,background,effects/blend,
        // performance}/.
        "BackgroundColor", "Color", "Opacity", "AccentColor", "CaretColor",
        // CSS Color HDR 1 §3.1 dynamic-range-limit. iOS: SwiftUI does
        // not expose HDR/SDR clamping at the View level so we accept it
        // for IR-coverage purposes only — the applier is identity until
        // CALayer.wantsExtendedDynamicRangeContent gating lands.
        "DynamicRangeLimit",
        "BackgroundImage",
        "BackgroundSize",
        "BackgroundPosition", "BackgroundPositionX", "BackgroundPositionY",
        // Logical-axis variants — folded onto Y/X in LTR horizontal mode
        // by BackgroundPositionExtractor.
        "BackgroundPositionBlock", "BackgroundPositionInline",
        "BackgroundRepeat",
        "BackgroundClip",
        "BackgroundOrigin",
        "BackgroundAttachment",
        "MixBlendMode", "BackgroundBlendMode",
        "Isolation",
        // Phase 5 — border family. Sides (physical + logical widths, colours,
        // styles + the three shorthand longhands), all eight radius corners,
        // outline quartet, border-image quintet, BoxShadow, and the keyword-
        // only miscellanies (BoxDecorationBreak, CornerShape, BorderBoundary).
        // See runtimes/swiftui/.../StyleEngine/borders/ and effects/shadow/.
        // Sides — shorthand longhands + 4 physical × 3 + 4 logical × 3 = 27.
        "BorderWidth", "BorderColor", "BorderStyle",
        "BorderTopWidth", "BorderTopColor", "BorderTopStyle",
        "BorderRightWidth", "BorderRightColor", "BorderRightStyle",
        "BorderBottomWidth", "BorderBottomColor", "BorderBottomStyle",
        "BorderLeftWidth", "BorderLeftColor", "BorderLeftStyle",
        "BorderBlockStartWidth", "BorderBlockStartColor", "BorderBlockStartStyle",
        "BorderBlockEndWidth",   "BorderBlockEndColor",   "BorderBlockEndStyle",
        "BorderInlineStartWidth", "BorderInlineStartColor", "BorderInlineStartStyle",
        "BorderInlineEndWidth",   "BorderInlineEndColor",   "BorderInlineEndStyle",
        // Radius — 4 physical + 4 logical = 8.
        "BorderTopLeftRadius", "BorderTopRightRadius",
        "BorderBottomRightRadius", "BorderBottomLeftRadius",
        "BorderStartStartRadius", "BorderStartEndRadius",
        "BorderEndEndRadius",   "BorderEndStartRadius",
        // Outline — 4.
        "OutlineWidth", "OutlineStyle", "OutlineColor", "OutlineOffset",
        // Border-image — 5.
        "BorderImageSource", "BorderImageSlice", "BorderImageWidth",
        "BorderImageOutset", "BorderImageRepeat",
        // BoxShadow — 1 (lives under effects/shadow/).
        "BoxShadow",
        // Miscellaneous keyword-only — 3.
        "BoxDecorationBreak", "CornerShape", "BorderBoundary",
        // Phase 6 — typography. Every rendering-capable triplet under
        // StyleEngine/typography/{font,font-variant,line,spacing,
        // decoration,wrapping,writing,other} is listed explicitly; the
        // five "unsupported" grouped extractors contribute their
        // {Group}Property.names lists via Set-union at the bottom.
        //
        // font/ (9)
        "FontSize", "FontFamily", "FontWeight", "FontStyle", "FontStretch",
        "FontFeatureSettings", "FontVariationSettings",
        "FontKerning", "FontOpticalSizing",
        // font-variant/ (7)
        "FontVariantCaps", "FontVariantNumeric", "FontVariantLigatures",
        "FontVariantEastAsian", "FontVariantPosition", "FontVariantAlternates",
        "FontVariantEmoji",
        // line/ (3)
        "LineHeight", "LineClamp", "MaxLines",
        // spacing/ (4)
        "LetterSpacing", "WordSpacing", "TabSize", "TextIndent",
        // decoration/ (8)
        "TextDecorationLine", "TextDecorationStyle", "TextDecorationColor",
        "TextDecorationThickness", "TextUnderlineOffset", "TextUnderlinePosition",
        "TextShadow", "TextTransform",
        // wrapping/ (11)
        "TextAlign", "TextAlignLast", "TextJustify", "TextWrap",
        "WhiteSpace", "WordBreak", "OverflowWrap", "LineBreak",
        "Hyphens", "HyphenateCharacter", "TextOverflow",
        // writing/ (5)
        "Direction", "UnicodeBidi", "WritingMode", "TextOrientation", "VerticalAlign",
        // other/ (2)
        "Quotes", "TextRendering",
    ])
    .union(UnsupportedSvgTypographyProperty.set)
    .union(UnsupportedPrintTypographyProperty.set)
    .union(UnsupportedRubyEmphasisProperty.set)
    .union(UnsupportedFontMetaProperty.set)
    .union(UnsupportedSpacingProperty.set)
    // Phase 7 — layout family (flexbox + grid + position + advanced +
    // root). Scaffold-only in step 1: the 60 names below are registered
    // so the renderer ledger reflects ownership, but `LayoutExtractor`
    // currently returns nil and `LayoutApplier` is identity. Real
    // extractors/appliers land in Phase 7 steps 2-5. See
    // runtimes/swiftui/.../StyleEngine/layout/LayoutExtractor.swift.
    .union(LayoutProperty.set)
    // Phase 8 — transforms, clip, visibility/overflow, filter, mask.
    // Each enum centralises its owned property-type names so the
    // extractors and self-tests stay in lockstep with this registry.
    .union(TransformsProperty.set)
    .union(ClipProperty.set)
    .union(VisibilityProperty.set)
    .union(FilterProperty.set)
    .union(MaskProperty.set)
    // Phase 9 — animations + transitions + view-timeline + view-transition
    // + timeline-scope (22 owned names) and the 3 scroll-timeline longhands.
    // Extractors live at StyleEngine/animations/ and StyleEngine/scrolling/;
    // appliers are identity in this phase (see AnimationsApplier.swift).
    .union(AnimationsProperty.set)
    .union(ScrollTimelineProperty.set)
    // Phase 10 — long-tail sweep (~22 categories, ~150 property names).
    // Each category owns a grouped Config/Extractor/Applier triplet under
    // StyleEngine/<category>/. Nearly all appliers are identity-with-TODO
    // because SwiftUI has no analog; a small SwiftUI-capable subset
    // (scroll-snap, overscroll-behavior, pointer-events:none, user-
    // select:none) is flagged in the per-category applier headers for a
    // future wiring pass. See examples/properties/README-phase10.md.
    .union(ScrollingProperty.set)
    .union(SvgProperty.set)
    .union(UnsupportedSpeechProperty.set)
    .union(RenderingProperty.set)
    .union(UnsupportedPrintProperty.set)
    .union(UnsupportedPagingProperty.set)
    .union(UnsupportedRegionsProperty.set)
    .union(InteractionsProperty.set)
    .union(PerformanceProperty.set)
    .union(ColumnsProperty.set)
    .union(TableProperty.set)
    .union(ShapesProperty.set)
    .union(RhythmProperty.set)
    .union(UnsupportedNavigationProperty.set)
    .union(ImagesProperty.set)
    .union(AppearanceProperty.set)
    .union(CountersProperty.set)
    .union(ListsProperty.set)
    .union(ContainerProperty.set)
    .union(UnsupportedMathProperty.set)
    .union(ExperimentalProperty.set)
    .union(ContentProperty.set)
    .union(GlobalProperty.set)

    // MARK: - Query helpers

    /// Returns `true` when the given IR property type is still served by
    /// the legacy `StyleBuilder`. The renderer uses this during transition
    /// to decide whether to dispatch to `StyleBuilder.build(from:)` or to a
    /// migrated extractor. In Phase 0 this is always `true`.
    ///
    /// - Parameter propertyType: The IR property `type` field (e.g. `"width"`,
    ///   `"background-color"`). Compared against `migrated` verbatim.
    /// - Returns: `true` if the property has not been migrated yet.
    static func isLegacy(_ propertyType: String) -> Bool {
        // `contains` is O(1) on `Set<String>`; invert because `migrated` is
        // the positive set.
        !migrated.contains(propertyType)
    }

    /// Count of migrated properties — exposed for the coverage report /
    /// rollout dashboard. Zero in Phase 0.
    static var migratedCount: Int {
        // Set.count is O(1).
        migrated.count
    }
}
